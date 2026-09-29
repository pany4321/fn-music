// SPDX-License-Identifier: GPL-3.0-only
// Matching behavior is derived from LDDC, commit 1ffa0e25426e654376e5d55d854b135ae601f43b.
package com.fnmusic.tv.core.lyrics

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class LyricsMatchPolicy(
    val minimumScore: Double = 80.0,
    val minimumTitleScore: Double = 50.0,
    /**
     * 本地与候选的时长容差：**固定 ±5 秒**。
     *
     * 曾按"长曲放宽 1%"改过（理由是 VBR/母带差异），自审后撤回：时长的绝对差才是判别力所在 ——
     * 同一录音的容器/编码差异最多一两秒（±5 秒足够），而**长曲上几秒到几十秒的差几乎一定意味着
     * 不同版本**（不同演出、剪辑、淡出）。按 1% 放宽等于让 60 分钟的曲子接受 36 秒偏差，而古典/民乐
     * 不同演的内部时间轴差异是分钟级的，总时长相近也毫无保证。实测该改动在真实曲库上零收益。
     */
    val maximumDurationDeltaMs: Long = 5_000L,
    val maximumWordTimingDeltaMs: Long = 2_000L,
    /** 兜底闸门：标题几乎一致时所需的标题分。 */
    val highTitleScore: Double = 95.0,
    /**
     * 兜底闸门允许的时长差：比 [maximumDurationDeltaMs] 更严，避免不同版本被误放行。
     */
    val strictDurationDeltaMs: Long = 2_000L,
    /**
     * 同分结果的来源优先序。LRCLIB 排在最后：它的歌词多为繁体、没有翻译与逐字，
     * 只有在中文平台都给不出结果时才由它兜底（它也是四家里唯一有公开文档的接口）。
     */
    val sourceOrder: List<LyricsSourceId> = listOf(
        LyricsSourceId.Netease,
        LyricsSourceId.QqMusic,
        LyricsSourceId.Kugou,
        LyricsSourceId.Lrclib,
    ),
)

data class ScoredLyricsCandidate(
    val candidate: LyricsCandidate,
    val score: Double,
    val titleScore: Double,
    val consensusCount: Int,
    val metadataScore: Double = score,
    val durationDeltaMs: Long? = null,
)

class LyricsCandidateScorer(
    private val policy: LyricsMatchPolicy = LyricsMatchPolicy(),
) {
    fun score(request: LyricsMatchRequest, candidates: List<LyricsCandidate>): List<ScoredLyricsCandidate> {
        val eligible = candidates.mapNotNull { scoreOne(request, it) }
        if (eligible.isEmpty()) return emptyList()
        return eligible.map { scored ->
            val consensus = eligible.asSequence()
                .filter { other ->
                    other.candidate.source != scored.candidate.source &&
                        sameRecording(scored.candidate, other.candidate)
                }
                .map { it.candidate.source }
                .distinct()
                .count() + 1
            scored.copy(
                score = (scored.score + (consensus - 1).coerceAtMost(2) * 1.5).coerceAtMost(100.0),
                consensusCount = consensus,
            )
        }.filter { it.passesGate(policy) }
            .sortedWith(
                compareByDescending<ScoredLyricsCandidate> { it.score }
                    .thenBy { policy.sourceOrder.indexOf(it.candidate.source).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
            )
    }

    /**
     * 入选闸门。
     *
     * 常规路径要求「标题 + 歌手」加权分够高；但合辑、古典、民族器乐这类曲库里，本地歌手标签
     * 常常与在线库对不上（群星 / 演奏者 vs 作曲家 / 中英文名混用），实测这是第二大漏配原因。
     * 因此增加一条**更严格时长**的兜底：标题几乎完全一致（≥[LyricsMatchPolicy.highTitleScore]）
     * 且时长差 ≤[LyricsMatchPolicy.strictDurationDeltaMs] 时放行 —— 标题与时长同时吻合已是很强的
     * 证据，不因歌手串对不上而丢弃（版本冲突仍在 [scoreOne] 里提前挡掉）。
     *
     * 试过把兜底再放宽成"时长吻合 + 综合分 ≥70 即可"：60 首真实曲库上**净增 0 首**（41/60 → 41/60），
     * 没有任何原本被拒的候选因此进来 —— 这套曲库的漏配主因是"在线库里没有同一个录音"，不是门槛
     * 太严。零收益却要承担误配风险，故撤回。
     */
    private fun ScoredLyricsCandidate.passesGate(policy: LyricsMatchPolicy): Boolean {
        if (score >= policy.minimumScore && titleScore >= policy.minimumTitleScore) return true
        val delta = durationDeltaMs ?: return false
        return titleScore >= policy.highTitleScore && delta <= policy.strictDurationDeltaMs
    }

    private fun scoreOne(request: LyricsMatchRequest, candidate: LyricsCandidate): ScoredLyricsCandidate? {
        if (candidate.title.isBlank() || request.title.isBlank()) return null
        val durationDelta = knownDurationDelta(request.durationMs, candidate.durationMs)
        if (durationDelta != null && durationDelta > policy.maximumDurationDeltaMs) return null
        if (hasHardVersionConflict(request.title, candidate.title, candidate.instrumental)) return null

        val titleScore = titleScore(request.title, candidate.title)
        val artistScore = artistScore(request.artists, candidate.artists)
        val albumScore = request.album?.takeIf(String::isNotBlank)?.let { local ->
            candidate.album?.takeIf(String::isNotBlank)?.let { remote -> sequenceRatio(normalize(local), normalize(remote)) * 100.0 }
        }
        val score = when {
            request.artists.isNotEmpty() && candidate.artists.isNotEmpty() && albumScore != null -> max(
                titleScore * 0.5 + artistScore * 0.5,
                titleScore * 0.5 + artistScore * 0.35 + albumScore * 0.15,
            )
            request.artists.isNotEmpty() && candidate.artists.isNotEmpty() ->
                titleScore * 0.5 + artistScore * 0.5
            albumScore != null -> max(titleScore * 0.7 + albumScore * 0.3, titleScore * 0.8)
            else -> titleScore
        }
        return ScoredLyricsCandidate(candidate, score, titleScore, consensusCount = 1, durationDeltaMs = durationDelta)
    }

    private fun sameRecording(left: LyricsCandidate, right: LyricsCandidate): Boolean {
        if (titleScore(left.title, right.title) < 92.0) return false
        if (artistScore(left.artists, right.artists) < 85.0) return false
        return knownDurationDelta(left.durationMs, right.durationMs)?.let { it <= policy.maximumDurationDeltaMs } ?: true
    }

    private fun titleScore(left: String, right: String): Double {
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)
        if (normalizedLeft == normalizedRight) return 100.0
        val full = sequenceRatio(normalizedLeft, normalizedRight) * 100.0
        val baseLeft = stripVersionTags(normalizedLeft)
        val baseRight = stripVersionTags(normalizedRight)
        val base = sequenceRatio(baseLeft, baseRight) * 95.0
        return max(full, base)
    }

    /**
     * 歌手分：**以较短一方为分母**。
     *
     * 早先用 `max(local.size, remote.size)` 作分母是个结构性错误：在线库常常只列主唱，而本地标签
     * 是「A / B / C」（或反过来，在线列一串 featuring 而本地只有主唱）。此时**即使主唱完全一致，
     * 分数也会被钉死在 33 分以下**，配合权重后必然低于入选门槛 —— 实测这是仅次于"版本时长不同"
     * 的漏配原因。改为除以较短一方后，语义变成"较短列表能被较长列表容纳得多好"，主唱一致即得满分。
     */
    private fun artistScore(left: List<String>, right: List<String>): Double {
        val local = splitArtists(left)
        val remote = splitArtists(right)
        if (local.isEmpty() || remote.isEmpty()) return 0.0
        val shorter = if (local.size <= remote.size) local else remote
        val longer = if (local.size <= remote.size) remote else local
        val used = mutableSetOf<Int>()
        var total = 0.0
        shorter.forEach { artist ->
            val best = longer.withIndex()
                .filterNot { it.index in used }
                .maxByOrNull { sequenceRatio(artist, it.value) }
            if (best != null) {
                used += best.index
                total += sequenceRatio(artist, best.value)
            }
        }
        return total / shorter.size * 100.0
    }

    private fun splitArtists(values: List<String>): List<String> = values
        .flatMap { it.split(ARTIST_SEPARATOR) }
        .map { normalize(it.replace(FEAT_PREFIX, "")) }
        .filter(String::isNotBlank)
        .distinct()

    private fun hasHardVersionConflict(local: String, remote: String, remoteInstrumental: Boolean): Boolean {
        val localFlags = versionFlags(local)
        val remoteFlags = versionFlags(remote).toMutableSet().apply {
            if (remoteInstrumental) add(VersionFlag.Instrumental)
        }
        if (VersionFlag.Instrumental !in localFlags && VersionFlag.Instrumental in remoteFlags) return true
        return listOf(
            VersionFlag.Live,
            VersionFlag.Remix,
            VersionFlag.Acoustic,
            VersionFlag.Cover,
            VersionFlag.Edit,
            VersionFlag.Solo,
            VersionFlag.Short,
        ).any { flag -> (flag in localFlags) != (flag in remoteFlags) }
    }

    private fun versionFlags(value: String): Set<VersionFlag> {
        val normalized = normalize(value)
        return VersionFlag.entries.filterTo(mutableSetOf()) { it.pattern.containsMatchIn(normalized) }
    }

    private fun stripVersionTags(value: String): String = VersionFlag.entries
        .fold(value) { text, flag -> flag.pattern.replace(text, " ") }
        .replace(VERSION_DECORATION, " ")
        .replace(WHITESPACE, " ")
        .trim()

    private enum class VersionFlag(val pattern: Regex) {
        Instrumental(Regex("(?:^|\\W)(?:inst(?:rumental)?|off\\s*vocal|伴奏|纯音乐)(?:$|\\W)")),
        Live(Regex("(?:^|\\W)(?:live|现场|演唱会)(?:$|\\W)")),
        // mix(?:ed)? 覆盖 "mixed"/"mixed ver"（LDDC 的标签归一化里有这一条，旧写法漏掉了）
        Remix(Regex("(?:^|\\W)(?:remix|mix(?:ed)?|混音)(?:$|\\W)")),
        Acoustic(Regex("(?:^|\\W)(?:acoustic|unplugged|不插电)(?:$|\\W)")),
        Cover(Regex("(?:^|\\W)(?:cover|翻唱)(?:$|\\W)")),
        // LDDC 的标签词表补进来的三类：编辑版 / 独唱版 / 电视与动画时长版
        Edit(Regex("(?:^|\\W)(?:edit(?:ed)?|剪?辑版)(?:$|\\W)")),
        Solo(Regex("(?:^|\\W)(?:solo|独唱版)(?:$|\\W)")),
        Short(Regex("(?:^|\\W)(?:tv\\s*size|anime\\s*size|radio\\s*edit|short\\s*ver|サイズ)(?:$|\\W)")),
    }

    companion object {
        private val ARTIST_SEPARATOR = Regex("\\s*(?:/|、|,|，|&|＆|;|；|·|・|\\bfeat\\.?\\b|\\bft\\.?\\b)\\s*", RegexOption.IGNORE_CASE)
        private val FEAT_PREFIX = Regex("^(?:feat\\.?|ft\\.?)\\s*", RegexOption.IGNORE_CASE)
        private val VERSION_DECORATION = Regex("[()\\[\\]{}<>_-]+")
        private val WHITESPACE = Regex("\\s+")

        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace('（', '(')
            .replace('）', ')')
            .replace(WHITESPACE, " ")
            .trim()

        private fun knownDurationDelta(left: Long?, right: Long?): Long? =
            if (left != null && left > 0 && right != null && right > 0) abs(left - right) else null
    }
}

internal fun sequenceRatio(left: String, right: String): Double {
    if (left == right) return 1.0
    if (left.isEmpty() || right.isEmpty()) return 0.0
    val leftPoints = left.codePoints().toArray()
    val rightPoints = right.codePoints().toArray()
    val matches = matchingBlocks(leftPoints, 0, leftPoints.size, rightPoints, 0, rightPoints.size)
        .sumOf { it.size }
    return 2.0 * matches / (leftPoints.size + rightPoints.size)
}

private data class MatchBlock(val left: Int, val right: Int, val size: Int)

private fun matchingBlocks(
    left: IntArray,
    leftStart: Int,
    leftEnd: Int,
    right: IntArray,
    rightStart: Int,
    rightEnd: Int,
): List<MatchBlock> {
    val longest = longestMatch(left, leftStart, leftEnd, right, rightStart, rightEnd)
    if (longest.size == 0) return emptyList()
    val before = matchingBlocks(left, leftStart, longest.left, right, rightStart, longest.right)
    val after = matchingBlocks(
        left,
        longest.left + longest.size,
        leftEnd,
        right,
        longest.right + longest.size,
        rightEnd,
    )
    return before + longest + after
}

private fun longestMatch(
    left: IntArray,
    leftStart: Int,
    leftEnd: Int,
    right: IntArray,
    rightStart: Int,
    rightEnd: Int,
): MatchBlock {
    var bestLeft = leftStart
    var bestRight = rightStart
    var bestSize = 0
    var previous = mutableMapOf<Int, Int>()
    for (leftIndex in leftStart until leftEnd) {
        val current = mutableMapOf<Int, Int>()
        for (rightIndex in rightStart until rightEnd) {
            if (left[leftIndex] != right[rightIndex]) continue
            val size = (previous[rightIndex - 1] ?: 0) + 1
            current[rightIndex] = size
            if (size > bestSize) {
                bestLeft = leftIndex - size + 1
                bestRight = rightIndex - size + 1
                bestSize = size
            }
        }
        previous = current
    }
    return MatchBlock(bestLeft, bestRight, bestSize)
}

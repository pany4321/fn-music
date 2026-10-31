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
        val requestProjection = projectionOf(request)
        val eligible = candidates.mapNotNull { candidate ->
            val projection = projectionOf(candidate)
            val scored = scoreOne(requestProjection, projection) ?: return@mapNotNull null
            ScoredWithProjection(scored, projection)
        }
        if (eligible.isEmpty()) return emptyList()
        return eligible
            .map { entry ->
                val scored = entry.scored
                val consensus = eligible.asSequence()
                    .filter { other ->
                        other.scored.candidate.source != scored.candidate.source &&
                            sameRecording(entry.projection, other.projection)
                    }
                    .map { other -> other.scored.candidate.source }
                    .distinct()
                    .count() + 1
                ScoredWithProjection(
                    scored = scored.copy(
                        score = (scored.score + (consensus - 1).coerceAtMost(2) * 1.5).coerceAtMost(100.0),
                        consensusCount = consensus,
                    ),
                    projection = entry.projection,
                )
            }
            .filter { it.scored.passesGate(policy) }
            .map { it.scored }
            .sortedWith(
                compareByDescending<ScoredLyricsCandidate> { it.score }
                    .thenBy { policy.sourceOrder.indexOf(it.candidate.source).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE },
            )
    }

    /**
     * 「是否存在能过闸的候选」的廉价探测：命中首个合格候选即返回，不算 consensus、不排序。
     * 编排器的重试判空（LyricsMatchCoordinator 的两处"没有合格候选"检查）只需要布尔答案——
     * 此前每源每轮要为它跑最多 3 次完整 O(n²) 评分与排序。
     *
     * 注意与完整评分的边界：consensus 只会加分，所以本探测为 true 时完整评分必然过闸；
     * 反之不成立——压线候选靠 consensus 过闸时本探测为 false，编排器会多发一次重试请求，
     * 最终仍由完整评分正确放行。这是"省 O(n²)"与"罕见多余重试"之间的有意取舍。
     */
    fun hasEligible(request: LyricsMatchRequest, candidates: List<LyricsCandidate>): Boolean {
        val requestProjection = projectionOf(request)
        return candidates.any { candidate ->
            scoreOne(requestProjection, projectionOf(candidate))?.passesGate(policy) == true
        }
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
     *
     * consensus 只会加分不会减分，所以本闸门与 consensus 计算无关（[hasEligible] 的短路探测
     * 因此与完整评分的闸门结论一致）。
     */
    private fun ScoredLyricsCandidate.passesGate(policy: LyricsMatchPolicy): Boolean {
        if (score >= policy.minimumScore && titleScore >= policy.minimumTitleScore) return true
        val delta = durationDeltaMs ?: return false
        return titleScore >= policy.highTitleScore && delta <= policy.strictDurationDeltaMs
    }

    private fun scoreOne(
        request: NormalizedTrack,
        candidate: NormalizedTrack,
    ): ScoredLyricsCandidate? {
        if (candidate.title.isEmpty() || request.title.isEmpty()) return null
        val durationDelta = knownDurationDelta(request.durationMs, candidate.durationMs)
        if (durationDelta != null && durationDelta > policy.maximumDurationDeltaMs) return null
        if (hasHardVersionConflict(request.flags, candidate.flags, candidate.instrumental)) return null

        val titleScore = normalizedTitleScore(request.title, candidate.title)
        val artistScore = artistScore(request.artists, candidate.artists)
        val albumScore = request.album?.let { local ->
            candidate.album?.let { remote -> sequenceRatio(local, remote) * 100.0 }
        }
        val score = when {
            request.rawHasArtists && candidate.rawHasArtists && albumScore != null -> max(
                titleScore * 0.5 + artistScore * 0.5,
                titleScore * 0.5 + artistScore * 0.35 + albumScore * 0.15,
            )
            request.rawHasArtists && candidate.rawHasArtists ->
                titleScore * 0.5 + artistScore * 0.5
            albumScore != null -> max(titleScore * 0.7 + albumScore * 0.3, titleScore * 0.8)
            else -> titleScore
        }
        return ScoredLyricsCandidate(
            checkNotNull(candidate.candidate) { "scoreOne requires a candidate projection" },
            score,
            titleScore,
            consensusCount = 1,
            durationDeltaMs = durationDelta,
        )
    }

    private fun sameRecording(left: NormalizedTrack, right: NormalizedTrack): Boolean {
        if (normalizedTitleScore(left.title, right.title) < 92.0) return false
        if (artistScore(left.artists, right.artists) < 85.0) return false
        return knownDurationDelta(left.durationMs, right.durationMs)?.let { it <= policy.maximumDurationDeltaMs } ?: true
    }

    /** 入参必须是 [normalize] 之后的文本（投影阶段完成归一化，比对期不再重复调用）。 */
    private fun normalizedTitleScore(left: String, right: String): Double {
        if (left == right) return 100.0
        val full = sequenceRatio(left, right) * 100.0
        val base = sequenceRatio(stripVersionTags(left), stripVersionTags(right)) * 95.0
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
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val shorter = if (left.size <= right.size) left else right
        val longer = if (left.size <= right.size) right else left
        val used = mutableSetOf<Int>()
        var total = 0.0
        shorter.forEach { artist ->
            var bestIndex = -1
            var bestScore = -1.0
            longer.forEachIndexed { index, value ->
                if (index in used) return@forEachIndexed
                val ratio = sequenceRatio(artist, value)
                if (ratio > bestScore) {
                    bestScore = ratio
                    bestIndex = index
                }
            }
            if (bestIndex >= 0) {
                used += bestIndex
                total += bestScore
            }
        }
        return total / shorter.size * 100.0
    }

    private fun hasHardVersionConflict(
        localFlags: Set<VersionFlag>,
        remoteFlags: Set<VersionFlag>,
        remoteInstrumental: Boolean,
    ): Boolean {
        val remote = remoteFlags.toMutableSet().apply {
            if (remoteInstrumental) add(VersionFlag.Instrumental)
        }
        if (VersionFlag.Instrumental !in localFlags && VersionFlag.Instrumental in remote) return true
        return listOf(
            VersionFlag.Live,
            VersionFlag.Remix,
            VersionFlag.Acoustic,
            VersionFlag.Cover,
            VersionFlag.Edit,
            VersionFlag.Solo,
            VersionFlag.Short,
        ).any { flag -> (flag in localFlags) != (flag in remote) }
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
        // LDDC 的标签词表补进来的三类：编辑版 / 独唱版 / 电视与动画时长版。
        // 注意必须用完整词"剪辑版/编辑版"：写成 `剪?辑版` 会把「专辑版」的子串"辑版"也命中
        // （汉字在 Java 正则里都算 \W，边界条件总被满足），误杀大量「歌名（专辑版）」条目。
        Edit(Regex("(?:^|\\W)(?:edit(?:ed)?|剪辑版|编辑版)(?:$|\\W)")),
        Solo(Regex("(?:^|\\W)(?:solo|独唱版)(?:$|\\W)")),
        // サイズ 必须带 tv/アニメ 前缀：裸サイズ会误报任何含该词的日文歌名，
        // 而无前缀要求时「TVサイズ」（动画版标准写法，前导 v 是 \w）反而测不到。
        Short(Regex("(?:^|\\W)(?:tv\\s*size|anime\\s*size|radio\\s*edit|short\\s*ver|(?:tv|アニメ)\\s*サイズ)(?:$|\\W)")),
    }

    /**
     * 归一化投影：标题/歌手/专辑/版本词在**投影阶段各算一次**，比对期（scoreOne、consensus 的
     * sameRecording）只做纯字符串比较。此前一轮匹配里同一标题串会被 normalize 上万次
     * （consensus 全对全 O(n²)，每次比对都从 normalize 从头算）。
     *
     * 缓存键是数据类 [LyricsCandidate]（按值相等），跨 score()/hasEligible 调用复用——
     * 编排器对同一批候选的最多 3 次重复评分因此共享投影；容量守卫超出即清空。
     */
    private val projectionCache = java.util.concurrent.ConcurrentHashMap<LyricsCandidate, NormalizedTrack>()

    private fun projectionOf(candidate: LyricsCandidate): NormalizedTrack {
        if (projectionCache.size > PROJECTION_CACHE_LIMIT) projectionCache.clear()
        return projectionCache.getOrPut(candidate) {
            val title = normalize(candidate.title)
            NormalizedTrack(
                title = title,
                rawHasArtists = candidate.artists.isNotEmpty(),
                artists = splitArtists(candidate.artists),
                album = candidate.album?.takeIf(String::isNotBlank)
                    ?.let(::normalize)?.takeIf(String::isNotEmpty),
                flags = versionFlags(title),
                durationMs = candidate.durationMs,
                instrumental = candidate.instrumental,
                candidate = candidate,
            )
        }
    }

    /** 评分结果与其投影的配对（consensus 比对直接用投影，避免重复归一化）。 */
    private class ScoredWithProjection(
        val scored: ScoredLyricsCandidate,
        val projection: NormalizedTrack,
    )

    private fun projectionOf(request: LyricsMatchRequest): NormalizedTrack {
        val title = normalize(request.title)
        return NormalizedTrack(
            title = title,
            rawHasArtists = request.artists.isNotEmpty(),
            artists = splitArtists(request.artists),
            album = request.album?.takeIf(String::isNotBlank)
                ?.let(::normalize)?.takeIf(String::isNotEmpty),
            flags = versionFlags(title),
            durationMs = request.durationMs,
            instrumental = false,
            candidate = null,
        )
    }

    private fun versionFlags(normalized: String): Set<VersionFlag> =
        VersionFlag.entries.filterTo(mutableSetOf()) { it.pattern.containsMatchIn(normalized) }

    private class NormalizedTrack(
        val title: String,
        val rawHasArtists: Boolean,
        val artists: List<String>,
        val album: String?,
        val flags: Set<VersionFlag>,
        val durationMs: Long?,
        val instrumental: Boolean,
        val candidate: LyricsCandidate?,
    )

    private fun splitArtists(values: List<String>): List<String> = values
        .flatMap { it.split(ARTIST_SEPARATOR) }
        .map { normalize(it.replace(FEAT_PREFIX, "")) }
        .filter(String::isNotBlank)
        .distinct()

    companion object {
        private val ARTIST_SEPARATOR = Regex("\\s*(?:/|、|,|，|&|＆|;|；|·|・|\\bfeat\\.?\\b|\\bft\\.?\\b)\\s*", RegexOption.IGNORE_CASE)
        private val FEAT_PREFIX = Regex("^(?:feat\\.?|ft\\.?)\\s*", RegexOption.IGNORE_CASE)
        private val VERSION_DECORATION = Regex("[()\\[\\]{}<>_-]+")
        private val WHITESPACE = Regex("\\s+")
        private const val PROJECTION_CACHE_LIMIT = 1024

        /**
         * 文本归一化：NFKC → 小写 → 繁→简 → 空白折叠。
         *
         * 繁简必须进归一化，否则评分对字形敏感：本地标题「春風秋雨」与在线候选「春风秋雨」
         * 相似度只有 50 分，被入选闸门直接拒掉——把查询转成简体也救不了，因为候选仍以繁体
         * （或反之）参与比对。放进这里后标题、歌手、专辑三类分数都不再受繁简影响。
         */
        fun normalize(value: String): String = TraditionalChinese.toSimplified(
            Normalizer.normalize(value, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
                .replace('（', '(')
                .replace('）', ')'),
        )
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

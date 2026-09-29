// SPDX-License-Identifier: GPL-3.0-only
// Auto-fetch orchestration is derived from LDDC, commit 1ffa0e25426e654376e5d55d854b135ae601f43b.
package com.fnmusic.tv.core.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 歌词匹配编排：**两轮**取词。
 *
 * 第一轮只跑快源（各家搜索都在 1.8 秒内），命中就直接返回 —— 大多数歌的歌词上屏时间不受慢源影响。
 * 只有第一轮**一首都没命中**时，才进入第二轮"深挖"：[deepSources] 用更宽松的预算跑，专收那些
 * 召回好但慢的通道（例如 QQ 的 `client_search_cp`：离线召回最好，但实测中位 3.01 秒、20/20 超
 * 1.8 秒预算）。这样既不为慢源牺牲普遍延迟，又不放弃它们的召回。
 *
 * 这个"两轮"设计来自 LDDC 的启发：它的自动匹配也是先按「歌手 - 标题」搜，没结果再用「标题」搜，
 * 并且给了整个流程 30 秒的总预算 —— 对一个批量下载工具，召回远比延迟重要。
 */
class LyricsMatchCoordinator(
    sources: List<LyricsSource>,
    private val policy: LyricsMatchPolicy = LyricsMatchPolicy(),
    private val sourceTimeoutMs: Long = 1_800L,
    deepSources: List<LyricsSource> = emptyList(),
    private val deepSourceTimeoutMs: Long = 4_500L,
    private val scorer: LyricsCandidateScorer = LyricsCandidateScorer(policy),
) {
    private val sources = sources.distinctBy(LyricsSource::id)
    private val deepSources = deepSources.distinctBy(LyricsSource::id)

    suspend fun match(request: LyricsMatchRequest): LyricsMatchResult {
        if (request.title.isBlank() || (sources.isEmpty() && deepSources.isEmpty())) {
            return LyricsMatchResult.NotFound
        }
        return matchAllSources(request)
    }

    private suspend fun matchAllSources(request: LyricsMatchRequest): LyricsMatchResult = supervisorScope {
        // 轮级整体预算：单源超时是各自独立计时的（搜索可能两次 + 取词最多 3 个候选），
        // 没有轮预算时最坏 2×timeout + 3×timeout，两轮合计可逼近 31.5s 的歌词 Loading。
        val fast = withTimeoutOrNull(FAST_ROUND_DEADLINE_MS) {
            runRound(sources, sourceTimeoutMs, request)
        } ?: RoundOutcome(failure = FailureKind.NetworkFailure)
        fast.found?.let { return@supervisorScope it }

        // 离线短路：快源**全体**网络失败（不是"没搜到"）时，深挖轮只会同样失败，纯浪费。
        val offline = fast.searchOutcomes.isNotEmpty() &&
            fast.searchOutcomes.all { it.failure == FailureKind.NetworkFailure }
        val deep = if (deepSources.isEmpty() || offline) {
            RoundOutcome()
        } else {
            withTimeoutOrNull(DEEP_ROUND_DEADLINE_MS) {
                runRound(deepSources, deepSourceTimeoutMs, request)
            } ?: RoundOutcome(failure = FailureKind.NetworkFailure)
        }
        deep.found?.let { return@supervisorScope it }
        classifyFailure(fast, deep)
    }

    private suspend fun CoroutineScope.runRound(
        roundSources: List<LyricsSource>,
        timeoutMs: Long,
        request: LyricsMatchRequest,
    ): RoundOutcome {
        val primaryKeyword = buildList {
            request.artists.filter(String::isNotBlank).joinToString(" / ").takeIf(String::isNotBlank)?.let(::add)
            add(request.title)
        }.joinToString(" - ")
        val searchOutcomes = roundSources.map { source ->
            async { searchSource(source, primaryKeyword, request, timeoutMs) }
        }.awaitAll()
        val ranked = scorer.score(request, searchOutcomes.flatMap(SearchOutcome::candidates))
        val fetchOutcomes = roundSources.map { source ->
            val sourceCandidates = ranked.asSequence()
                .filter { it.candidate.source == source.id }
                .sortedWith(
                    compareByDescending<ScoredLyricsCandidate>(ScoredLyricsCandidate::metadataScore)
                        .thenByDescending(ScoredLyricsCandidate::consensusCount)
                        .thenBy { it.candidate.remoteId },
                )
                .take(MAX_FETCH_ATTEMPTS_PER_SOURCE)
                .toList()
            async { fetchSource(source, sourceCandidates, request, timeoutMs) }
        }.awaitAll()

        val matches = fetchOutcomes.flatMap(FetchOutcome::matches)
        return RoundOutcome(
            searchOutcomes = searchOutcomes,
            fetchOutcomes = fetchOutcomes,
            found = matches.takeIf(List<*>::isNotEmpty)?.let { found ->
                val selected = found.sortedWith(contentComparator()).first()
                LyricsMatchResult.Found(
                    MatchedLyrics(
                        source = selected.scored.candidate.source,
                        candidate = selected.scored.candidate,
                        score = selected.scored.score,
                        lyrics = selected.lyrics,
                        quality = selected.content.quality,
                    ),
                )
            },
        )
    }

    /**
     * 主关键词搜索 + （需要时）纯标题重试。
     *
     * 重试与主搜索**分开捕获异常**：重试失败只记录失败、保留主候选 —— 此前两者共用一个 try 块，
     * 主搜索成功但重试超时会把整个源标成 NetworkFailure 且候选清零（恰好"主候选全不合格"正是
     * 触发重试的条件，所以丢候选无害，但失败标记会让本可负缓存的"无歌词"变成"网络故障"，
     * 每次播放都全量重探）。
     */
    private suspend fun searchSource(
        source: LyricsSource,
        primaryKeyword: String,
        request: LyricsMatchRequest,
        timeoutMs: Long,
    ): SearchOutcome {
        val primaryCandidates = try {
            search(source, primaryKeyword, request, timeoutMs)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            return SearchOutcome(source, emptyList(), cause.toFailureKind())
        }
        val shouldRetryWithTitle = primaryKeyword != request.title &&
            source.supportsKeywordVariants &&
            scorer.score(request, primaryCandidates).isEmpty()
        if (!shouldRetryWithTitle) return SearchOutcome(source, primaryCandidates)
        return try {
            val retryCandidates = search(source, request.title, request, timeoutMs)
            SearchOutcome(source, (primaryCandidates + retryCandidates).distinctBy { it.source to it.remoteId })
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            SearchOutcome(source, primaryCandidates, cause.toFailureKind())
        }
    }

    private suspend fun fetchSource(
        source: LyricsSource,
        candidates: List<ScoredLyricsCandidate>,
        request: LyricsMatchRequest,
        timeoutMs: Long,
    ): FetchOutcome {
        if (candidates.isEmpty()) return FetchOutcome()

        var failure: FailureKind? = null
        val matches = mutableListOf<ProviderMatch>()
        candidates.forEach { scored ->
            try {
                val lyrics = withTimeoutOrNull(timeoutMs) { source.fetch(scored.candidate) }
                    ?: throw LyricsTransportException("${source.id} lyrics timed out")
                if (!lyrics.hasUsableLines()) {
                    failure = FailureKind.InvalidResponse
                    return@forEach
                }
                val rawContent = LyricsContentQualityEvaluator.evaluate(lyrics)
                val durationDeltaMs = knownDurationDelta(request.durationMs, scored.candidate.durationMs)
                val wordEligible = durationDeltaMs != null &&
                    durationDeltaMs <= policy.maximumWordTimingDeltaMs &&
                    rawContent.wordTimedCoverage > 0.0
                val eligibleLyrics = if (wordEligible) lyrics else lyrics.withoutWordTiming()
                matches += ProviderMatch(
                    scored = scored,
                    lyrics = eligibleLyrics,
                    content = LyricsContentQualityEvaluator.evaluate(eligibleLyrics),
                    durationDeltaMs = durationDeltaMs,
                    wordEligible = wordEligible,
                )
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                failure = failure.combine(cause.toFailureKind())
            }
        }
        return FetchOutcome(
            matches = matches,
            failure = failure ?: if (matches.isEmpty()) FailureKind.InvalidResponse else null,
        )
    }

    private fun contentComparator(): Comparator<ProviderMatch> =
        compareByDescending<ProviderMatch> { it.content.translationCoverage > 0.0 }
            .thenByDescending { it.content.translationCoverage }
            .thenByDescending { it.wordEligible }
            .thenBy { it.durationDeltaMs ?: Long.MAX_VALUE }
            .thenBy { sourceOrderIndex(it.scored.candidate.source) }
            .thenBy { it.scored.candidate.remoteId }

    private fun knownDurationDelta(left: Long?, right: Long?): Long? =
        if (left != null && left > 0L && right != null && right > 0L) {
            kotlin.math.abs(left - right)
        } else {
            null
        }

    private fun classifyFailure(vararg rounds: RoundOutcome): LyricsMatchResult {
        val failures = rounds.flatMap { round ->
            round.searchOutcomes.mapNotNull(SearchOutcome::failure) +
                round.fetchOutcomes.mapNotNull(FetchOutcome::failure) +
                listOfNotNull(round.failure)
        }
        return when {
            FailureKind.InvalidResponse in failures -> LyricsMatchResult.InvalidResponse
            FailureKind.NetworkFailure in failures -> LyricsMatchResult.NetworkFailure
            else -> LyricsMatchResult.NotFound
        }
    }

    private fun sourceOrderIndex(source: LyricsSourceId): Int =
        policy.sourceOrder.indexOf(source).takeIf { it >= 0 } ?: Int.MAX_VALUE

    private suspend fun search(
        source: LyricsSource,
        keyword: String,
        request: LyricsMatchRequest,
        timeoutMs: Long,
    ): List<LyricsCandidate> = withTimeoutOrNull(timeoutMs) {
        source.search(LyricsSearchQuery(keyword, request))
    } ?: throw LyricsTransportException("${source.id} search timed out")

    private fun Throwable.toFailureKind(): FailureKind =
        if (this is LyricsTransportException) FailureKind.NetworkFailure else FailureKind.InvalidResponse

    private fun FailureKind?.combine(other: FailureKind): FailureKind = when {
        this == FailureKind.InvalidResponse || other == FailureKind.InvalidResponse -> FailureKind.InvalidResponse
        else -> other
    }

    /** 一轮（快源或深挖）的完整结果。 */
    private class RoundOutcome(
        val searchOutcomes: List<SearchOutcome> = emptyList(),
        val fetchOutcomes: List<FetchOutcome> = emptyList(),
        val found: LyricsMatchResult.Found? = null,
        /** 轮级整体预算超时时的失败标记（此时 searchOutcomes 已不可得）。 */
        val failure: FailureKind? = null,
    )

    private data class SearchOutcome(
        val source: LyricsSource,
        val candidates: List<LyricsCandidate>,
        val failure: FailureKind? = null,
    )

    private data class FetchOutcome(
        val matches: List<ProviderMatch> = emptyList(),
        val failure: FailureKind? = null,
    )

    private data class ProviderMatch(
        val scored: ScoredLyricsCandidate,
        val lyrics: com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics,
        val content: LyricsContentQualityProjection,
        val durationDeltaMs: Long?,
        val wordEligible: Boolean,
    )

    private enum class FailureKind { NetworkFailure, InvalidResponse }

    private companion object {
        const val MAX_FETCH_ATTEMPTS_PER_SOURCE = 3

        /**
         * 轮级整体预算：单源超时各自独立计时（搜索最多两次 + 取词最多 3 个候选），
         * 没有轮预算时快轮最坏 9s、深挖轮最坏 22.5s。取值 = 各自单源预算 × 上限的宽松覆盖，
         * 超时的轮按 NetworkFailure 计（不进负缓存，下次播放重试）。
         */
        const val FAST_ROUND_DEADLINE_MS = 8_000L
        const val DEEP_ROUND_DEADLINE_MS = 14_000L
    }
}

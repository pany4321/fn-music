// SPDX-License-Identifier: GPL-3.0-only
package com.fnmusic.tv.core.lyrics

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import kotlinx.serialization.Serializable

@Serializable
data class LyricsMatchRequest(
    val localId: String,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
)

@Serializable
enum class LyricsSourceId { QqMusic, Kugou, Netease, Lrclib }

@Serializable
enum class LyricsContentQuality(internal val rank: Int) {
    Basic(0),
    Translated(1),
    WordTimed(2),
}

@Serializable
data class LyricsCandidate(
    val source: LyricsSourceId,
    val remoteId: String,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val mediaId: String? = null,
    val fileHash: String? = null,
    val accessKey: String? = null,
    val instrumental: Boolean = false,
)

data class MatchedLyrics(
    val source: LyricsSourceId,
    val candidate: LyricsCandidate,
    val score: Double,
    val lyrics: SyncedLyrics,
    val quality: LyricsContentQuality = LyricsContentQuality.Basic,
)

sealed interface LyricsMatchResult {
    data class Found(val lyrics: MatchedLyrics) : LyricsMatchResult
    data object NotFound : LyricsMatchResult
    data object NetworkFailure : LyricsMatchResult
    data object InvalidResponse : LyricsMatchResult
}

data class LyricsSearchQuery(
    val keyword: String,
    val request: LyricsMatchRequest,
)

interface LyricsSource {
    val id: LyricsSourceId

    /**
     * 本源的搜索是否利用 [LyricsSearchQuery.keyword]（「歌手 - 标题」形态）。
     *
     * 不利用的源（例如 QQ 桌面搜索**只认标题**，带歌手反而带偏）应返回 false：
     * 编排层在主关键词没有合格候选时，会用纯标题对支持关键词变化的源再试一次；
     * 对不支持的源重试只会发出一模一样的请求，纯属浪费。
     */
    val supportsKeywordVariants: Boolean get() = true

    suspend fun search(query: LyricsSearchQuery): List<LyricsCandidate>
    suspend fun fetch(candidate: LyricsCandidate): SyncedLyrics
}

/**
 * 传输层失败。[status] 是 HTTP 状态码（拿不到时为 null），[retryAfterMs] 来自 `Retry-After`
 * 响应头 —— LRCLIB 要求客户端在 429 时读取并遵守它，否则可能被临时封禁。
 */
class LyricsTransportException(
    message: String,
    cause: Throwable? = null,
    val status: Int? = null,
    val retryAfterMs: Long? = null,
) : Exception(message, cause)

class LyricsPayloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

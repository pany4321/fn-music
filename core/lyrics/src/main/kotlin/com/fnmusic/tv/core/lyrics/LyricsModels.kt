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

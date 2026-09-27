package com.fnmusic.tv.core.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

internal object PlaybackCommands {
    const val ConfigureAuth = "com.fnmusic.tv.CONFIGURE_AUTH"
    const val ClearAuth = "com.fnmusic.tv.CLEAR_AUTH"
    const val SetShuffleOrder = "com.fnmusic.tv.SET_SHUFFLE_ORDER"
    const val CacheNamespace = "cache_namespace"
    const val ApiBase = "api_base"
    const val StreamPathPrefix = "stream_path_prefix"
    const val HeaderNames = "header_names"
    const val HeaderValues = "header_values"
    const val MediaIds = "media_ids"
    const val SnapshotRevision = "snapshot_revision"
    val ConfigureAuthCommand = SessionCommand(ConfigureAuth, android.os.Bundle.EMPTY)
    val ClearAuthCommand = SessionCommand(ClearAuth, android.os.Bundle.EMPTY)
    val SetShuffleOrderCommand = SessionCommand(SetShuffleOrder, android.os.Bundle.EMPTY)
}

/**
 * Bundle 装不下 Map，用两个平行数组搬运请求头（顺序一致）。
 * 请求头由各后端组装（见 `PlaybackAuth`），播放服务只有这一条注入路径。
 */
internal fun Bundle.putRequestHeaders(headers: Map<String, String>) {
    putStringArrayList(PlaybackCommands.HeaderNames, ArrayList(headers.keys))
    putStringArrayList(PlaybackCommands.HeaderValues, ArrayList(headers.values))
}

internal fun Bundle.requestHeaders(): Map<String, String> =
    getStringArrayList(PlaybackCommands.HeaderNames).orEmpty()
        .zip(getStringArrayList(PlaybackCommands.HeaderValues).orEmpty())
        .toMap()

package com.fnmusic.tv.core.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource

/**
 * Rebases a stale stream URI onto the newest authenticated api base: path and
 * query survive, scheme and authority follow the re-homed origin. Returns null
 * when the URI is already current, local/foreign, or not below the backend's own
 * stream path prefix — only http(s) URIs owned by the active backend are re-basable.
 */
internal fun rebaseApiUri(uri: Uri, apiBase: String, streamPathPrefix: String): Uri? {
    if (streamPathPrefix.isEmpty()) return null
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    val base = Uri.parse(apiBase)
    val baseAuthority = base.authority ?: return null
    val baseScheme = base.scheme?.takeIf(String::isNotEmpty) ?: return null
    if (uri.scheme == baseScheme && uri.authority == baseAuthority) return null
    val path = uri.path ?: return null
    if (!path.startsWith(streamPathPrefix)) return null
    return uri.buildUpon()
        .scheme(baseScheme)
        .authority(baseAuthority)
        .build()
}

/** 重挂目标：新的 api 基址 + 本后端流地址的路径前缀（两者都来自当前会话的 [com.fnmusic.tv.core.model.PlaybackAuth]）。 */
internal data class RehomeTarget(val apiBase: String, val streamPathPrefix: String)

/**
 * Applied at data-source open time so a queue installed against a dead origin
 * (home LAN IP) keeps playing after the session re-homes onto a reachable
 * candidate (relay or another address) without rebuilding the Media3 timeline.
 */
@OptIn(UnstableApi::class)
internal class ApiBaseRewritingResolver(
    private val currentTarget: () -> RehomeTarget?,
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val target = currentTarget() ?: return dataSpec
        val rebased = rebaseApiUri(dataSpec.uri, target.apiBase, target.streamPathPrefix) ?: return dataSpec
        return dataSpec.buildUpon().setUri(rebased).build()
    }
}

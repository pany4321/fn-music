package com.fnmusic.tv.core.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource

private const val API_PATH_PREFIX = "/music/api/v1/"

/**
 * Rebases a stale stream URI onto the newest authenticated api base: path and
 * query survive, scheme and authority follow the re-homed origin. Returns null
 * when the URI is already current, local/foreign, or not one of our API paths —
 * only http(s) URIs below `/music/api/v1/` are re-basable.
 */
internal fun rebaseApiUri(uri: Uri, apiBase: String): Uri? {
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    val base = Uri.parse(apiBase)
    val baseAuthority = base.authority ?: return null
    val baseScheme = base.scheme?.takeIf(String::isNotEmpty) ?: return null
    if (uri.scheme == baseScheme && uri.authority == baseAuthority) return null
    val path = uri.path ?: return null
    if (!path.startsWith(API_PATH_PREFIX)) return null
    return uri.buildUpon()
        .scheme(baseScheme)
        .authority(baseAuthority)
        .build()
}

/**
 * Applied at data-source open time so a queue installed against a dead origin
 * (home LAN IP) keeps playing after the session re-homes onto a reachable
 * candidate (relay or another address) without rebuilding the Media3 timeline.
 */
@OptIn(UnstableApi::class)
internal class ApiBaseRewritingResolver(
    private val currentApiBase: () -> String?,
) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val base = currentApiBase() ?: return dataSpec
        val rebased = rebaseApiUri(dataSpec.uri, base) ?: return dataSpec
        return dataSpec.buildUpon().setUri(rebased).build()
    }
}

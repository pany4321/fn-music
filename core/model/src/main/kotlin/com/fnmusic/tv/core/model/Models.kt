package com.fnmusic.tv.core.model

@JvmInline value class ServerGuid(val value: String)
@JvmInline value class UserGuid(val value: String)
@JvmInline value class TrackGuid(val value: String)
@JvmInline value class CollectionGuid(val value: String)

data class ServerIdentity(
    val guid: ServerGuid,
    val name: String,
    val serverVersion: String,
    val mediaServerVersion: String,
)

data class User(
    val guid: UserGuid,
    val username: String,
    val nickname: String?,
)

data class Playlist(
    val guid: CollectionGuid,
    val name: String,
    val coverId: String?,
    val trackCount: Int? = null,
)

data class Artist(
    val guid: CollectionGuid,
    val name: String,
    val coverId: String?,
    val trackCount: Int? = null,
    val albumCount: Int? = null,
)

data class Album(
    val guid: CollectionGuid,
    val name: String,
    val artistName: String?,
    val coverId: String?,
    val trackCount: Int? = null,
    val releaseDate: String? = null,
)

data class Genre(
    val guid: CollectionGuid,
    val name: String,
    val coverId: String?,
    val trackCount: Int?,
)

data class Track(
    val guid: TrackGuid,
    val title: String,
    val artistName: String?,
    val albumName: String?,
    val coverId: String?,
    val durationMs: Long?,
    val isCue: Boolean,
    val accessStatus: Int? = null,
    val audioFormat: String? = null,
    val isFavorite: Boolean = false,
)

data class SharedLibrary(
    val guid: CollectionGuid,
    val name: String,
    val accessStatus: Int,
    val updatedAt: Long,
)

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val sort: String,
) {
    val hasNext: Boolean get() = page * pageSize < total
}

data class LyricDocument(
    val guid: String,
    val content: String,
    val isLrc: Boolean,
    val offsetMs: Long,
)

data class PlaybackCredentials(
    val apiBase: String,
    val rawAuthorization: String,
    val cacheNamespace: String,
    val accessCodeHeader: String? = null,
    val relayMode: Boolean = false,
)

data class PlaybackTrack(
    val track: Track,
    val streamUrl: String,
    val artworkUrl: String?,
)

data class RoamNode(val roamId: String, val track: Track)
data class RoamWindow(val previous: RoamNode?, val current: RoamNode, val next: RoamNode?)

enum class PlayerStyle { Cover, Poster }

/** 界面配色主题（设备级偏好）。 */
enum class AppTheme { CoralNight, Jade, ForestGreen, Violet, SakuraPink, GraphiteBlue, Ink, Mocha }

/** 界面缩放档位：自动按设备密度判定，其余为固定倍数（车机以 1.5 倍为标准）。 */
enum class UiScaleMode { Auto, Standard, Large, Larger }

/** 界面缩放倍数；[UiScaleMode.Auto] 时由设备密度决定。 */
fun UiScaleMode.factor(deviceDensity: Float): Float = when (this) {
    UiScaleMode.Auto -> when {
        deviceDensity <= 1.33f -> 1.5f
        deviceDensity < 1.8f -> 1.25f
        else -> 1.0f
    }
    UiScaleMode.Standard -> 1.5f
    UiScaleMode.Large -> 1.6f
    UiScaleMode.Larger -> 1.75f
}

enum class CoverVariant(val width: Int?) {
    Compact(200),
    Grid(400),
    Player(800),
    Poster(null),
}

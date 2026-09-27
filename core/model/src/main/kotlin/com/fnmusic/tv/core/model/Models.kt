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

/**
 * 播放器要用的会话材料：流地址所在的服务基址、请求头、缓存命名空间，
 * 以及"哪些地址属于本后端"的路径前缀（会话重挂只重写落在它下面的 URL）。
 *
 * 请求头由各后端组装（飞牛的 Authorization/Cookie/x-access-code 与 Jellyfin 的
 * MediaBrowser Token 都装得下），播放服务只管原样注入。
 */
data class PlaybackAuth(
    val apiBase: String,
    val headers: Map<String, String>,
    val cacheNamespace: String,
    /**
     * 本后端流地址的路径前缀（飞牛 `/music/api/v1/`、Jellyfin `/Audio/`）。
     * 跨 Media3 的 Bundle 只能传字符串，所以这里用前缀而不是判定函数。
     */
    val streamPathPrefix: String,
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
enum class AppTheme {
    CoralNight,
    Jade,
    ForestGreen,
    Violet,
    SakuraPink,
    GraphiteBlue,
    Ink,
    Mocha,
    Bordeaux,
    Plum,
    Matcha,
    Pewter,
}

/** 界面缩放档位：自动按设备密度判定，其余为固定倍数（车机标准档 1.35 倍）。 */
enum class UiScaleMode { Auto, Standard, Large, Larger }

/** 界面缩放倍数；[UiScaleMode.Auto] 时由设备密度决定。 */
fun UiScaleMode.factor(deviceDensity: Float): Float = when (this) {
    // 实测：1080P 车机（密度 ≤1.33）1.25 倍最合适，电视/手机不缩放。
    UiScaleMode.Auto -> if (deviceDensity < 1.8f) 1.25f else 1.0f
    UiScaleMode.Standard -> 1.35f
    UiScaleMode.Large -> 1.5f
    UiScaleMode.Larger -> 1.75f
}

enum class CoverVariant(val width: Int?) {
    Compact(200),
    Grid(400),
    Player(800),
    Poster(null),
}

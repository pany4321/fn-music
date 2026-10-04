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
    /** 这台服务器是哪一类后端（默认飞牛：老会话/旧数据都按飞牛解释）。 */
    val kind: ServerKind = ServerKind.FnOs,
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
    /**
     * 服务器响应自带的收藏时间（epoch 毫秒，尽力而为）：飞牛 `favorite-track/list` 的
     * `favoriteAt`、Jellyfin 的 `UserData.DateLastSaved`。仅作收藏列表排序的候选种子，
     * 权威时间在本地 favorite_time 表（见 MusicRepository 对账逻辑）；拿不到为 null。
     */
    val favoritedAt: Long? = null,
    /**
     * 歌单内条目的 id（Jellyfin 从歌单删曲目用 `entryIds`；其它后端为 null）。
     */
    val playlistEntryId: String? = null,
    /**
     * 非空 = 后端判定本机解码器放不了这个格式（如飞牛源的 DSD/裸 PCM，飞牛没有转码兜底），
     * 文案直接给 UI 的行提示用；null = 可播。Jellyfin 源恒为 null（不支持就转码）。
     */
    val unplayableReason: String? = null,
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
    /** 这条 URL 的形态：直连原文件，还是 HLS 转码流（播放器据此选 MediaSource）。 */
    val streamMode: StreamMode = StreamMode.Direct,
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
    // 1024：1080p 电视海报足够清晰。曾经用 null=下载原图，实测两台服务器的专辑原图
    // 虽然只有 48-244KB，但内嵌大图的 FLAC/极端封面没有上限保护，广域网换歌会卡。
    Poster(1024),
}

package com.fnmusic.tv.core.data.backend

import com.fnmusic.tv.core.model.Album
import com.fnmusic.tv.core.model.Artist
import com.fnmusic.tv.core.model.Genre
import com.fnmusic.tv.core.model.LyricDocument
import com.fnmusic.tv.core.model.Playlist
import com.fnmusic.tv.core.model.RoamWindow
import com.fnmusic.tv.core.model.ServerIdentity
import com.fnmusic.tv.core.model.ServerKind
import com.fnmusic.tv.core.model.SharedLibrary
import com.fnmusic.tv.core.model.StreamPlan
import com.fnmusic.tv.core.model.StreamMode
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.User

/**
 * 后端能力声明：把"某些服务器有、某些没有"的差异集中在这里，
 * 上层（UI/仓库）按能力决定走哪条路，而不是到处判断服务器类型。
 */
data class BackendCapabilities(
    /** 随机漫游由服务端维护前后节点（飞牛有；Jellyfin 没有，要靠客户端策略）。 */
    val serverSideRoam: Boolean,
    /** 服务端能提供歌词（Jellyfin 需 10.9+，没有时退回在线歌词源）。 */
    val serverSideLyrics: Boolean,
    /** 服务端记录播放历史（最近播放）。 */
    val serverSidePlayHistory: Boolean,
    /** 服务端可转码（Jellyfin 的 PlaybackInfo/stream 转码参数）。 */
    val transcoding: Boolean,
    /** 收藏有"收藏时间"排序（飞牛有；Jellyfin 只能按创建时间）。 */
    val favoriteTimeSort: Boolean,
)

/**
 * 后端产出的一页原始数据。
 *
 * `rawJson` 是**可缓存、可持久化**的响应体：它直接进现有响应缓存与 Room，
 * 解码交给 [MusicBackend.decodePage]（同一份缓存在两个后端下都能用）。
 * 其余字段是分页元信息，用于分页决策与漂移校验。
 */
data class RawPage(
    val rawJson: String,
    val page: Int,
    val pageSize: Int,
    val total: Int?,
    val sort: String,
)

/** 后端产出的索引文档（不参与分页，一次取回）：同样是可缓存的原始体。 */
data class RawIndex(val rawJson: String)

/** 解码后的一页：领域条目 + 该页的分页元信息（与改动前一样取自响应体）。 */
data class DecodedPage<T>(
    val items: List<T>,
    val total: Int,
    val sort: String,
)

/**
 * 分页类目录来源：**后端无关的描述**，各后端映射到自己的 endpoint + 排序串。
 * 每个来源只对应一种领域类型 —— [MusicBackend.decodePage] 依赖这个一一对应关系。
 */
sealed interface CatalogPageSource<T> {
    /** 缓存源键：与抽取前的字符串逐字一致，保证内存缓存与 Room 的键不变。 */
    val cacheKey: String

    /** 页大小是否参与缓存键（电视卡片这类按 size 取样的列表要按 size 分开缓存）。 */
    val sizeSensitive: Boolean

    data class PlaylistTracks(val guid: String) : CatalogPageSource<Track> {
        override val cacheKey: String = "playlist:$guid"
        override val sizeSensitive: Boolean = false
    }

    data class ArtistTracks(val guid: String) : CatalogPageSource<Track> {
        override val cacheKey: String = "artist-tracks:$guid"
        override val sizeSensitive: Boolean = false
    }

    data class ArtistAlbums(val guid: String) : CatalogPageSource<Album> {
        override val cacheKey: String = "artist-albums:$guid"
        override val sizeSensitive: Boolean = false
    }

    data class AlbumTracks(val guid: String) : CatalogPageSource<Track> {
        override val cacheKey: String = "album-tracks:$guid"
        override val sizeSensitive: Boolean = false
    }

    data class GenreTracks(val guid: String) : CatalogPageSource<Track> {
        override val cacheKey: String = "genre-tracks:$guid"
        override val sizeSensitive: Boolean = false
    }

    data object Artists : CatalogPageSource<Artist> {
        override val cacheKey: String = "artists"
        override val sizeSensitive: Boolean = true
    }

    data object Albums : CatalogPageSource<Album> {
        override val cacheKey: String = "albums"
        override val sizeSensitive: Boolean = true
    }

    data object AllTracks : CatalogPageSource<Track> {
        override val cacheKey: String = "all-tracks"
        override val sizeSensitive: Boolean = true
    }

    data object RecentlyAdded : CatalogPageSource<Track> {
        override val cacheKey: String = "recently-added"
        override val sizeSensitive: Boolean = true
    }
}

/**
 * 索引类目录来源（歌单列表/详情、歌手、专辑、风格、共享库、曲目元信息）：
 * 一次取回整个文档，走 `cachedIndex` 那张缓存表。
 */
sealed interface CatalogIndexSource<T> {
    /** 缓存源键：与抽取前一致。 */
    val cacheKey: String

    data object Playlists : CatalogIndexSource<List<Playlist>> {
        override val cacheKey: String = "playlists"
    }

    data class PlaylistDetail(val guid: String) : CatalogIndexSource<Playlist> {
        override val cacheKey: String = "playlist:$guid"
    }

    data class ArtistDetail(val guid: String) : CatalogIndexSource<Artist> {
        override val cacheKey: String = "artist:$guid"
    }

    data class AlbumDetail(val guid: String) : CatalogIndexSource<Album> {
        override val cacheKey: String = "album:$guid"
    }

    data object Genres : CatalogIndexSource<List<Genre>> {
        override val cacheKey: String = "genres"
    }

    data object SharedLibraries : CatalogIndexSource<List<SharedLibrary>> {
        override val cacheKey: String = "shared-libraries"
    }

    data class TrackMetadata(val guid: String) : CatalogIndexSource<Track> {
        override val cacheKey: String = "track-metadata:$guid"
    }
}

/**
 * 音乐服务器后端。**只负责"把服务器的接口变成领域模型"**，
 * 缓存、分页保留、收藏状态、队列装配都由 `MusicRepository` 负责。
 *
 * 目录类查询一律返回"可缓存的原始页/原始索引"（[RawPage]/[RawIndex]），
 * 再由后端自己解码（[decodePage]/[decodeIndex]）—— 这样一份缓存同时服务所有后端，
 * Room 的持久化格式也不需要按后端分叉。
 * 变更类操作（新建歌单/加删曲目/收藏）保持领域参数：它们本就是命令语义，不进缓存。
 */
interface MusicBackend {
    val kind: ServerKind

    val capabilities: BackendCapabilities

    /** 当前登录用户（用于会话校验与"我的"页面展示）。 */
    suspend fun me(): User

    /** 封面图片的完整 URL；[com.fnmusic.tv.core.model.CoverVariant] 的宽度直接映射到各家参数。 */
    fun artworkUrl(coverId: String, variantWidth: Int?): String

    /** 下载封面原始字节（走各后端自己的鉴权方式）。 */
    suspend fun artwork(coverId: String, variantWidth: Int?): ByteArray

    /**
     * 纯拼直连 URL（不联网）：批量装配队列时用，避免为每首歌发一次协商请求。
     */
    fun directStreamUrl(track: Track): String

    /**
     * 决定这首歌怎么放：能直连就直连，否则让服务端转码。
     * 返回的 [StreamMode] 告诉播放器要不要走 HLS 解析。
     */
    suspend fun streamPlan(track: Track): StreamPlan

    /**
     * 批量装队列时的播放计划：默认与 [directStreamUrl] 相同（飞牛），
     * 能转码的后端可以按曲目已知信息直接决定走直连还是 HLS，不必逐首协商。
     */
    fun queueStreamPlan(track: Track): StreamPlan = StreamPlan(directStreamUrl(track), StreamMode.Direct)

    /** 随机取 [size] 首歌（首页卡片封面、随机行用），各后端用自己最省的方式实现。 */
    suspend fun randomTracks(size: Int): List<Track>

    // ---- 目录：分页 ----

    /** 按 [source] 取一页原始数据（可缓存）。 */
    suspend fun catalogPage(source: CatalogPageSource<*>, page: Int, size: Int): RawPage

    /** 把原始页解码成领域条目；[source] 与领域类型是一一对应的（实现在此前提下做一次安全转换）。 */
    fun <T> decodePage(source: CatalogPageSource<T>, rawJson: String): DecodedPage<T>

    // ---- 目录：索引 ----

    /** 取一个索引文档的原始数据（可缓存）。 */
    suspend fun catalogIndex(source: CatalogIndexSource<*>): RawIndex

    /** 把原始索引解码成领域对象。 */
    fun <T> decodeIndex(source: CatalogIndexSource<T>, rawJson: String): T

    // ---- 搜索 / 收藏 / 最近播放（不缓存，取回即可用） ----

    suspend fun searchTracks(query: String, page: Int, size: Int): DecodedPage<Track>

    suspend fun searchArtists(query: String, page: Int, size: Int): DecodedPage<Artist>

    suspend fun searchAlbums(query: String, page: Int, size: Int): DecodedPage<Album>

    suspend fun favoriteTracks(page: Int, size: Int): DecodedPage<Track>

    suspend fun recentTracks(page: Int, size: Int): DecodedPage<Track>

    /** 歌单曲目数（歌单列表页要显示"共 N 首"）。 */
    suspend fun playlistTrackCounts(guids: List<String>): Map<String, Int>

    // ---- 变更 ----

    suspend fun setFavorite(trackGuid: String, favorite: Boolean)

    suspend fun createPlaylist(name: String): Playlist

    suspend fun addToPlaylist(playlistGuid: String, trackGuid: String)

    /**
     * 从歌单移除一首曲目。
     * 飞牛用曲目 guid；Jellyfin 必须用歌单条目 id（见 [Track.playlistEntryId]），所以要传整首曲目。
     */
    suspend fun removeFromPlaylist(playlistGuid: String, track: Track)

    /**
     * 删除整个歌单。
     * 飞牛走官方客户端同款推测端点 `playlist/delete`（仓库无文档，服务端不支持时会以 HTTP 错误浮出）；
     * Jellyfin 走标准 `DELETE /Items/{itemId}`（歌单是普通库条目；/Playlists 下没有删除端点）。
     */
    suspend fun deletePlaylist(playlistGuid: String)

    /**
     * 记一次播放（"最近播放"的数据来源）。
     * 服务端按曲目去重、刷新最近播放时间——重复播放不产生多条历史。
     * 飞牛走官方客户端同款 `event/report`；Jellyfin 走幂等的 `PlayedItems` 标记。
     */
    suspend fun reportTrackPlayed(trackGuid: String)

    // ---- 歌词 ----

    /** 服务端歌词的原始响应体（可缓存；没有歌词时按各家约定返回空表示）。 */
    suspend fun lyricsRaw(trackGuid: String): String

    /** 解码原始歌词；返回 null 表示该曲服务端没有歌词（上层走在线歌词源）。 */
    fun decodeLyrics(rawJson: String): LyricDocument?

    // ---- 漫游 ----

    suspend fun startRoam(): RoamWindow?

    suspend fun nextRoam(roamId: String): RoamWindow

    suspend fun previousRoam(roamId: String): RoamWindow
}

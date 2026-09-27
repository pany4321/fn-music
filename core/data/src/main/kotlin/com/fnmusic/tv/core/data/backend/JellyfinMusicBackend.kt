package com.fnmusic.tv.core.data.backend

import com.fnmusic.tv.core.model.Album
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.AppException
import com.fnmusic.tv.core.model.Artist
import com.fnmusic.tv.core.model.LyricDocument
import com.fnmusic.tv.core.model.Playlist
import com.fnmusic.tv.core.model.RoamWindow
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.User
import com.fnmusic.tv.core.model.UserGuid

/**
 * Jellyfin 后端：实现 [MusicBackend]（当前是抽取的第一批方法）。
 * 与飞牛的关键差异都在这里消化：
 * - 无服务端漫游（后续用客户端漫游策略补齐，接口形状不变）
 * - 播放要自己决定直连还是转码（PlaybackInfo 不返回 URL）
 * - 收藏没有"收藏时间"排序
 */
internal class JellyfinMusicBackend(
    private val api: JellyfinApi,
    private val userId: String,
) : MusicBackend {

    override val kind: ServerKind = ServerKind.Jellyfin

    override val capabilities: BackendCapabilities = BackendCapabilities(
        serverSideRoam = false,
        serverSideLyrics = true, // 10.9+ 的 /Audio/{id}/Lyrics；无歌词时返回 404 → 上层走在线歌词
        serverSidePlayHistory = true,
        transcoding = true,
        favoriteTimeSort = false,
    )

    override suspend fun me(): User = api.me().let { User(UserGuid(it.Id), it.Name, null) }

    override fun artworkUrl(coverId: String, variantWidth: Int?): String =
        api.imageUrl(coverId, variantWidth)

    override suspend fun artwork(coverId: String, variantWidth: Int?): ByteArray =
        api.imageBytes(coverId, variantWidth)

    override fun directStreamUrl(track: Track): String = api.directStreamUrl(track.guid.value)

    /**
     * 能直连就直连，否则走 HLS 转码。
     * 判定依据实测：`PlaybackInfo` 只给能力位与容器，URL 由客户端拼。
     */
    override suspend fun streamPlan(track: Track): StreamPlan {
        val item = runCatching { api.item(track.guid.value, userId) }.getOrNull()
        val source = item?.MediaSources?.firstOrNull()
        val container = (source?.Container ?: item?.Container)?.lowercase()
        val directPlayable = container != null && container in DIRECT_PLAYABLE_CONTAINERS
        val itemId = track.guid.value
        return when {
            source == null -> StreamPlan(api.directStreamUrl(itemId), StreamMode.Direct)
            source.SupportsDirectPlay && directPlayable ->
                StreamPlan(api.directStreamUrl(itemId), StreamMode.Direct)
            source.SupportsTranscoding ->
                StreamPlan(api.hlsTranscodeUrl(itemId), StreamMode.Hls)
            else -> StreamPlan(api.directStreamUrl(itemId), StreamMode.Direct)
        }
    }

    override suspend fun randomTracks(size: Int): List<Track> =
        api.randomTracks(userId, size.coerceAtLeast(1)).map(JellyfinItemDto::toTrack)

    // ---- Step 2b 待接入 ----
    // 目录/搜索/收藏/歌词/漫游的接口形状已按飞牛侧抽好，Jellyfin 实现排在 Step 2b
    // （要和 JellyfinConnector、登录页识别一起接线）。在那之前会话只会是飞牛，
    // 这些方法不会被调用；显式失败好过静默返回空数据被当成"服务器里真的没有"。
    override suspend fun catalogPage(source: CatalogPageSource<*>, page: Int, size: Int): RawPage =
        pending("catalog")

    override fun <T> decodePage(source: CatalogPageSource<T>, rawJson: String): DecodedPage<T> =
        pending("catalog")

    override suspend fun catalogIndex(source: CatalogIndexSource<*>): RawIndex = pending("catalog")

    override fun <T> decodeIndex(source: CatalogIndexSource<T>, rawJson: String): T = pending("catalog")

    override suspend fun searchTracks(query: String, page: Int, size: Int): DecodedPage<Track> =
        pending("search")

    override suspend fun searchArtists(query: String, page: Int, size: Int): DecodedPage<Artist> =
        pending("search")

    override suspend fun searchAlbums(query: String, page: Int, size: Int): DecodedPage<Album> =
        pending("search")

    override suspend fun favoriteTracks(page: Int, size: Int): DecodedPage<Track> = pending("favorites")

    override suspend fun recentTracks(page: Int, size: Int): DecodedPage<Track> = pending("recent")

    override suspend fun playlistTrackCounts(guids: List<String>): Map<String, Int> = pending("playlist")

    override suspend fun setFavorite(trackGuid: String, favorite: Boolean) = pending("favorites")

    override suspend fun createPlaylist(name: String): Playlist = pending("playlist")

    override suspend fun addToPlaylist(playlistGuid: String, trackGuid: String) = pending("playlist")

    override suspend fun removeFromPlaylist(playlistGuid: String, trackGuid: String) = pending("playlist")

    override suspend fun lyricsRaw(trackGuid: String): String = pending("lyrics")

    override fun decodeLyrics(rawJson: String): LyricDocument? = pending("lyrics")

    override suspend fun startRoam(): RoamWindow? = pending("roam")

    override suspend fun nextRoam(roamId: String): RoamWindow = pending("roam")

    override suspend fun previousRoam(roamId: String): RoamWindow = pending("roam")

    /** 供会话/登录页使用：把登录结果里的用户与令牌收起来。 */
    suspend fun authenticate(username: String, password: String): JellyfinAuthResultDto =
        api.authenticate(username, password)

    private fun pending(feature: String): Nothing =
        throw AppException(AppError.Unknown("jellyfin_${feature}_pending"))

    private companion object {
        /** 能与 ExoPlayer 直连的容器（其余交给服务端转码）。 */
        val DIRECT_PLAYABLE_CONTAINERS = setOf(
            "flac", "mp3", "aac", "m4a", "mp4", "ogg", "oga", "opus", "wav", "pcm", "mka",
        )

    }
}


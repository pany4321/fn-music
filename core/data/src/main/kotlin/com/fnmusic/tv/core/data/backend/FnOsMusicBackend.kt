package com.fnmusic.tv.core.data.backend

import com.fnmusic.tv.core.data.api.AlbumDto
import com.fnmusic.tv.core.data.api.ApiDecoder
import com.fnmusic.tv.core.data.api.ArtistDto
import com.fnmusic.tv.core.data.api.GenreDto
import com.fnmusic.tv.core.data.api.LyricDto
import com.fnmusic.tv.core.data.api.LyricListDto
import com.fnmusic.tv.core.data.api.PageListDto
import com.fnmusic.tv.core.data.api.PlaylistDetailDto
import com.fnmusic.tv.core.data.api.PlaylistDto
import com.fnmusic.tv.core.data.api.SharedLibraryDto
import com.fnmusic.tv.core.data.api.SortedPageListDto
import com.fnmusic.tv.core.data.api.TrackDto
import com.fnmusic.tv.core.data.api.TrackMetadataDto
import com.fnmusic.tv.core.data.repository.SessionRepository
import com.fnmusic.tv.core.model.Album
import com.fnmusic.tv.core.model.Artist
import com.fnmusic.tv.core.model.CollectionGuid
import com.fnmusic.tv.core.model.Genre
import com.fnmusic.tv.core.model.LyricDocument
import com.fnmusic.tv.core.model.Playlist
import com.fnmusic.tv.core.model.RoamWindow
import com.fnmusic.tv.core.model.SharedLibrary
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.User
import kotlin.random.Random

/**
 * 飞牛音乐（fnOS）后端：把所有 `TrimMusicApi` 调用与 DTO 映射收拢到这里。
 * 本类的行为与抽取前 `MusicRepository` 内的实现逐字一致（含 endpoint 与排序串），
 * 抽取阶段不做任何行为改动。
 */
internal class FnOsMusicBackend(
    private val session: SessionRepository,
) : MusicBackend {

    override val kind: ServerKind = ServerKind.FnOs

    override val capabilities: BackendCapabilities = BackendCapabilities(
        serverSideRoam = true,
        serverSideLyrics = true,
        serverSidePlayHistory = true,
        transcoding = false,
        favoriteTimeSort = true,
    )

    override suspend fun me(): User = session.authenticated { it.me() }.toDomain()

    override fun artworkUrl(coverId: String, variantWidth: Int?): String =
        session.requireApi().coverUrl(coverId, variantWidth).toString()

    override suspend fun artwork(coverId: String, variantWidth: Int?): ByteArray =
        session.authenticated { it.cover(coverId, variantWidth) }

    override fun directStreamUrl(track: Track): String =
        session.requireApi().streamUrl(track.guid.value).toString()

    override suspend fun streamPlan(track: Track): StreamPlan =
        // 飞牛只做直连（无转码）：cue/受限曲目在上层已被拒绝。
        StreamPlan(url = directStreamUrl(track), mode = StreamMode.Direct)

    /**
     * 随机取歌：先探测总数，再随机挑一页。
     * 与原实现相同（沿用 `track/list` 的分页语义），只是不再走响应缓存。
     */
    override suspend fun randomTracks(size: Int): List<Track> {
        val pageSize = size.coerceAtLeast(1)
        val probe = session.authenticated { it.allTracks(page = 1, size = 1) }
        val total = probe.total
        if (total <= pageSize) {
            return session.authenticated { it.allTracks(page = 1, size = pageSize) }
                .list
                .map(TrackDto::toDomain)
        }
        val lastPage = (total + pageSize - 1) / pageSize
        val page = Random.nextInt(1, lastPage + 1)
        return session.authenticated { it.allTracks(page = page, size = pageSize) }
            .list
            .map(TrackDto::toDomain)
    }

    override suspend fun catalogPage(source: CatalogPageSource<*>, page: Int, size: Int): RawPage =
        session.authenticated { api ->
            when (source) {
                is CatalogPageSource.PlaylistTracks ->
                    api.playlistTracks(source.guid, page, size).toRawPage(page, size)
                CatalogPageSource.Artists ->
                    api.artists(page, size).toRawPage(page, size)
                is CatalogPageSource.ArtistTracks ->
                    api.artistTracks(source.guid, page, size).toRawPage(page, size)
                is CatalogPageSource.ArtistAlbums ->
                    api.artistAlbums(source.guid, page, size).toRawPage(page, size)
                CatalogPageSource.Albums ->
                    api.albums(page, size).toRawPage(page, size)
                is CatalogPageSource.AlbumTracks ->
                    api.albumTracks(source.guid, page, size).toRawPage(page, size)
                CatalogPageSource.AllTracks ->
                    api.allTracks(page, size).toRawPage(page, size)
                CatalogPageSource.RecentlyAdded ->
                    api.recentlyAddedTracks(page, size).toRawPage(page, size)
                is CatalogPageSource.GenreTracks ->
                    api.genreTracks(source.guid, page, size).toRawPage(page, size)
            }
        }

    override fun <T> decodePage(source: CatalogPageSource<T>, rawJson: String): DecodedPage<T> {
        val decoded: DecodedPage<*> = when (source) {
            is CatalogPageSource.PlaylistTracks -> decodeSortedPage<TrackDto, Track>(rawJson, TrackDto::toDomain)
            CatalogPageSource.Artists -> decodeSortedPage<ArtistDto, Artist>(rawJson, ArtistDto::toDomain)
            is CatalogPageSource.ArtistTracks -> decodeSortedPage<TrackDto, Track>(rawJson, TrackDto::toDomain)
            is CatalogPageSource.ArtistAlbums -> decodeSortedPage<AlbumDto, Album>(rawJson, AlbumDto::toDomain)
            CatalogPageSource.Albums -> decodeSortedPage<AlbumDto, Album>(rawJson, AlbumDto::toDomain)
            is CatalogPageSource.AlbumTracks -> decodeSortedPage<TrackDto, Track>(rawJson, TrackDto::toDomain)
            CatalogPageSource.AllTracks -> decodeSortedPage<TrackDto, Track>(rawJson, TrackDto::toDomain)
            CatalogPageSource.RecentlyAdded -> decodeSortedPage<TrackDto, Track>(rawJson, TrackDto::toDomain)
            is CatalogPageSource.GenreTracks -> decodeSortedPage<TrackDto, Track>(rawJson, TrackDto::toDomain)
        }
        // 每个来源只有一种领域类型（见 CatalogPageSource 的定义），所以这里的转换是安全的。
        @Suppress("UNCHECKED_CAST")
        return decoded as DecodedPage<T>
    }

    override suspend fun catalogIndex(source: CatalogIndexSource<*>): RawIndex =
        session.authenticated { api ->
            when (source) {
                CatalogIndexSource.Playlists -> RawIndex(ApiDecoder.json.encodeToString(api.playlists()))
                is CatalogIndexSource.PlaylistDetail ->
                    RawIndex(ApiDecoder.json.encodeToString(api.playlist(source.guid)))
                is CatalogIndexSource.ArtistDetail ->
                    RawIndex(ApiDecoder.json.encodeToString(api.artist(source.guid)))
                is CatalogIndexSource.AlbumDetail ->
                    RawIndex(ApiDecoder.json.encodeToString(api.album(source.guid)))
                CatalogIndexSource.Genres ->
                    RawIndex(ApiDecoder.json.encodeToString(api.genres(page = 1, size = 500).list))
                CatalogIndexSource.SharedLibraries ->
                    RawIndex(ApiDecoder.json.encodeToString(api.sharedLibraries()))
                is CatalogIndexSource.TrackMetadata ->
                    RawIndex(ApiDecoder.json.encodeToString(api.metadata(source.guid)))
            }
        }

    override fun <T> decodeIndex(source: CatalogIndexSource<T>, rawJson: String): T {
        val decoded: Any = when (source) {
            CatalogIndexSource.Playlists ->
                ApiDecoder.json.decodeFromString<List<PlaylistDto>>(rawJson).map(PlaylistDto::toDomain)
            is CatalogIndexSource.PlaylistDetail ->
                ApiDecoder.json.decodeFromString<PlaylistDetailDto>(rawJson).toDomain()
            is CatalogIndexSource.ArtistDetail ->
                ApiDecoder.json.decodeFromString<ArtistDto>(rawJson).toDomain()
            is CatalogIndexSource.AlbumDetail ->
                ApiDecoder.json.decodeFromString<AlbumDto>(rawJson).toDomain()
            CatalogIndexSource.Genres ->
                ApiDecoder.json.decodeFromString<List<GenreDto>>(rawJson)
                    .map { Genre(CollectionGuid(it.guid), it.name, it.coverId, it.trackCount) }
            CatalogIndexSource.SharedLibraries ->
                ApiDecoder.json.decodeFromString<List<SharedLibraryDto>>(rawJson).map(SharedLibraryDto::toDomain)
            is CatalogIndexSource.TrackMetadata ->
                ApiDecoder.json.decodeFromString<TrackMetadataDto>(rawJson).toDomain()
        }
        // 同上：来源与领域类型一一对应。
        @Suppress("UNCHECKED_CAST")
        return decoded as T
    }

    override suspend fun searchTracks(query: String, page: Int, size: Int): DecodedPage<Track> =
        session.authenticated { it.searchTracks(query, page, size) }
            .toDecodedPage(SEARCH_SORT, TrackDto::toDomain)

    override suspend fun searchArtists(query: String, page: Int, size: Int): DecodedPage<Artist> =
        session.authenticated { it.searchArtists(query, page, size) }
            .toDecodedPage(SEARCH_SORT, ArtistDto::toDomain)

    override suspend fun searchAlbums(query: String, page: Int, size: Int): DecodedPage<Album> =
        session.authenticated { it.searchAlbums(query, page, size) }
            .toDecodedPage(SEARCH_SORT, AlbumDto::toDomain)

    override suspend fun favoriteTracks(page: Int, size: Int): DecodedPage<Track> =
        session.authenticated { it.favoriteTracks(page, size) }
            .toDecodedPage(FAVORITE_SORT, TrackDto::toFavoriteDomain)

    override suspend fun recentTracks(page: Int, size: Int): DecodedPage<Track> =
        session.authenticated { it.playHistory(page, size) }
            .toDecodedPage(RECENT_SORT, TrackDto::toDomain)

    override suspend fun playlistTrackCounts(guids: List<String>): Map<String, Int> =
        session.authenticated { it.playlistBatchDetail(guids) }
            .associate { dto -> dto.guid to dto.trackCount }

    override suspend fun setFavorite(trackGuid: String, favorite: Boolean) {
        session.authenticated { api ->
            if (favorite) api.createFavorite(trackGuid) else api.deleteFavorite(trackGuid)
        }
    }

    /**
     * 新建歌单：`coverId` 由飞牛约定（`playlist_<32位hex>`），所以在后端生成；
     * guid 由服务端下发。
     */
    override suspend fun createPlaylist(name: String): Playlist {
        val coverId = newPlaylistCoverId()
        val guid = session.authenticated { it.createPlaylist(coverId, name) }
        return Playlist(CollectionGuid(guid), name, coverId)
    }

    override suspend fun addToPlaylist(playlistGuid: String, trackGuid: String) {
        session.authenticated { it.addToPlaylist(playlistGuid, listOf(trackGuid)) }
    }

    override suspend fun removeFromPlaylist(playlistGuid: String, trackGuid: String) {
        session.authenticated { it.removeFromPlaylist(playlistGuid, listOf(trackGuid)) }
    }

    /** 原始歌词响应体（`LyricListDto` 的 JSON）：与改动前写进缓存/本地库的内容一致。 */
    override suspend fun lyricsRaw(trackGuid: String): String =
        ApiDecoder.json.encodeToString(session.authenticated { it.lyrics(trackGuid) })

    override fun decodeLyrics(rawJson: String): LyricDocument? =
        selectLyricDocument(ApiDecoder.json.decodeFromString<LyricListDto>(rawJson))

    override suspend fun startRoam(): RoamWindow? =
        session.authenticated { it.roamStart(session.deviceId) }?.let {
            RoamWindow(null, it.current.toDomain(), it.next?.toDomain())
        }

    override suspend fun nextRoam(roamId: String): RoamWindow =
        session.authenticated { it.roamNext(session.deviceId, roamId).toDomain() }

    override suspend fun previousRoam(roamId: String): RoamWindow =
        session.authenticated { it.roamPrevious(session.deviceId, roamId).toDomain() }

    private fun newPlaylistCoverId(): String {
        val hex = "0123456789abcdef"
        return "playlist_" + buildString { repeat(32) { append(hex[Random.nextInt(hex.length)]) } }
    }

    private companion object {
        const val SEARCH_SORT = "relevance"
        const val FAVORITE_SORT = "favoriteAt,desc"
        const val RECENT_SORT = "recent"
    }
}

/** 收藏列表里的曲目一律视为已收藏（与服务端返回值无关，与改动前一致）。 */
internal fun TrackDto.toFavoriteDomain(): Track = toDomain().copy(isFavorite = true)

/**
 * 歌词版本选择（与改动前 `MusicRepository.decodeLyrics` 的优先级一致）：
 * 服务端标定的 preferred → 标了 LRC 的 → 第一条。
 */
internal fun selectLyricDocument(response: LyricListDto): LyricDocument? {
    val selected: LyricDto? = response.list.firstOrNull { it.guid == response.preferred }
        ?: response.list.firstOrNull { it.isLRC }
        ?: response.list.firstOrNull()
    return selected?.toDomain()
}

/** 分页响应 → 可缓存的原始页（`rawJson` 与抽取前写进缓存的内容逐字一致）。 */
private inline fun <reified Dto> SortedPageListDto<Dto>.toRawPage(page: Int, pageSize: Int): RawPage =
    RawPage(
        rawJson = ApiDecoder.json.encodeToString(this),
        page = page,
        pageSize = pageSize,
        total = total,
        sort = sort,
    )

private inline fun <reified Dto, Domain> decodeSortedPage(
    rawJson: String,
    transform: (Dto) -> Domain,
): DecodedPage<Domain> {
    val page = ApiDecoder.json.decodeFromString<SortedPageListDto<Dto>>(rawJson)
    return DecodedPage(page.list.map(transform), page.total, page.sort)
}

private inline fun <reified Dto, Domain> SortedPageListDto<Dto>.toDecodedPage(
    sortKey: String,
    transform: (Dto) -> Domain,
): DecodedPage<Domain> = DecodedPage(list.map(transform), total, sortKey)

private inline fun <reified Dto, Domain> PageListDto<Dto>.toDecodedPage(
    sortKey: String,
    transform: (Dto) -> Domain,
): DecodedPage<Domain> = DecodedPage(list.map(transform), total, sortKey)

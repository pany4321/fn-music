package com.fnmusic.tv.core.data.backend

import com.fnmusic.tv.core.data.api.ApiDecoder
import com.fnmusic.tv.core.data.repository.SessionRepository
import com.fnmusic.tv.core.model.Album
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.AppException
import com.fnmusic.tv.core.model.Artist
import com.fnmusic.tv.core.model.CollectionGuid
import com.fnmusic.tv.core.model.Genre
import com.fnmusic.tv.core.model.LyricDocument
import com.fnmusic.tv.core.model.Playlist
import com.fnmusic.tv.core.model.RoamNode
import com.fnmusic.tv.core.model.RoamWindow
import com.fnmusic.tv.core.model.ServerKind
import com.fnmusic.tv.core.model.StreamMode
import com.fnmusic.tv.core.model.StreamPlan
import com.fnmusic.tv.core.model.SharedLibrary
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.User
import com.fnmusic.tv.core.model.UserGuid

/**
 * Jellyfin 后端：实现 [MusicBackend]，与飞牛的差异都在这里消化。
 *
 * - **没有服务端漫游** → 客户端漫游：`SortBy=Random` 取一批种子，本地维护 prev/current/next 游标，
 *   仍然合成 `RoamWindow`，播放内核一行都不用改；
 * - **播放 URL 要自己拼**（PlaybackInfo 不返回 URL）：能直连就直连，否则 HLS 转码；
 * - **收藏没有"收藏时间"排序**（用 DateCreated）；
 * - **删歌单条目要用条目 id**（`PlaylistItemId` → `Track.playlistEntryId`）；
 * - **歌词**：`/Audio/{id}/Lyrics` 的 ticks → LRC，404 = 该曲无歌词（交给在线歌词源）。
 */
internal class JellyfinMusicBackend(
    private val session: SessionRepository,
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

    override suspend fun me(): User = call { api.me() }.let { User(UserGuid(it.Id), it.Name, null) }

    override fun artworkUrl(coverId: String, variantWidth: Int?): String =
        api.imageUrl(coverId, variantWidth)

    override suspend fun artwork(coverId: String, variantWidth: Int?): ByteArray =
        call { api.imageBytes(coverId, variantWidth) }

    override fun directStreamUrl(track: Track): String = api.directStreamUrl(track.guid.value)

    /**
     * 能直连就直连，否则走 HLS 转码。
     * 判定依据实测：`PlaybackInfo` 只给能力位与容器，URL 由客户端拼。
     */
    override suspend fun streamPlan(track: Track): StreamPlan {
        val item = runCatching { api.item(track.guid.value, userId) }.getOrNull() ?: return directPlan(track)
        val source = item.MediaSources.firstOrNull()
        val container = (source?.Container ?: item.Container)?.lowercase()
        return when {
            source == null -> directPlan(track)
            source.SupportsDirectPlay && container in DIRECT_PLAYABLE_CONTAINERS -> directPlan(track)
            source.SupportsTranscoding -> StreamPlan(api.hlsTranscodeUrl(track.guid.value), StreamMode.Hls)
            else -> directPlan(track)
        }
    }

    /**
     * 批量装队列时用：不联网，直接看条目上已知的容器
     * （`audioFormat` 就是 `MediaSources[0].Container`）。白名单外的走 HLS 转码，
     * 避免整条队列里偶尔一首放不出来。
     */
    override fun queueStreamPlan(track: Track): StreamPlan =
        if (track.audioFormat?.lowercase() in DIRECT_PLAYABLE_CONTAINERS) {
            directPlan(track)
        } else {
            StreamPlan(api.hlsTranscodeUrl(track.guid.value), StreamMode.Hls)
        }

    override suspend fun randomTracks(size: Int): List<Track> =
        call { api.randomTracks(userId, size.coerceAtLeast(1)) }
            .map(JellyfinItemDto::toTrack)
            // 服务端已是随机顺序，这里只把相邻同专辑的拆开：列表里"连着好几首一张碟"很扎眼。
            .spreadByKey { it.albumName }

    // ---- 目录：分页 ----

    override suspend fun catalogPage(source: CatalogPageSource<*>, page: Int, size: Int): RawPage {
        val response = call { fetchPage(source, page, size) }
        return RawPage(
            rawJson = ApiDecoder.json.encodeToString(response),
            page = page,
            pageSize = size,
            total = response.TotalRecordCount,
            sort = sortKeyOf(source),
        )
    }

    override fun <T> decodePage(source: CatalogPageSource<T>, rawJson: String): DecodedPage<T> {
        val response = ApiDecoder.json.decodeFromString<JellyfinItemsDto>(rawJson)
        val decoded: DecodedPage<*> = when (source) {
            is CatalogPageSource.PlaylistTracks -> response.decoded("playlist") { it.toTrack() }
            CatalogPageSource.Artists -> response.decoded("sortName,asc") { it.toArtist() }
            is CatalogPageSource.ArtistTracks -> response.decoded("dateCreated,desc") { it.toTrack() }
            is CatalogPageSource.ArtistAlbums -> response.decoded("dateCreated,desc") { it.toAlbum() }
            CatalogPageSource.Albums -> response.decoded("sortName,asc") { it.toAlbum() }
            is CatalogPageSource.AlbumTracks -> response.decoded("trackNo,asc") { it.toTrack() }
            CatalogPageSource.AllTracks -> response.decoded("dateCreated,desc") { it.toTrack() }
            CatalogPageSource.RecentlyAdded -> response.decoded("dateCreated,desc") { it.toTrack() }
            is CatalogPageSource.GenreTracks -> response.decoded("sortName,asc") { it.toTrack() }
        }
        // 每个来源只有一种领域类型（见 CatalogPageSource 的定义），所以这里的转换是安全的。
        @Suppress("UNCHECKED_CAST")
        return decoded as DecodedPage<T>
    }

    // ---- 目录：索引 ----

    override suspend fun catalogIndex(source: CatalogIndexSource<*>): RawIndex {
        val rawJson = call {
            when (source) {
                CatalogIndexSource.Playlists -> ApiDecoder.json.encodeToString(
                    api.items(userId, includeItemTypes = PLAYLIST, sortBy = SORT_NAME, limit = INDEX_LIMIT)
                        // 音乐 App 只列音频歌单：Jellyfin 的歌单里混着"电影/电视剧"这类视频歌单。
                        .let { page -> page.copy(Items = page.Items.filter { it.isAudioPlaylist() }) },
                )
                is CatalogIndexSource.PlaylistDetail -> encodeItem(requireItem(source.guid))
                is CatalogIndexSource.ArtistDetail -> encodeItem(requireItem(source.guid))
                is CatalogIndexSource.AlbumDetail -> encodeItem(requireItem(source.guid))
                CatalogIndexSource.Genres -> ApiDecoder.json.encodeToString(
                    api.musicGenres(userId, startIndex = 0, limit = GENRE_LIMIT),
                )
                CatalogIndexSource.SharedLibraries -> ApiDecoder.json.encodeToString(api.views(userId))
                is CatalogIndexSource.TrackMetadata -> encodeItem(requireItem(source.guid))
            }
        }
        return RawIndex(rawJson)
    }

    override fun <T> decodeIndex(source: CatalogIndexSource<T>, rawJson: String): T {
        val decoded: Any = when (source) {
            CatalogIndexSource.Playlists -> decodeItems(rawJson) { it.toPlaylist() }
            is CatalogIndexSource.PlaylistDetail -> decodeItem(rawJson) { it.toPlaylist() }
            is CatalogIndexSource.ArtistDetail -> decodeItem(rawJson) { it.toArtist() }
            is CatalogIndexSource.AlbumDetail -> decodeItem(rawJson) { it.toAlbum() }
            CatalogIndexSource.Genres -> decodeItems(rawJson) { it.toGenre() }
            CatalogIndexSource.SharedLibraries -> decodeItems(rawJson) { dto ->
                SharedLibrary(
                    guid = CollectionGuid(dto.Id),
                    name = dto.Name,
                    accessStatus = 0,
                    updatedAt = 0L,
                )
            }
            is CatalogIndexSource.TrackMetadata -> decodeItem(rawJson) { it.toTrack() }
        }
        @Suppress("UNCHECKED_CAST")
        return decoded as T
    }

    // ---- 搜索 / 收藏 / 最近播放（不缓存） ----

    override suspend fun searchTracks(query: String, page: Int, size: Int): DecodedPage<Track> {
        val merged = searchMergedItems(query, page, size)
        return DecodedPage(merged.Items.map { it.toTrack() }, merged.TotalRecordCount, SEARCH_SORT)
    }

    override suspend fun searchArtists(query: String, page: Int, size: Int): DecodedPage<Artist> {
        // /Items?MusicArtist 集合基本无产出（实测 74 个文件夹派生条目，搜真歌手名为 0）；
        // 歌手主力查询是目录歌手页同源的 /Artists/AlbumArtists?searchTerm=（实测命中真条目、
        // 带封面，其 guid 过滤音轨有效）。两源合并去重。
        val direct = runSearchItems(query, page, size, MUSIC_ARTIST)
        val facet = call { api.albumArtists(userId, startIndex = offsetOf(page, size), limit = size, searchTerm = query) }
        return mergePages(listOf(direct, facet), page, size)
            .let { DecodedPage(it.Items.map(JellyfinItemDto::toArtist), it.TotalRecordCount, SEARCH_SORT) }
    }

    override suspend fun searchAlbums(query: String, page: Int, size: Int): DecodedPage<Album> {
        val merged = searchAlbumsMergedItems(query, page, size)
        return DecodedPage(merged.Items.map { it.toAlbum() }, merged.TotalRecordCount, SEARCH_SORT)
    }

    override suspend fun favoriteTracks(page: Int, size: Int): DecodedPage<Track> {
        val response = call {
            api.items(
                userId = userId,
                includeItemTypes = AUDIO,
                filters = FILTER_FAVORITE,
                sortBy = SORT_DATE_CREATED,
                sortOrder = SORT_DESCENDING,
                startIndex = offsetOf(page, size),
                limit = size,
            )
        }
        return response.decoded(FAVORITE_SORT) { it.toTrack().copy(isFavorite = true) }
    }

    override suspend fun recentTracks(page: Int, size: Int): DecodedPage<Track> {
        val response = call {
            api.items(
                userId = userId,
                includeItemTypes = AUDIO,
                filters = FILTER_PLAYED,
                sortBy = SORT_DATE_PLAYED,
                sortOrder = SORT_DESCENDING,
                startIndex = offsetOf(page, size),
                limit = size,
            )
        }
        return response.decoded(RECENT_SORT) { it.toTrack() }
    }

    override suspend fun playlistTrackCounts(guids: List<String>): Map<String, Int> {
        if (guids.isEmpty()) return emptyMap()
        val response = call {
            api.items(
                userId = userId,
                includeItemTypes = PLAYLIST,
                ids = guids.joinToString(","),
                limit = guids.size,
            )
        }
        return response.Items.associate { it.Id to (it.RecursiveItemCount ?: it.ChildCount ?: 0) }
    }

    // ---- 变更 ----

    override suspend fun setFavorite(trackGuid: String, favorite: Boolean) {
        call { api.setFavorite(userId, trackGuid, favorite) }
    }

    /**
     * 新建歌单并返回它。
     * ⚠️ 实测：`POST /Playlists` 只回 `{"Id":"…"}`（不是完整条目），
     * 所以名字用我们传进去的那个，别去解返回体（否则会拿到空名字）。
     */
    override suspend fun createPlaylist(name: String): Playlist {
        val created = call { api.createPlaylist(name, userId) }
        return Playlist(CollectionGuid(created.Id), name, coverId = null, trackCount = 0)
    }

    override suspend fun addToPlaylist(playlistGuid: String, trackGuid: String) {
        call { api.addToPlaylist(playlistGuid, listOf(trackGuid), userId) }
    }

    /**
     * 删歌单条目：Jellyfin 要的是条目 id（`PlaylistItemId`）。
     * 优先用 [Track.playlistEntryId]（歌单页取回的曲目自带），没有就回源查一次。
     */
    override suspend fun removeFromPlaylist(playlistGuid: String, track: Track) {
        val entryId = track.playlistEntryId?.takeIf(String::isNotBlank)
            ?: call { findPlaylistEntryId(playlistGuid, track.guid.value) }
            ?: throw AppException(AppError.NotFound)
        call { api.removeFromPlaylist(playlistGuid, listOf(entryId)) }
    }

    // ---- 歌词 ----

    /**
     * 原始歌词：端点 404 = 该曲没有歌词（不是错误），此时缓存一份空歌词，
     * [decodeLyrics] 解成 null 交给在线歌词源。
     */
    override suspend fun lyricsRaw(trackGuid: String): String {
        val lyrics = call { api.lyrics(trackGuid) } ?: JellyfinLyricsDto()
        return ApiDecoder.json.encodeToString(lyrics)
    }

    override fun decodeLyrics(rawJson: String): LyricDocument? =
        ApiDecoder.json.decodeFromString<JellyfinLyricsDto>(rawJson).toLyricDocument()

    // ---- 客户端漫游（Jellyfin 没有服务端 roam-*） ----

    private val roamLock = Any()
    private var roamSeed: List<Track> = emptyList()
    private var roamIndex = -1

    override suspend fun startRoam(): RoamWindow? {
        val seed = fetchRoamSeed(excludeGuid = null)
        if (seed.isEmpty()) return null
        synchronized(roamLock) {
            roamSeed = seed
            roamIndex = 0
            return roamWindow()
        }
    }

    override suspend fun nextRoam(roamId: String): RoamWindow {
        val advanced = synchronized(roamLock) { moveCursor(roamId, step = 1) }
        if (!advanced) replaceRoamSeed(excludeGuid = roamId, startAtEnd = false)
        return synchronized(roamLock) { roamWindow() }
    }

    override suspend fun previousRoam(roamId: String): RoamWindow {
        val advanced = synchronized(roamLock) { moveCursor(roamId, step = -1) }
        if (!advanced) replaceRoamSeed(excludeGuid = roamId, startAtEnd = true)
        return synchronized(roamLock) { roamWindow() }
    }

    private fun moveCursor(roamId: String, step: Int): Boolean {
        val at = roamSeed.indexOfFirst { it.guid.value == roamId }
        val candidate = (if (at >= 0) at else roamIndex) + step
        if (candidate !in roamSeed.indices) return false
        roamIndex = candidate
        return true
    }

    /** 走到这一批种子的边界：换一批随机曲目（排除当前这首，保证 roamId 一定变化）。 */
    private suspend fun replaceRoamSeed(excludeGuid: String, startAtEnd: Boolean) {
        val seed = fetchRoamSeed(excludeGuid = excludeGuid)
        if (seed.isEmpty()) throw AppException(AppError.Empty)
        synchronized(roamLock) {
            roamSeed = seed
            roamIndex = if (startAtEnd) seed.lastIndex else 0
        }
    }

    private fun roamWindow(): RoamWindow {
        val current = roamSeed.getOrNull(roamIndex) ?: throw AppException(AppError.Empty)
        return RoamWindow(
            previous = roamSeed.getOrNull(roamIndex - 1)?.let { RoamNode(it.guid.value, it) },
            current = RoamNode(current.guid.value, current),
            next = roamSeed.getOrNull(roamIndex + 1)?.let { RoamNode(it.guid.value, it) },
        )
    }

    private suspend fun fetchRoamSeed(excludeGuid: String?): List<Track> =
        call { api.randomTracks(userId, ROAM_SEED_SIZE) }
            .map(JellyfinItemDto::toTrack)
            // 漫游是"一首接一首"顺着这批种子走的：同一张碟连播会很出戏，先拆开。
            .spreadByKey { it.albumName }
            .filter { it.guid.value != excludeGuid }

    // ---- 内部工具 ----

    private suspend fun fetchPage(source: CatalogPageSource<*>, page: Int, size: Int): JellyfinItemsDto {
        val startIndex = offsetOf(page, size)
        val ordering = orderOf(source)
        return when (source) {
            is CatalogPageSource.PlaylistTracks ->
                api.playlistItems(source.guid, userId, startIndex, size)
            CatalogPageSource.Artists -> api.albumArtists(userId, startIndex, size)
            is CatalogPageSource.ArtistTracks -> api.items(
                userId = userId,
                includeItemTypes = AUDIO,
                artistIds = source.guid,
                sortBy = ordering.sortBy,
                sortOrder = ordering.sortOrder,
                startIndex = startIndex,
                limit = size,
            )
            is CatalogPageSource.ArtistAlbums -> api.items(
                userId = userId,
                includeItemTypes = MUSIC_ALBUM,
                albumArtistIds = source.guid,
                sortBy = ordering.sortBy,
                sortOrder = ordering.sortOrder,
                startIndex = startIndex,
                limit = size,
            )
            CatalogPageSource.Albums -> api.items(
                userId = userId,
                includeItemTypes = MUSIC_ALBUM,
                sortBy = ordering.sortBy,
                sortOrder = ordering.sortOrder,
                startIndex = startIndex,
                limit = size,
            )
            is CatalogPageSource.AlbumTracks -> api.items(
                userId = userId,
                includeItemTypes = AUDIO,
                albumIds = source.guid,
                sortBy = ordering.sortBy,
                sortOrder = ordering.sortOrder,
                startIndex = startIndex,
                limit = size,
            )
            CatalogPageSource.AllTracks -> api.items(
                userId = userId,
                includeItemTypes = AUDIO,
                sortBy = ordering.sortBy,
                sortOrder = ordering.sortOrder,
                startIndex = startIndex,
                limit = size,
            )
            CatalogPageSource.RecentlyAdded -> api.items(
                userId = userId,
                includeItemTypes = AUDIO,
                sortBy = ordering.sortBy,
                sortOrder = ordering.sortOrder,
                startIndex = startIndex,
                limit = size,
            )
            is CatalogPageSource.GenreTracks -> api.items(
                userId = userId,
                includeItemTypes = AUDIO,
                genreIds = source.guid,
                sortBy = ordering.sortBy,
                sortOrder = ordering.sortOrder,
                startIndex = startIndex,
                limit = size,
            )
        }
    }

    private suspend fun <Domain> search(
        query: String,
        page: Int,
        size: Int,
        types: String,
        map: (JellyfinItemDto) -> Domain,
    ): DecodedPage<Domain> {
        val response = call {
            api.items(
                userId = userId,
                includeItemTypes = types,
                searchTerm = query,
                sortBy = SORT_NAME,
                startIndex = offsetOf(page, size),
                limit = size,
            )
        }
        return response.decoded(SEARCH_SORT, map)
    }

    /**
     * 音轨区搜索：对齐飞牛 `search/track` 的**全字段匹配**语义（实测：歌手名/专辑名都能命中音轨）。
     *
     * Jellyfin 的 `SearchTerm` 只匹配条目自身的 Name——搜歌手名或专辑名时音轨区恒为空（实测 0 条）。
     * 因此并上两条补充查询：
     * - 专辑名命中的专辑（`MusicAlbum` + SearchTerm）→ `AlbumIds` 过滤音轨（实测 25 条/张）；
     * - 歌手名命中的歌手（`MusicArtist` + SearchTerm）→ `ArtistIds`/`AlbumArtistIds` 过滤音轨
     *   （元数据齐全的库生效；实测本库音轨缺歌手元数据时为 0，保留以不亏）。
     * 三源按 guid 去重、本地按名称排序后切窗——搜索 UI 只取第一页，无跨页漂移问题。
     */
    private suspend fun searchMergedItems(query: String, page: Int, size: Int): JellyfinItemsDto {
        val direct = runSearchItems(query, page, size, AUDIO)
        val albumItems = runSearchItems(query, 1, RELATED_SEARCH_LIMIT, MUSIC_ALBUM)
        val albumIds = albumItems.Items.take(RELATED_SEARCH_LIMIT).map { it.Id }
        val artistItems = runSearchItems(query, 1, RELATED_SEARCH_LIMIT, MUSIC_ARTIST)
        val artistIds = artistItems.Items.take(RELATED_SEARCH_LIMIT).map { it.Id }

        val extras = buildList {
            if (albumIds.isNotEmpty()) {
                add(
                    call {
                        api.items(
                            userId = userId,
                            includeItemTypes = AUDIO,
                            albumIds = albumIds.joinToString(","),
                            sortBy = SORT_NAME,
                            startIndex = 0,
                            limit = offsetOf(page, size) + size,
                        )
                    },
                )
            }
            if (artistIds.isNotEmpty()) {
                add(
                    call {
                        api.items(
                            userId = userId,
                            includeItemTypes = AUDIO,
                            artistIds = artistIds.joinToString(","),
                            sortBy = SORT_NAME,
                            startIndex = 0,
                            limit = offsetOf(page, size) + size,
                        )
                    },
                )
                add(
                    call {
                        api.items(
                            userId = userId,
                            includeItemTypes = AUDIO,
                            albumArtistIds = artistIds.joinToString(","),
                            sortBy = SORT_NAME,
                            startIndex = 0,
                            limit = offsetOf(page, size) + size,
                        )
                    },
                )
            }
        }
        return mergePages(listOf(direct) + extras, page, size)
    }

    /**
     * 专辑区搜索：对齐飞牛 `search/album` 的匹配面（实测歌手名也能命中专辑）。
     * `SearchTerm`（专辑名）并上「歌手名命中的歌手的专辑」。
     */
    private suspend fun searchAlbumsMergedItems(query: String, page: Int, size: Int): JellyfinItemsDto {
        val direct = runSearchItems(query, page, size, MUSIC_ALBUM)
        val artistItems = runSearchItems(query, 1, RELATED_SEARCH_LIMIT, MUSIC_ARTIST)
        val artistIds = artistItems.Items.take(RELATED_SEARCH_LIMIT).map { it.Id }
        val extras = if (artistIds.isEmpty()) {
            emptyList()
        } else {
            listOf(
                call {
                    api.items(
                        userId = userId,
                        includeItemTypes = MUSIC_ALBUM,
                        albumArtistIds = artistIds.joinToString(","),
                        sortBy = SORT_NAME,
                        startIndex = 0,
                        limit = offsetOf(page, size) + size,
                    )
                },
            )
        }
        return mergePages(listOf(direct) + extras, page, size)
    }

    private suspend fun runSearchItems(query: String, page: Int, size: Int, types: String): JellyfinItemsDto =
        call {
            api.items(
                userId = userId,
                includeItemTypes = types,
                searchTerm = query,
                sortBy = SORT_NAME,
                startIndex = offsetOf(page, size),
                limit = size,
            )
        }

    private fun mergePages(pages: List<JellyfinItemsDto>, page: Int, size: Int): JellyfinItemsDto =
        mergeSearchPages(pages, page, size)

    private suspend fun requireItem(guid: String): JellyfinItemDto =
        api.item(guid, userId) ?: throw AppException(AppError.NotFound)

    private fun encodeItem(item: JellyfinItemDto): String = ApiDecoder.json.encodeToString(item)

    private fun directPlan(track: Track): StreamPlan =
        StreamPlan(api.directStreamUrl(track.guid.value), StreamMode.Direct)

    /** 歌单里找一首曲目的条目 id（`Track.playlistEntryId` 缺失时的回源查询）。 */
    private suspend fun findPlaylistEntryId(playlistGuid: String, trackGuid: String): String? {
        var startIndex = 0
        while (true) {
            val page = api.playlistItems(playlistGuid, userId, startIndex, PLAYLIST_SCAN_SIZE)
            page.Items.firstOrNull { it.Id == trackGuid }?.let { return it.PlaylistItemId }
            if (page.Items.isEmpty() || startIndex + page.Items.size >= page.TotalRecordCount) return null
            startIndex += page.Items.size
        }
    }

    /** 后端调用统一出口：401/账号停用要通知会话（与飞牛的 `authenticated` 同一套反应）。 */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (cause: AppException) {
        session.reportRequestFailure(cause.error)
        throw cause
    }

    private data class Ordering(val sortBy: String?, val sortOrder: String?)

    private companion object {
        const val AUDIO = "Audio"
        const val MUSIC_ALBUM = "MusicAlbum"
        const val MUSIC_ARTIST = "MusicArtist"
        const val PLAYLIST = "Playlist"
        const val SORT_NAME = "SortName"
        const val SORT_DATE_CREATED = "DateCreated"
        const val SORT_DATE_PLAYED = "DatePlayed"
        const val SORT_DESCENDING = "Descending"
        const val SORT_ASCENDING = "Ascending"
        const val FILTER_FAVORITE = "IsFavorite"
        const val FILTER_PLAYED = "IsPlayed"
        const val SEARCH_SORT = "relevance"

        /** 合并查询时，专辑/歌手名命中条目的收集上限（控制 URL 长度与请求数）。 */
        const val RELATED_SEARCH_LIMIT = 30
        const val FAVORITE_SORT = "dateCreated,desc"
        const val RECENT_SORT = "datePlayed,desc"
        const val ROAM_SEED_SIZE = 30
        const val GENRE_LIMIT = 500
        const val INDEX_LIMIT = 500
        const val PLAYLIST_SCAN_SIZE = 200

        /** 每个目录来源固定的排序键（后端不透明游标：它会进队列/快照身份）。 */
        fun sortKeyOf(source: CatalogPageSource<*>): String = when (source) {
            is CatalogPageSource.PlaylistTracks -> PLAYLIST_SORT
            CatalogPageSource.Artists -> "sortName,asc"
            is CatalogPageSource.ArtistTracks -> "dateCreated,desc"
            is CatalogPageSource.ArtistAlbums -> "dateCreated,desc"
            CatalogPageSource.Albums -> "sortName,asc"
            is CatalogPageSource.AlbumTracks -> "trackNo,asc"
            CatalogPageSource.AllTracks -> "dateCreated,desc"
            CatalogPageSource.RecentlyAdded -> "dateCreated,desc"
            is CatalogPageSource.GenreTracks -> "sortName,asc"
        }

        const val PLAYLIST_SORT = "playlist"

        fun orderOf(source: CatalogPageSource<*>): Ordering = when (source) {
            CatalogPageSource.Artists -> Ordering(SORT_NAME, SORT_ASCENDING)
            CatalogPageSource.Albums -> Ordering(SORT_NAME, SORT_ASCENDING)
            is CatalogPageSource.GenreTracks -> Ordering(SORT_NAME, SORT_ASCENDING)
            is CatalogPageSource.AlbumTracks -> Ordering("ParentIndexNumber,IndexNumber", SORT_ASCENDING)
            is CatalogPageSource.PlaylistTracks -> Ordering(null, null)
            else -> Ordering(SORT_DATE_CREATED, SORT_DESCENDING)
        }

        fun offsetOf(page: Int, size: Int): Int = (page.coerceAtLeast(1) - 1) * size

        /** 能与 ExoPlayer 直连的容器（其余交给服务端转码）。 */
        val DIRECT_PLAYABLE_CONTAINERS = setOf(
            "flac", "mp3", "aac", "m4a", "mp4", "ogg", "oga", "opus", "wav", "pcm", "mka",
        )
    }
}

/** 音频歌单（`MediaType` 缺省时按音频处理，宁可多留也不要漏）。 */
private fun JellyfinItemDto.isAudioPlaylist(): Boolean =
    MediaType == null || MediaType.equals("Audio", ignoreCase = true)

/** 搜索多源合并：按条目 id 去重、名称排序，切出当前页窗口；total = 去重后的总条数。 */
internal fun mergeSearchPages(pages: List<JellyfinItemsDto>, page: Int, size: Int): JellyfinItemsDto {
    val seen = mutableSetOf<String>()
    val merged = pages.asSequence()
        .flatMap { it.Items.asSequence() }
        .filter { seen.add(it.Id) }
        .sortedBy { it.Name?.lowercase().orEmpty() }
        .toList()
    val offset = ((page.coerceAtLeast(1) - 1) * size)
    val window = merged.drop(offset).take(size)
    return JellyfinItemsDto(Items = window, TotalRecordCount = merged.size)
}

private fun <Domain> JellyfinItemsDto.decoded(
    sort: String,
    map: (JellyfinItemDto) -> Domain,
): DecodedPage<Domain> = DecodedPage(Items.map(map), TotalRecordCount, sort)

private fun <Domain> decodeItems(
    rawJson: String,
    map: (JellyfinItemDto) -> Domain,
): List<Domain> = ApiDecoder.json.decodeFromString<JellyfinItemsDto>(rawJson).Items.map(map)

private fun <Domain> decodeItem(
    rawJson: String,
    map: (JellyfinItemDto) -> Domain,
): Domain = map(ApiDecoder.json.decodeFromString<JellyfinItemDto>(rawJson))

package com.fnmusic.tv.core.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import com.fnmusic.tv.core.data.backend.CatalogIndexSource
import com.fnmusic.tv.core.data.backend.CatalogPageSource
import com.fnmusic.tv.core.data.backend.DecodedPage
import com.fnmusic.tv.core.data.backend.FnOsMusicBackend
import com.fnmusic.tv.core.data.backend.MusicBackend
import com.fnmusic.tv.core.data.api.isRetryableRequestFailure
import com.fnmusic.tv.core.data.local.CachedIndexEntity
import com.fnmusic.tv.core.data.local.CachedLyricEntity
import com.fnmusic.tv.core.data.local.CachedPageEntity
import com.fnmusic.tv.core.data.local.LocalStore
import com.fnmusic.tv.core.data.preferences.AppPreferences
import com.fnmusic.tv.core.model.Album
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.AppException
import com.fnmusic.tv.core.model.Artist
import com.fnmusic.tv.core.model.CoverVariant
import com.fnmusic.tv.core.model.Genre
import com.fnmusic.tv.core.model.LyricDocument
import com.fnmusic.tv.core.model.Page
import com.fnmusic.tv.core.model.PlaybackTrack
import com.fnmusic.tv.core.model.Playlist
import com.fnmusic.tv.core.model.RoamWindow
import com.fnmusic.tv.core.model.SharedLibrary
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.playback.QueueSource
import com.fnmusic.tv.core.model.preferences.CacheUsage
import com.fnmusic.tv.core.lyrics.DefaultLyricsSources
import com.fnmusic.tv.core.lyrics.LyricsMatchCoordinator
import com.fnmusic.tv.core.lyrics.LyricsMatchRequest
import com.fnmusic.tv.core.lyrics.LyricsMatchResult
import com.fnmusic.tv.core.lyrics.hasUsableLines
import com.fnmusic.tv.core.lyrics.parseLyrics
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAX_ARTWORK_DOWNLOAD_BYTES = 20 * 1024 * 1024
private const val MAX_ARTWORK_EDGE = 8_192
private const val MAX_ARTWORK_PIXELS = 16_000_000L
private const val ARTWORK_VALIDATION_LONG_EDGE = 128

internal data class ArtworkBounds(val width: Int, val height: Int)

data class FavoriteLibraryState(
    val namespace: String? = null,
    val statuses: Map<String, Boolean> = emptyMap(),
    val pending: Set<String> = emptySet(),
    val revision: Long = 0L,
    val error: AppError? = null,
)

internal data class FavoriteMutation(
    val namespace: String,
    val trackGuid: String,
    val confirmed: Boolean,
    val desired: Boolean,
)

internal fun FavoriteLibraryState.bindNamespace(namespace: String): FavoriteLibraryState =
    if (this.namespace == namespace) this else FavoriteLibraryState(namespace = namespace)

internal fun FavoriteLibraryState.observe(trackGuid: String, favorite: Boolean): FavoriteLibraryState =
    if (trackGuid in pending) this else copy(statuses = statuses + (trackGuid to favorite))

internal fun FavoriteLibraryState.beginMutation(
    trackGuid: String,
    fallbackFavorite: Boolean,
): Pair<FavoriteLibraryState, FavoriteMutation> {
    val boundNamespace = checkNotNull(namespace)
    val confirmed = statuses[trackGuid] ?: fallbackFavorite
    val mutation = FavoriteMutation(boundNamespace, trackGuid, confirmed, !confirmed)
    return copy(
        statuses = statuses + (trackGuid to mutation.desired),
        pending = pending + trackGuid,
        error = null,
    ) to mutation
}

internal fun FavoriteLibraryState.complete(mutation: FavoriteMutation): FavoriteLibraryState =
    if (namespace != mutation.namespace) this else copy(
        statuses = statuses + (mutation.trackGuid to mutation.desired),
        pending = pending - mutation.trackGuid,
        revision = revision + 1L,
        error = null,
    )

internal fun FavoriteLibraryState.rollback(
    mutation: FavoriteMutation,
    error: AppError? = this.error,
): FavoriteLibraryState = if (namespace != mutation.namespace) this else copy(
    statuses = statuses + (mutation.trackGuid to mutation.confirmed),
    pending = pending - mutation.trackGuid,
    error = error,
)

internal fun isValidArtworkBytes(bytes: ByteArray): Boolean = isValidArtworkBytes(
    bytes = bytes,
    readBounds = { encoded ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
        ArtworkBounds(options.outWidth, options.outHeight)
    },
    decodeSampled = { encoded, sample ->
        BitmapFactory.decodeByteArray(
            encoded,
            0,
            encoded.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )?.let { bitmap ->
            bitmap.recycle()
            true
        } ?: false
    },
)

internal fun isValidArtworkBytes(
    bytes: ByteArray,
    readBounds: (ByteArray) -> ArtworkBounds?,
    decodeSampled: (ByteArray, Int) -> Boolean,
): Boolean {
    if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_DOWNLOAD_BYTES) return false
    val bounds = runCatching { readBounds(bytes) }.getOrNull() ?: return false
    if (
        bounds.width !in 1..MAX_ARTWORK_EDGE ||
        bounds.height !in 1..MAX_ARTWORK_EDGE ||
        bounds.width.toLong() * bounds.height > MAX_ARTWORK_PIXELS
    ) {
        return false
    }

    var sample = 1
    while (
        bounds.width / sample > ARTWORK_VALIDATION_LONG_EDGE ||
        bounds.height / sample > ARTWORK_VALIDATION_LONG_EDGE
    ) {
        sample *= 2
    }
    return runCatching { decodeSampled(bytes, sample) }.getOrDefault(false)
}

/**
 * 门面 + 缓存 + 状态：公开签名与抽取前一致，网络请求全部交给 [backend]。
 * 目录类查询一律"取原始体 → 缓存 → 由后端解码成领域模型"，所以一份缓存同时服务所有后端。
 */
class MusicRepository internal constructor(
    context: Context,
    private val session: SessionRepository,
    private val preferences: AppPreferences,
    private val localStore: LocalStore,
    metadataCapacityBytes: Int,
    repositoryScope: CoroutineScope,
    onlineLyricsMatcher: suspend (LyricsMatchRequest) -> LyricsMatchResult,
    /** 当前服务器后端：抽取阶段默认飞牛，后续按会话的服务器类型选择。 */
    private val backend: MusicBackend = FnOsMusicBackend(session),
) {
    constructor(
        context: Context,
        session: SessionRepository,
        preferences: AppPreferences,
        localStore: LocalStore,
    ) : this(
        context = context,
        session = session,
        preferences = preferences,
        localStore = localStore,
        metadataCapacityBytes = METADATA_CAPACITY_BYTES,
        repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        onlineLyricsMatcher = defaultOnlineLyricsMatcher(),
    )

    private val responses = SerializedResponseCache(metadataCapacityBytes, repositoryScope)
    private val onlineLyricsResolver = OnlineLyricsResolver(
        localStore = localStore,
        responses = responses,
        namespace = session::cacheNamespace,
        matcher = onlineLyricsMatcher,
    )
    private val favoriteMutationMutex = Mutex()
    private val _favoriteState = MutableStateFlow(FavoriteLibraryState())
    val favoriteState: StateFlow<FavoriteLibraryState> = _favoriteState.asStateFlow()

    // Derived playlist artwork: first-track cover ids per playlist guid, used when
    // a playlist carries no cover of its own. Session-lifetime, cleared with the
    // namespace.
    private val _playlistCovers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val playlistCovers: StateFlow<Map<String, List<String>>> = _playlistCovers.asStateFlow()
    private val playlistCoverFetchInFlight = mutableSetOf<String>()
    private val playlistCoverSemaphore = Semaphore(PLAYLIST_COVER_PARALLELISM)
    private val artworkCache = ArtworkCache(
        root = context.cacheDir.resolve("artwork"),
        memoryCapacityBytes = ARTWORK_MEMORY_CAPACITY_BYTES,
        diskBudgetBytes = { preferences.state.value.cacheBudget.artworkBytes },
        isValid = ::isValidArtworkBytes,
        scope = repositoryScope,
    )

    init {
        repositoryScope.launch { artworkCache.initialize() }
    }

    suspend fun playlists(): List<Playlist> = cachedIndex(CatalogIndexSource.Playlists)

    suspend fun playlist(guid: String): Playlist = cachedIndex(CatalogIndexSource.PlaylistDetail(guid))

    suspend fun playlistTracks(guid: String, page: Int) =
        cachedPage(CatalogPageSource.PlaylistTracks(guid), page, PAGE_SIZE)
            .also(::observeFavoriteTracks)

    /**
     * Cover ids of the playlist's first tracks (at most [size]), for coverless
     * playlist tiles. Failures settle to an empty list; both results are cached
     * for the session and cleared with the namespace.
     */
    suspend fun playlistCoverCandidates(guid: String, size: Int = 3): List<String> {
        _playlistCovers.value[guid]?.let { return it }
        synchronized(playlistCoverFetchInFlight) {
            if (guid in playlistCoverFetchInFlight) return _playlistCovers.value[guid].orEmpty()
            playlistCoverFetchInFlight += guid
        }
        val covers = try {
            // 首页/歌单页会为每个歌单各拉一次候选封面；不限制并发时十几二十个请求
            // 同时打向 NAS，把首页首屏一起拖慢。这里串行化到固定并发数。
            playlistCoverSemaphore.withPermit {
                val source = CatalogPageSource.PlaylistTracks(guid)
                backend.catalogPage(source, page = 1, size = COVER_CANDIDATE_PAGE_SIZE)
                    .let { backend.decodePage(source, it.rawJson).items }
                    .asSequence()
                    .mapNotNull { track -> track.coverId?.trim()?.takeIf(String::isNotEmpty) }
                    .distinct()
                    .shuffled()
                    .take(size)
                    .toList()
            }
        } catch (cause: CancellationException) {
            synchronized(playlistCoverFetchInFlight) { playlistCoverFetchInFlight -= guid }
            throw cause
        } catch (_: Exception) {
            emptyList()
        }
        synchronized(playlistCoverFetchInFlight) { playlistCoverFetchInFlight -= guid }
        _playlistCovers.value = _playlistCovers.value + (guid to covers)
        return covers
    }

    suspend fun artists(page: Int, size: Int = 50) =
        cachedPage(CatalogPageSource.Artists, page, size)

    suspend fun artist(guid: String): Artist = cachedIndex(CatalogIndexSource.ArtistDetail(guid))

    suspend fun artistTracks(guid: String, page: Int) =
        cachedPage(CatalogPageSource.ArtistTracks(guid), page, PAGE_SIZE)
            .also(::observeFavoriteTracks)

    suspend fun artistAlbums(guid: String, page: Int) =
        cachedPage(CatalogPageSource.ArtistAlbums(guid), page, PAGE_SIZE)

    /**
     * Up to [count] albums sampled across the whole library: one probe call reads
     * the total, then each album comes from a randomly picked server page so the
     * set changes on every call.
     */
    suspend fun albums(page: Int, size: Int = 50) =
        cachedPage(CatalogPageSource.Albums, page, size)

    suspend fun album(guid: String): Album = cachedIndex(CatalogIndexSource.AlbumDetail(guid))

    suspend fun albumTracks(guid: String, page: Int) =
        cachedPage(CatalogPageSource.AlbumTracks(guid), page, PAGE_SIZE)
            .also(::observeFavoriteTracks)

    suspend fun allTracks(page: Int, size: Int = 50) =
        cachedPage(CatalogPageSource.AllTracks, page, size).also(::observeFavoriteTracks)

    /** 卡片封面用的轻量随机采样：探测总数后只随机取一页（共 2 次请求）。 */
    suspend fun randomAlbumSample(size: Int = 24): List<Album> {
        val pageSize = size.coerceAtLeast(1)
        val probe = albums(page = 1, size = 1)
        val total = probe.total ?: return probe.items
        if (total <= pageSize) return albums(page = 1, size = pageSize).items
        val lastPage = (total + pageSize - 1) / pageSize
        return albums(page = Random.nextInt(1, lastPage + 1), size = pageSize).items
    }

    /**
     * 卡片封面用的轻量随机采样：探测总数后只随机取一页（共 2 次请求）。
     * [randomTracks] 为了拿到 count 首会并发拉 count 页，用在只需要 3 张封面的
     * 卡片上过重（17 次请求换 3 张封面），这里改成单页随机。
     */
    suspend fun randomTrackSample(size: Int = 24): List<Track> =
        backend.randomTracks(size).also { tracks ->
            // 与原实现一致：随机取到的歌也要参与收藏状态观察（否则首页随机行的红心不会随收藏变化）。
            observeFavoriteTracks(
                Page(items = tracks, page = 1, pageSize = tracks.size, total = tracks.size, sort = ""),
            )
        }

    suspend fun recentlyAddedTracks(page: Int, size: Int = 16) =
        cachedPage(CatalogPageSource.RecentlyAdded, page, size).also(::observeFavoriteTracks)

    suspend fun genres(): List<Genre> = cachedIndex(CatalogIndexSource.Genres)

    suspend fun genreTracks(guid: String, page: Int) =
        cachedPage(CatalogPageSource.GenreTracks(guid), page, PAGE_SIZE)
            .also(::observeFavoriteTracks)

    suspend fun searchTracks(query: String, page: Int = 1, size: Int = 20): Page<Track> =
        backend.searchTracks(query, page, size).toDomainPage(page, size)

    suspend fun searchArtists(query: String, page: Int = 1, size: Int = 20): Page<Artist> =
        backend.searchArtists(query, page, size).toDomainPage(page, size)

    suspend fun searchAlbums(query: String, page: Int = 1, size: Int = 20): Page<Album> =
        backend.searchAlbums(query, page, size).toDomainPage(page, size)

    suspend fun playlistTrackCounts(guids: List<String>): Map<String, Int> {
        if (guids.isEmpty()) return emptyMap()
        return runCatching { backend.playlistTrackCounts(guids) }.getOrDefault(emptyMap())
    }

    suspend fun favoriteTracks(page: Int): Page<Track> {
        val namespace = session.cacheNamespace()
        val result = backend.favoriteTracks(page, FAVORITE_PAGE_SIZE).toDomainPage(page, FAVORITE_PAGE_SIZE)
        observeFavoriteTracks(result, namespace)
        return result
    }

    suspend fun recentTracks(page: Int): Page<Track> =
        backend.recentTracks(page, RECENT_PAGE_SIZE).toDomainPage(page, RECENT_PAGE_SIZE)

    suspend fun addToPlaylist(playlistGuid: String, trackGuid: String) {
        backend.addToPlaylist(playlistGuid, trackGuid)
        invalidatePlaylistPages(playlistGuid)
    }

    suspend fun removeFromPlaylist(playlistGuid: String, trackGuid: String) {
        backend.removeFromPlaylist(playlistGuid, trackGuid)
        invalidatePlaylistPages(playlistGuid)
    }

    /**
     * 新建歌单并返回它（guid 由服务端下发，封面 id 由后端按自家约定生成）。
     * 歌单索引（"playlists"）是带缓存的，创建后失效索引缓存，首页歌单行/全部歌单页下次加载就能看到。
     */
    suspend fun createPlaylist(name: String): Playlist {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        val playlist = backend.createPlaylist(trimmed)
        // 缓存键是 (namespace, kind, businessKey)：歌单索引登记在 businessKey="playlists" 上，
        // 用 "index"（那是 kind）失效不掉，会导致重新打开弹窗时又读到旧列表。
        runCatching { responses.invalidateSource(session.cacheNamespace(), "playlists") }
        return playlist
    }

    /**
     * 清空全部收藏：服务端没有批量接口，先分页取回全部收藏 guid，再逐条取消收藏。
     * 每删掉一条就回调一次进度，调用方可以随时取消（协程取消即可中断）。
     */
    suspend fun clearAllFavorites(
        onProgress: suspend (removed: Int, total: Int) -> Unit = { _, _ -> },
    ): FavoritesClearOutcome {
        val guids = mutableListOf<String>()
        var page = 1
        while (true) {
            val loaded = favoriteTracks(page)
            guids += loaded.items.map { it.guid.value }
            if (!loaded.hasNext) break
            page += 1
        }
        var removed = 0
        var failed = 0
        guids.forEach { guid ->
            if (toggleFavorite(guid, fallbackFavorite = true).isSuccess) removed++ else failed++
            onProgress(removed, guids.size)
        }
        return FavoritesClearOutcome(removed = removed, failed = failed)
    }

    /** 歌单内容在 NAS 上已变化，丢弃该歌单的缓存页，让下一次加载直接回源。 */
    private suspend fun invalidatePlaylistPages(playlistGuid: String) {
        runCatching { responses.invalidateSource(session.cacheNamespace(), "playlist:$playlistGuid") }
    }

    suspend fun toggleFavorite(trackGuid: String, fallbackFavorite: Boolean): Result<Boolean> =
        favoriteMutationMutex.withLock {
            val namespace = session.cacheNamespace()
            bindFavoriteNamespace(namespace)
            val (optimisticState, mutation) = _favoriteState.value.beginMutation(trackGuid, fallbackFavorite)
            _favoriteState.value = optimisticState
            try {
                backend.setFavorite(trackGuid, mutation.desired)
                _favoriteState.update { it.complete(mutation) }
                Result.success(mutation.desired)
            } catch (cause: CancellationException) {
                _favoriteState.update { it.rollback(mutation) }
                throw cause
            } catch (cause: Exception) {
                val error = (cause as? AppException)?.error ?: AppError.Unknown(cause.message)
                _favoriteState.update { it.rollback(mutation, error) }
                Result.failure(cause)
            }
        }

    fun clearFavoriteState() {
        _favoriteState.value = FavoriteLibraryState()
    }

    suspend fun queuePage(source: QueueSource, page: Int): Page<Track> = when (source) {
        is QueueSource.Playlist -> playlistTracks(source.guid, page)
        is QueueSource.Artist -> artistTracks(source.guid, page)
        is QueueSource.Album -> albumTracks(source.guid, page)
        is QueueSource.LibraryAllTracks -> allTracks(page)
        is QueueSource.Favorites -> favoriteTracks(page)
        is QueueSource.Recent -> recentTracks(page)
        is QueueSource.Genre -> genreTracks(source.guid, page)
    }

    suspend fun sharedLibraries(): List<SharedLibrary> = cachedIndex(CatalogIndexSource.SharedLibraries)

    suspend fun trackMetadata(trackGuid: String): Track =
        cachedIndex(CatalogIndexSource.TrackMetadata(trackGuid)).also(::observeFavoriteTrack)

    suspend fun currentTrackMetadata(trackGuid: String): CurrentResourceResult<Track> = currentResource {
        withCurrentResourceRetry { trackMetadata(trackGuid) }
    }

    suspend fun prepare(track: Track): PlaybackTrack {
        if (track.accessStatus != null && track.accessStatus != 0) throw AppException(AppError.UnavailableTrack)
        val refreshed = trackMetadata(track.guid.value)
        if (refreshed.isCue) throw AppException(AppError.TranscodeUnavailable)
        return PlaybackTrack(
            refreshed,
            backend.streamPlan(refreshed).url,
            refreshed.coverId?.let { backend.artworkUrl(it, CoverVariant.Player.width) },
        )
    }

    fun prepareQueue(tracks: List<Track>): List<PlaybackTrack> =
        tracks.asSequence()
            .filter { it.accessStatus == null || it.accessStatus == 0 }
            .filterNot(Track::isCue)
            .take(250)
            .map { track ->
                PlaybackTrack(
                    track,
                    backend.directStreamUrl(track),
                    track.coverId?.let { backend.artworkUrl(it, CoverVariant.Player.width) },
                )
            }
            .toList()

    suspend fun lyrics(trackGuid: String): Pair<LyricDocument?, SyncedLyrics?> {
        val document = lyricDocument(trackGuid)
        val syncedLyrics = document?.let { parseLyrics(it.content) }?.takeIf(SyncedLyrics::hasUsableLines)
        return document to syncedLyrics
    }

    private suspend fun firstPartyCurrentLyrics(trackGuid: String): CurrentResourceResult<CurrentLyrics> = currentResource {
        val (document, syncedLyrics) = withCurrentResourceRetry { lyrics(trackGuid) }
        document?.takeIf { it.content.isNotBlank() }?.let { CurrentLyrics(it, syncedLyrics) }
    }

    suspend fun currentLyrics(
        track: Track,
        onlineMatchingEnabled: Boolean = preferences.state.value.onlineLyricsMatchingEnabled,
    ): CurrentResourceResult<CurrentLyrics> = resolveLyricsWithFallback(
        onlineMatchingEnabled = onlineMatchingEnabled,
        online = { onlineLyricsResolver.resolve(track) },
        firstParty = { firstPartyCurrentLyrics(track.guid.value) },
    )

    suspend fun startRoam(): RoamWindow? {
        val namespace = session.cacheNamespace()
        return backend.startRoam().also { observeFavoriteWindow(it, namespace) }
    }

    suspend fun nextRoam(roamId: String): RoamWindow {
        val namespace = session.cacheNamespace()
        return backend.nextRoam(roamId).also { observeFavoriteWindow(it, namespace) }
    }

    suspend fun previousRoam(roamId: String): RoamWindow {
        val namespace = session.cacheNamespace()
        return backend.previousRoam(roamId).also { observeFavoriteWindow(it, namespace) }
    }

    suspend fun artwork(coverId: String, variant: CoverVariant): ByteArray? = try {
        loadArtwork(coverId, variant)
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: AppException) {
        null
    }

    suspend fun currentArtwork(
        coverId: String,
        variant: CoverVariant,
    ): CurrentResourceResult<ByteArray> = currentResource {
        withCurrentResourceRetry { loadArtwork(coverId, variant) }
    }

    suspend fun invalidateNamespace(namespace: String, includeEssential: Boolean = false) {
        responses.invalidateNamespace(namespace)
        artworkCache.clearNamespace(namespace)
        localStore.clearNamespace(namespace, includeEssential)
        _playlistCovers.value = emptyMap()
    }

    suspend fun clearLocalNamespace(includeEssential: Boolean) {
        val namespace = runCatching(session::cacheNamespace).getOrNull() ?: return
        invalidateNamespace(namespace, includeEssential)
    }

    suspend fun clearArtwork() = artworkCache.clearAll()

    suspend fun clearAllEvictableCaches() {
        responses.invalidateAll()
        artworkCache.clearAll()
        localStore.clearAllEvictable()
        _playlistCovers.value = emptyMap()
    }

    suspend fun cacheUsage(): CacheUsage = CacheUsage(
        artworkBytes = artworkCache.usageBytes(),
        indexBytes = localStore.physicalBytes(),
    )

    suspend fun applyArtworkBudget() = artworkCache.applyBudget()

    private fun observeFavoriteTracks(page: Page<Track>) {
        page.items.forEach(::observeFavoriteTrack)
    }

    private fun observeFavoriteTracks(page: Page<Track>, namespace: String) {
        page.items.forEach { observeFavoriteTrack(it, namespace) }
    }

    private fun observeFavoriteWindow(window: RoamWindow?, namespace: String) {
        window ?: return
        listOfNotNull(window.previous, window.current, window.next)
            .forEach { observeFavoriteTrack(it.track, namespace) }
    }

    private fun observeFavoriteTrack(track: Track) {
        val namespace = runCatching(session::cacheNamespace).getOrNull() ?: return
        observeFavoriteTrack(track, namespace)
    }

    private fun observeFavoriteTrack(track: Track, namespace: String) {
        if (runCatching(session::cacheNamespace).getOrNull() != namespace) return
        bindFavoriteNamespace(namespace)
        _favoriteState.update { it.observe(track.guid.value, track.isFavorite) }
    }

    private fun bindFavoriteNamespace(namespace: String) {
        if (_favoriteState.value.namespace != namespace) {
            _favoriteState.update { it.bindNamespace(namespace) }
        }
    }

    private suspend fun loadArtwork(coverId: String, variant: CoverVariant): ByteArray? {
        val namespace = session.cacheNamespace()
        return artworkCache.get(namespace, coverId, variant) {
            backend.artwork(coverId, variant.width)
        }
    }

    /**
     * 取回服务端歌词（原始体进缓存/本地库，解码交给后端）。
     * 离线时回落到本地库里的原始体；缓存体不可解析才把网络错误抛出去（与改动前一致）。
     */
    private suspend fun lyricDocument(trackGuid: String): LyricDocument? {
        val namespace = session.cacheNamespace()
        val key = ResponseCacheKey(namespace, "lyric", trackGuid)
        var decoded: LyricDocument? = null
        var resolved = false
        val payload = responses.getOrFetch(
            key = key,
            persist = { encoded ->
                bestEffort {
                    localStore.saveLyric(CachedLyricEntity(namespace, trackGuid, encoded, now()))
                }
            },
        ) {
            try {
                backend.lyricsRaw(trackGuid).also { raw ->
                    decoded = backend.decodeLyrics(raw)
                    resolved = true
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: AppException) {
                if (cause.error != AppError.NetworkUnavailable) throw cause
                val cached = fallback { localStore.lyric(namespace, trackGuid) } ?: throw cause
                decoded = try {
                    backend.decodeLyrics(cached.payload)
                } catch (decodeFailure: Exception) {
                    if (decodeFailure is CancellationException) throw decodeFailure
                    throw cause
                }
                resolved = true
                cached.payload
            }
        }
        return if (resolved) decoded else backend.decodeLyrics(payload)
    }

    /**
     * 目录页：原始体走响应缓存（内存 + Room），解码由后端完成。
     * 缓存源键与抽取前逐字一致，所以老的本地缓存仍然命中。
     */
    private suspend fun <T> cachedPage(
        source: CatalogPageSource<T>,
        page: Int,
        pageSize: Int,
    ): Page<T> {
        require(pageSize > 0)
        val namespace = session.cacheNamespace()
        val sourceKey = if (source.sizeSensitive) sizedPageSourceKey(source.cacheKey, pageSize) else source.cacheKey
        val key = ResponseCacheKey(namespace, "page", sourceKey, page)
        var decoded: DecodedPage<T>? = null
        val payload = responses.getOrFetch(
            key = key,
            persist = { encoded ->
                bestEffort {
                    val resolved = decoded ?: backend.decodePage(source, encoded)
                    localStore.savePage(
                        CachedPageEntity(
                            namespace = namespace,
                            sourceKey = sourceKey,
                            page = page,
                            payload = encoded,
                            total = resolved.total,
                            sort = resolved.sort,
                            accessedAt = now(),
                        ),
                    )
                }
            },
        ) {
            try {
                val raw = backend.catalogPage(source, page, pageSize)
                decoded = backend.decodePage(source, raw.rawJson)
                raw.rawJson
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: AppException) {
                if (cause.error != AppError.NetworkUnavailable) throw cause
                val cached = fallback { localStore.page(namespace, sourceKey, page) } ?: throw cause
                decoded = try {
                    backend.decodePage(source, cached.payload)
                } catch (decodeFailure: Exception) {
                    if (decodeFailure is CancellationException) throw decodeFailure
                    throw cause
                }
                cached.payload
            }
        }
        return (decoded ?: backend.decodePage(source, payload)).toDomainPage(page, pageSize)
    }

    /** 目录索引：与 [cachedPage] 同一套缓存语义（内存 + Room），只是不分页。 */
    private suspend fun <T> cachedIndex(source: CatalogIndexSource<T>): T {
        val namespace = session.cacheNamespace()
        val cacheKey = ResponseCacheKey(namespace, "index", source.cacheKey)
        var decoded: T? = null
        val payload = responses.getOrFetch(
            key = cacheKey,
            persist = { encoded ->
                bestEffort {
                    localStore.saveIndex(CachedIndexEntity(namespace, source.cacheKey, encoded, now()))
                }
            },
        ) {
            try {
                backend.catalogIndex(source).rawJson.also { raw ->
                    decoded = backend.decodeIndex(source, raw)
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: AppException) {
                if (cause.error != AppError.NetworkUnavailable) throw cause
                val cached = fallback { localStore.index(namespace, source.cacheKey) } ?: throw cause
                decoded = try {
                    backend.decodeIndex(source, cached.payload)
                } catch (decodeFailure: Exception) {
                    if (decodeFailure is CancellationException) throw decodeFailure
                    throw cause
                }
                cached.payload
            }
        }
        return decoded ?: backend.decodeIndex(source, payload)
    }

    private suspend fun <T> currentResource(block: suspend () -> T?): CurrentResourceResult<T> = try {
        block()?.let { CurrentResourceResult.Ready(it) } ?: CurrentResourceResult.Absent
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: AppException) {
        when (cause.error) {
            AppError.NotFound, AppError.Empty -> CurrentResourceResult.Absent
            else -> CurrentResourceResult.Failure(cause.error, cause.isRetryableRequestFailure)
        }
    }

    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            // Room remains a fallback tier; a disk write failure must not fail a valid network response.
        }
    }

    private suspend fun <T> fallback(block: suspend () -> T): T? = try {
        block()
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        null
    }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val METADATA_CAPACITY_BYTES = 8 * 1024 * 1024
        const val ARTWORK_MEMORY_CAPACITY_BYTES = 24 * 1024 * 1024
        const val PAGE_SIZE = 50
        const val FAVORITE_PAGE_SIZE = 50
        const val RECENT_PAGE_SIZE = 50

        fun defaultOnlineLyricsMatcher(): suspend (LyricsMatchRequest) -> LyricsMatchResult {
            val client = okhttp3.OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .connectTimeout(1_200L, TimeUnit.MILLISECONDS)
                .readTimeout(1_800L, TimeUnit.MILLISECONDS)
                .writeTimeout(1_800L, TimeUnit.MILLISECONDS)
                .build()
            val coordinator = LyricsMatchCoordinator(DefaultLyricsSources.create(client))
            return coordinator::match
        }
    }
}

internal fun sizedPageSourceKey(sourceKey: String, size: Int): String {
    require(size > 0)
    return "$sourceKey:size=$size"
}

/** 解码后的一页 → 领域分页：页/页大小来自请求，total/sort 来自响应（与抽取前一致）。 */
internal fun <T> DecodedPage<T>.toDomainPage(page: Int, pageSize: Int): Page<T> {
    require(pageSize > 0)
    return Page(items, page, pageSize, total, sort)
}

internal suspend fun resolveLyricsWithFallback(
    onlineMatchingEnabled: Boolean,
    online: suspend () -> CurrentLyrics?,
    firstParty: suspend () -> CurrentResourceResult<CurrentLyrics>,
): CurrentResourceResult<CurrentLyrics> {
    if (!onlineMatchingEnabled) return firstParty()
    return try {
        online()?.let { CurrentResourceResult.Ready(it) } ?: firstParty()
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        firstParty()
    }
}

/** 歌单候选封面的最大并发请求数：NAS 是家用设备，并发过高会把首屏一起拖慢。 */
private const val PLAYLIST_COVER_PARALLELISM = 4

/** 取候选封面时的取样页大小：3 张封面用不着整页 50 首。 */
private const val COVER_CANDIDATE_PAGE_SIZE = 12

/** 清空收藏的结果：成功删除数与失败数（失败的可以再点一次继续清）。 */
data class FavoritesClearOutcome(val removed: Int, val failed: Int)

package com.fnmusic.tv.core.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import com.fnmusic.tv.core.data.backend.CatalogIndexSource
import com.fnmusic.tv.core.data.backend.CatalogPageSource
import com.fnmusic.tv.core.data.backend.DecodedPage
import com.fnmusic.tv.core.data.backend.MusicBackend
import com.fnmusic.tv.core.data.backend.SessionBackends
import com.fnmusic.tv.core.data.backend.randomPages
import com.fnmusic.tv.core.data.backend.spreadByKey
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

/** 方向性收藏（上下文菜单「加入收藏」）：把目标状态定为 [desired]，不翻转既有状态。 */
internal fun FavoriteLibraryState.beginSetMutation(
    trackGuid: String,
    desired: Boolean,
): Pair<FavoriteLibraryState, FavoriteMutation> {
    val boundNamespace = checkNotNull(namespace)
    val confirmed = statuses[trackGuid] ?: desired
    val mutation = FavoriteMutation(boundNamespace, trackGuid, confirmed, desired)
    return copy(
        statuses = statuses + (trackGuid to desired),
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
    /** 当前会话的后端（按会话的后端类型解析，同一会话内复用）。 */
    private val backends: SessionBackends = SessionBackends(session),
) {
    /** 当前后端：飞牛或 Jellyfin，由会话决定（见 [SessionBackends]）。 */
    private val backend: MusicBackend get() = backends.current()

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

    /**
     * 收藏列表的本地排序快照（namespace → 按收藏时间升序的全量曲目），会话内内存缓存。
     * 收藏/取消收藏后失效，下次读取重建（服务器始终是收藏成员的权威）。
     */
    private data class FavoriteListSnapshot(val namespace: String, val tracks: List<Track>)

    @Volatile private var favoriteListSnapshot: FavoriteListSnapshot? = null

    /** 全量构建失败后的降级标记：降级路径粘滞，直到下一次收藏动作重试重建。 */
    @Volatile private var favoriteListDegraded = false

    /** 无封面条目的负缓存（key 含 namespace/variant），5 分钟后允许重试。 */
    private val negativeArtworkUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val _favoriteState = MutableStateFlow(FavoriteLibraryState())
    val favoriteState: StateFlow<FavoriteLibraryState> = _favoriteState.asStateFlow()

    /** 上一次"随机专辑"用到的页号：下次优先换一页，避免刷新两次拿到同一页。 */
    @Volatile private var lastRandomAlbumPage: Int? = null

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
     * 搜索结果点歌手/专辑时的"整表入队"用：分页取完全部歌曲。
     * 详情页有翻页 UI，搜索的入队没有——只取第 1 页会让 50 首以上的专辑被静默截断。
     * 上限 [ALL_TRACKS_PAGE_CAP] 页是防御值：家用 NAS 逐页顺序取，不并发轰炸。
     */
    suspend fun artistTracksAll(guid: String): List<Track> = fetchAllPages { artistTracks(guid, it) }

    suspend fun albumTracksAll(guid: String): List<Track> = fetchAllPages { albumTracks(guid, it) }

    private suspend fun fetchAllPages(fetch: suspend (Int) -> Page<Track>): List<Track> = buildList {
        for (page in 1..ALL_TRACKS_PAGE_CAP) {
            val result = fetch(page)
            addAll(result.items)
            if (!result.hasNext) return@buildList
        }
    }

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

    /**
     * 卡片封面用的轻量随机采样：探测总数 → 三个不同随机页 → 洗牌 + 拆开相邻同歌手。
     *
     * 只取一页时"随机专辑"看上去并不随机：页内按名称排序，成段都是同一批歌手；
     * 而且页号撞上缓存里的同一页就会返回与上次逐条相同的一页。三页把取样窗口再拉开一截。
     */
    suspend fun randomAlbumSample(size: Int = 24): List<Album> {
        val pageSize = size.coerceAtLeast(1)
        val probe = albums(page = 1, size = 1)
        val total = probe.total ?: return probe.items.shuffled()
        if (total <= pageSize) return albums(page = 1, size = pageSize).items.shuffled()
        val pages = randomPages(
            total = total,
            pageSize = pageSize,
            count = RANDOM_SAMPLE_PAGES,
            exclude = lastRandomAlbumPage,
        )
        lastRandomAlbumPage = pages.lastOrNull() ?: lastRandomAlbumPage
        val merged = pages.flatMap { page -> albums(page = page, size = pageSize).items }
        return merged.shuffled().spreadByKey { it.artistName }.take(pageSize)
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

    suspend fun searchTracks(query: String, page: Int = 1, size: Int = 20): Page<Track> {
        val result = backend.searchTracks(query, page, size).toDomainPage(page, size)
        // fn 服务端不按 size 截断（真机观测整页返回 85 条），客户端统一截断，保证两源上限一致
        return if (result.items.size > size) result.copy(items = result.items.take(size)) else result
    }

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
        // 全量快照的构建与失效都走 favoriteMutationMutex：保证"收藏动作失效快照"不会被
        // 一个更早启动、尚未完成的重建用旧数据覆盖回去（invalidate 一定排在它后面执行）。
        favoriteMutationMutex.withLock {
            val snapshot = favoriteSnapshotLocked(namespace)
            if (snapshot != null) {
                val paged = sliceFavoritePage(snapshot, page, FAVORITE_PAGE_SIZE)
                observeFavoriteTracks(paged, namespace)
                return paged
            }
        }
        // 降级：服务端分页原序。降级粘滞到下次收藏动作，避免同一轮加载里各页 sort
        // 忽而 desc 忽而 asc 触发 UI 的漂移误报（CollectionChanged）。
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

    /** 移除歌单里的曲目：传整首曲目是因为 Jellyfin 要用条目 id（[Track.playlistEntryId]）。 */
    suspend fun removeFromPlaylist(playlistGuid: String, track: Track) {
        backend.removeFromPlaylist(playlistGuid, track)
        invalidatePlaylistPages(playlistGuid)
    }

    /**
     * 新建歌单并返回它（guid 由服务端下发，封面 id 由后端按自家约定生成）。
     * 歌单索引（"playlists"）是带缓存的，创建后失效索引缓存，首页歌单行/全部歌单页下次加载就能看到。
     */
    /**
     * 删除整个歌单：失效歌单索引与该歌单的详情/曲目页缓存。
     * fn 源的删除端点是按命名规律推测的，服务端不支持时会以 HTTP 错误浮出。
     */
    suspend fun deletePlaylist(playlistGuid: String) {
        backend.deletePlaylist(playlistGuid)
        runCatching { responses.invalidateSource(session.cacheNamespace(), "playlists") }
        invalidatePlaylistPages(playlistGuid)
    }

    /**
     * 上报一次播放（"最近播放"的数据来源）：服务端按曲目去重、刷新最近播放时间。
     * 未登录/网络失败时静默失败——上报不能影响播放本身。
     */
    suspend fun reportTrackPlayed(trackGuid: String) {
        if (trackGuid.isBlank()) return
        runCatching { backend.reportTrackPlayed(trackGuid) }
    }

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
                recordFavoriteTimeChange(mutation)
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

    /**
     * 方向性收藏（上下文菜单「加入收藏」）：与 [toggleFavorite] 的差别是不翻转——
     * 已收藏的曲目重复"加入收藏"只是幂等地再设一次 true。
     */
    suspend fun setFavorite(trackGuid: String, favorite: Boolean): Result<Boolean> =
        favoriteMutationMutex.withLock {
            val namespace = session.cacheNamespace()
            bindFavoriteNamespace(namespace)
            val (optimisticState, mutation) = _favoriteState.value.beginSetMutation(trackGuid, favorite)
            _favoriteState.value = optimisticState
            try {
                backend.setFavorite(trackGuid, mutation.desired)
                _favoriteState.update { it.complete(mutation) }
                recordFavoriteTimeChange(mutation)
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
        favoriteListSnapshot = null
        favoriteListDegraded = false
    }

    /**
     * 收藏动作成功后的本地时间维护 + 快照失效：
     * 收藏（含重复收藏）→ 记录当前时间（"重复收藏=刷新最后一次收藏时间"）；取消 → 删除记录。
     * 时间按 mutation 的 namespace 落库，天然隔离多账号。失效快照让下一次读取重建排序。
     */
    private suspend fun recordFavoriteTimeChange(mutation: FavoriteMutation) {
        runCatching {
            if (mutation.desired) {
                localStore.recordFavoriteTimes(mutation.namespace, mapOf(mutation.trackGuid to now()))
            } else {
                localStore.deleteFavoriteTime(mutation.namespace, mutation.trackGuid)
            }
        }
        favoriteListSnapshot = null
        favoriteListDegraded = false
    }

    /** 持有 [favoriteMutationMutex] 调用。返回 null = 走降级路径（构建失败或已降级）。 */
    private suspend fun favoriteSnapshotLocked(namespace: String): List<Track>? {
        favoriteListSnapshot?.takeIf { it.namespace == namespace }?.let { return it.tracks }
        if (favoriteListDegraded) return null
        return try {
            loadFavoriteSnapshot(namespace).also {
                favoriteListSnapshot = FavoriteListSnapshot(namespace, it)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            favoriteListDegraded = true
            null
        }
    }

    /** 全量拉取收藏成员 → 对账回填本地收藏时间 → 按"先收藏在前"升序排序。 */
    private suspend fun loadFavoriteSnapshot(namespace: String): List<Track> {
        val fetched = mutableListOf<Track>()
        var page = 1
        while (true) {
            val decoded = backend.favoriteTracks(page, FAVORITE_PAGE_SIZE).toDomainPage(page, FAVORITE_PAGE_SIZE)
            fetched += decoded.items
            if (!decoded.hasNext) break
            page += 1
        }
        val localTimes = runCatching { localStore.favoriteTimes(namespace) }.getOrDefault(emptyMap())
        val reconciliation = reconcileFavoriteTimes(
            serverOrder = fetched.map { it.guid.value },
            serverTimes = fetched.mapNotNull { track -> track.favoritedAt?.let { track.guid.value to it } }.toMap(),
            localTimes = localTimes,
            nowMillis = now(),
        )
        runCatching { localStore.recordFavoriteTimes(namespace, reconciliation.upserts) }
        return fetched.sortedBy { reconciliation.times[it.guid.value] ?: Long.MAX_VALUE }
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
        if (refreshed.unplayableReason != null) throw AppException(AppError.TranscodeUnavailable)
        val plan = backend.streamPlan(refreshed)
        return PlaybackTrack(
            refreshed,
            plan.url,
            refreshed.coverId?.let { backend.artworkUrl(it, CoverVariant.Player.width) },
            plan.mode,
        )
    }

    fun prepareQueue(tracks: List<Track>): List<PlaybackTrack> =
        tracks.asSequence()
            .filter { it.accessStatus == null || it.accessStatus == 0 }
            .filterNot(Track::isCue)
            .filter { it.unplayableReason == null }
            .take(250)
            .map { track ->
                val plan = backend.queueStreamPlan(track)
                PlaybackTrack(
                    track,
                    plan.url,
                    track.coverId?.let { backend.artworkUrl(it, CoverVariant.Player.width) },
                    plan.mode,
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
        server = { firstPartyCurrentLyrics(track.guid.value) },
        online = { onlineLyricsResolver.resolve(track) },
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

    suspend fun artwork(coverId: String, variant: CoverVariant): ByteArray? {
        val namespace = runCatching(session::cacheNamespace).getOrNull() ?: return null
        // 404/失败负缓存：无封面的条目（每次滚动回可视区都重组）不再重发注定失败的请求
        negativeArtworkUntil["$namespace|$coverId|$variant"]?.let { until ->
            if (now() < until) return null
            negativeArtworkUntil.remove("$namespace|$coverId|$variant")
        }
        val bytes = try {
            loadArtwork(coverId, variant)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: AppException) {
            null
        }
        if (bytes == null) {
            negativeArtworkUntil["$namespace|$coverId|$variant"] = now() + NEGATIVE_ARTWORK_TTL_MS
        } else {
            negativeArtworkUntil.remove("$namespace|$coverId|$variant")
        }
        return bytes
    }

    suspend fun currentArtwork(
        coverId: String,
        variant: CoverVariant,
    ): CurrentResourceResult<ByteArray> = currentResource {
        withCurrentResourceRetry { loadArtwork(coverId, variant) }
    }

    suspend fun invalidateNamespace(namespace: String, includeEssential: Boolean = false) {
        responses.invalidateNamespace(namespace)
        negativeArtworkUntil.keys.removeAll { it.startsWith("$namespace|") }
        artworkCache.clearNamespace(namespace)
        localStore.clearNamespace(namespace, includeEssential)
        _playlistCovers.value = emptyMap()
        favoriteListSnapshot = null
        favoriteListDegraded = false
    }

    suspend fun clearLocalNamespace(includeEssential: Boolean) {
        val namespace = runCatching(session::cacheNamespace).getOrNull() ?: return
        invalidateNamespace(namespace, includeEssential)
    }

    suspend fun clearArtwork() {
        negativeArtworkUntil.clear()
        artworkCache.clearAll()
    }

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
        // 缓存键拼上数据契约版本：服务端歌词的解码/响应形状变了，旧 payload 必须能作废
        // （与 page/index 键同一机制；磁盘行的离线兜底键保持裸 guid，不参与版本化）。
        val key = ResponseCacheKey(namespace, "lyric", contractVersionedKey(trackGuid))
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
        val sourceKey = contractVersionedKey(
            if (source.sizeSensitive) sizedPageSourceKey(source.cacheKey, pageSize) else source.cacheKey,
        )
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
        val sourceKey = contractVersionedKey(source.cacheKey)
        val cacheKey = ResponseCacheKey(namespace, "index", sourceKey)
        var decoded: T? = null
        val payload = responses.getOrFetch(
            key = cacheKey,
            persist = { encoded ->
                bestEffort {
                    localStore.saveIndex(CachedIndexEntity(namespace, sourceKey, encoded, now()))
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
                val cached = fallback { localStore.index(namespace, sourceKey) } ?: throw cause
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
            // socket 超时要**大于**协调器深挖轮的预算（4.5s，实测 QQ 老接口最慢 4.09s），
            // 否则慢源会被 socket 提前截断；快源仍由各自的 1.8s 协程预算收口（取消时会 cancel 掉请求）。
            val client = okhttp3.OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .connectTimeout(2_000L, TimeUnit.MILLISECONDS)
                .readTimeout(5_000L, TimeUnit.MILLISECONDS)
                .writeTimeout(5_000L, TimeUnit.MILLISECONDS)
                .build()
            val coordinator = LyricsMatchCoordinator(
                sources = DefaultLyricsSources.create(client),
                deepSources = DefaultLyricsSources.createDeep(client),
            )
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

/** 收藏列表本地排序路径的 sort 标识：与降级路径（服务端 "favoriteAt,desc"）严格区分。 */
internal const val FAVORITE_LOCAL_SORT = "favoriteAt,asc"

/** 种子时间的间隔：首次对账回填时逐首递减 1 秒，保证全部早于本次对账时刻。 */
private const val FAVORITE_SEED_STEP_MS = 1_000L

/** 收藏时间对账结果：times=全部成员的最终时间；upserts=需写库的；removals=需删库的。 */
internal data class FavoriteTimeReconciliation(
    val times: Map<String, Long>,
    val upserts: Map<String, Long>,
    val removals: Set<String>,
)

/**
 * 收藏时间对账（每次收藏列表全量拉取后执行一次，本地表=服务器收藏集合的投影）：
 * - 本 App 收藏动作已写入的时间优先，已存在的本地记录不覆盖；
 * - 服务器响应自带收藏时间（飞牛 favoriteAt / Jellyfin DateLastSaved）的新成员直接回填真实值；
 * - 其余新成员按"服务端返回序反转"分配种子时间（从 [nowMillis] 向回每首 1 秒）：
 *   服务端 desc 序（最新在前）反转后即"先收藏在前"，拿不到真实时间顺序也正确；
 * - 本地有、服务器没有的（其他端取消收藏）进入 removals。
 */
internal fun reconcileFavoriteTimes(
    serverOrder: List<String>,
    serverTimes: Map<String, Long>,
    localTimes: Map<String, Long>,
    nowMillis: Long,
): FavoriteTimeReconciliation {
    val serverSet = serverOrder.toHashSet()
    val removals = localTimes.keys.filterNot(serverSet::contains).toSet()
    val kept = localTimes.filterKeys { it !in removals }
    val seeds = serverOrder.asReversed().filter { it !in kept && it !in serverTimes }
    val seedTimes = seeds.mapIndexed { index, guid ->
        guid to nowMillis - (seeds.size - index) * FAVORITE_SEED_STEP_MS
    }.toMap()
    val upserts = serverTimes.filterKeys { it !in kept } + seedTimes
    return FavoriteTimeReconciliation(times = kept + upserts, upserts = upserts, removals = removals)
}

/** 本地排序后的收藏列表内存切片分页（页码从 1 起，页语义与服务器分页一致）。 */
internal fun <T> sliceFavoritePage(items: List<T>, page: Int, pageSize: Int): Page<T> {
    require(page >= 1)
    require(pageSize > 0)
    val from = ((page - 1) * pageSize).coerceAtMost(items.size)
    val to = (from + pageSize).coerceAtMost(items.size)
    return Page(
        items = items.subList(from, to),
        page = page,
        pageSize = pageSize,
        total = items.size,
        sort = FAVORITE_LOCAL_SORT,
    )
}

/**
 * 服务器歌词第一优先（jellyfin 的歌词接口即从音频文件内嵌提取），
 * 在线匹配仅在服务器缺失/失败时兜底。在线抛错不吞取消。
 */
internal suspend fun resolveLyricsWithFallback(
    onlineMatchingEnabled: Boolean,
    server: suspend () -> CurrentResourceResult<CurrentLyrics>,
    online: suspend () -> CurrentLyrics?,
): CurrentResourceResult<CurrentLyrics> {
    val serverResult = server()
    if (serverResult is CurrentResourceResult.Ready || !onlineMatchingEnabled) return serverResult
    return try {
        online()?.let { CurrentResourceResult.Ready(it) } ?: serverResult
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        serverResult
    }
}

/** 歌单候选封面的最大并发请求数：NAS 是家用设备，并发过高会把首屏一起拖慢。 */
private const val PLAYLIST_COVER_PARALLELISM = 4

/** 取候选封面时的取样页大小：3 张封面用不着整页 50 首。 */
private const val COVER_CANDIDATE_PAGE_SIZE = 12

/**
 * 列表响应的"数据契约版本"，会拼进页面/索引的缓存键。
 *
 * 为什么需要它：缓存按 (namespace, 源键, 页) 命中，**不关心请求里带了哪些字段**。
 * 一旦某个查询请求的字段集合变了（例如 1.7.0 给 Jellyfin 补上 `ImageTags` 来拿封面），
 * 旧版本缓存下来的那份 payload 会继续被读出来 —— 升级后界面看起来"没修好"。
 * 改字段/改响应形状时把这里 +1，让旧 payload 自然作废（封面缓存按 coverId 单独存，不受影响）。
 */
private const val CACHE_CONTRACT_VERSION = 3

internal fun contractVersionedKey(key: String): String = "$CACHE_CONTRACT_VERSION:$key"

/** 随机专辑合并的页数：1 页成段连续，3 页把取样窗口拉开一截。 */
private const val RANDOM_SAMPLE_PAGES = 3

/** 无封面（下载失败/404）负缓存的时长：期间直接返回 null，不再打网络。 */
private const val NEGATIVE_ARTWORK_TTL_MS = 5 * 60 * 1_000L

/** "整表入队"的分页上限（50 首/页 × 40 = 2000 首，超出即视为元数据异常）。 */
private const val ALL_TRACKS_PAGE_CAP = 40

/** 清空收藏的结果：成功删除数与失败数（失败的可以再点一次继续清）。 */
data class FavoritesClearOutcome(val removed: Int, val failed: Int)

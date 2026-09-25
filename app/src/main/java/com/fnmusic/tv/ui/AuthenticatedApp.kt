package com.fnmusic.tv.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import androidx.tv.material3.Button as TvMaterialButton
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import androidx.compose.ui.window.Dialog
import com.fnmusic.tv.AuthenticatedAppDependencies
import com.fnmusic.tv.BuildConfig
import com.fnmusic.tv.NowPlayingPresentation
import com.fnmusic.tv.NowPlayingResourceState
import com.fnmusic.tv.R
import com.fnmusic.tv.decodeArtwork
import com.fnmusic.tv.core.data.repository.CurrentLyrics
import com.fnmusic.tv.core.data.repository.SessionState
import com.fnmusic.tv.core.model.Genre
import com.fnmusic.tv.core.model.Album
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.AppException
import com.fnmusic.tv.core.model.Artist
import com.fnmusic.tv.core.model.CoverVariant
import com.fnmusic.tv.core.model.Page
import com.fnmusic.tv.core.model.PlayerStyle
import com.fnmusic.tv.core.model.Playlist
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.playback.QueueSource
import com.fnmusic.tv.core.model.playback.QueueKind
import com.fnmusic.tv.core.model.playback.PlayMode
import com.fnmusic.tv.core.model.playback.PlaybackQueueItem
import com.fnmusic.tv.core.model.playback.NowPlayingIdentity
import com.fnmusic.tv.core.model.playback.MAX_ACTIVE_QUEUE_ITEMS
import com.fnmusic.tv.core.model.playback.QueuePageItem
import com.fnmusic.tv.core.model.playback.QueuePageSegment
import com.fnmusic.tv.core.model.playback.boundedQueueWindow
import com.fnmusic.tv.core.playback.PlaybackUiState
import com.fnmusic.tv.core.playback.PlaybackProgressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

private val LocalAuthenticatedDependencies = staticCompositionLocalOf<AuthenticatedAppDependencies> {
    error("Missing authenticated app dependencies")
}

internal val LocalLibraryRetainedState = staticCompositionLocalOf<LibraryRetainedStateStore> {
    error("Missing library retained state")
}

private const val FULL_CATALOG_PAGE_SIZE = 12
private const val SEARCH_TRACK_PAGE_SIZE = 20

@Composable
internal fun AuthenticatedApp(
    container: AuthenticatedAppDependencies,
    session: SessionState.SignedIn,
    playback: PlaybackUiState,
    onExitApplication: () -> Unit,
) {
    var stack by remember(session.user.guid) { mutableStateOf(listOf<LibraryRoute>(LibraryRoute.Home)) }
    var lastHomeBackAt by remember(session.user.guid) { mutableStateOf(0L) }
    // 最近播放的内容随每次播放变化，进入页面时递增触发整表刷新。
    var recentContentTick by remember(session.user.guid) { mutableStateOf(0L) }
    val route = stack.last()
    val context = LocalContext.current
    val stateHolder = rememberSaveableStateHolder()
    val retainedScope = rememberCoroutineScope()
    val retainedState = remember(session.user.guid, retainedScope) {
        LibraryRetainedStateStore(retainedScope)
    }
    val routeStateLifecycle = remember(session.user.guid) { LibraryRouteStateLifecycle() }
    val playerVisualContinuity = remember(session.user.guid) { PlayerVisualContinuity() }
    val open: (LibraryRoute) -> Unit = { stack = stack + it }
    val root: (LibraryRoute) -> Unit = { stack = listOf(it) }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    LaunchedEffect(route) {
        container.updateController.setAutomaticPromptAllowed(route !is LibraryRoute.Player)
    }
    DisposableEffect(container.updateController) {
        onDispose { container.updateController.setAutomaticPromptAllowed(true) }
    }
    LaunchedEffect(route.storageKey()) { lastHomeBackAt = 0L }
    LaunchedEffect(stack) {
        routeStateLifecycle.update(stack).forEach { removedRoute ->
            stateHolder.removeState(removedRoute.storageKey())
            retainedState.remove(removedRoute.retainedStateKeys())
        }
    }
    BackHandler {
        when {
            stack.size > 1 -> stack = stack.dropLast(1)
            route == LibraryRoute.My -> root(LibraryRoute.Home)
            route == LibraryRoute.Home -> {
                val now = SystemClock.elapsedRealtime()
                if (isHomeBackConfirmed(lastHomeBackAt, now)) {
                    lastHomeBackAt = 0L
                    onExitApplication()
                } else {
                    lastHomeBackAt = now
                    Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    LaunchedEffect(playback.error) {
        if (playback.error?.requiresSessionVerification == true) {
            container.authenticatedActions.verifyCurrentSession()
        }
    }

    stateHolder.SaveableStateProvider(route.storageKey()) {
        CompositionLocalProvider(
            LocalAuthenticatedDependencies provides container,
            LocalLibraryRetainedState provides retainedState,
        ) {
            when (route) {
        LibraryRoute.Home -> BrowseHome(
            container,
            playback,
            onMy = { root(LibraryRoute.My) },
            onPlaylist = { open(LibraryRoute.PlaylistDetail(it)) },
            onFavorites = { open(LibraryRoute.Favorites) },
            onRecent = {
                recentContentTick++
                open(LibraryRoute.Recent)
            },
            onAll = { open(LibraryRoute.AllPlaylists) },
            onAlbum = { open(LibraryRoute.AlbumDetail(it)) },
            onPlayer = { open(LibraryRoute.Player(null)) },
        )
        LibraryRoute.My -> BrowseMy(
            container,
            session,
            playback,
            onHome = { root(LibraryRoute.Home) },
            onSearch = { open(LibraryRoute.Search) },
            onGenres = { open(LibraryRoute.Genres) },
            onGenre = { open(LibraryRoute.GenreDetail(it)) },
            onArtists = { open(LibraryRoute.Artists) },
            onAlbums = { open(LibraryRoute.Albums) },
            onAllTracks = { open(LibraryRoute.AllTracks) },
            onArtist = { open(LibraryRoute.ArtistDetail(it)) },
            onAlbum = { open(LibraryRoute.AlbumDetail(it)) },
            onSettings = { open(LibraryRoute.Settings) },
            onPlayer = { open(LibraryRoute.Player(null)) },
        )
        LibraryRoute.AllPlaylists -> AllPlaylists(container, onBack = back, onOpen = { open(LibraryRoute.PlaylistDetail(it)) })
        LibraryRoute.Genres -> Genres(container, onBack = back, onGenre = { open(LibraryRoute.GenreDetail(it)) })
        is LibraryRoute.GenreDetail -> TrackCollection(
            container = container,
            stateKey = "genre:" + route.genre.guid.value + ":tracks",
            title = route.genre.name,
            subtitle = "风格",
            loader = { container.musicRepository.genreTracks(route.genre.guid.value, it) },
            queueSource = { sort -> QueueSource.Genre(route.genre.guid.value, sort) },
            onPlayer = { open(LibraryRoute.Player(it)) },
            detailHeader = TrackDetailHeader(
                kind = "风格",
                declaredTrackCount = route.genre.trackCount,
                onBack = back,
            ),
            emptyMessage = "该风格暂无歌曲",
        )
        LibraryRoute.Search -> SearchRoute(
            container = container,
            onBack = back,
            onArtist = { open(LibraryRoute.ArtistDetail(it)) },
            onAlbum = { open(LibraryRoute.AlbumDetail(it)) },
            onPlayer = { open(LibraryRoute.Player(it)) },
        )
        is LibraryRoute.PlaylistDetail -> {
            // 封面与首页歌单卡片同源：会话级拼排缓存（3 首随机歌曲封面）。
            val playlistCovers by container.musicRepository.playlistCovers.collectAsStateWithLifecycle()
            LaunchedEffect(route.playlist.guid.value) {
                container.musicRepository.playlistCoverCandidates(route.playlist.guid.value)
            }
            TrackCollection(
                container = container,
                stateKey = "playlist:${route.playlist.guid.value}:tracks",
                title = route.playlist.name,
                coverId = route.playlist.coverId,
                loader = { container.musicRepository.playlistTracks(route.playlist.guid.value, it) },
                queueSource = { sort -> QueueSource.Playlist(route.playlist.guid.value, sort) },
                onPlayer = { open(LibraryRoute.Player(it)) },
                detailHeader = TrackDetailHeader(
                    kind = "歌单",
                    declaredTrackCount = route.playlist.trackCount,
                    deckCovers = playlistCovers[route.playlist.guid.value].orEmpty(),
                    onBack = back,
                ),
                contentRevision = 0L,
                removeTrack = { track ->
                    runCatching {
                        container.musicRepository.removeFromPlaylist(route.playlist.guid.value, track.guid.value)
                    }.isSuccess
                },
            )
        }
        LibraryRoute.Artists -> ArtistGrid(container, onOpen = { open(LibraryRoute.ArtistDetail(it)) })
        LibraryRoute.Albums -> AlbumGrid(container, onOpen = { open(LibraryRoute.AlbumDetail(it)) })
        LibraryRoute.AllTracks -> TrackCollection(
            container = container,
            stateKey = "all-tracks",
            title = "全部歌曲",
            coverId = null,
            loader = container.musicRepository::allTracks,
            queueSource = QueueSource::LibraryAllTracks,
            onPlayer = { open(LibraryRoute.Player(it)) },
            detailHeader = TrackDetailHeader(
                kind = "音乐库",
                artworkFallback = CollectionArtworkFallback.Collection,
                onBack = back,
            ),
            primaryAction = TrackCollectionPrimaryAction.StartRoam {
                open(LibraryRoute.Player(null))
            },
        )
        LibraryRoute.Favorites -> {
            val favoriteState by container.musicRepository.favoriteState.collectAsStateWithLifecycle()
            // 封面与首页“收藏”卡片同源：会话级封面组（covers:favorites）。
            val favoritesDeck = LocalLibraryRetainedState.current
                .list<FeatureArtworkItem>("covers:favorites")
                .snapshot.entries
                .mapNotNull { it.coverId }
            TrackCollection(
                container = container,
                stateKey = "favorites:tracks",
                title = "收藏",
                loader = container.musicRepository::favoriteTracks,
                queueSource = QueueSource::Favorites,
                onPlayer = { open(LibraryRoute.Player(it)) },
                detailHeader = TrackDetailHeader(
                    kind = "收藏",
                    artworkFallback = CollectionArtworkFallback.Favorites,
                    deckCovers = favoritesDeck,
                    onBack = back,
                ),
                contentRevision = favoriteState.revision,
                emptyMessage = "还没有收藏歌曲",
            )
        }
        LibraryRoute.Recent -> {
            // 封面与首页“最近播放”卡片同源：会话级封面组（covers:recent）。
            val recentDeck = LocalLibraryRetainedState.current
                .list<FeatureArtworkItem>("covers:recent")
                .snapshot.entries
                .mapNotNull { it.coverId }
            TrackCollection(
                container = container,
                stateKey = "recent:tracks",
                title = "最近播放",
                loader = container.musicRepository::recentTracks,
                queueSource = QueueSource::Recent,
                onPlayer = { open(LibraryRoute.Player(it)) },
                detailHeader = TrackDetailHeader(
                    kind = "最近播放",
                    artworkFallback = CollectionArtworkFallback.Collection,
                    deckCovers = recentDeck,
                    onBack = back,
                ),
                contentRevision = recentContentTick,
                emptyMessage = "还没有播放记录",
            )
        }
        is LibraryRoute.ArtistDetail -> ArtistDetail(
            container = container,
            artist = route.artist,
            onBack = back,
            onAlbum = { open(LibraryRoute.AlbumDetail(it)) },
            onPlayer = { open(LibraryRoute.Player(it)) },
        )
        is LibraryRoute.AlbumDetail -> TrackCollection(
            container = container,
            stateKey = "album:${route.album.guid.value}:tracks",
            title = route.album.name,
            subtitle = route.album.artistName.orEmpty(),
            coverId = route.album.coverId,
            loader = { container.musicRepository.albumTracks(route.album.guid.value, it) },
            queueSource = { sort -> QueueSource.Album(route.album.guid.value, sort) },
            onPlayer = { open(LibraryRoute.Player(it)) },
            detailHeader = TrackDetailHeader(
                kind = "专辑",
                extraMetadata = route.album.releaseDate,
                declaredTrackCount = route.album.trackCount,
                onBack = back,
            ),
        )
        is LibraryRoute.Player -> ImmersivePlayer(
            container = container,
            playback = playback,
            visualContinuity = playerVisualContinuity,
            onExitRoam = {
                retainedScope.launch {
                    stack = if (container.playbackController.exitRoamDurably()) {
                        stack.dropLast(1) + LibraryRoute.Player(null)
                    } else {
                        listOf(LibraryRoute.Home)
                    }
                }
            },
            onBack = back,
        )
                LibraryRoute.Settings -> SettingsScreen(container, onBack = back)
            }
        }
    }
}

@Composable
private fun LibraryTopBar(
    playback: PlaybackUiState,
    selectedHome: Boolean,
    onHome: () -> Unit,
    onMy: () -> Unit,
    onPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    nowPlayingFocus: FocusRequester,
    homeTabFocus: FocusRequester,
    myTabFocus: FocusRequester,
    contentDownFocus: FocusRequester,
) {
    Row(
        Modifier.fillMaxWidth().height(70.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playback.hasMedia) {
            NowPlayingPill(
                playback = playback,
                onClick = onPlayer,
                modifier = modifier
                    .focusProperties {
                        right = homeTabFocus
                        down = contentDownFocus
                    }
                    .focusRequester(nowPlayingFocus),
            )
        } else {
            Spacer(Modifier)
        }
        SegmentedLibraryTabs(
            selectedHome = selectedHome,
            playbackAvailable = playback.hasMedia,
            homeFocus = homeTabFocus,
            myFocus = myTabFocus,
            nowPlayingFocus = nowPlayingFocus,
            contentDownFocus = contentDownFocus,
            onHome = onHome,
            onMy = onMy,
        )
    }
}

@Composable
private fun SegmentedLibraryTabs(
    selectedHome: Boolean,
    playbackAvailable: Boolean,
    homeFocus: FocusRequester,
    myFocus: FocusRequester,
    nowPlayingFocus: FocusRequester,
    contentDownFocus: FocusRequester,
    onHome: () -> Unit,
    onMy: () -> Unit,
) {
    val containerShape = CircleShape
    Row(
        modifier = Modifier
            .size(width = 170.dp, height = 54.dp)
            .background(Color(0xFF171B1D), containerShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), containerShape)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryTab(
            label = "首页",
            selected = selectedHome,
            modifier = Modifier
                .focusProperties {
                    left = if (playbackAvailable) nowPlayingFocus else FocusRequester.Cancel
                    right = myFocus
                    down = contentDownFocus
                }
                .focusRequester(homeFocus),
            onClick = { if (!selectedHome) onHome() },
        )
        LibraryTab(
            label = "我的",
            selected = !selectedHome,
            modifier = Modifier
                .focusProperties {
                    left = homeFocus
                    right = FocusRequester.Cancel
                    down = contentDownFocus
                }
                .focusRequester(myFocus),
            onClick = { if (selectedHome) onMy() },
        )
    }
}

@Composable
private fun LibraryTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Button(
        onClick = onClick,
        modifier = modifier
            .size(width = 82.dp, height = 48.dp)
            .semantics { this.selected = selected },
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) FnColors.Coral else Color.Transparent,
            contentColor = if (selected) FnColors.Background else Color(0xFFADB0B6),
            focusedContainerColor = if (selected) Color(0xFFFF866D) else FnColors.FocusFill,
            focusedContentColor = if (selected) FnColors.Background else FnColors.Text,
            pressedContainerColor = FnColors.Coral,
            pressedContentColor = FnColors.Background,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
            pressedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 21.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
internal fun NowPlayingPill(
    playback: PlaybackUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTitleTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val shape = CircleShape
    val coverId = playback.coverId
    val fontScale = LocalDensity.current.fontScale
    val pillHeight = (42f + (fontScale - 1f).coerceAtLeast(0f) * 28f).dp
    Button(
        onClick = onClick,
        modifier = modifier
            .size(width = 186.dp, height = pillHeight)
            .semantics { contentDescription = "当前播放：${playback.title}" },
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF232827),
            contentColor = FnColors.Text,
            focusedContainerColor = Color(0xFF343A38),
            focusedContentColor = FnColors.Text,
            pressedContainerColor = Color(0xFF3B413F),
            pressedContentColor = FnColors.Text,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(0.5.dp, Color(0xFF454C49)), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
            pressedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
        ),
        contentPadding = PaddingValues(start = 5.dp, top = 4.5.dp, end = 9.dp, bottom = 4.5.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (coverId != null) {
                RemoteArtwork(
                    container = LocalAuthenticatedDependencies.current,
                    coverId = coverId,
                    variant = CoverVariant.Compact,
                    modifier = Modifier.size(27.dp),
                    shape = CircleShape,
                    contentScale = ContentScale.Crop,
                    placeholderContent = { NowPlayingArtworkFallback(playback.title) },
                )
            } else {
                NowPlayingArtworkFallback(playback.title)
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(3.5.dp).background(if (playback.isPlaying) FnColors.Coral else FnColors.Muted, CircleShape))
                    Spacer(Modifier.width(3.5.dp))
                    Text(
                        if (playback.isPlaying) "正在播放" else "已暂停",
                        color = Color(0xFFB7BBB7),
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(1.5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        playback.title.ifBlank { "正在播放" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = onTitleTextLayout,
                        modifier = Modifier.weight(1f),
                    )
                    if (playback.artist.isNotBlank()) {
                        Spacer(Modifier.width(4.5.dp))
                        Text(
                            playback.artist,
                            color = Color(0xFFA7ABA7),
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.62f, fill = false),
                        )
                    }
                }
            }
            Spacer(Modifier.width(5.dp))
            Text("›", color = Color(0xFFC6C9C5), fontSize = 17.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun NowPlayingArtworkFallback(title: String) {
    Box(
        Modifier.size(27.dp).background(Color(0xFF31413D), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.trim().take(1).ifBlank { "音" }.uppercase(),
            color = FnColors.Teal,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BrowseHome(
    container: AuthenticatedAppDependencies,
    playback: PlaybackUiState,
    onMy: () -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onFavorites: () -> Unit,
    onRecent: () -> Unit,
    onAll: () -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayer: () -> Unit,
) {
    val retainedStore = LocalLibraryRetainedState.current
    val playlistState = retainedStore.list<Playlist>("playlists")
    val albumState = retainedStore.paged<Album>("grid:albums")
    val favoritePreviewState = retainedStore.paged<Track>("home:favorites-preview")
    val recentPreviewState = retainedStore.paged<Track>("home:recent-preview")
    val favoriteLibraryState by container.musicRepository.favoriteState.collectAsStateWithLifecycle()
    val playlistSnapshot = playlistState.snapshot
    val playlists = playlistSnapshot.entries
    val playlistsLoaded = playlistSnapshot.initialLoadCompleted
    val albums = albumState.snapshot.entries
    val favoriteTracks = favoritePreviewState.snapshot.entries
    val roamCoverState = retainedStore.list<FeatureArtworkItem>("covers:roam")
    LaunchedEffect(albums, playlists) {
        if ((albums.isNotEmpty() || playlists.isNotEmpty()) && !roamCoverState.snapshot.initialLoadCompleted) {
            val deck = featureArtworkSlots(
                primary = albums.map { FeatureArtworkItem(it.name, it.coverId) },
                fallback = playlists.map { FeatureArtworkItem(it.name, it.coverId) },
            )
            if (deck.isNotEmpty()) roamCoverState.snapshot = retainLoadedList(roamCoverState.snapshot, deck)
        }
    }
    val roamArtwork = roamCoverState.snapshot.entries
    val favoriteCoverState = retainedStore.list<FeatureArtworkItem>("covers:favorites")
    LaunchedEffect(favoriteTracks) {
        if (favoriteTracks.isNotEmpty() && !favoriteCoverState.snapshot.initialLoadCompleted) {
            val deck = featureArtworkSlots(
                primary = favoriteTracks.shuffled().map { FeatureArtworkItem(it.title, it.coverId) },
            )
            if (deck.isNotEmpty()) favoriteCoverState.snapshot = retainLoadedList(favoriteCoverState.snapshot, deck)
        }
    }
    val favoriteArtwork = favoriteCoverState.snapshot.entries
    val recentTracksPreview = recentPreviewState.snapshot.entries
    val recentCoverState = retainedStore.list<FeatureArtworkItem>("covers:recent")
    LaunchedEffect(recentTracksPreview) {
        if (recentTracksPreview.isNotEmpty() && !recentCoverState.snapshot.initialLoadCompleted) {
            val deck = featureArtworkSlots(
                primary = recentTracksPreview.shuffled().map { FeatureArtworkItem(it.title, it.coverId) },
            )
            if (deck.isNotEmpty()) recentCoverState.snapshot = retainLoadedList(recentCoverState.snapshot, deck)
        }
    }
    val recentArtwork = recentCoverState.snapshot.entries
    // “全部歌单”卡片：与其它歌单卡片同一套拼排逻辑——启动时从曲库随机取
    // 3 首歌曲的封面，运行期间保持不变，下次启动重新生成。
    val allPlaylistsDeckState = retainedStore.list<FeatureArtworkItem>("covers:all-playlists")
    val allPlaylistsDeck = allPlaylistsDeckState.snapshot.entries
    LaunchedEffect(Unit) {
        if (!allPlaylistsDeckState.snapshot.initialLoadCompleted) {
            retainedStore.scope.launch {
                runCatching { container.musicRepository.randomTracks(16) }.onSuccess { tracks ->
                    val deck = tracks.asSequence()
                        .mapNotNull { track -> track.coverId?.let { FeatureArtworkItem(track.title, it) } }
                        .distinctBy { it.coverId }
                        .take(3)
                        .toList()
                    if (deck.isNotEmpty()) {
                        allPlaylistsDeckState.snapshot = retainLoadedList(allPlaylistsDeckState.snapshot, deck)
                    }
                }
            }
        }
    }
    val allPlaylistsDeckCovers = allPlaylistsDeck.mapNotNull { it.coverId }
    // 随机专辑/歌曲存会话级缓存：页面切换、返回不重拉；
    // 仅启动时缓存为空才拉一次，手动“刷新”按钮才重新采样。
    val randomAlbumState = retainedStore.list<Album>("random-albums")
    val randomAlbums = randomAlbumState.snapshot.entries
    var randomAlbumsLoading by remember { mutableStateOf(false) }
    fun refreshRandomAlbums() {
        if (randomAlbumsLoading) return
        randomAlbumsLoading = true
        retainedStore.scope.launch {
            runCatching { container.musicRepository.randomAlbums(16) }
                .onSuccess { randomAlbumState.snapshot = retainLoadedList(randomAlbumState.snapshot, it) }
            randomAlbumsLoading = false
        }
    }
    val randomSongState = retainedStore.list<Track>("random-songs")
    val randomSongs = randomSongState.snapshot.entries
    var randomSongsLoading by remember { mutableStateOf(false) }
    val randomSongScope = rememberCoroutineScope()
    fun refreshRandomSongs() {
        if (randomSongsLoading) return
        randomSongsLoading = true
        randomSongScope.launch {
            runCatching { container.musicRepository.randomTracks(16) }
                .onSuccess { randomSongState.snapshot = retainLoadedList(randomSongState.snapshot, it) }
            randomSongsLoading = false
        }
    }
    fun openSampledTrack(source: List<Track>, track: Track) {
        randomSongScope.launch {
            val prepared = runCatching { container.musicRepository.prepareQueue(source) }
                .getOrDefault(emptyList())
            val startIndex = prepared.indexOfFirst { it.track.guid == track.guid }
            if (startIndex < 0) return@launch
            runCatching {
                container.playbackController.playQueue(
                    tracks = prepared,
                    startIndex = startIndex,
                    source = null,
                )
            }.onSuccess { onPlayer() }
        }
    }

    fun openRandomSong(track: Track) {
        openSampledTrack(randomSongs, track)
    }
    LaunchedEffect(Unit) {
        retainedStore.scope.launch {
            runCatching { container.musicRepository.recentTracks(1) }.onSuccess { page ->
                recentPreviewState.snapshot = retainLoadedPage(recentPreviewState.snapshot, page) { it.guid.value }
            }
        }
        if (randomAlbums.isEmpty()) refreshRandomAlbums()
        if (randomSongs.isEmpty()) refreshRandomSongs()
    }
    val recentlyAddedState = retainedStore.list<Track>("recently-added")
    val recentlyAdded = recentlyAddedState.snapshot.entries
    LaunchedEffect(Unit) {
        if (recentlyAdded.isEmpty()) {
            retainedStore.scope.launch {
                runCatching { container.musicRepository.recentlyAddedTracks(16) }
                    .onSuccess { recentlyAddedState.snapshot = retainLoadedList(recentlyAddedState.snapshot, it.items) }
            }
        }
    }
    val playlistCovers by container.musicRepository.playlistCovers.collectAsStateWithLifecycle()
    LaunchedEffect(playlists) {
        // playlist/list 不带 trackCount（永远 null），不能拿它当过滤条件，
        // 否则拼排封面永远不会被拉取。空歌单自然返回空列表并回落默认样式。
        playlists.take(12).forEach { playlist ->
            retainedStore.scope.launch {
                container.musicRepository.playlistCoverCandidates(playlist.guid.value)
            }
        }
    }
    var actionError by remember { mutableStateOf<AppError?>(null) }
    var roamActionRunning by remember { mutableStateOf(false) }
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocusRequested by remember { mutableStateOf(false) }
    val contentFocus = remember { FocusRequester() }
    val nowPlayingFocus = remember { FocusRequester() }
    val homeTabFocus = remember { FocusRequester() }
    val myTabFocus = remember { FocusRequester() }
    val roamFocus = remember { FocusRequester() }
    val favoritesFocus = remember { FocusRequester() }
    val recentFocus = remember { FocusRequester() }
    val playlistRowFocus = remember { FocusRequester() }
    val refreshAlbumsFocus = remember { FocusRequester() }
    val randomAlbumsRowFocus = remember { FocusRequester() }
    val refreshSongsFocus = remember { FocusRequester() }
    val randomSongsRowFocus = remember { FocusRequester() }
    val recentlyAddedRowFocus = remember { FocusRequester() }
    val albumRowState = rememberLazyListState()
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        retainedStore.loadListOnce(playlistState, container.musicRepository::playlists)
        retainedStore.loadFirstPageOnce(
            albumState,
            { page -> container.musicRepository.albums(page, FULL_CATALOG_PAGE_SIZE) },
        ) { it.guid.value }
    }
    LaunchedEffect(favoriteLibraryState.revision) {
        retainedStore.loadFirstPageForRevision(
            state = favoritePreviewState,
            revision = favoriteLibraryState.revision,
            loader = container.musicRepository::favoriteTracks,
            key = { it.guid.value },
        )
    }
    LaunchedEffect(playlistsLoaded, playback.hasMedia, focusedKey) {
        if (initialFocusRequested) return@LaunchedEffect
        val availableKeys = buildList {
            add("roam")
            add("favorites")
            add("recent")
            if (playlistsLoaded) {
                addAll(playlists.take(12).map { "playlist:${it.guid.value}" })
                add("all-playlists")
            }
            if (playback.hasMedia) add("now-playing")
        }
        focusedKey = focusedKey?.takeIf(availableKeys::contains) ?: availableKeys.firstOrNull()
        yield()
        if (focusedKey != null) {
            runCatching { contentFocus.requestFocus() }
            initialFocusRequested = true
        }
    }
    val window = LocalAdaptiveWindow.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = window.horizontalMargin,
                vertical = if (window.shortHeight) 12.dp else 24.dp,
            ),
    ) {
        // The tab bar stays pinned outside the scroll container: short car
        // viewports scroll the content beneath it, never the tabs themselves.
        LibraryTopBar(
            playback = playback,
            selectedHome = true,
            onHome = {},
            onMy = onMy,
            onPlayer = {
                focusedKey = "now-playing"
                onPlayer()
            },
            modifier = Modifier
                .then(if (focusedKey == "now-playing") Modifier.focusRequester(contentFocus) else Modifier)
                .onFocusChanged { if (it.isFocused) focusedKey = "now-playing" },
            nowPlayingFocus = nowPlayingFocus,
            homeTabFocus = homeTabFocus,
            myTabFocus = myTabFocus,
            contentDownFocus = roamFocus,
        )
        Box(Modifier.fillMaxSize().weight(1f)) {
        Column(
            Modifier
                .fillMaxSize()
                // Short car viewports scroll the fixed home stack; TV content fits
                // and never scrolls.
                .verticalScroll(rememberScrollState())
        ) {
        Spacer(Modifier.height(12.dp))
        Text("听点什么", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            HomeFeatureCard(
                title = "随机漫游",
                kind = HomeArtworkKind.Roam,
                artwork = roamArtwork,
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        up = if (playback.hasMedia) nowPlayingFocus else homeTabFocus
                        right = favoritesFocus
                        down = playlistRowFocus
                    }
                    .focusRequester(roamFocus)
                    .then(if (focusedKey == "roam") Modifier.focusRequester(contentFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused) focusedKey = "roam" },
                onClick = {
                focusedKey = "roam"
                if (playback.queueKind == QueueKind.Roam) {
                    onPlayer()
                    return@HomeFeatureCard
                }
                if (roamActionRunning || playback.roamBusy) return@HomeFeatureCard
                roamActionRunning = true
                scope.launch {
                    try {
                        if (container.playbackController.startRoam()) {
                            actionError = null
                            onPlayer()
                        } else {
                            actionError = container.playbackController.state.value.roamError ?: AppError.Unknown()
                        }
                    } finally {
                        roamActionRunning = false
                    }
                }
                },
            )
            HomeFeatureCard(
                title = "收藏",
                kind = HomeArtworkKind.Favorites,
                artwork = favoriteArtwork,
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        up = if (playback.hasMedia) nowPlayingFocus else homeTabFocus
                        left = roamFocus
                        right = recentFocus
                        down = playlistRowFocus
                    }
                    .focusRequester(favoritesFocus)
                    .then(if (focusedKey == "favorites") Modifier.focusRequester(contentFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused) focusedKey = "favorites" },
                onClick = {
                    focusedKey = "favorites"
                    onFavorites()
                },
            )
            HomeFeatureCard(
                title = "最近播放",
                kind = HomeArtworkKind.Recent,
                artwork = recentArtwork,
                modifier = Modifier
                    .weight(1f)
                    .focusProperties {
                        up = if (playback.hasMedia) nowPlayingFocus else homeTabFocus
                        left = favoritesFocus
                        right = FocusRequester.Cancel
                        down = playlistRowFocus
                    }
                    .focusRequester(recentFocus)
                    .then(if (focusedKey == "recent") Modifier.focusRequester(contentFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused) focusedKey = "recent" },
                onClick = {
                    focusedKey = "recent"
                    onRecent()
                },
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("歌单", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(playlists.take(12), key = { _, playlist -> playlist.guid.value }) { index, playlist ->
                val key = "playlist:${playlist.guid.value}"
                HomePlaylistLockup(
                    title = playlist.name,
                    subtitle = "歌单",
                    coverId = playlist.coverId,
                    derivedCovers = playlistCovers[playlist.guid.value].orEmpty(),
                    modifier = Modifier
                        .focusProperties {
                            up = if (index == 0) roamFocus else favoritesFocus
                        }
                        // 仅行尾钳制：中间卡按右正常移动，行尾不逃逸到顶部标签。
                        .then(
                            if (index == playlists.take(12).lastIndex) {
                                Modifier.focusProperties { right = FocusRequester.Cancel }
                            } else {
                                Modifier
                            }
                        )
                        .then(if (index == 0) Modifier.focusRequester(playlistRowFocus) else Modifier)
                        .then(if (focusedKey == key) Modifier.focusRequester(contentFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) focusedKey = key },
                    onClick = {
                        focusedKey = key
                        onPlaylist(playlist)
                    },
                )
            }
            item {
                HomePlaylistLockup(
                    title = "全部歌单",
                    subtitle = "浏览全部",
                    coverId = null,
                    derivedCovers = allPlaylistsDeckCovers,
                    modifier = Modifier
                        .focusProperties {
                            up = favoritesFocus
                            right = FocusRequester.Cancel
                        }
                        .then(if (playlists.isEmpty()) Modifier.focusRequester(playlistRowFocus) else Modifier)
                        .then(if (focusedKey == "all-playlists") Modifier.focusRequester(contentFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) focusedKey = "all-playlists" },
                    onClick = {
                        focusedKey = "all-playlists"
                        onAll()
                    },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("随机专辑", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Button(
                onClick = { refreshRandomAlbums() },
                enabled = !randomAlbumsLoading,
                modifier = Modifier
                    .size(width = 88.dp, height = 38.dp)
                    .focusRequester(refreshAlbumsFocus)
                    .focusProperties {
                        up = playlistRowFocus
                        down = randomAlbumsRowFocus
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (randomAlbumsLoading) "刷新中…" else "刷新",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            state = albumRowState,
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(randomAlbums, key = { _, album -> "rand-album:${album.guid.value}" }) { index, album ->
                AlbumLockup(
                    title = album.name,
                    subtitle = album.artistName.orEmpty(),
                    coverId = album.coverId,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(randomAlbumsRowFocus) else Modifier)
                        .focusProperties {
                            up = refreshAlbumsFocus
                        }
                        .then(
                            if (index == randomAlbums.lastIndex) {
                                Modifier.focusProperties { right = FocusRequester.Cancel }
                            } else {
                                Modifier
                            }
                        ),
                    onClick = { onAlbum(album) },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("随机歌曲", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Button(
                onClick = { refreshRandomSongs() },
                enabled = !randomSongsLoading,
                modifier = Modifier
                    .size(width = 88.dp, height = 38.dp)
                    .focusRequester(refreshSongsFocus)
                    .focusProperties {
                        up = randomAlbumsRowFocus
                        down = randomSongsRowFocus
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                    },
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (randomSongsLoading) "刷新中…" else "刷新",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(randomSongs, key = { _, track -> "rand-song:${track.guid.value}" }) { index, track ->
                TrackLockup(
                    title = track.title,
                    subtitle = track.artistName.orEmpty(),
                    coverId = track.coverId,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(randomSongsRowFocus) else Modifier)
                        .focusProperties {
                            up = refreshSongsFocus
                        }
                        .then(
                            if (index == randomSongs.lastIndex) {
                                Modifier.focusProperties { right = FocusRequester.Cancel }
                            } else {
                                Modifier
                            }
                        ),
                    onClick = { openSampledTrack(randomSongs, track) },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("最近添加", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(recentlyAdded, key = { _, track -> "recently-added:${track.guid.value}" }) { index, track ->
                TrackLockup(
                    title = track.title,
                    subtitle = track.artistName.orEmpty(),
                    coverId = track.coverId,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(recentlyAddedRowFocus) else Modifier)
                        .focusProperties {
                            // 随机歌曲行可能为空（未加载完成或曲库过小），此时上键显式取消。
                            up = if (randomSongs.isNotEmpty()) randomSongsRowFocus else FocusRequester.Cancel
                        }
                        .then(
                            if (index == recentlyAdded.lastIndex) {
                                Modifier.focusProperties { right = FocusRequester.Cancel }
                            } else {
                                Modifier
                            }
                        ),
                    onClick = { openSampledTrack(recentlyAdded, track) },
                )
            }
        }
        (actionError ?: playlistSnapshot.error)?.let { InlineError(it) }
        }
        }
    }
}

internal data class FeatureArtworkItem(
    val title: String,
    val coverId: String?,
)

internal fun featureArtworkSlots(
    primary: List<FeatureArtworkItem>,
    fallback: List<FeatureArtworkItem> = emptyList(),
    limit: Int = 3,
): List<FeatureArtworkItem> {
    if (limit <= 0) return emptyList()
    return (primary + fallback)
        .asSequence()
        .mapNotNull { item ->
            item.coverId?.trim()?.takeIf(String::isNotEmpty)?.let { item.copy(coverId = it) }
        }
        .distinctBy { it.coverId }
        .take(limit)
        .toList()
}

@Composable
private fun HomeFeatureCard(
    title: String,
    kind: HomeArtworkKind,
    artwork: List<FeatureArtworkItem>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val background = when (kind) {
        HomeArtworkKind.Roam -> Brush.horizontalGradient(listOf(Color(0xFF071D19), Color(0xFF102823)))
        HomeArtworkKind.Favorites -> Brush.horizontalGradient(listOf(Color(0xFF1C1110), Color(0xFF2A1615)))
        HomeArtworkKind.Recent -> Brush.horizontalGradient(listOf(Color(0xFF10151C), Color(0xFF1A2330)))
        HomeArtworkKind.Collection,
        HomeArtworkKind.PlaylistGrid,
        -> Brush.horizontalGradient(listOf(Color(0xFF17201E), Color(0xFF22302D)))
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(150.dp),
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.018f),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = FnColors.Text,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = FnColors.Text,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = FnColors.Text,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
            pressedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxSize().background(background)) {
            FeatureCoverDeck(kind, artwork, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.27f),
                            0.38f to Color.Black.copy(alpha = 0.06f),
                            1f to Color.Black.copy(alpha = 0.13f),
                        ),
                    ),
            )
            FeatureGlyph(
                kind = kind,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 18.dp).size(48.dp),
            )
            Text(
                title,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 18.dp),
                fontSize = 20.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FeatureCoverDeck(
    kind: HomeArtworkKind,
    artwork: List<FeatureArtworkItem>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val placements = deckPlacements(kind, artwork.size)
    Box(modifier.clipToBounds()) {
        placements.forEach { placement ->
            val item = artwork.getOrNull(placement.itemIndex) ?: return@forEach
            val artworkShape = RoundedCornerShape(7.dp)
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(112.dp)
                    .graphicsLayer {
                        translationX = with(density) { placement.translationXDp.dp.toPx() }
                        translationY = with(density) { placement.translationYDp.dp.toPx() }
                        rotationZ = placement.rotation
                        shadowElevation = with(density) { 7.dp.toPx() }
                        shape = artworkShape
                        clip = true
                    }
                    .border(0.5.dp, Color.White.copy(alpha = 0.22f), artworkShape),
            ) {
                val coverId = item.coverId
                if (coverId != null) {
                    RemoteArtwork(
                        container = LocalAuthenticatedDependencies.current,
                        coverId = coverId,
                        variant = CoverVariant.Grid,
                        fallbackVariant = CoverVariant.Compact,
                        modifier = Modifier.fillMaxSize(),
                        shape = artworkShape,
                        contentScale = ContentScale.Crop,
                        placeholderContent = {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF242927), artworkShape),
                            )
                        },
                    )
                } else {
                    InitialArtworkPlaceholder(
                        text = item.title,
                        accent = if (kind == HomeArtworkKind.Roam) FnColors.Teal else FnColors.Coral,
                        modifier = Modifier.fillMaxSize(),
                        shape = artworkShape,
                    )
                }
            }
        }
    }
}

private fun deckPlacements(kind: HomeArtworkKind, artworkCount: Int): List<DeckPlacement> =
    when (kind) {
        HomeArtworkKind.Roam -> when (artworkCount.coerceAtMost(3)) {
            1 -> listOf(DeckPlacement(0, -72f, 3f, 0f))
            2 -> listOf(
                DeckPlacement(0, -118f, 7f, -8f),
                DeckPlacement(1, -24f, 5f, 8f),
            )
            3 -> listOf(
                DeckPlacement(0, -146f, 6f, -11f),
                DeckPlacement(1, -76f, 8f, 0f),
                DeckPlacement(2, -8f, 3f, 10f),
            )
            else -> emptyList()
        }
        HomeArtworkKind.Favorites -> when (artworkCount.coerceAtMost(3)) {
            1 -> listOf(DeckPlacement(0, -72f, 0f, 0f))
            2 -> listOf(
                DeckPlacement(0, -118f, 7f, -6f),
                DeckPlacement(1, -24f, 7f, 6f),
            )
            3 -> listOf(
                DeckPlacement(0, -140f, 8f, -7f),
                DeckPlacement(2, -8f, 8f, 7f),
                DeckPlacement(1, -72f, 0f, 0f),
            )
            else -> emptyList()
        }
        HomeArtworkKind.Recent -> when (artworkCount.coerceAtMost(3)) {
            1 -> listOf(DeckPlacement(0, -72f, 0f, 0f))
            2 -> listOf(
                DeckPlacement(0, -118f, 7f, -6f),
                DeckPlacement(1, -24f, 7f, 6f),
            )
            3 -> listOf(
                DeckPlacement(0, -140f, 8f, -7f),
                DeckPlacement(2, -8f, 8f, 7f),
                DeckPlacement(1, -72f, 0f, 0f),
            )
            else -> emptyList()
        }
        HomeArtworkKind.Collection,
        HomeArtworkKind.PlaylistGrid,
        -> emptyList()
    }

private data class DeckPlacement(
    val itemIndex: Int,
    val translationXDp: Float,
    val translationYDp: Float,
    val rotation: Float,
)

@Composable
private fun FeatureGlyph(kind: HomeArtworkKind, modifier: Modifier = Modifier) {
    val accent = when (kind) {
        HomeArtworkKind.Roam -> FnColors.Teal
        HomeArtworkKind.Recent -> FnColors.Warning
        else -> FnColors.Coral
    }
    Box(
        modifier
            .background(Color(0xFF0E1314).copy(alpha = 0.92f), CircleShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(25.dp)) {
            if (kind == HomeArtworkKind.Recent) {
                drawCircle(accent, radius = size.minDimension * 0.36f, style = Stroke(2.2.dp.toPx()))
                drawLine(accent, center, androidx.compose.ui.geometry.Offset(center.x, center.y - size.height * 0.22f), 2.2.dp.toPx(), StrokeCap.Round)
                drawLine(accent, center, androidx.compose.ui.geometry.Offset(center.x + size.width * 0.16f, center.y + size.height * 0.06f), 2.2.dp.toPx(), StrokeCap.Round)
            } else if (kind == HomeArtworkKind.Favorites) {
                drawPath(heartPath(size), color = accent, style = Stroke(width = 2.2.dp.toPx()))
            } else {
                val stroke = 2.1.dp.toPx()
                val startX = size.width * 0.08f
                val endX = size.width * 0.88f
                fun point(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
                drawLine(accent, point(startX, size.height * 0.24f), point(size.width * 0.34f, size.height * 0.24f), stroke, StrokeCap.Round)
                drawLine(accent, point(size.width * 0.34f, size.height * 0.24f), point(size.width * 0.67f, size.height * 0.76f), stroke, StrokeCap.Round)
                drawLine(accent, point(size.width * 0.67f, size.height * 0.76f), point(endX, size.height * 0.76f), stroke, StrokeCap.Round)
                drawLine(accent, point(startX, size.height * 0.76f), point(size.width * 0.34f, size.height * 0.76f), stroke, StrokeCap.Round)
                drawLine(accent, point(size.width * 0.34f, size.height * 0.76f), point(size.width * 0.67f, size.height * 0.24f), stroke, StrokeCap.Round)
                drawLine(accent, point(size.width * 0.67f, size.height * 0.24f), point(endX, size.height * 0.24f), stroke, StrokeCap.Round)
                drawLine(accent, point(endX, size.height * 0.24f), point(size.width * 0.75f, size.height * 0.12f), stroke, StrokeCap.Round)
                drawLine(accent, point(endX, size.height * 0.24f), point(size.width * 0.75f, size.height * 0.36f), stroke, StrokeCap.Round)
                drawLine(accent, point(endX, size.height * 0.76f), point(size.width * 0.75f, size.height * 0.64f), stroke, StrokeCap.Round)
                drawLine(accent, point(endX, size.height * 0.76f), point(size.width * 0.75f, size.height * 0.88f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun HomePlaylistLockup(
    title: String,
    subtitle: String,
    coverId: String?,
    modifier: Modifier = Modifier,
    fallback: HomeArtworkKind? = null,
    derivedCovers: List<String> = emptyList(),
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .size(width = 190.dp, height = 174.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.018f),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = FnColors.Text,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = FnColors.Text,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = FnColors.Text,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .then(if (focused) Modifier.border(1.5.dp, FnColors.Coral, shape) else Modifier)
                    .clip(shape),
            ) {
                PlaylistTileArtwork(
                    title = title,
                    coverId = coverId,
                    accent = FnColors.Coral,
                    modifier = Modifier.fillMaxSize(),
                    featureArtwork = fallback,
                    derivedCovers = derivedCovers,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(title, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = FnColors.Muted, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun isHomeBackConfirmed(
    previousBackAt: Long,
    currentBackAt: Long,
    windowMs: Long = 2_000L,
): Boolean = previousBackAt > 0L && currentBackAt >= previousBackAt &&
    currentBackAt - previousBackAt <= windowMs

@Composable
private fun BrowseMy(
    container: AuthenticatedAppDependencies,
    session: SessionState.SignedIn,
    playback: PlaybackUiState,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onGenres: () -> Unit,
    onGenre: (Genre) -> Unit,
    onArtists: () -> Unit,
    onAlbums: () -> Unit,
    onAllTracks: () -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onSettings: () -> Unit,
    onPlayer: () -> Unit,
) {
    val retainedStore = LocalLibraryRetainedState.current
    val artistState = retainedStore.paged<Artist>("grid:artists")
    val albumState = retainedStore.paged<Album>("grid:albums")
    val artists = artistState.snapshot.entries
    val albums = albumState.snapshot.entries
    val artistsLoaded = artistState.snapshot.initialLoadCompleted
    val albumsLoaded = albumState.snapshot.initialLoadCompleted
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var initialFocusRequested by remember { mutableStateOf(false) }
    val contentFocus = remember { FocusRequester() }
    val nowPlayingFocus = remember { FocusRequester() }
    val homeTabFocus = remember { FocusRequester() }
    val myTabFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val searchEntryFocus = remember { FocusRequester() }
    val switchAccountFocus = remember { FocusRequester() }
    val artistRowFocus = remember { FocusRequester() }
    val albumRowFocus = remember { FocusRequester() }
    val genreRowFocus = remember { FocusRequester() }
    val libraryRowFocus = remember { FocusRequester() }
    val genreState = retainedStore.list<Genre>("genres")
    val genres = genreState.snapshot.entries
    LaunchedEffect(Unit) {
        retainedStore.loadListOnce(genreState) { container.musicRepository.genres() }
    }
    val listState = rememberLazyListState()
    val scope = retainedStore.scope
    LaunchedEffect(Unit) {
        retainedStore.loadFirstPageOnce(
            artistState,
            { page -> container.musicRepository.artists(page, FULL_CATALOG_PAGE_SIZE) },
        ) { it.guid.value }
        retainedStore.loadFirstPageOnce(
            albumState,
            { page -> container.musicRepository.albums(page, FULL_CATALOG_PAGE_SIZE) },
        ) { it.guid.value }
    }
    LaunchedEffect(artistsLoaded, albumsLoaded, playback.hasMedia, focusedKey) {
        if (initialFocusRequested) return@LaunchedEffect
        val allContentLoaded = artistsLoaded && albumsLoaded
        val restoringChrome = focusedKey == "settings" || focusedKey == "switch-account" ||
            playback.hasMedia && focusedKey == "now-playing"
        if (!allContentLoaded && !restoringChrome) return@LaunchedEffect
        val availableKeys = buildList {
            if (allContentLoaded) {
                addAll(artists.take(8).map { "artist:${it.guid.value}" })
                add("all-artists")
                addAll(albums.take(8).map { "album:${it.guid.value}" })
                add("all-albums")
                add("all-tracks")
            }
            if (playback.hasMedia) add("now-playing")
            add("search")
            add("settings")
            add("switch-account")
        }
        focusedKey = focusedKey?.takeIf(availableKeys::contains) ?: availableKeys.firstOrNull()
        yield()
        if (focusedKey != null) {
            runCatching { contentFocus.requestFocus() }
            initialFocusRequested = true
        }
    }
    val window = LocalAdaptiveWindow.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = window.horizontalMargin)
            .padding(top = if (window.shortHeight) 12.dp else 24.dp),
    ) {
        LibraryTopBar(
            playback = playback,
            selectedHome = false,
            onHome = onHome,
            onMy = {},
            onPlayer = {
                focusedKey = "now-playing"
                onPlayer()
            },
            modifier = Modifier
                .then(if (focusedKey == "now-playing") Modifier.focusRequester(contentFocus) else Modifier)
                .onFocusChanged { if (it.isFocused) focusedKey = "now-playing" },
            nowPlayingFocus = nowPlayingFocus,
            homeTabFocus = homeTabFocus,
            myTabFocus = myTabFocus,
            contentDownFocus = searchEntryFocus,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .focusRequester(searchEntryFocus)
                .focusProperties { down = settingsFocus }
                .then(if (focusedKey == "search") Modifier.focusRequester(contentFocus) else Modifier)
                .onFocusChanged { if (it.isFocused) focusedKey = "search" },
            colors = ButtonDefaults.colors(
                containerColor = Color(0xFF1B201F),
                contentColor = FnColors.Muted,
                focusedContainerColor = Color(0xFF303634),
                focusedContentColor = FnColors.Text,
            ),
            contentPadding = PaddingValues(start = 20.dp),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Text("🔍", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text("搜索歌手、专辑、歌曲", fontSize = 17.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        ProfileStrip(
            username = session.user.username,
            serverName = session.server.name,
            focusedKey = focusedKey,
            restoredFocus = contentFocus,
            settingsFocus = settingsFocus,
            switchAccountFocus = switchAccountFocus,
            homeTabFocus = homeTabFocus,
            myTabFocus = myTabFocus,
            artistRowFocus = artistRowFocus,
            searchUpFocus = searchEntryFocus,
            onFocused = { focusedKey = it },
            onSettings = {
                focusedKey = "settings"
                onSettings()
            },
            onSwitchAccount = {
                focusedKey = "switch-account"
                scope.launch { container.authenticatedActions.switchAccount() }
            },
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(state = listState) {
            item {
                MediaBand(
                    "歌手",
                    artists.take(8).map {
                        val key = "artist:${it.guid.value}"
                        BandEntry(it.name, "${it.trackCount ?: 0} 首歌曲", it.coverId, BandKind.Artist, key) {
                            focusedKey = key
                            onArtist(it)
                        }
                    },
                    BandEntry("全部歌手", "浏览完整列表", null, BandKind.Artist, "all-artists") {
                        focusedKey = "all-artists"
                        onArtists()
                    },
                    focusedKey,
                    contentFocus,
                    rowFocusRequester = artistRowFocus,
                    upFocusRequester = settingsFocus,
                    downFocusRequester = albumRowFocus,
                    onFocused = { focusedKey = it },
                )
            }
            item {
                MediaBand(
                    "专辑",
                    albums.take(8).map {
                        val key = "album:${it.guid.value}"
                        BandEntry(it.name, it.artistName.orEmpty(), it.coverId, BandKind.Album, key) {
                            focusedKey = key
                            onAlbum(it)
                        }
                    },
                    BandEntry("全部专辑", "浏览完整列表", null, BandKind.Album, "all-albums", artworkRes = R.drawable.cover_all_albums) {
                        focusedKey = "all-albums"
                        onAlbums()
                    },
                    focusedKey,
                    contentFocus,
                    rowFocusRequester = albumRowFocus,
                    upFocusRequester = artistRowFocus,
                    downFocusRequester = genreRowFocus,
                    onFocused = { focusedKey = it },
                )
            }
            item {
                MediaBand(
                    "风格",
                    genres.take(8).map { genre ->
                        val key = "genre:" + genre.guid.value
                        BandEntry(genre.name, "风格", null, BandKind.Genre, key) {
                            focusedKey = key
                            onGenre(genre)
                        }
                    },
                    BandEntry("全部风格", "按风格筛选歌曲", null, BandKind.Genre, "all-genres") {
                        focusedKey = "all-genres"
                        onGenres()
                    },
                    focusedKey,
                    contentFocus,
                    rowFocusRequester = genreRowFocus,
                    upFocusRequester = albumRowFocus,
                    downFocusRequester = libraryRowFocus,
                    onFocused = { focusedKey = it },
                )
            }
            item {
                MediaBand(
                    "音乐库",
                    emptyList(),
                    BandEntry("全部歌曲", "完整曲库", null, BandKind.Library, "all-tracks") {
                        focusedKey = "all-tracks"
                        onAllTracks()
                    },
                    focusedKey,
                    contentFocus,
                    rowFocusRequester = libraryRowFocus,
                    upFocusRequester = genreRowFocus,
                    downFocusRequester = FocusRequester.Cancel,
                    onFocused = { focusedKey = it },
                )
            }
        }
    }
}

@Composable
private fun ProfileStrip(
    username: String,
    serverName: String,
    focusedKey: String?,
    restoredFocus: FocusRequester,
    settingsFocus: FocusRequester,
    switchAccountFocus: FocusRequester,
    homeTabFocus: FocusRequester,
    myTabFocus: FocusRequester,
    artistRowFocus: FocusRequester,
    searchUpFocus: FocusRequester? = null,
    onFocused: (String) -> Unit,
    onSettings: () -> Unit,
    onSwitchAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(username, Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                username.ifBlank { "音乐用户" },
                fontSize = 18.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            ServerChip(serverName)
        }
        Spacer(Modifier.width(12.dp))
        ProfileActionButton(
            label = "设置",
            glyph = ProfileGlyph.Settings,
            modifier = Modifier
                .width(72.dp)
                .focusProperties {
                    left = FocusRequester.Cancel
                    right = switchAccountFocus
                    up = searchUpFocus ?: homeTabFocus
                    down = artistRowFocus
                }
                .focusRequester(settingsFocus)
                .then(if (focusedKey == "settings") Modifier.focusRequester(restoredFocus) else Modifier)
                .onFocusChanged { if (it.isFocused) onFocused("settings") },
            onClick = onSettings,
        )
        Spacer(Modifier.width(8.dp))
        ProfileActionButton(
            label = "切换账号",
            glyph = ProfileGlyph.SwitchAccount,
            modifier = Modifier
                .width(102.dp)
                .focusProperties {
                    left = settingsFocus
                    right = FocusRequester.Cancel
                    up = myTabFocus
                    down = artistRowFocus
                }
                .focusRequester(switchAccountFocus)
                .then(if (focusedKey == "switch-account") Modifier.focusRequester(restoredFocus) else Modifier)
                .onFocusChanged { if (it.isFocused) onFocused("switch-account") },
            onClick = onSwitchAccount,
        )
    }
}

@Composable
private fun ProfileAvatar(username: String, modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFF8A70), Color(0xFFD6A35E), Color(0xFF70C8AF)),
            ),
            shape = CircleShape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            username.trim().take(1).ifBlank { "音" }.uppercase(),
            color = FnColors.Background,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ServerChip(serverName: String) {
    val shape = CircleShape
    Row(
        modifier = Modifier
            .width(132.dp)
            .height(18.dp)
            .border(0.5.dp, Color.White.copy(alpha = 0.17f), shape)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileGlyphCanvas(ProfileGlyph.Server, Modifier.size(11.dp), Color(0xFF9FA5A8))
        Spacer(Modifier.width(5.dp))
        Text(
            serverName.ifBlank { "NAS" },
            color = Color(0xFFB7BBBE),
            fontSize = 10.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class ProfileGlyph { Settings, SwitchAccount, Server }

@Composable
private fun ProfileActionButton(
    label: String,
    glyph: ProfileGlyph,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.035f),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFFC7CACC),
            focusedContainerColor = FnColors.Coral.copy(alpha = 0.08f),
            focusedContentColor = FnColors.Coral,
            pressedContainerColor = FnColors.Coral.copy(alpha = 0.13f),
            pressedContentColor = FnColors.Coral,
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.2.dp, FnColors.Coral), shape = shape),
            pressedBorder = Border(BorderStroke(1.2.dp, FnColors.Coral), shape = shape),
        ),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
    ) {
        ProfileGlyphCanvas(glyph, Modifier.size(15.dp), LocalContentColor.current)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, lineHeight = 15.sp, maxLines = 1)
    }
}

@Composable
private fun ProfileGlyphCanvas(
    glyph: ProfileGlyph,
    modifier: Modifier = Modifier,
    color: Color,
) {
    Canvas(modifier) {
        val stroke = 1.45.dp.toPx()
        when (glyph) {
            ProfileGlyph.Settings -> {
                drawCircle(color, size.minDimension * 0.24f, center, style = Stroke(stroke))
                drawCircle(color, size.minDimension * 0.06f, center)
                repeat(8) { index ->
                    val angle = index * Math.PI.toFloat() / 4f
                    val inner = size.minDimension * 0.34f
                    val outer = size.minDimension * 0.46f
                    drawLine(
                        color,
                        androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle) * inner, center.y + kotlin.math.sin(angle) * inner),
                        androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle) * outer, center.y + kotlin.math.sin(angle) * outer),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }
            ProfileGlyph.SwitchAccount -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.33f), androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.33f), stroke, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.33f), androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.17f), stroke, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.33f), androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.49f), stroke, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.69f), androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.69f), stroke, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.69f), androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.53f), stroke, StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.69f), androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.85f), stroke, StrokeCap.Round)
            }
            ProfileGlyph.Server -> {
                val shape = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.06f, size.height * 0.08f), size = androidx.compose.ui.geometry.Size(size.width * 0.88f, size.height * 0.34f), cornerRadius = shape, style = Stroke(stroke))
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.06f, size.height * 0.58f), size = androidx.compose.ui.geometry.Size(size.width * 0.88f, size.height * 0.34f), cornerRadius = shape, style = Stroke(stroke))
            }
        }
    }
}

private enum class BandKind { Artist, Album, Genre, Library }

private data class BandEntry(
    val title: String,
    val subtitle: String,
    val coverId: String?,
    val kind: BandKind,
    val focusKey: String,
    /** 终端卡片的静态封面资源（如“全部专辑”），优先于 coverId/占位图。 */
    val artworkRes: Int? = null,
    val action: (() -> Unit)?,
)

internal fun mediaBandReturnFocusKey(
    entryKeys: List<String>,
    terminalKey: String,
    lastFocusedKey: String?,
): String = lastFocusedKey
    ?.takeIf { it == terminalKey || it in entryKeys }
    ?: entryKeys.firstOrNull()
    ?: terminalKey

@Composable
private fun MediaBand(
    title: String,
    entries: List<BandEntry>,
    terminalEntry: BandEntry,
    focusedKey: String?,
    focusRequester: FocusRequester,
    rowFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onFocused: (String) -> Unit,
) {
    var lastFocusedEntryKey by rememberSaveable(terminalEntry.focusKey) {
        mutableStateOf<String?>(null)
    }
    val entryKeys = entries.map(BandEntry::focusKey)
    val returnFocusKey = mediaBandReturnFocusKey(
        entryKeys = entryKeys,
        terminalKey = terminalEntry.focusKey,
        lastFocusedKey = lastFocusedEntryKey,
    )
    Column {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.focusKey }) { index, entry ->
                BandLockup(
                    entry,
                    Modifier
                        .focusProperties {
                            if (index == 0) left = FocusRequester.Cancel
                            up = upFocusRequester
                            down = downFocusRequester
                        }
                        .then(if (returnFocusKey == entry.focusKey) Modifier.focusRequester(rowFocusRequester) else Modifier)
                        .then(if (focusedKey == entry.focusKey) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastFocusedEntryKey = entry.focusKey
                                onFocused(entry.focusKey)
                            }
                        },
                )
            }
            item {
                BandLockup(
                    terminalEntry,
                    Modifier
                        .focusProperties {
                            if (entries.isEmpty()) left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            up = upFocusRequester
                            down = downFocusRequester
                        }
                        .then(if (returnFocusKey == terminalEntry.focusKey) Modifier.focusRequester(rowFocusRequester) else Modifier)
                        .then(if (focusedKey == terminalEntry.focusKey) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastFocusedEntryKey = terminalEntry.focusKey
                                onFocused(terminalEntry.focusKey)
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun BandLockup(entry: BandEntry, modifier: Modifier = Modifier) {
    when (entry.kind) {
        BandKind.Artist -> ArtistLockup(
            entry.title,
            entry.subtitle,
            entry.coverId,
            modifier = modifier,
            enabled = entry.action != null,
        ) { entry.action?.invoke() }
        BandKind.Album -> AlbumLockup(
            entry.title,
            entry.subtitle,
            entry.coverId,
            modifier = modifier,
            enabled = entry.action != null,
            artworkRes = entry.artworkRes,
        ) { entry.action?.invoke() }
        BandKind.Genre -> GenreLockup(
            entry.title,
            entry.subtitle,
            modifier = modifier,
            enabled = entry.action != null,
        ) { entry.action?.invoke() }
        BandKind.Library -> MyLibraryLockup(
            entry.title,
            entry.subtitle,
            modifier = modifier,
            enabled = entry.action != null,
        ) { entry.action?.invoke() }
    }
}

@Composable
private fun MyLibraryLockup(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val artworkShape = RoundedCornerShape(4.dp)
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.size(width = 170.dp, height = 95.dp),
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = lockupButtonColors(),
        border = lockupButtonBorder(shape),
        contentPadding = PaddingValues(7.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            CollectionArtworkFallbackContent(
                title = title,
                fallback = CollectionArtworkFallback.Collection,
                modifier = Modifier.size(73.dp),
                shape = artworkShape,
            )
            Spacer(Modifier.width(9.dp))
            LockupLabels(title, subtitle, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AllPlaylists(container: AuthenticatedAppDependencies, onBack: () -> Unit, onOpen: (Playlist) -> Unit) {
    val retainedStore = LocalLibraryRetainedState.current
    val playlistState = retainedStore.list<Playlist>("playlists")
    val playlists = playlistState.snapshot.entries
    val playlistCovers by container.musicRepository.playlistCovers.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        retainedStore.loadListOnce(playlistState, container.musicRepository::playlists)
    }
    LaunchedEffect(playlists) {
        // playlist/list 不带 trackCount（永远 null），不能拿它当过滤条件，
        // 否则拼排封面永远不会被拉取。空歌单自然返回空列表并回落默认样式。
        playlists.forEach { playlist ->
            retainedStore.scope.launch {
                container.musicRepository.playlistCoverCandidates(playlist.guid.value)
            }
        }
    }
    GridPage("全部歌单", playlists, { it.guid.value }, onBack = onBack) { playlist, modifier ->
        PlaylistTile(
            playlist.name,
            "歌单",
            playlist.coverId,
            FnColors.Coral,
            derivedCovers = playlistCovers[playlist.guid.value].orEmpty(),
            modifier = modifier,
        ) { onOpen(playlist) }
    }
}

@Composable
private fun ArtistGrid(container: AuthenticatedAppDependencies, onOpen: (Artist) -> Unit) {
    PagedCatalogPage(
        stateKey = "artists",
        title = "全部歌手",
        totalLabel = { "$it 位歌手" },
        loader = { page -> container.musicRepository.artists(page, FULL_CATALOG_PAGE_SIZE) },
        key = { it.guid.value },
    ) { artist, modifier ->
        ArtistLockup(artist.name, "${artist.trackCount ?: 0} 首歌曲", artist.coverId, modifier = modifier) { onOpen(artist) }
    }
}

@Composable
private fun AlbumGrid(container: AuthenticatedAppDependencies, onOpen: (Album) -> Unit) {
    PagedCatalogPage(
        stateKey = "albums",
        title = "全部专辑",
        totalLabel = { "$it 张专辑" },
        loader = { page -> container.musicRepository.albums(page, FULL_CATALOG_PAGE_SIZE) },
        key = { it.guid.value },
    ) { album, modifier ->
        AlbumLockup(album.name, album.artistName.orEmpty(), album.coverId, modifier = modifier) { onOpen(album) }
    }
}

@Composable
private fun <T> PagedCatalogPage(
    stateKey: String,
    title: String,
    totalLabel: (Int) -> String,
    loader: suspend (Int) -> Page<T>,
    key: (T) -> String,
    onBack: (() -> Unit)? = null,
    item: @Composable (T, Modifier) -> Unit,
) {
    val retainedStore = LocalLibraryRetainedState.current
    val retained = retainedStore.paged<T>("grid:$stateKey")
    val snapshot = retained.snapshot
    val entries = snapshot.entries
    var currentPage by rememberSaveable(stateKey) { mutableStateOf(1) }
    var focusedKey by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }
    var lastFocusedIndex by rememberSaveable(stateKey) { mutableStateOf(0) }
    var pendingPage by remember(stateKey) { mutableStateOf<Int?>(null) }
    var pagerFocusedTarget by remember(stateKey) { mutableStateOf<CatalogPagerTarget?>(null) }
    var initialFocusRequested by remember(stateKey) { mutableStateOf(false) }
    val previousPageFocus = remember(stateKey) { FocusRequester() }
    val nextPageFocus = remember(stateKey) { FocusRequester() }

    fun load(target: Int) {
        if (retained.loading || target > 1 && !retained.snapshot.hasNext) return
        retained.loading = true
        retainedStore.scope.launch {
            runCatching { loader(target) }
                .onSuccess { retained.snapshot = retainLoadedPage(retained.snapshot, it, key) }
                .onFailure {
                    retained.snapshot = retained.snapshot.copy(
                        error = (it as? AppException)?.error ?: AppError.Unknown(),
                        initialLoadCompleted = retained.snapshot.initialLoadCompleted || target == 1,
                    )
                }
            retained.loading = false
        }
    }
    LaunchedEffect(stateKey) {
        if (shouldLoadInitialPage(retained.snapshot)) {
            load(1)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val window = LocalAdaptiveWindow.current
        val pageSize = FULL_CATALOG_PAGE_SIZE
        val horizontalPadding = window.horizontalMargin
        val verticalPadding = if (window.shortHeight) 16.dp else 44.dp
        // Four fixed columns stay the tuned TV layout; narrower car screens
        // shrink the column count and enable grid scrolling so all 12 page
        // items remain reachable.
        val columns = fittedGridColumns(
            availableWidth = maxWidth - horizontalPadding * 2,
            maxColumns = 4,
        )
        val scrollableGrid = columns < 4
        val gridHeight = 95.dp * 3 + 14.dp * 2 + 8.dp
        val totalPages = catalogPageCount(snapshot.total, entries.size, pageSize)
        val visibleEntries = catalogPageEntries(entries, currentPage, pageSize)
        val itemFocuses = remember(stateKey, pageSize) { List(pageSize) { FocusRequester() } }
        val retryFocus = remember(stateKey) { FocusRequester() }
        val canPrevious = currentPage > 1
        val canNext = currentPage < totalPages
        val returnItemFocus = itemFocuses[
            lastFocusedIndex.coerceIn(0, visibleEntries.lastIndex.coerceAtLeast(0)),
        ]

        fun showPage(target: Int) {
            if (target !in 1..totalPages) return
            val start = catalogPageStartIndex(target, pageSize)
            if (start < entries.size) {
                currentPage = target
                pendingPage = null
            } else {
                pendingPage = target
                if (!retained.loading) load(target)
            }
        }

        LaunchedEffect(snapshot.initialLoadCompleted, entries, pageSize, focusedKey) {
            if (!snapshot.initialLoadCompleted || entries.isEmpty() || initialFocusRequested) return@LaunchedEffect
            val retainedIndex = focusedKey?.let { retainedKey -> entries.indexOfFirst { key(it) == retainedKey } } ?: -1
            val targetPage = if (retainedIndex >= 0) retainedIndex / pageSize + 1 else currentPage.coerceIn(1, totalPages)
            if (currentPage != targetPage) {
                currentPage = targetPage
                return@LaunchedEffect
            }
            val targetIndex = if (retainedIndex >= 0) retainedIndex % pageSize else 0
            focusedKey = key(visibleEntries.getOrElse(targetIndex) { visibleEntries.first() })
            lastFocusedIndex = targetIndex.coerceAtMost(visibleEntries.lastIndex)
            yield()
            runCatching { itemFocuses[lastFocusedIndex].requestFocus() }
            initialFocusRequested = true
        }

        LaunchedEffect(snapshot.initialLoadCompleted, entries, snapshot.error) {
            if (
                !snapshot.initialLoadCompleted ||
                entries.isNotEmpty() ||
                snapshot.error == null ||
                initialFocusRequested
            ) return@LaunchedEffect
            yield()
            runCatching { retryFocus.requestFocus() }
            initialFocusRequested = true
        }

        LaunchedEffect(snapshot.initialLoadCompleted, totalPages, pageSize) {
            if (snapshot.initialLoadCompleted && currentPage > totalPages) currentPage = totalPages
        }

        LaunchedEffect(currentPage, pageSize, entries.size, snapshot.page, snapshot.hasNext) {
            if (
                snapshot.initialLoadCompleted &&
                !retained.loading &&
                shouldPrefetchCatalogContinuation(currentPage, pageSize, entries.size, snapshot.hasNext)
            ) {
                load(snapshot.page + 1)
            }
        }

        LaunchedEffect(pendingPage, entries.size, snapshot.hasNext, retained.loading, snapshot.error) {
            val target = pendingPage ?: return@LaunchedEffect
            if (catalogPageStartIndex(target, pageSize) < entries.size) {
                currentPage = target.coerceAtMost(totalPages)
                pendingPage = null
            } else if (!retained.loading && snapshot.hasNext && snapshot.error == null) {
                load(target)
            } else if (!retained.loading) {
                pendingPage = null
            }
        }

        LaunchedEffect(canPrevious, canNext, pagerFocusedTarget) {
            when {
                pagerFocusedTarget == CatalogPagerTarget.Previous && !canPrevious && canNext -> {
                    yield()
                    runCatching { nextPageFocus.requestFocus() }
                }

                pagerFocusedTarget == CatalogPagerTarget.Next && !canNext && canPrevious -> {
                    yield()
                    runCatching { previousPageFocus.requestFocus() }
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onBack?.let { onBack ->
                    DetailBackButton(onClick = onBack)
                    Spacer(Modifier.width(16.dp))
                }
                Text(title, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                snapshot.total?.let { total ->
                    Spacer(Modifier.width(14.dp))
                    Text(totalLabel(total), color = FnColors.Muted, fontSize = 12.sp)
                }
                snapshot.error?.let { error ->
                    Spacer(Modifier.width(18.dp))
                    Text(appErrorMessage(error), color = FnColors.Coral, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = if (scrollableGrid) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth().height(gridHeight)
                },
                userScrollEnabled = scrollableGrid,
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(
                    count = visibleEntries.size,
                    key = { index -> key(visibleEntries[index]) },
                ) { index ->
                    val entry = visibleEntries[index]
                    val entryKey = key(entry)
                    val column = index % columns
                    val nextRowIndex = index + columns
                    val pagerTarget = catalogPagerTarget(column, columns, canPrevious, canNext)
                    item(
                        entry,
                        Modifier
                            .focusProperties {
                                left = itemFocuses.getOrNull(index - 1)
                                    ?.takeIf { column > 0 }
                                    ?: FocusRequester.Cancel
                                right = itemFocuses.getOrNull(index + 1)
                                    ?.takeIf { column < columns - 1 && index + 1 < visibleEntries.size }
                                    ?: FocusRequester.Cancel
                                up = itemFocuses.getOrNull(index - columns) ?: FocusRequester.Cancel
                                down = if (nextRowIndex < visibleEntries.size) {
                                    itemFocuses[nextRowIndex]
                                } else {
                                    when (pagerTarget) {
                                        CatalogPagerTarget.Previous -> previousPageFocus
                                        CatalogPagerTarget.Next -> nextPageFocus
                                        null -> FocusRequester.Cancel
                                    }
                                }
                            }
                            .focusRequester(itemFocuses[index])
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focusedKey = entryKey
                                    lastFocusedIndex = index
                                }
                            },
                    )
                }

                if (visibleEntries.isEmpty() && snapshot.error != null) {
                    item {
                        CatalogRetryButton(
                            loading = retained.loading,
                            modifier = Modifier.focusRequester(retryFocus),
                        ) { load(1) }
                    }
                }
            }
            if (!scrollableGrid) {
                Spacer(Modifier.weight(1f))
            }
            CatalogPager(
                currentPage = currentPage,
                totalPages = totalPages,
                canPrevious = canPrevious,
                canNext = canNext,
                previousFocus = previousPageFocus,
                nextFocus = nextPageFocus,
                upFocus = returnItemFocus,
                onPagerFocused = { pagerFocusedTarget = it },
                onPrevious = { showPage(currentPage - 1) },
                onNext = { showPage(currentPage + 1) },
            )
        }
    }
}

@Composable
private fun <T> GridPage(
    title: String,
    entries: List<T>,
    key: (T) -> String,
    onBack: (() -> Unit)? = null,
    item: @Composable (T, Modifier) -> Unit,
) {
    var focusedKey by rememberSaveable(title) { mutableStateOf<String?>(null) }
    var initialFocusRequested by remember(title) { mutableStateOf(false) }
    val contentFocus = remember(title) { FocusRequester() }
    val gridState = rememberLazyGridState()
    LaunchedEffect(entries, focusedKey) {
        if (entries.isEmpty() || initialFocusRequested) return@LaunchedEffect
        val keys = entries.map(key)
        focusedKey = focusedKey?.takeIf(keys::contains) ?: keys.first()
        yield()
        runCatching { contentFocus.requestFocus() }
        initialFocusRequested = true
    }
    val window = LocalAdaptiveWindow.current
    Column(
        Modifier.fillMaxSize().padding(
            horizontal = window.horizontalMargin,
            vertical = if (window.shortHeight) 16.dp else 44.dp,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                DetailBackButton(onClick = onBack)
                Spacer(Modifier.width(16.dp))
            }
            Text(title, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            state = gridState,
            // Playlist tiles are 193dp wide; adaptive cells keep exactly four
            // columns on TV and reflow on narrower car screens.
            columns = GridCells.Adaptive(minSize = 193.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(entries, key = key) { entry ->
                val entryKey = key(entry)
                item(
                    entry,
                    Modifier.then(if (focusedKey == entryKey) Modifier.focusRequester(contentFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) focusedKey = entryKey },
                )
            }
        }
    }
}

private enum class ArtistDetailSection { Songs, Albums }

private data class TrackDetailTab(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onFocus: () -> Unit,
    val onSelect: () -> Unit,
)

private data class TrackDetailHeader(
    val kind: String,
    val declaredTrackCount: Int? = null,
    val extraMetadata: String? = null,
    val artworkFallback: CollectionArtworkFallback = CollectionArtworkFallback.Initial,
    val tabs: List<TrackDetailTab> = emptyList(),
    /** 与首页对应卡片同一来源的封面拼排；非空时优先于单张封面。 */
    val deckCovers: List<String> = emptyList(),
    val onBack: () -> Unit,
)

private enum class CollectionArtworkFallback { Initial, Artist, Favorites, Collection }

private sealed interface TrackCollectionPrimaryAction {
    data object PlayAll : TrackCollectionPrimaryAction
    data class StartRoam(val onStarted: () -> Unit) : TrackCollectionPrimaryAction
}

@Composable
private fun ArtistDetail(
    container: AuthenticatedAppDependencies,
    artist: Artist,
    onBack: () -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayer: (Track) -> Unit,
) {
    val stateKey = "artist:${artist.guid.value}"
    val retainedStore = LocalLibraryRetainedState.current
    val albumState = retainedStore.paged<Album>("$stateKey:albums")
    val albumSnapshot = albumState.snapshot
    val albums = albumSnapshot.entries
    var selectedSection by rememberSaveable(stateKey) { mutableStateOf(ArtistDetailSection.Songs) }
    var focusedArea by rememberSaveable(stateKey) { mutableStateOf("songs") }
    fun loadAlbums(target: Int) {
        if (albumState.loading || target > 1 && !albumState.snapshot.hasNext) return
        albumState.loading = true
        retainedStore.scope.launch {
            runCatching { container.musicRepository.artistAlbums(artist.guid.value, target) }
                .onSuccess {
                    albumState.snapshot = retainLoadedPage(albumState.snapshot, it) { album -> album.guid.value }
                }
                .onFailure {
                    albumState.snapshot = albumState.snapshot.copy(
                        error = (it as? AppException)?.error ?: AppError.Unknown(),
                        initialLoadCompleted = albumState.snapshot.initialLoadCompleted || target == 1,
                    )
                }
            albumState.loading = false
        }
    }
    LaunchedEffect(stateKey) {
        if (shouldLoadInitialPage(albumState.snapshot)) loadAlbums(1)
    }
    val tabs = ArtistDetailSection.entries.map { section ->
        TrackDetailTab(
            key = "artist-tab:${section.name}",
            label = if (section == ArtistDetailSection.Songs) "歌曲" else "专辑",
            selected = selectedSection == section,
            onFocus = { focusedArea = "tabs" },
            onSelect = {
                selectedSection = section
                focusedArea = "tabs"
            },
        )
    }
    TrackCollection(
        container = container,
        stateKey = "$stateKey:tracks",
        title = artist.name,
        coverId = artist.coverId,
        loader = { container.musicRepository.artistTracks(artist.guid.value, it) },
        queueSource = { sort -> QueueSource.Artist(artist.guid.value, sort) },
        onPlayer = onPlayer,
        initialFocusEnabled = selectedSection == ArtistDetailSection.Songs && focusedArea == "songs",
        onFocusOwnerChanged = { focusedArea = "songs" },
        detailHeader = TrackDetailHeader(
            kind = "歌手",
            declaredTrackCount = artist.trackCount,
            extraMetadata = artist.albumCount?.let { "$it 张专辑" },
            artworkFallback = CollectionArtworkFallback.Artist,
            tabs = tabs,
            onBack = onBack,
        ),
        showTrackList = selectedSection == ArtistDetailSection.Songs,
        alternateContent = {
            ArtistAlbumGrid(
                stateKey = "$stateKey:album-grid",
                snapshot = albumSnapshot,
                loading = albumState.loading,
                initialFocusEnabled = selectedSection == ArtistDetailSection.Albums && focusedArea == "albums",
                onLoad = ::loadAlbums,
                onFocused = { focusedArea = "albums" },
                onAlbum = onAlbum,
            )
        },
    )
}

@Composable
private fun ArtistAlbumGrid(
    stateKey: String,
    snapshot: RetainedPageSnapshot<Album>,
    loading: Boolean,
    initialFocusEnabled: Boolean,
    onLoad: (Int) -> Unit,
    onFocused: () -> Unit,
    onAlbum: (Album) -> Unit,
) {
    val albums = snapshot.entries
    var focusedKey by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }
    var initialFocusRequested by remember(stateKey) { mutableStateOf(false) }
    val restoredFocus = remember(stateKey) { FocusRequester() }
    val gridState = rememberLazyGridState()
    LaunchedEffect(snapshot.initialLoadCompleted, albums, initialFocusEnabled, focusedKey) {
        if (!initialFocusEnabled || !snapshot.initialLoadCompleted || albums.isEmpty() || initialFocusRequested) return@LaunchedEffect
        val availableKeys = albums.map { it.guid.value }
        val targetKey = focusedKey?.takeIf(availableKeys::contains) ?: availableKeys.first()
        if (focusedKey != targetKey) {
            focusedKey = targetKey
            return@LaunchedEffect
        }
        repeat(3) {
            withFrameNanos { }
            if (runCatching { restoredFocus.requestFocus() }.getOrDefault(false)) {
                initialFocusRequested = true
                return@LaunchedEffect
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            // Three album columns stay on TV; narrow car screens drop to what fits.
            columns = GridCells.Fixed(
                fittedGridColumns(
                    availableWidth = maxWidth,
                    maxColumns = 3,
                    minTileWidth = 165.dp,
                    spacing = 12.dp,
                ),
            ),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (albums.isEmpty() && loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("正在加载专辑", color = FnColors.Muted, fontSize = 16.sp, modifier = Modifier.padding(vertical = 20.dp))
            }
        }
        items(albums, key = { it.guid.value }) { album ->
            val index = albums.indexOf(album)
            DetailAlbumCard(
                album = album,
                modifier = Modifier
                    .then(if (focusedKey == album.guid.value) Modifier.focusRequester(restoredFocus) else Modifier)
                    .onFocusChanged {
                        if (it.isFocused) {
                            focusedKey = album.guid.value
                            onFocused()
                            if (snapshot.hasNext && index >= albums.size - 6) onLoad(snapshot.page + 1)
                        }
                    },
                onClick = {
                    focusedKey = album.guid.value
                    onAlbum(album)
                },
            )
        }
        if (albums.isEmpty() && !loading && snapshot.error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(onClick = { onLoad(1) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("专辑加载失败，重试")
                }
            }
        }
        if (snapshot.hasNext) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(
                    enabled = !loading,
                    onClick = { onLoad(snapshot.page + 1) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (loading) "正在加载" else "加载更多专辑")
                }
            }
        }
        }
    }
}

@Composable
private fun DetailAlbumCard(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val container = LocalAuthenticatedDependencies.current
    val albumCoverId = album.coverId
    val shape = RoundedCornerShape(6.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(106.dp)
            .onFocusChanged { state ->
                if (state.isFocused && albumCoverId != null) {
                    container.artworkBitmapCache.prefetch(albumCoverId, CoverVariant.Grid)
                }
            },
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = ButtonDefaults.colors(
            containerColor = FnColors.Surface,
            contentColor = FnColors.Text,
            focusedContainerColor = FnColors.FocusFill,
            focusedContentColor = FnColors.Text,
            pressedContainerColor = FnColors.FocusFill,
            pressedContentColor = FnColors.Text,
        ),
        contentPadding = PaddingValues(9.dp),
    ) {
        val contentColor = LocalContentColor.current
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            val artworkShape = RoundedCornerShape(4.dp)
            if (albumCoverId != null) {
                RemoteArtwork(
                    container = container,
                    coverId = albumCoverId,
                    variant = CoverVariant.Compact,
                    modifier = Modifier.size(88.dp),
                    shape = artworkShape,
                    contentScale = ContentScale.Crop,
                    placeholderContent = {
                        InitialArtworkPlaceholder(album.name, FnColors.Coral, Modifier.fillMaxSize(), artworkShape)
                    },
                )
            } else {
                InitialArtworkPlaceholder(album.name, FnColors.Coral, Modifier.size(88.dp), artworkShape)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    album.name,
                    color = contentColor,
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val metadata = listOfNotNull(
                    album.releaseDate?.takeIf(String::isNotBlank),
                    album.trackCount?.let { "$it 首" },
                ).joinToString(" · ")
                if (metadata.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        metadata,
                        color = contentColor.copy(alpha = 0.68f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackCollection(
    container: AuthenticatedAppDependencies,
    stateKey: String,
    title: String,
    subtitle: String = "",
    coverId: String? = null,
    loader: suspend (Int) -> Page<Track>,
    queueSource: (String) -> QueueSource,
    onPlayer: (Track) -> Unit,
    initialFocusEnabled: Boolean = true,
    onFocusOwnerChanged: () -> Unit = {},
    detailHeader: TrackDetailHeader,
    primaryAction: TrackCollectionPrimaryAction = TrackCollectionPrimaryAction.PlayAll,
    showTrackList: Boolean = true,
    alternateContent: @Composable () -> Unit = {},
    contentRevision: Long = 0L,
    emptyMessage: String = "暂无歌曲",
    removeTrack: (suspend (Track) -> Boolean)? = null,
) {
    val retainedStore = LocalLibraryRetainedState.current
    val retained = retainedStore.tracks(stateKey)
    val snapshot = retained.snapshot
    val tracks = snapshot.tracks
    val loadedPages = snapshot.loadedPages
    val page = snapshot.page
    val hasNext = snapshot.hasNext
    val loading = retained.loading
    val error = snapshot.error
    val expectedTotal = snapshot.expectedTotal
    val expectedSort = snapshot.expectedSort
    var focusedKey by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }
    var initialFocusRequested by remember(stateKey) { mutableStateOf(false) }
    val restoredFocus = remember(stateKey) { FocusRequester() }
    val listState = rememberLazyListState()
    val actionScope = rememberCoroutineScope()
    var primaryActionRunning by remember(stateKey) { mutableStateOf(false) }
    var loadedContentRevision by rememberSaveable(stateKey) { mutableStateOf<Long?>(null) }
    var pendingRemoveTrack by remember(stateKey) { mutableStateOf<Track?>(null) }
    var removingTrack by remember(stateKey) { mutableStateOf(false) }
    var removeMessage by remember(stateKey) { mutableStateOf<String?>(null) }

    fun requestRemove(track: Track) {
        if (removingTrack || removeTrack == null) return
        removeMessage = null
        pendingRemoveTrack = track
    }

    fun confirmRemove(track: Track) {
        val remove = removeTrack ?: return
        if (removingTrack) return
        removingTrack = true
        removeMessage = null
        actionScope.launch {
            val succeeded = runCatching { remove(track) }.getOrDefault(false)
            removingTrack = false
            if (!succeeded) {
                // 删除失败：对话框保持打开并给出提示，列表保持原状。
                removeMessage = "删除失败，请检查网络后重试"
                return@launch
            }
            // 就地移除，不回源、不重置页码，焦点落到相邻歌曲上。
            pendingRemoveTrack = null
            val before = retained.snapshot
            val removedIndex = before.tracks.indexOfFirst { it.guid == track.guid }
            retained.snapshot = removeTrackFromCollection(before, track.guid)
            val neighbor = before.tracks.getOrNull(removedIndex + 1)
                ?: before.tracks.getOrNull(removedIndex - 1)
            if (neighbor != null) {
                focusedKey = neighbor.guid.value
                yield()
                runCatching { restoredFocus.requestFocus() }
            }
        }
    }
    fun setError(value: AppError?) {
        retained.snapshot = retained.snapshot.copy(error = value)
    }
    fun load(target: Int) {
        if (retained.loading) return
        retained.loading = true
        retainedStore.scope.launch {
            runCatching { loader(target) }
                .onSuccess {
                    retained.snapshot = retainTrackCollectionPage(retained.snapshot, it, target)
                }
                .onFailure {
                    retained.snapshot = retained.snapshot.copy(
                        error = (it as? AppException)?.error ?: AppError.Unknown(),
                        initialLoadCompleted = retained.snapshot.initialLoadCompleted || target == 1,
                    )
                }
            retained.loading = false
        }
    }
    fun play(index: Int) {
        val target = tracks.getOrNull(index)?.takeIf(::isTrackPlayable) ?: return
        val window = exactTrackQueueWindow(loadedPages, index) ?: run {
            setError(AppError.CollectionChanged)
            return
        }
        val queue = container.musicRepository.prepareQueue(window.items)
        val queueIds = queue.map { it.track.guid.value }
        val segmentIds = window.segments.flatMap(QueuePageSegment::mediaIds)
        val queueIndex = queue.indexOfFirst { it.track.guid == target.guid }
        if (queueIds != segmentIds || queueIndex < 0) {
            setError(AppError.TranscodeUnavailable)
            return
        }
        if (queue.isEmpty()) {
            setError(AppError.TranscodeUnavailable)
            return
        }
        val sort = expectedSort ?: run {
            setError(AppError.CollectionChanged)
            return
        }
        actionScope.launch {
            val transition = runCatching {
                container.playbackController.playQueue(
                    tracks = queue,
                    startIndex = queueIndex,
                    source = queueSource(sort),
                    windowStart = window.segments.first().sourceStartIndex,
                    firstPage = window.segments.first().page,
                    lastPage = window.segments.last().page,
                    knownTotal = expectedTotal,
                    segments = window.segments,
                )
            }.getOrElse {
                setError(AppError.Unknown(it.message))
                return@launch
            }
            if (transition == null) {
                setError(AppError.TranscodeUnavailable)
                return@launch
            }
            runCatching { transition.awaitCommitted() }
                .onSuccess {
                    setError(null)
                    onPlayer(target)
                }
                .onFailure { setError(AppError.Unknown(it.message)) }
        }
    }
    fun runPrimaryAction() {
        when (val action = primaryAction) {
            TrackCollectionPrimaryAction.PlayAll -> play(tracks.indexOfFirst(::isTrackPlayable))
            is TrackCollectionPrimaryAction.StartRoam -> {
                if (primaryActionRunning) return
                if (container.playbackController.state.value.queueKind == QueueKind.Roam) {
                    action.onStarted()
                    return
                }
                primaryActionRunning = true
                actionScope.launch {
                    if (container.playbackController.startRoam()) {
                        setError(null)
                        action.onStarted()
                    } else {
                        setError(container.playbackController.state.value.roamError ?: AppError.Unknown())
                    }
                    primaryActionRunning = false
                }
            }
        }
    }
    LaunchedEffect(stateKey, contentRevision) {
        if (loadedContentRevision != null && loadedContentRevision != contentRevision) {
            retained.snapshot = RetainedTrackCollectionSnapshot()
            initialFocusRequested = false
        }
        loadedContentRevision = contentRevision
        if (!retained.snapshot.initialLoadCompleted) {
            load(1)
        }
    }
    val playableTracks = tracks.filter(::isTrackPlayable)
    val primaryActionEnabled = when (primaryAction) {
        TrackCollectionPrimaryAction.PlayAll -> playableTracks.isNotEmpty()
        is TrackCollectionPrimaryAction.StartRoam -> true
    }
    val primaryActionLabel = when (primaryAction) {
        TrackCollectionPrimaryAction.PlayAll -> "播放全部"
        is TrackCollectionPrimaryAction.StartRoam -> "随机漫游"
    }
    LaunchedEffect(snapshot.initialLoadCompleted, tracks, focusedKey, initialFocusEnabled, showTrackList, primaryActionEnabled) {
        if (!initialFocusEnabled || !snapshot.initialLoadCompleted || initialFocusRequested) return@LaunchedEffect
        val availableKeys = buildList {
            if (primaryActionEnabled) add("primary-action")
            detailHeader.tabs.filter { it.selected }.forEach { add(it.key) }
            if (showTrackList) addAll(playableTracks.map { it.guid.value })
            add("detail-back")
        }
        val targetKey = focusedKey?.takeIf(availableKeys::contains) ?: availableKeys.firstOrNull()
        if (focusedKey != targetKey) {
            focusedKey = targetKey
            return@LaunchedEffect
        }
        if (targetKey != null) {
            repeat(3) {
                withFrameNanos { }
                if (runCatching { restoredFocus.requestFocus() }.getOrDefault(false)) {
                    initialFocusRequested = true
                    return@LaunchedEffect
                }
            }
        }
    }
    DetailTrackCollection(
        container = container,
        header = detailHeader,
        title = title,
        subtitle = subtitle,
        coverId = coverId,
        trackCount = detailHeader.declaredTrackCount ?: expectedTotal?.takeIf { it > 0 },
        tracks = tracks,
        loading = loading,
        error = error,
        hasNext = hasNext,
        listState = listState,
        focusedKey = focusedKey,
        restoredFocus = restoredFocus,
        primaryActionLabel = primaryActionLabel,
        primaryActionEnabled = primaryActionEnabled,
        showTrackList = showTrackList,
        onFocusKey = {
            focusedKey = it
            onFocusOwnerChanged()
        },
        onPrimaryAction = ::runPrimaryAction,
        onTrackFocused = { index, key ->
            focusedKey = key
            onFocusOwnerChanged()
            if (hasNext && index >= tracks.size - 15) load(page + 1)
        },
        onTrack = ::play,
        onLoadMore = { load(page + 1) },
        alternateContent = alternateContent,
        emptyMessage = emptyMessage,
        canRemoveTrack = removeTrack != null,
        onRequestRemove = ::requestRemove,
    )

    pendingRemoveTrack?.let { track ->
        val cancelFocus = remember(track) { FocusRequester() }
        LaunchedEffect(track) {
            yield()
            runCatching { cancelFocus.requestFocus() }
        }
        Dialog(onDismissRequest = { if (!removingTrack) pendingRemoveTrack = null }) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(0.9f)
                    .background(FnColors.Surface, RoundedCornerShape(8.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("从歌单删除", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "将「" + track.title + "」从当前歌单中删除？",
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
                if (removingTrack) {
                    Text("正在删除…", color = FnColors.Muted, fontSize = 14.sp)
                } else {
                    removeMessage?.let {
                        Text(it, color = FnColors.Warning, fontSize = 14.sp)
                    }
                    val dialogShape = RoundedCornerShape(24.dp)
                    val dialogScale = ButtonDefaults.scale(focusedScale = 1.05f)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Button(
                            onClick = { pendingRemoveTrack = null },
                            modifier = Modifier
                                .size(width = 132.dp, height = 46.dp)
                                .focusRequester(cancelFocus),
                            shape = ButtonDefaults.shape(dialogShape, dialogShape, dialogShape, dialogShape, dialogShape),
                            scale = dialogScale,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("取消", fontSize = 14.sp)
                            }
                        }
                        Button(
                            onClick = { confirmRemove(track) },
                            modifier = Modifier.size(width = 132.dp, height = 46.dp),
                            shape = ButtonDefaults.shape(dialogShape, dialogShape, dialogShape, dialogShape, dialogShape),
                            scale = dialogScale,
                            colors = ButtonDefaults.colors(
                                containerColor = FnColors.Coral,
                                contentColor = FnColors.Text,
                                focusedContainerColor = FnColors.Coral,
                                focusedContentColor = FnColors.Text,
                            ),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("删除", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTrackCollection(
    container: AuthenticatedAppDependencies,
    header: TrackDetailHeader,
    title: String,
    subtitle: String,
    coverId: String?,
    trackCount: Int?,
    tracks: List<Track>,
    loading: Boolean,
    error: AppError?,
    hasNext: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusedKey: String?,
    restoredFocus: FocusRequester,
    primaryActionLabel: String,
    primaryActionEnabled: Boolean,
    showTrackList: Boolean,
    onFocusKey: (String) -> Unit,
    onPrimaryAction: () -> Unit,
    onTrackFocused: (Int, String) -> Unit,
    onTrack: (Int) -> Unit,
    onLoadMore: () -> Unit,
    canRemoveTrack: Boolean = false,
    onRequestRemove: (Track) -> Unit = {},
    alternateContent: @Composable () -> Unit = {},
    emptyMessage: String,
) {
    val window = LocalAdaptiveWindow.current
    Column(
        Modifier.fillMaxSize().padding(
            horizontal = minOf(window.horizontalMargin, 42.dp),
            vertical = if (window.shortHeight) 12.dp else 24.dp,
        ),
    ) {
        Row(Modifier.fillMaxWidth().height(184.dp), verticalAlignment = Alignment.Top) {
            DetailBackButton(
                modifier = Modifier
                    .then(if (focusedKey == "detail-back") Modifier.focusRequester(restoredFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocusKey("detail-back") },
                onClick = header.onBack,
            )
            Spacer(Modifier.width(18.dp))
            val artworkShape = RoundedCornerShape(6.dp)
            CollectionArtwork(
                container = container,
                title = title,
                coverId = coverId,
                fallback = header.artworkFallback,
                modifier = Modifier.size(184.dp),
                shape = artworkShape,
                deckCovers = header.deckCovers,
            )
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (header.kind.isNotBlank() && header.kind != title) {
                    Text(header.kind, color = FnColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                }
                Text(
                    title,
                    fontSize = 31.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val metadata = buildList {
                    if (subtitle.isNotBlank()) add(subtitle)
                    trackCount?.let { add("$it 首歌曲") }
                    header.extraMetadata?.takeIf(String::isNotBlank)?.let(::add)
                }.joinToString("  ·  ")
                if (metadata.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(metadata, color = FnColors.Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    header.tabs.forEach { tab ->
                        DetailTabButton(
                            label = tab.label,
                            selected = tab.selected,
                            modifier = Modifier
                                .then(if (focusedKey == tab.key) Modifier.focusRequester(restoredFocus) else Modifier)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onFocusKey(tab.key)
                                        tab.onFocus()
                                    }
                                },
                            onClick = tab.onSelect,
                        )
                    }
                    val primaryShape = RoundedCornerShape(6.dp)
                    Button(
                        enabled = primaryActionEnabled,
                        onClick = onPrimaryAction,
                        modifier = Modifier
                            .then(if (focusedKey == "primary-action") Modifier.focusRequester(restoredFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) onFocusKey("primary-action") }
                            .height(44.dp),
                        shape = ButtonDefaults.shape(
                            primaryShape,
                            primaryShape,
                            primaryShape,
                            primaryShape,
                            primaryShape,
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1.035f),
                        colors = ButtonDefaults.colors(
                            containerColor = FnColors.Surface,
                            contentColor = FnColors.Text,
                            focusedContainerColor = FnColors.FocusFill,
                            focusedContentColor = FnColors.Text,
                            pressedContainerColor = FnColors.FocusFill,
                            pressedContentColor = FnColors.Text,
                        ),
                        border = ButtonDefaults.border(
                            border = Border(BorderStroke(1.dp, FnColors.Coral.copy(alpha = 0.76f)), shape = primaryShape),
                            focusedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = primaryShape),
                            pressedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = primaryShape),
                        ),
                        contentPadding = PaddingValues(horizontal = 19.dp, vertical = 0.dp),
                    ) {
                        Text("▶  $primaryActionLabel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        if (showTrackList) {
            DetailTrackColumnHeader(withRemoveColumn = canRemoveTrack)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.09f)))
            LazyColumn(Modifier.weight(1f), state = listState) {
                if (error != null) {
                    item { InlineError(error) }
                }
                if (tracks.isEmpty() && loading) {
                    item {
                        Text("正在加载歌曲", color = FnColors.Muted, fontSize = 16.sp, modifier = Modifier.padding(vertical = 20.dp))
                    }
                }
                if (tracks.isEmpty() && !loading && error == null) {
                    item {
                        Text(
                            emptyMessage,
                            color = FnColors.Muted,
                            fontSize = 17.sp,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                itemsIndexed(tracks, key = { _, track -> track.guid.value }) { index, track ->
                    DetailTrackRow(
                        index = index,
                        track = track,
                        enabled = isTrackPlayable(track),
                        canRemove = canRemoveTrack,
                        onRemove = { onRequestRemove(track) },
                        modifier = Modifier
                            .then(if (focusedKey == track.guid.value) Modifier.focusRequester(restoredFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) onTrackFocused(index, track.guid.value) },
                        onClick = { onTrack(index) },
                    )
                }
                if (hasNext) {
                    item {
                        Button(
                            enabled = !loading,
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 5.dp),
                        ) {
                            Text(if (loading) "正在加载" else "加载更多")
                        }
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) { alternateContent() }
        }
    }
}

@Composable
private fun CollectionArtwork(
    container: AuthenticatedAppDependencies,
    title: String,
    coverId: String?,
    fallback: CollectionArtworkFallback,
    modifier: Modifier,
    shape: Shape,
    deckCovers: List<String> = emptyList(),
) {
    val resolvedShape = if (fallback == CollectionArtworkFallback.Artist) CircleShape else shape
    // 与首页卡片一致：有封面拼排时优先展示（歌单/收藏/最近播放详情页）。
    if (deckCovers.isNotEmpty()) {
        PlaylistCoverDeck(
            covers = deckCovers.take(3),
            title = title,
            accent = FnColors.Coral,
            modifier = modifier,
        )
    } else if (coverId != null) {
        RemoteArtwork(
            container = container,
            coverId = coverId,
            variant = CoverVariant.Grid,
            modifier = modifier,
            shape = resolvedShape,
            contentScale = ContentScale.Crop,
            placeholderContent = {
                CollectionArtworkFallbackContent(title, fallback, Modifier.fillMaxSize(), resolvedShape)
            },
        )
    } else {
        CollectionArtworkFallbackContent(title, fallback, modifier, resolvedShape)
    }
}

@Composable
private fun CollectionArtworkFallbackContent(
    title: String,
    fallback: CollectionArtworkFallback,
    modifier: Modifier,
    shape: Shape,
) {
    when (fallback) {
        CollectionArtworkFallback.Artist -> ArtistAvatarPlaceholder(
            title = title,
            modifier = modifier,
            fontSize = 58.sp,
        )
        CollectionArtworkFallback.Favorites -> Box(modifier.clip(shape)) {
            HomeFeatureArtwork(HomeArtworkKind.Favorites, Modifier.fillMaxSize())
        }
        CollectionArtworkFallback.Collection -> Box(modifier.clip(shape)) {
            HomeFeatureArtwork(HomeArtworkKind.Collection, Modifier.fillMaxSize())
        }
        CollectionArtworkFallback.Initial -> InitialArtworkPlaceholder(
            text = title,
            accent = FnColors.Coral,
            modifier = modifier,
            shape = shape,
        )
    }
}

@Composable
internal fun DetailBackButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = CircleShape
    Button(
        onClick = onClick,
        modifier = modifier.size(48.dp).semantics { contentDescription = "返回" },
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.08f),
        colors = ButtonDefaults.colors(
            containerColor = FnColors.Surface,
            contentColor = FnColors.Text,
            focusedContainerColor = FnColors.FocusFill,
            focusedContentColor = FnColors.Text,
            pressedContainerColor = FnColors.FocusFill,
            pressedContentColor = FnColors.Text,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text("‹", fontSize = 36.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailTabButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp).semantics { this.selected = selected },
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) FnColors.Coral.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = if (selected) FnColors.Coral else FnColors.Muted,
            focusedContainerColor = FnColors.FocusFill,
            focusedContentColor = FnColors.Text,
            pressedContainerColor = FnColors.FocusFill,
            pressedContentColor = FnColors.Text,
        ),
        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 0.dp),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun DetailTrackColumnHeader(withRemoveColumn: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", color = FnColors.Muted, fontSize = 12.sp, modifier = Modifier.width(54.dp))
        Text("歌曲", color = FnColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("歌手", color = FnColors.Muted, fontSize = 12.sp, modifier = Modifier.width(220.dp))
        Text("时长", color = FnColors.Muted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(70.dp))
        // 与数据行的删除按钮占同宽，保证“时长”列与时间数值纵向对齐。
        if (withRemoveColumn) {
            Spacer(Modifier.width(12.dp + 44.dp))
        }
    }
}

@Composable
private fun DetailTrackRow(
    index: Int,
    track: Track,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    canRemove: Boolean = false,
    onRemove: () -> Unit = {},
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    // 删除按钮必须是行按钮的兄弟节点：tv Button 会吞掉其内部子节点的点击，
    // 嵌套时点删除会触发行播放。兄弟节点同时保证不可播放的行也能删除。
    // 注意：modifier（restoredFocus/焦点回调）必须挂在播放按钮上，
    // FocusRequester 只有和焦点目标同链才能恢复焦点。
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            enabled = enabled,
            onClick = onClick,
            modifier = modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics {
                    contentDescription = "${index + 1}. ${track.title} ${track.artistName.orEmpty()}"
                },
            shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
            scale = ButtonDefaults.scale(focusedScale = 1f),
            colors = ButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = FnColors.Text,
                focusedContainerColor = FnColors.FocusFill,
                focusedContentColor = FnColors.Text,
                pressedContainerColor = FnColors.FocusFill,
                pressedContentColor = FnColors.Text,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = FnColors.Muted.copy(alpha = 0.5f),
            ),
            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 0.dp),
        ) {
            val contentColor = LocalContentColor.current
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    color = contentColor.copy(alpha = 0.66f),
                    fontSize = 13.sp,
                    modifier = Modifier.width(54.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(track.title, color = contentColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    track.albumName?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = contentColor.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    track.artistName.orEmpty(),
                    color = contentColor.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(220.dp),
                )
                Text(
                    if (track.isCue) "需兼容" else formatDuration(track.durationMs ?: 0),
                    color = contentColor.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(70.dp),
                )
            }
        }
            if (canRemove) {
                Spacer(Modifier.width(12.dp))
                val removeShape = CircleShape
                Button(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "删除" + track.title },
                shape = ButtonDefaults.shape(removeShape, removeShape, removeShape, removeShape, removeShape),
                scale = ButtonDefaults.scale(focusedScale = 1.1f),
                colors = ButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = FnColors.Text.copy(alpha = 0.66f),
                    focusedContainerColor = FnColors.Coral,
                    focusedContentColor = FnColors.Text,
                    pressedContainerColor = FnColors.Coral,
                    pressedContentColor = FnColors.Text,
                ),
                border = ButtonDefaults.border(
                    border = Border(
                        BorderStroke(1.dp, Color(0xFF454B4D)),
                        shape = removeShape,
                    ),
                    focusedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = removeShape),
                    pressedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = removeShape),
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("✕", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

internal data class ExactTrackQueueWindow(
    val items: List<Track>,
    val segments: List<QueuePageSegment>,
)

internal fun exactTrackQueueWindow(
    pages: List<Page<Track>>,
    selectedIndex: Int,
    maxSize: Int = MAX_ACTIVE_QUEUE_ITEMS,
): ExactTrackQueueWindow? {
    val first = pages.firstOrNull() ?: return null
    if (first.pageSize <= 0 || maxSize < first.pageSize) return null
    val rawWindowSize = maxSize / first.pageSize * first.pageSize
    if (pages.withIndex().any { (index, page) ->
            page.page != first.page + index ||
                page.pageSize != first.pageSize ||
                page.total != first.total ||
                page.sort != first.sort ||
                page.items.size > page.pageSize
        }
    ) return null

    val allItems = pages.flatMap(Page<Track>::items)
    val selected = allItems.getOrNull(selectedIndex)?.takeIf(::isTrackPlayable) ?: return null
    val bounded = boundedQueueWindow(
        items = allItems,
        selectedIndex = selectedIndex,
        maxSize = rawWindowSize,
        pageSize = first.pageSize,
    )
    val windowStart = bounded.startIndex
    val windowEnd = windowStart + bounded.items.size
    var loadedOffset = 0
    val retainedPages = buildList {
        pages.forEach { page ->
            val pageStart = loadedOffset
            val pageEnd = pageStart + page.items.size
            loadedOffset = pageEnd
            if (pageEnd <= windowStart || pageStart >= windowEnd) return@forEach
            if (pageStart < windowStart || pageEnd > windowEnd) return null
            add(page)
        }
    }
    if (retainedPages.flatMap(Page<Track>::items) != bounded.items) return null
    val playableIds = bounded.items.filter(::isTrackPlayable).map { it.guid.value }
    if (playableIds.distinct().size != playableIds.size) return null

    val segments = retainedPages.map { page ->
        val sourceStartIndex = (page.page - 1) * page.pageSize
        QueuePageSegment(
            page = page.page,
            rawRowCount = page.items.size,
            playableItems = page.items.mapIndexedNotNull { index, track ->
                track.takeIf(::isTrackPlayable)?.let {
                    QueuePageItem(it.guid.value, sourceStartIndex + index)
                }
            },
            sort = page.sort,
            knownTotal = page.total,
            pageSize = page.pageSize,
            sourceStartIndex = sourceStartIndex,
        )
    }
    if (segments.flatMap(QueuePageSegment::mediaIds) != playableIds) return null
    if (selected.guid.value !in playableIds) return null
    return ExactTrackQueueWindow(items = bounded.items, segments = segments)
}

private fun isTrackPlayable(track: Track): Boolean =
    !track.isCue && (track.accessStatus == null || track.accessStatus == 0)

@Composable
private fun PlaylistTile(
    title: String,
    subtitle: String,
    coverId: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    featureArtwork: HomeArtworkKind? = null,
    derivedCovers: List<String> = emptyList(),
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(8.dp)
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.size(width = 193.dp, height = 142.dp),
        shape = ButtonDefaults.shape(
            shape = cardShape,
            focusedShape = cardShape,
            pressedShape = cardShape,
            disabledShape = cardShape,
            focusedDisabledShape = cardShape,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF1B201F),
            contentColor = FnColors.Text,
            focusedContainerColor = Color(0xFF303634),
            focusedContentColor = FnColors.Text,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            PlaylistTileArtwork(
                title = title,
                coverId = coverId,
                accent = accent,
                modifier = Modifier.fillMaxWidth().height(108.dp),
                featureArtwork = featureArtwork,
                derivedCovers = derivedCovers,
            )
            Row(
                Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 9.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    maxLines = if (subtitle.isBlank()) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = FnColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PlaylistTileArtwork(
    title: String,
    coverId: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    featureArtwork: HomeArtworkKind? = null,
    derivedCovers: List<String> = emptyList(),
) {
    val container = LocalAuthenticatedDependencies.current
    val shape = RectangleShape
    if (featureArtwork != null) {
        HomeFeatureArtwork(featureArtwork, modifier)
    } else if (derivedCovers.isNotEmpty()) {
        PlaylistCoverDeck(derivedCovers, title, accent, modifier)
    } else if (coverId != null) {
        RemoteArtwork(
            container = container,
            coverId = coverId,
            variant = CoverVariant.Grid,
            modifier = modifier,
            shape = shape,
            contentScale = ContentScale.Crop,
            placeholderContent = {
                InitialArtworkPlaceholder(title, accent, Modifier.fillMaxSize(), shape)
            },
        )
    } else {
        InitialArtworkPlaceholder(title, accent, modifier, shape)
    }
}

/** Horizontal 1-3 cover deck for coverless playlists, mirroring the Favorites deck. */
@Composable
private fun PlaylistCoverDeck(
    covers: List<String>,
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val container = LocalAuthenticatedDependencies.current
    val deckShape = RoundedCornerShape(6.dp)
    val count = covers.size.coerceIn(1, 3)
    val coverSize = when (count) {
        1 -> 84.dp
        2 -> 78.dp
        else -> 64.dp
    }
    val spacing = when (count) {
        1 -> 0.dp
        2 -> (-12).dp
        else -> (-14).dp
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            covers.take(count).forEachIndexed { index, coverId ->
                Box(
                    Modifier
                        .zIndex((count - index).toFloat())
                        .graphicsLayer {
                            rotationZ = when {
                                count < 3 -> 0f
                                index == 0 -> -5f
                                index == 1 -> 0f
                                else -> 5f
                            }
                        }
                        .border(0.5.dp, Color.White.copy(alpha = 0.22f), deckShape),
                ) {
                    RemoteArtwork(
                        container = container,
                        coverId = coverId,
                        variant = CoverVariant.Grid,
                        fallbackVariant = CoverVariant.Compact,
                        modifier = Modifier.size(coverSize),
                        shape = deckShape,
                        contentScale = ContentScale.Crop,
                        placeholderContent = {
                            Box(Modifier.size(coverSize).background(Color(0xFF242927), deckShape))
                        },
                    )
                }
            }
        }
    }
}

private enum class HomeArtworkKind { Roam, Favorites, Recent, Collection, PlaylistGrid }

@Composable
private fun HomeFeatureArtwork(kind: HomeArtworkKind, modifier: Modifier) {
    Canvas(modifier) {
        val shortEdge = minOf(size.width, size.height)
        fun point(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y)
        fun drawVinyl(center: androidx.compose.ui.geometry.Offset, radius: Float, label: Color) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38393D),
                        Color(0xFF0B0C0E),
                        Color(0xFF27282C),
                        Color(0xFF08090A),
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            listOf(0.58f, 0.72f, 0.86f).forEach { scale ->
                drawCircle(
                    Color.White.copy(alpha = 0.08f),
                    radius * scale,
                    center,
                    style = Stroke(shortEdge * 0.006f),
                )
            }
            drawCircle(label, radius * 0.24f, center)
            drawCircle(Color(0xFFF8F2E7), radius * 0.055f, center)
        }

        when (kind) {
            HomeArtworkKind.Recent -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF101820), Color(0xFF1B2733)),
                        start = point(0.08f, 0f),
                        end = point(0.94f, 1f),
                    ),
                )
                // Large clock face: rim, hands, center pin.
                val clockCenter = point(0.66f, 0.5f)
                val clockRadius = shortEdge * 0.34f
                drawCircle(Color(0xFF0D1114).copy(alpha = 0.9f), clockRadius, clockCenter)
                drawCircle(
                    Color.White.copy(alpha = 0.16f),
                    clockRadius,
                    clockCenter,
                    style = Stroke(shortEdge * 0.012f),
                )
                val handStroke = shortEdge * 0.022f
                drawLine(
                    FnColors.Warning,
                    clockCenter,
                    clockCenter + androidx.compose.ui.geometry.Offset(0f, -clockRadius * 0.62f),
                    handStroke,
                    StrokeCap.Round,
                )
                drawLine(
                    FnColors.Warning,
                    clockCenter,
                    clockCenter + androidx.compose.ui.geometry.Offset(clockRadius * 0.42f, clockRadius * 0.12f),
                    handStroke,
                    StrokeCap.Round,
                )
                drawCircle(FnColors.Warning, clockRadius * 0.07f, clockCenter)
                // Trailing tick marks on the right edge.
                listOf(0.16f, 0.32f, 0.48f).forEach { y ->
                    drawLine(
                        Color.White.copy(alpha = 0.10f),
                        point(0.92f, y),
                        point(0.98f, y),
                        shortEdge * 0.008f,
                    )
                }
            }
            HomeArtworkKind.Roam -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFEAE6DF), Color(0xFFC5CDCB)),
                        start = point(0.08f, 0f),
                        end = point(0.94f, 1f),
                    ),
                )
                drawVinyl(point(0.69f, 0.51f), shortEdge * 0.43f, FnColors.Warning)

                val sleeveEdge = size.height * 0.84f
                val sleeveTopLeft = point(0.12f, 0.08f)
                val sleeveSize = androidx.compose.ui.geometry.Size(sleeveEdge, sleeveEdge)
                val sleeveCorner = androidx.compose.ui.geometry.CornerRadius(shortEdge * 0.065f)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.20f),
                    topLeft = sleeveTopLeft + androidx.compose.ui.geometry.Offset(shortEdge * 0.035f, shortEdge * 0.045f),
                    size = sleeveSize,
                    cornerRadius = sleeveCorner,
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF4938),
                            FnColors.Coral,
                            Color(0xFFEC477E),
                            Color(0xFF7569D8),
                        ),
                        start = sleeveTopLeft,
                        end = sleeveTopLeft + androidx.compose.ui.geometry.Offset(sleeveEdge, sleeveEdge),
                    ),
                    topLeft = sleeveTopLeft,
                    size = sleeveSize,
                    cornerRadius = sleeveCorner,
                )
                val orangeWave = Path().apply {
                    moveTo(sleeveTopLeft.x, sleeveTopLeft.y + sleeveEdge * 0.68f)
                    cubicTo(
                        sleeveTopLeft.x + sleeveEdge * 0.18f, sleeveTopLeft.y + sleeveEdge * 0.48f,
                        sleeveTopLeft.x + sleeveEdge * 0.32f, sleeveTopLeft.y + sleeveEdge * 0.56f,
                        sleeveTopLeft.x + sleeveEdge * 0.48f, sleeveTopLeft.y + sleeveEdge * 0.42f,
                    )
                    cubicTo(
                        sleeveTopLeft.x + sleeveEdge * 0.66f, sleeveTopLeft.y + sleeveEdge * 0.24f,
                        sleeveTopLeft.x + sleeveEdge * 0.78f, sleeveTopLeft.y + sleeveEdge * 0.24f,
                        sleeveTopLeft.x + sleeveEdge, sleeveTopLeft.y + sleeveEdge * 0.05f,
                    )
                    lineTo(sleeveTopLeft.x + sleeveEdge, sleeveTopLeft.y + sleeveEdge)
                    lineTo(sleeveTopLeft.x, sleeveTopLeft.y + sleeveEdge)
                    close()
                }
                drawPath(orangeWave, Color(0xFFFFA538).copy(alpha = 0.84f))
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        start = sleeveTopLeft,
                        end = sleeveTopLeft + androidx.compose.ui.geometry.Offset(sleeveEdge * 0.72f, sleeveEdge),
                    ),
                    topLeft = sleeveTopLeft,
                    size = sleeveSize,
                    cornerRadius = sleeveCorner,
                )
            }

            HomeArtworkKind.Favorites -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF304D),
                            Color(0xFFFF563F),
                            Color(0xFFFF9D37),
                            Color(0xFFFFD45A),
                        ),
                        start = point(0.08f, 0.06f),
                        end = point(0.94f, 0.92f),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFD91442).copy(alpha = 0.72f), Color.Transparent),
                        center = point(0.20f, 0.20f),
                        radius = shortEdge * 0.78f,
                    ),
                    radius = shortEdge * 0.78f,
                    center = point(0.20f, 0.20f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFDA70).copy(alpha = 0.65f), Color.Transparent),
                        center = point(0.89f, 0.89f),
                        radius = shortEdge * 0.62f,
                    ),
                    radius = shortEdge * 0.62f,
                    center = point(0.89f, 0.89f),
                )
                val heartSize = androidx.compose.ui.geometry.Size(shortEdge * 0.56f, shortEdge * 0.56f)
                val heartOrigin = androidx.compose.ui.geometry.Offset(
                    (size.width - heartSize.width) / 2f,
                    (size.height - heartSize.height) / 2f - shortEdge * 0.015f,
                )
                withTransform({ translate(heartOrigin.x + shortEdge * 0.035f, heartOrigin.y + shortEdge * 0.055f) }) {
                    drawPath(heartPath(heartSize), Color(0xFF9E1731).copy(alpha = 0.18f))
                }
                withTransform({ translate(heartOrigin.x, heartOrigin.y) }) {
                    drawPath(heartPath(heartSize), Color(0xFFFFDDD8).copy(alpha = 0.78f))
                }
            }

            HomeArtworkKind.Collection -> {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF124A3A), Color(0xFF267A60), FnColors.Teal),
                        start = point(0f, 0.10f),
                        end = point(1f, 0.90f),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFB4ECD7).copy(alpha = 0.30f), Color.Transparent),
                        center = point(0.88f, 0.12f),
                        radius = shortEdge * 0.72f,
                    ),
                    radius = shortEdge * 0.72f,
                    center = point(0.88f, 0.12f),
                )
                val discCenter = point(0.70f, 0.50f)
                drawCircle(Color.Black.copy(alpha = 0.18f), shortEdge * 0.36f, discCenter + point(0.015f, 0.035f))
                drawVinyl(discCenter, shortEdge * 0.35f, FnColors.Coral)

                val coverEdge = size.height * 0.70f
                val corner = androidx.compose.ui.geometry.CornerRadius(shortEdge * 0.045f)
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.18f),
                    topLeft = point(0.16f, 0.20f),
                    size = androidx.compose.ui.geometry.Size(coverEdge, coverEdge),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = Color(0xFFE7C95D),
                    topLeft = point(0.12f, 0.13f),
                    size = androidx.compose.ui.geometry.Size(coverEdge, coverEdge),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFF4EFE5), Color(0xFFD5E0DB)),
                        start = point(0.19f, 0.17f),
                        end = point(0.53f, 0.84f),
                    ),
                    topLeft = point(0.19f, 0.17f),
                    size = androidx.compose.ui.geometry.Size(coverEdge, coverEdge),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = FnColors.Coral,
                    topLeft = point(0.24f, 0.24f),
                    size = androidx.compose.ui.geometry.Size(coverEdge * 0.23f, coverEdge * 0.62f),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = Color(0xFF202528),
                    topLeft = point(0.35f, 0.24f),
                    size = androidx.compose.ui.geometry.Size(coverEdge * 0.14f, coverEdge * 0.62f),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = FnColors.Teal,
                    topLeft = point(0.43f, 0.24f),
                    size = androidx.compose.ui.geometry.Size(coverEdge * 0.09f, coverEdge * 0.62f),
                    cornerRadius = corner,
                )
            }

            HomeArtworkKind.PlaylistGrid -> {
                // 彩色拼贴：四段渐变底 + 三张错位彩色封面片 + 白色音符
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2B1B3D),
                            Color(0xFF173F4E),
                            Color(0xFF4E2317),
                            Color(0xFF1F3A2A),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    ),
                )
                val tileColors = listOf(
                    Color(0xFFF6C445),
                    Color(0xFF4FC3F7),
                    Color(0xFFBA68C8),
                )
                val tileEdge = size.height * 0.52f
                tileColors.forEachIndexed { index, color ->
                    val topLeft = Offset(size.width * (0.20f + index * 0.17f), size.height * (0.30f + (index % 2) * 0.10f))
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.55f)),
                            start = topLeft,
                            end = topLeft + Offset(tileEdge, tileEdge),
                        ),
                        topLeft = topLeft,
                        size = Size(tileEdge, tileEdge),
                        cornerRadius = CornerRadius(shortEdge * 0.05f),
                    )
                }
                val noteStroke = shortEdge * 0.016f
                val noteColor = Color.White.copy(alpha = 0.92f)
                drawCircle(noteColor, radius = shortEdge * 0.030f, center = Offset(size.width * 0.575f, size.height * 0.615f))
                drawLine(noteColor, Offset(size.width * 0.598f, size.height * 0.595f), Offset(size.width * 0.598f, size.height * 0.315f), noteStroke, StrokeCap.Round)
                drawLine(noteColor, Offset(size.width * 0.40f, size.height * 0.345f), Offset(size.width * 0.598f, size.height * 0.315f), noteStroke, StrokeCap.Round)
                drawCircle(noteColor, radius = shortEdge * 0.030f, center = Offset(size.width * 0.378f, size.height * 0.385f))
                drawLine(noteColor, Offset(size.width * 0.400f, size.height * 0.365f), Offset(size.width * 0.400f, size.height * 0.665f), noteStroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun ArtistLockup(
    title: String,
    subtitle: String,
    coverId: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val container = LocalAuthenticatedDependencies.current
    val shape = RoundedCornerShape(8.dp)
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .size(width = 170.dp, height = 95.dp)
            .onFocusChanged { state ->
                if (state.isFocused && coverId != null) {
                    container.artworkBitmapCache.prefetch(coverId, CoverVariant.Grid)
                }
            },
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = lockupButtonColors(),
        border = lockupButtonBorder(shape),
        contentPadding = PaddingValues(7.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (coverId != null) {
                RemoteArtwork(
                    container = container,
                    coverId = coverId,
                    variant = CoverVariant.Compact,
                    modifier = Modifier.size(61.dp),
                    shape = CircleShape,
                    contentScale = ContentScale.Crop,
                    placeholderContent = { ArtistAvatarPlaceholder(title, Modifier.size(61.dp)) },
                )
            } else {
                ArtistAvatarPlaceholder(title, Modifier.size(61.dp))
            }
            Spacer(Modifier.width(10.dp))
            LockupLabels(title, subtitle, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArtistAvatarPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 23.sp,
) {
    Box(
        modifier.background(Color(0xFF2D4A46), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.trim().take(1).ifBlank { "音" }.uppercase(),
            color = FnColors.Teal,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TrackLockup(
    title: String,
    subtitle: String,
    coverId: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = AlbumLockup(
    title = title,
    subtitle = subtitle,
    coverId = coverId,
    modifier = modifier,
    onClick = onClick,
)

@Composable
private fun AlbumLockup(
    title: String,
    subtitle: String,
    coverId: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    artworkRes: Int? = null,
    onClick: () -> Unit,
) {
    val container = LocalAuthenticatedDependencies.current
    val shape = RoundedCornerShape(8.dp)
    val artworkShape = RoundedCornerShape(4.dp)
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .size(width = 165.dp, height = 95.dp)
            .onFocusChanged { state ->
                if (state.isFocused && coverId != null) {
                    container.artworkBitmapCache.prefetch(coverId, CoverVariant.Grid)
                }
            },
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = lockupButtonColors(),
        border = lockupButtonBorder(shape),
        contentPadding = PaddingValues(7.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            when {
                artworkRes != null -> Image(
                    painter = painterResource(artworkRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(73.dp)
                        .clip(artworkShape),
                )
                coverId != null -> RemoteArtwork(
                    container = container,
                    coverId = coverId,
                    variant = CoverVariant.Compact,
                    modifier = Modifier.size(73.dp),
                    shape = artworkShape,
                    contentScale = ContentScale.Crop,
                    placeholderContent = {
                        InitialArtworkPlaceholder(title, FnColors.Coral, Modifier.fillMaxSize(), artworkShape)
                    },
                )
                else -> InitialArtworkPlaceholder(title, FnColors.Coral, Modifier.size(73.dp), artworkShape)
            }
            Spacer(Modifier.width(9.dp))
            LockupLabels(title, subtitle, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LockupLabels(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(title, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = FnColors.Muted, fontSize = 9.sp, maxLines = 1)
        }
    }
}

/** 风格卡片：均衡器渐变背景铺满整卡，左下角标题，选中态与其它卡片一致。 */
@Composable
private fun GenreLockup(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(width = 165.dp, height = 95.dp),
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF171B1D),
            contentColor = FnColors.Text,
            focusedContainerColor = Color(0xFF171B1D),
            focusedContentColor = FnColors.Text,
            pressedContainerColor = Color(0xFF171B1D),
            pressedContentColor = FnColors.Text,
            disabledContainerColor = Color(0xFF171B1D),
        ),
        border = lockupButtonBorder(shape),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.bg_genre_equalizer),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x14000000), Color(0xCC000000)),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 9.dp, vertical = 8.dp),
            ) {
                Text(
                    title,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = FnColors.Text.copy(alpha = 0.78f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun lockupButtonColors() = ButtonDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = FnColors.Text,
    focusedContainerColor = Color(0xFF303634),
    focusedContentColor = FnColors.Text,
    pressedContainerColor = Color(0xFF303634),
    pressedContentColor = FnColors.Text,
    disabledContainerColor = Color.Transparent,
)

@Composable
private fun lockupButtonBorder(shape: Shape) = ButtonDefaults.border(
    border = Border(BorderStroke(1.5.dp, Color.Transparent), shape = shape),
    focusedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
    pressedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
)

@Composable
private fun CatalogPager(
    currentPage: Int,
    totalPages: Int,
    canPrevious: Boolean,
    canNext: Boolean,
    previousFocus: FocusRequester,
    nextFocus: FocusRequester,
    upFocus: FocusRequester,
    onPagerFocused: (CatalogPagerTarget?) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatalogPageArrowButton(
            direction = CatalogPagerTarget.Previous,
            enabled = canPrevious,
            modifier = Modifier
                .focusProperties {
                    left = FocusRequester.Cancel
                    right = if (canNext) nextFocus else FocusRequester.Cancel
                    up = upFocus
                }
                .focusRequester(previousFocus)
                .onFocusChanged { if (it.isFocused) onPagerFocused(CatalogPagerTarget.Previous) },
            onClick = onPrevious,
        )
        Row(
            modifier = Modifier.width(82.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(currentPage.toString(), color = FnColors.Muted, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("/", color = FnColors.Muted, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(totalPages.toString(), color = FnColors.Muted, fontSize = 13.sp)
        }
        CatalogPageArrowButton(
            direction = CatalogPagerTarget.Next,
            enabled = canNext,
            modifier = Modifier
                .focusProperties {
                    left = if (canPrevious) previousFocus else FocusRequester.Cancel
                    right = FocusRequester.Cancel
                    up = upFocus
                }
                .focusRequester(nextFocus)
                .onFocusChanged { if (it.isFocused) onPagerFocused(CatalogPagerTarget.Next) },
            onClick = onNext,
        )
    }
}

@Composable
private fun CatalogPageArrowButton(
    direction: CatalogPagerTarget,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = if (direction == CatalogPagerTarget.Previous) "上一页" else "下一页"
            },
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF171B1D),
            contentColor = Color(0xFFB8BEC1),
            focusedContainerColor = FnColors.Coral.copy(alpha = 0.09f),
            focusedContentColor = FnColors.Coral,
            pressedContainerColor = FnColors.Coral.copy(alpha = 0.14f),
            pressedContentColor = FnColors.Coral,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color(0xFF50575A),
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.5.dp, Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
            pressedBorder = Border(BorderStroke(1.5.dp, FnColors.Coral), shape = shape),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        val contentColor = LocalContentColor.current
        Canvas(Modifier.size(15.dp)) {
            val left = if (direction == CatalogPagerTarget.Previous) size.width * 0.66f else size.width * 0.34f
            val right = if (direction == CatalogPagerTarget.Previous) size.width * 0.34f else size.width * 0.66f
            drawLine(
                color = contentColor,
                start = androidx.compose.ui.geometry.Offset(left, size.height * 0.18f),
                end = androidx.compose.ui.geometry.Offset(right, size.height * 0.50f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = contentColor,
                start = androidx.compose.ui.geometry.Offset(right, size.height * 0.50f),
                end = androidx.compose.ui.geometry.Offset(left, size.height * 0.82f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun CatalogRetryButton(
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Button(
        enabled = !loading,
        onClick = onClick,
        modifier = modifier.size(width = 170.dp, height = 95.dp),
        shape = ButtonDefaults.shape(shape, shape, shape, shape, shape),
        scale = ButtonDefaults.scale(focusedScale = 1.025f),
        colors = lockupButtonColors(),
        border = lockupButtonBorder(shape),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(if (loading) "正在加载" else "重试", fontSize = 12.sp)
            Text("重新加载列表", color = FnColors.Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun RemoteArtwork(
    container: AuthenticatedAppDependencies,
    coverId: String,
    variant: CoverVariant,
    modifier: Modifier = Modifier,
    fallbackVariant: CoverVariant? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Fit,
    placeholderContent: (@Composable () -> Unit)? = null,
) {
    val bitmap = rememberRemoteArtworkBitmap(container, coverId, variant, fallbackVariant)
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, modifier.clip(shape), contentScale = contentScale)
    } else {
        Box(modifier.background(FnColors.Surface, shape), contentAlignment = Alignment.Center) {
            if (placeholderContent != null) {
                placeholderContent()
            } else {
                InitialArtworkPlaceholder(
                    text = "",
                    accent = FnColors.Teal,
                    modifier = Modifier.fillMaxSize(),
                    shape = shape,
                )
            }
        }
    }
}

@Composable
private fun rememberRemoteArtworkBitmap(
    container: AuthenticatedAppDependencies,
    coverId: String?,
    variant: CoverVariant,
    fallbackVariant: CoverVariant? = null,
): Bitmap? {
    val initialBitmap = remember(container, coverId, variant, fallbackVariant) {
        coverId?.let { id ->
            container.artworkBitmapCache.peek(id, variant)
                ?: fallbackVariant?.let { container.artworkBitmapCache.peek(id, it) }
        }
    }
    val bitmap by produceState(initialBitmap, container, coverId, variant, fallbackVariant) {
        value = coverId?.let { id ->
            container.artworkBitmapCache.getProgressively(
                coverId = id,
                variant = variant,
                fallbackVariant = fallbackVariant,
                onIntermediate = { value = it },
            )
        }
    }
    return if (coverId == null) null else bitmap
}

@Composable
private fun InlineError(error: AppError) {
    Text(appErrorMessage(error), color = FnColors.Coral, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
}

internal fun appErrorMessage(error: AppError): String = when (error) {
    AppError.NetworkUnavailable -> "NAS 暂时不可用"
    AppError.Empty -> "暂无内容"
    AppError.UnavailableTrack -> "歌曲不可访问"
    AppError.TranscodeUnavailable -> "兼容播放参数尚未确认"
    AppError.CollectionChanged -> "列表已更新，请返回后重新载入"
    else -> "加载失败，请重试"
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}


@Composable
private fun Genres(container: AuthenticatedAppDependencies, onBack: () -> Unit, onGenre: (Genre) -> Unit) {
    val retainedStore = LocalLibraryRetainedState.current
    val genreState = retainedStore.list<Genre>("genres")
    val genres = genreState.snapshot.entries
    LaunchedEffect(Unit) {
        retainedStore.loadListOnce(genreState) { container.musicRepository.genres() }
    }
    GridPage("全部风格", genres, { it.guid.value }, onBack = onBack) { genre, modifier ->
        GenreLockup(
            title = genre.name,
            subtitle = (genre.trackCount ?: 0).toString() + " 首歌曲",
            modifier = modifier,
            onClick = { onGenre(genre) },
        )
    }
}

@Composable
private fun SearchRoute(
    container: AuthenticatedAppDependencies,
    onBack: () -> Unit,
    onArtist: (Artist) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlayer: (Track) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var searched by rememberSaveable { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val fieldFocus = remember { FocusRequester() }
    val resultsFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(500)
        val q = query.trim()
        if (q.isEmpty()) {
            tracks = emptyList(); artists = emptyList(); albums = emptyList()
            loading = false
            return@LaunchedEffect
        }
        searched = q
        loading = true
        try {
            coroutineScope {
                val t = async { runCatching { container.musicRepository.searchTracks(q) }.getOrNull() }
                val a = async { runCatching { container.musicRepository.searchArtists(q) }.getOrNull() }
                val al = async { runCatching { container.musicRepository.searchAlbums(q) }.getOrNull() }
                tracks = t.await()?.items ?: emptyList()
                artists = a.await()?.items ?: emptyList()
                albums = al.await()?.items ?: emptyList()
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(FnColors.Background)
            .padding(horizontal = 42.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailBackButton(onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Text("搜索", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        val fieldShape = RoundedCornerShape(27.dp)
        var fieldFocused by remember { mutableStateOf(false) }
        // 无结果时向下不指向任何目标，显式取消，避免焦点搜索落到未挂载的 FocusRequester。
        val hasResults = artists.isNotEmpty() || albums.isNotEmpty() || tracks.isNotEmpty()
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = FnColors.Text, fontSize = 22.sp),
            cursorBrush = SolidColor(FnColors.Coral),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .focusRequester(fieldFocus)
                .focusProperties { down = if (hasResults) resultsFocus else FocusRequester.Cancel }
                .onFocusChanged { state -> fieldFocused = state.isFocused }
                .background(Color(0xFF1B201F), fieldShape)
                .border(
                    if (fieldFocused) 1.5.dp else 0.5.dp,
                    if (fieldFocused) FnColors.Coral else Color(0xFF454A50),
                    fieldShape,
                )
                .padding(horizontal = 20.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔍", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("搜索歌手、专辑、歌曲", color = FnColors.Muted, fontSize = 20.sp)
                        }
                        inner()
                    }
                }
            },
        )
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when {
                loading -> Text("正在搜索…", color = FnColors.Muted, fontSize = 16.sp)
                query.isBlank() -> Text(
                    "按确认键呼出键盘输入，将同时匹配歌手、专辑和歌曲",
                    color = FnColors.Muted,
                    fontSize = 16.sp,
                )
                tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() ->
                    Text("没有找到与「" + searched + "」匹配的内容", color = FnColors.Muted, fontSize = 16.sp)
                else -> {
                    val firstSection = when {
                        artists.isNotEmpty() -> "artist"
                        albums.isNotEmpty() -> "album"
                        else -> "track"
                    }
                    // 初始焦点只挂在第一个非空分区的首项上；三处同时挂载会导致
                    // 后挂载者生效，遥控器按下后跳过歌手列。
                    if (artists.isNotEmpty()) {
                        Text("歌手", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            itemsIndexed(artists, key = { _, item -> "s-artist:" + item.guid.value }) { index, artist ->
                                AlbumLockup(
                                    title = artist.name,
                                    subtitle = (artist.trackCount ?: 0).toString() + " 首歌曲",
                                    coverId = artist.coverId,
                                    modifier = Modifier
                                        .then(
                                            if (firstSection == "artist" && index == 0) {
                                                Modifier.focusRequester(resultsFocus)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .then(
                                            if (index == artists.lastIndex) {
                                                Modifier.focusProperties { right = FocusRequester.Cancel }
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    onClick = {
                                        scope.launch {
                                            val page = runCatching {
                                                container.musicRepository.artistTracks(artist.guid.value, 1)
                                            }.getOrNull() ?: return@launch
                                            val prepared = container.musicRepository.prepareQueue(page.items)
                                            if (prepared.isEmpty()) return@launch
                                            runCatching {
                                                container.playbackController.playQueue(
                                                    tracks = prepared,
                                                    startIndex = 0,
                                                    source = null,
                                                )
                                            }.onSuccess { onPlayer(prepared[0].track) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (albums.isNotEmpty()) {
                        Text("专辑", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            itemsIndexed(albums, key = { _, item -> "s-album:" + item.guid.value }) { index, album ->
                                AlbumLockup(
                                    title = album.name,
                                    subtitle = album.artistName.orEmpty(),
                                    coverId = album.coverId,
                                    modifier = Modifier
                                        .then(
                                            if (firstSection == "album" && index == 0) {
                                                Modifier.focusRequester(resultsFocus)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .then(
                                            if (index == albums.lastIndex) {
                                                Modifier.focusProperties { right = FocusRequester.Cancel }
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    onClick = {
                                        scope.launch {
                                            val page = runCatching {
                                                container.musicRepository.albumTracks(album.guid.value, 1)
                                            }.getOrNull() ?: return@launch
                                            val prepared = container.musicRepository.prepareQueue(page.items)
                                            if (prepared.isEmpty()) return@launch
                                            runCatching {
                                                container.playbackController.playQueue(
                                                    tracks = prepared,
                                                    startIndex = 0,
                                                    source = null,
                                                )
                                            }.onSuccess { onPlayer(prepared[0].track) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (tracks.isNotEmpty()) {
                        Text("歌曲", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        tracks.forEachIndexed { index, track ->
                            TrackResultRow(
                                index = index + 1,
                                title = track.title,
                                subtitle = track.artistName.orEmpty(),
                                coverId = track.coverId,
                                modifier = if (firstSection == "track" && index == 0) {
                                    Modifier.focusRequester(resultsFocus)
                                } else {
                                    Modifier
                                },
                                onClick = {
                                    scope.launch {
                                        val prepared = runCatching { container.musicRepository.prepareQueue(tracks) }
                                            .getOrDefault(emptyList())
                                        val start = prepared.indexOfFirst { it.track.guid == track.guid }
                                        if (start >= 0) {
                                            runCatching {
                                                container.playbackController.playQueue(
                                                    tracks = prepared,
                                                    startIndex = start,
                                                    source = null,
                                                )
                                            }.onSuccess { onPlayer(track) }
                                        }
                                    }
                                },
                            )
                        }
                        if (tracks.size >= SEARCH_TRACK_PAGE_SIZE) {
                            Text(
                                "歌曲较多，仅显示前 ${tracks.size} 首，可换更精确的关键词",
                                color = FnColors.Muted,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackResultRow(
    index: Int,
    title: String,
    subtitle: String,
    coverId: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = LocalAuthenticatedDependencies.current
    val artworkShape = RoundedCornerShape(6.dp)
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(74.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color(0xFF1B201F),
            contentColor = FnColors.Text,
            focusedContainerColor = Color(0xFF303634),
            focusedContentColor = FnColors.Text,
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(index.toString(), color = FnColors.Muted, fontSize = 15.sp, modifier = Modifier.width(30.dp))
            if (coverId != null) {
                RemoteArtwork(
                    container = container,
                    coverId = coverId,
                    variant = CoverVariant.Compact,
                    modifier = Modifier.size(52.dp),
                    shape = artworkShape,
                    contentScale = ContentScale.Crop,
                    placeholderContent = {
                        InitialArtworkPlaceholder(title, FnColors.Coral, Modifier.size(52.dp), artworkShape)
                    },
                )
            } else {
                InitialArtworkPlaceholder(title, FnColors.Coral, Modifier.size(52.dp), artworkShape)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle.ifBlank { "未知演唱者" },
                    color = FnColors.Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

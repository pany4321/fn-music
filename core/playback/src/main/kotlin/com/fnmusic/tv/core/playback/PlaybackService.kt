package com.fnmusic.tv.core.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal const val FORWARD_BUFFER_DURATION_MS = 50_000
internal const val BACK_BUFFER_DURATION_MS = 15_000
private const val LEGACY_AUDIO_CACHE_DIRECTORY = "media"
private const val TAG = "PlaybackService"

internal fun validatedShuffleIndices(
    canonicalIds: List<String>,
    requestedIds: List<String>,
): IntArray? {
    if (
        requestedIds.size != canonicalIds.size ||
        requestedIds.any(String::isBlank) ||
        canonicalIds.any(String::isBlank) ||
        requestedIds.distinct().size != requestedIds.size ||
        canonicalIds.distinct().size != canonicalIds.size ||
        requestedIds.toSet() != canonicalIds.toSet()
    ) {
        return null
    }
    val indexById = canonicalIds.withIndex().associate { (index, id) -> id to index }
    return requestedIds.map { id -> indexById.getValue(id) }.toIntArray()
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createPlaybackLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        FORWARD_BUFFER_DURATION_MS,
        FORWARD_BUFFER_DURATION_MS,
        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    )
    .setBackBuffer(BACK_BUFFER_DURATION_MS, false)
    .build()

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createPlaybackRenderersFactory(context: Context): DefaultRenderersFactory =
    DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

@androidx.annotation.OptIn(UnstableApi::class)
internal fun createPlaybackHttpDataSourceFactory(): DefaultHttpDataSource.Factory =
    DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(false)
        .setUserAgent("FnMusicTV/0.1")

internal fun playbackRequestHeaders(token: String, accessCode: String?, relayMode: Boolean): Map<String, String> =
    buildMap {
        put("Authorization", token)
        if (relayMode) put("Cookie", "music-token=$token; mode=relay")
        if (!accessCode.isNullOrBlank()) {
            put("x-access-code", accessCode)
            put("x-access-source", "app")
        }
    }

internal fun deleteLegacyAudioCache(cacheDirectory: File): Boolean = runCatching {
    val legacyCache = File(cacheDirectory.canonicalFile, LEGACY_AUDIO_CACHE_DIRECTORY)
    if (!legacyCache.exists()) {
        legacyCache.delete()
        return@runCatching true
    }
    // NIO attribute check instead of the historical canonical-vs-absolute
    // heuristic: Windows canonicalization never resolves symlinks, so the old
    // branch could not fire there and deleteRecursively traversed the target.
    val isSymbolicLink = Files.readAttributes(
        legacyCache.toPath(),
        BasicFileAttributes::class.java,
        LinkOption.NOFOLLOW_LINKS,
    ).isSymbolicLink
    if (isSymbolicLink) {
        // Unlink only the entry — never traverse its target.
        return@runCatching Files.deleteIfExists(legacyCache.toPath())
    }
    legacyCache.deleteRecursively()
}.getOrDefault(false)

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val resumptionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var apiBaseOverride: String? = null
    private val rehomingResolver = ApiBaseRewritingResolver { apiBaseOverride }
    private val httpFactory = createPlaybackHttpDataSourceFactory()

    override fun onCreate() {
        super.onCreate()
        if (!deleteLegacyAudioCache(cacheDir)) {
            Log.w(TAG, "Unable to delete legacy audio cache")
        }
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val exoPlayer = ExoPlayer.Builder(this, createPlaybackRenderersFactory(this))
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(ResolvingDataSource.Factory(httpFactory, rehomingResolver)),
            )
            .setLoadControl(createPlaybackLoadControl())
            .build().apply {
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            // Keeps audio alive on screen-off / throttled car units; the service
            // owns the foreground notification so the lock is short-lived.
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, RoutingPlayer(exoPlayer))            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val available = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(PlaybackCommands.ConfigureAuthCommand)
                        .add(PlaybackCommands.ClearAuthCommand)
                        .add(PlaybackCommands.SetShuffleOrderCommand)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(available)
                        .build()
                }

                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = buildResumptionFuture()

                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    isAutomotiveConfig: Boolean,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = buildResumptionFuture()

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: android.os.Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == PlaybackCommands.ClearAuth) {
                        httpFactory.setDefaultRequestProperties(emptyMap())
                        apiBaseOverride = null
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    if (customCommand.customAction == PlaybackCommands.ConfigureAuth) {
                        val token = args.getString(PlaybackCommands.Token)
                        val namespace = args.getString(PlaybackCommands.CacheNamespace)
                        if (token.isNullOrBlank() || namespace.isNullOrBlank()) {
                            return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                        }
                        val relayMode = args.getBoolean(PlaybackCommands.RelayMode, false)
                        val accessCode = args.getString(PlaybackCommands.AccessCode)
                        val headers = playbackRequestHeaders(token, accessCode, relayMode)
                        httpFactory.setDefaultRequestProperties(headers)
                        apiBaseOverride = args.getString(PlaybackCommands.ApiBase)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    if (customCommand.customAction == PlaybackCommands.SetShuffleOrder) {
                        val requested = args.getStringArrayList(PlaybackCommands.MediaIds).orEmpty()
                        val revision = args.getLong(PlaybackCommands.SnapshotRevision, -1L)
                        val canonical = List(exoPlayer.mediaItemCount) { index ->
                            exoPlayer.getMediaItemAt(index).mediaId
                        }
                        val order = validatedShuffleIndices(canonical, requested)
                        if (revision < 0L || order == null) {
                            return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                        }
                        exoPlayer.setShuffleOrder(DefaultShuffleOrder(order, revision))
                        val acknowledgement = android.os.Bundle().apply {
                            putLong(PlaybackCommands.SnapshotRevision, revision)
                            putStringArrayList(PlaybackCommands.MediaIds, ArrayList(requested))
                        }
                        return Futures.immediateFuture(
                            SessionResult(SessionResult.RESULT_SUCCESS, acknowledgement),
                        )
                    }
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Media-button resume after process death (ACC restart on car units): serve the
     * persisted snapshot queue plus remembered credentials so the system play key
     * restarts the last queue instead of dying on an empty session. The resume hook
     * lives on the session callback in current Media3; both overloads share one path.
     */
    private fun buildResumptionFuture(): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val provider = (application as? PlaybackServiceDependencies)?.playbackResumptionProvider
        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        if (provider == null) {
            result.setException(IllegalStateException("Playback resumption provider is unavailable"))
            return result
        }
        resumptionScope.launch {
            try {
                val data = provider.loadResumption()
                val snapshot = data?.let { PlaybackSnapshotCodec.decode(it.queueJson) }
                    ?.takeIf { it.items.isNotEmpty() }
                if (data == null || snapshot == null) {
                    result.setException(NoSuchElementException("No resumable playback snapshot"))
                    return@launch
                }
                val headers = playbackRequestHeaders(data.rawAuthorization, data.accessCodeHeader, data.relayMode)
                httpFactory.setDefaultRequestProperties(headers)
                result.set(
                    MediaSession.MediaItemsWithStartPosition(
                        snapshot.items,
                        snapshot.index.coerceIn(snapshot.items.indices),
                        snapshot.positionMs.coerceAtLeast(0L),
                    ),
                )
            } catch (cause: Throwable) {
                result.setException(cause)
            }
        }
        return result
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        resumptionScope.cancel()
        super.onDestroy()
    }
}

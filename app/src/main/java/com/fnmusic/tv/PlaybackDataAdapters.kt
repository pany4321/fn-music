package com.fnmusic.tv

import android.content.Context
import androidx.core.content.edit
import com.fnmusic.tv.core.data.local.LocalStore
import com.fnmusic.tv.core.data.repository.MusicRepository
import com.fnmusic.tv.core.data.repository.PersistedPlaybackAuth
import com.fnmusic.tv.core.data.repository.SessionRepository
import com.fnmusic.tv.core.model.Page
import com.fnmusic.tv.core.model.PlaybackTrack
import com.fnmusic.tv.core.model.RoamWindow
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.playback.QueueSource
import com.fnmusic.tv.core.playback.PlaybackContentSource
import com.fnmusic.tv.core.playback.PlaybackResumptionData
import com.fnmusic.tv.core.playback.PlaybackResumptionProvider
import com.fnmusic.tv.core.playback.PlaybackSessionStore
import com.fnmusic.tv.core.playback.StoredPlaybackSession

internal class LocalPlaybackSessionStore(
    private val context: Context,
    private val localStore: LocalStore,
) : PlaybackSessionStore {
    override suspend fun read(namespace: String): StoredPlaybackSession? =
        localStore.account(namespace)?.let { account ->
            StoredPlaybackSession(account.queueJson, account.frozenQueueJson)
        }

    override suspend fun save(namespace: String, snapshotJson: String?) {
        localStore.savePlaybackSnapshot(namespace, snapshotJson)
        // Cold-start media-button resume cannot reconstruct the account namespace
        // offline, so remember the last writer.
        if (snapshotJson != null) {
            context.getSharedPreferences(RESUMPTION_PREFS, Context.MODE_PRIVATE)
                .edit { putString(LAST_NAMESPACE, namespace) }
        }
    }

    override suspend fun clear(namespace: String) {
        localStore.clearNamespace(namespace, includeEssential = true)
        context.getSharedPreferences(RESUMPTION_PREFS, Context.MODE_PRIVATE)
            .edit { putString(LAST_NAMESPACE, null) }
    }

    private companion object {
        const val RESUMPTION_PREFS = "playback_resumption"
        const val LAST_NAMESPACE = "last_namespace"
    }
}

internal class RepositoryPlaybackResumptionSource(
    context: Context,
    private val localStore: LocalStore,
    private val persistedAuth: suspend () -> PersistedPlaybackAuth?,
) : PlaybackResumptionProvider {
    private val resumptionPrefs = context.getSharedPreferences("playback_resumption", Context.MODE_PRIVATE)

    override suspend fun loadResumption(): PlaybackResumptionData? {
        val namespace = resumptionPrefs.getString(LAST_NAMESPACE, null) ?: return null
        val session = localStore.account(namespace) ?: return null
        val queueJson = session.queueJson
        if (queueJson.isNullOrBlank()) return null
        val auth = persistedAuth() ?: return null
        return PlaybackResumptionData(
            queueJson = queueJson,
            rawAuthorization = auth.rawAuthorization,
            accessCodeHeader = auth.accessCodeHeader,
            relayMode = auth.relayMode,
        )
    }

    private companion object {
        const val LAST_NAMESPACE = "last_namespace"
    }
}

internal class RepositoryPlaybackContentSource(
    private val repository: MusicRepository,
) : PlaybackContentSource {
    override suspend fun queuePage(source: QueueSource, page: Int): Page<Track> =
        repository.queuePage(source, page)

    override suspend fun prepare(track: Track): PlaybackTrack = repository.prepare(track)

    override fun prepareQueue(tracks: List<Track>): List<PlaybackTrack> = repository.prepareQueue(tracks)

    override suspend fun startRoam(): RoamWindow? = repository.startRoam()

    override suspend fun nextRoam(roamId: String): RoamWindow = repository.nextRoam(roamId)

    override suspend fun previousRoam(roamId: String): RoamWindow = repository.previousRoam(roamId)
}

package com.fnmusic.tv.core.playback

/**
 * Persisted playback payload for media-button resume after process death:
 * the snapshot queue plus the remembered request headers. Read-only.
 */
class PlaybackResumptionData(
    val queueJson: String,
    val headers: Map<String, String>,
)

interface PlaybackResumptionProvider {
    /**
     * Returns the last-played account's resumption payload, or null when nothing
     * is resumable (never played, storage cleared, or credentials unavailable).
     */
    suspend fun loadResumption(): PlaybackResumptionData?
}

/** Implemented by the Application so the system-started service can reach the provider. */
interface PlaybackServiceDependencies {
    val playbackResumptionProvider: PlaybackResumptionProvider
}

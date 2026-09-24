package com.fnmusic.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fnmusic.tv.core.data.local.AppDatabase
import com.fnmusic.tv.core.data.local.LocalStore
import com.fnmusic.tv.core.data.repository.PersistedPlaybackAuth
import com.fnmusic.tv.core.playback.PlaybackSessionStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaybackResumptionSourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var localStore: LocalStore

    @Before fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        context.getSharedPreferences("playback_resumption", Context.MODE_PRIVATE).edit().clear().commit()
        localStore = LocalStore(context)
    }

    @After fun tearDown() {
        // Room runtime is not on the app test classpath; Robolectric's per-test
        // environment discards the database with the test.
        context.deleteDatabase(AppDatabase.NAME)
    }

    @Test fun `resumption serves the last saved snapshot with remembered credentials`() = runBlocking {
        val store: PlaybackSessionStore = LocalPlaybackSessionStore(context, localStore)
        val source = RepositoryPlaybackResumptionSource(
            context = context,
            localStore = localStore,
            persistedAuth = { PersistedPlaybackAuth("token-1", "encoded-code", relayMode = true) },
        )
        store.save("server:user", SNAPSHOT_JSON)

        val data = source.loadResumption()

        assertEquals(SNAPSHOT_JSON, data?.queueJson)
        assertEquals("token-1", data?.rawAuthorization)
        assertEquals("encoded-code", data?.accessCodeHeader)
        assertEquals(true, data?.relayMode)
    }

    @Test fun `resumption returns null without a remembered snapshot or credentials`() = runBlocking {
        val source = RepositoryPlaybackResumptionSource(
            context = context,
            localStore = localStore,
            persistedAuth = { null },
        )

        assertNull(source.loadResumption())

        context.getSharedPreferences("playback_resumption", Context.MODE_PRIVATE).edit()
            .putString("last_namespace", "server:user")
            .commit()
        localStore.savePlaybackSnapshot("server:user", SNAPSHOT_JSON)

        assertNull(source.loadResumption())
        return@runBlocking
    }

    private companion object {
        // A minimal valid snapshot with one item and paused intent.
        val SNAPSHOT_JSON = """
            {"version":2,"generation":1,"revision":3,"kind":"Normal","mode":"ListRepeat",
             "items":[{"id":"g1","uri":"http://nas/music/api/v1/track/stream?guid=g1",
                       "title":"T","shuffleOrder":[]}],"index":0,"position":0,
             "playIntent":"Pause","shuffleOrder":[]}
        """.trimIndent().replace("\n", "")
    }
}

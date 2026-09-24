package com.fnmusic.tv.core.playback

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaybackRehomingTest {

    @Test fun `stale stream uris rebase onto the newest api origin`() {
        val stale = Uri.parse("http://192.168.1.10:5666/music/api/v1/track/stream?guid=abc")
        val rebased = rebaseApiUri(stale, "https://relay.example.com/music/api/v1/")
        assertEquals(
            "https://relay.example.com/music/api/v1/track/stream?guid=abc",
            rebased.toString(),
        )
    }

    @Test fun `current-origin uris stay untouched`() {
        val current = Uri.parse("https://relay.example.com/music/api/v1/track/stream?guid=abc")
        assertNull(rebaseApiUri(current, "https://relay.example.com/music/api/v1/"))
    }

    @Test fun `non-api and non-http uris are never rewritten`() {
        assertNull(rebaseApiUri(Uri.parse("content://media/track"), "http://10.0.0.2:5666/music/api/v1/"))
        assertNull(rebaseApiUri(Uri.parse("http://other.example.com/download?x=1"), "http://10.0.0.2:5666/music/api/v1/"))
        assertNull(rebaseApiUri(Uri.parse("http://192.168.1.10:5666/artwork/cover.png"), "http://10.0.0.2:5666/music/api/v1/"))
    }

    @Test fun `invalid bases leave the data spec alone`() {
        val stale = Uri.parse("http://192.168.1.10:5666/music/api/v1/track/stream?guid=abc")
        assertNull(rebaseApiUri(stale, "not-a-base"))
    }
}

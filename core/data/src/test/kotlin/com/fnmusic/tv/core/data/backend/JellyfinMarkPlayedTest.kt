package com.fnmusic.tv.core.data.backend

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 最近播放上报（markPlayed）的请求契约：
 * - `POST /Users/{userId}/PlayedItems/{itemId}`；
 * - 必须显式携带 `datePlayed`——实测部分服务器对裸标记返回 200 却不刷新 LastPlayedDate，
 *   最近播放（DatePlayed 排序）因此不随播放变化。
 */
class JellyfinMarkPlayedTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.close()
    }

    @Test fun `markPlayed posts played items with explicit datePlayed`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body("""{"Played":true,"LastPlayedDate":"2026-10-02T10:00:00.000Z"}""")
                .build(),
        )

        val api = JellyfinApi(
            origin = server.url("/"),
            client = OkHttpClient.Builder()
                .callTimeout(5, TimeUnit.SECONDS)
                .build(),
            deviceId = "test-device",
            tokenProvider = { "test-token" },
        )

        api.markPlayed(userId = "user-1", itemId = "item-9")

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertTrue(
            "target=${request.target}",
            request.target.startsWith("/Users/user-1/PlayedItems/item-9?"),
        )
        assertTrue(
            "target=${request.target} 含 datePlayed",
            request.target.contains("datePlayed="),
        )
        assertNotNull(request.headers["Authorization"])
    }

    @Test fun `deletePlaylist deletes the playlist as a plain library item`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(204)
                .build(),
        )

        val api = JellyfinApi(
            origin = server.url("/"),
            client = OkHttpClient.Builder()
                .callTimeout(5, TimeUnit.SECONDS)
                .build(),
            deviceId = "test-device",
            tokenProvider = { "test-token" },
        )

        api.deletePlaylist(playlistId = "playlist-9")

        // /Playlists 下没有删除端点（此前 DELETE /Playlists/{id} 必然 404）：
        // 歌单是普通库条目，标准删除走 /Items/{itemId}。
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("DELETE", request.method)
        assertEquals("/Items/playlist-9", request.target)
        assertNotNull(request.headers["Authorization"])
    }
}

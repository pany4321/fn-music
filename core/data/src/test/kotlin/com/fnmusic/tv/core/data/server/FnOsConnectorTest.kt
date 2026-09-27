package com.fnmusic.tv.core.data.server

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 飞牛连接器的纯函数部分：播放请求头与重挂路径前缀。
 * 这里原本是 `core:playback` 里的 `playbackRequestHeaders`，抽取后按后端归位。
 */
class FnOsConnectorTest {
    private val connector = FnOsConnector(
        clientFactory = { OkHttpClient() },
        tokenProvider = { null },
    )

    @Test
    fun `playback headers include access code and relay cookies`() {
        val headers = connector.playbackHeaders("token", "encoded-code", relayMode = true)

        assertEquals("token", headers["Authorization"])
        assertEquals("music-token=token; mode=relay", headers["Cookie"])
        assertEquals("encoded-code", headers["x-access-code"])
        assertEquals("app", headers["x-access-source"])
    }

    @Test
    fun `lan playback only carries the authorization token`() {
        val headers = connector.playbackHeaders("token", encodedAccessCode = null, relayMode = false)

        assertEquals(mapOf("Authorization" to "token"), headers)
    }

    @Test
    fun `stream path prefix scopes playback rehoming to the api base`() {
        assertEquals("/music/api/v1/", connector.streamPathPrefix)
    }
}

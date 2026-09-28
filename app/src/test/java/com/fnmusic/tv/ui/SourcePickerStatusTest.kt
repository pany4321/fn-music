package com.fnmusic.tv.ui

import com.fnmusic.tv.core.data.repository.LoginHistoryEntry
import com.fnmusic.tv.core.data.repository.SourceTestResult
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.ServerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 音乐源列表的展示逻辑：标题回退、副标题、以及"测试"结果 → 状态行文案。 */
class SourcePickerStatusTest {

    private fun entry(kind: ServerKind, serverName: String? = null) = LoginHistoryEntry(
        id = "p-1",
        server = "192.168.1.10:8096",
        username = "pan",
        useHttps = false,
        kind = kind,
        serverName = serverName,
    )

    @Test
    fun `source title prefers the server name and falls back to the backend label`() {
        assertEquals("客厅音乐", sourceTitle(entry(ServerKind.Jellyfin, "客厅音乐")))
        assertEquals("Jellyfin", sourceTitle(entry(ServerKind.Jellyfin)))
        assertEquals("飞牛音乐", sourceTitle(entry(ServerKind.FnOs)))
    }

    @Test
    fun `source subtitle carries type address and account`() {
        assertEquals(
            "Jellyfin · 192.168.1.10:8096 · pan",
            sourceSubtitle(entry(ServerKind.Jellyfin)),
        )
    }

    @Test
    fun `successful test reports the elapsed time`() {
        val status = sourceTestStatus(SourceTestResult.Ok(elapsedMillis = 128))

        assertEquals(SourceRowStatus.Tone.Positive, status.tone)
        assertTrue(status.text, status.text.contains("连接正常"))
        assertTrue(status.text, status.text.contains("128 ms"))
    }

    @Test
    fun `renewed credentials are called out`() {
        val status = sourceTestStatus(SourceTestResult.Ok(elapsedMillis = 900, renewedCredentials = true))

        assertEquals(SourceRowStatus.Tone.Positive, status.tone)
        assertTrue(status.text, status.text.contains("续期"))
    }

    @Test
    fun `expired credentials ask for a new login`() {
        val status = sourceTestStatus(SourceTestResult.CredentialsExpired(elapsedMillis = 42))

        assertEquals(SourceRowStatus.Tone.Negative, status.tone)
        assertTrue(status.text, status.text.contains("重新登录"))
    }

    @Test
    fun `failure reuses the shared error wording`() {
        val status = sourceTestStatus(SourceTestResult.Failed(AppError.NetworkUnavailable, 6_000))

        assertEquals(SourceRowStatus.Tone.Negative, status.tone)
        assertEquals("无法连接：${errorMessage(AppError.NetworkUnavailable)}", status.text)
    }

    @Test
    fun `a null result never claims success`() {
        val status = sourceTestStatus(null)

        assertEquals(SourceRowStatus.Tone.Negative, status.tone)
    }
}

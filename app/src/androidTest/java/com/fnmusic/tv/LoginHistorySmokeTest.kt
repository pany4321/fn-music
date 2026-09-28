package com.fnmusic.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fnmusic.tv.core.data.repository.LoginDraft
import com.fnmusic.tv.core.data.repository.LoginHistoryEntry
import com.fnmusic.tv.ui.FnMusicTheme
import com.fnmusic.tv.ui.LoginScreen
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginHistorySmokeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun historySelectionFillsTheFormWithoutLoggingIn() {
        val loginCount = AtomicInteger()
        val deleteCount = AtomicInteger()
        val selectedProfile = AtomicReference<String>()
        val entry = LoginHistoryEntry("profile-1", "10.0.0.115:5666", "test", useHttps = false)
        composeRule.setContent {
            FnMusicTheme {
                LoginScreen(
                    savedServer = "",
                    recentServers = emptyList(),
                    initialError = null,
                    onLogin = { _, _, _, password, _, accessCode, _ ->
                        password.fill('\u0000')
                        accessCode.fill('\u0000')
                    },
                    loginHistory = listOf(entry),
                    onHistoryLogin = { profileId, accessCode, _ ->
                        accessCode?.fill('\u0000')
                        selectedProfile.set(profileId)
                        loginCount.incrementAndGet()
                    },
                    onHistoryDelete = { deleteCount.incrementAndGet() },
                    historyDraft = {
                        LoginDraft(
                            profileId = entry.id,
                            server = entry.server,
                            username = entry.username,
                            useHttps = entry.useHttps,
                            accessCode = "654321",
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription("历史").performClick()
        // 行标题（服务器名或类型名）与副标题：用副标题断言，避免和表单上的"飞牛音乐"类型按钮撞名
        composeRule.onNodeWithText("飞牛音乐 · 10.0.0.115:5666 · test").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("音乐源：飞牛音乐").performClick()

        // 统一逻辑：点一条只把该源带进表单（地址/账号被回填），不自动登录。
        composeRule.onNodeWithText("10.0.0.115:5666").assertIsDisplayed()
        composeRule.waitForIdle()
        assertEquals(0, loginCount.get())
        assertEquals(null, selectedProfile.get())
        assertEquals(0, deleteCount.get())
    }

    @Test fun historyDeleteDoesNotTriggerLogin() {
        val loginCount = AtomicInteger()
        val deleteCount = AtomicInteger()
        val entry = LoginHistoryEntry("profile-1", "nas.local", "test", useHttps = true)
        composeRule.setContent {
            FnMusicTheme {
                LoginScreen(
                    savedServer = "",
                    recentServers = emptyList(),
                    initialError = null,
                    onLogin = { _, _, _, password, _, accessCode, _ ->
                        password.fill('\u0000')
                        accessCode.fill('\u0000')
                    },
                    loginHistory = listOf(entry),
                    onHistoryLogin = { _, accessCode, _ ->
                        accessCode?.fill('\u0000')
                        loginCount.incrementAndGet()
                    },
                    onHistoryDelete = { deleteCount.incrementAndGet() },
                    historyDraft = { null },
                )
            }
        }

        composeRule.onNodeWithContentDescription("历史").performClick()
        composeRule.onNodeWithContentDescription("删除音乐源：飞牛音乐").performClick()
        composeRule.waitUntil(5_000) { deleteCount.get() == 1 }

        assertEquals(0, loginCount.get())
    }
}

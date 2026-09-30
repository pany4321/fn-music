package com.fnmusic.tv.core.data.repository

import com.fnmusic.tv.core.data.api.LyricDto
import com.fnmusic.tv.core.data.api.LyricListDto
import com.fnmusic.tv.core.data.backend.selectLyricDocument
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.LyricDocument
import com.fnmusic.tv.core.lyrics.hasUsableLines
import com.fnmusic.tv.core.lyrics.lyricText
import com.fnmusic.tv.core.lyrics.parseLyrics
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MusicRepositoryLyricsTest {
    @Test fun `parses preferred yrc even when api does not mark it as lrc`() {
        val response = LyricListDto(
            list = listOf(
                LyricDto(
                    guid = "yrc",
                    content = "[20630,5210](20630,180,0)あ(20810,160,0)な",
                    isLRC = false,
                ),
            ),
            preferred = "yrc",
        )

        // 后端只负责挑出歌词版本，LRC 解析在仓库侧（与运行期同一条链路）。
        val document = selectLyricDocument(response)
        val syncedLyrics = document?.let { parseLyrics(it.content) }?.takeIf(SyncedLyrics::hasUsableLines)

        assertFalse(document!!.isLrc)
        assertNotNull(syncedLyrics)
        assertEquals(20_630, syncedLyrics!!.lines.single().start)
        assertEquals("あな", syncedLyrics.lines.single().lyricText())
    }

    @Test fun `server lyrics win while misses and failures fall back to online`() = runBlocking {
        val onlineCalls = AtomicInteger()
        val online = {
            onlineCalls.incrementAndGet()
            currentLyrics("online")
        }

        val ready = resolveLyricsWithFallback(
            true,
            { CurrentResourceResult.Ready(currentLyrics("server")) },
            online,
        )
        assertEquals("server", (ready as CurrentResourceResult.Ready).value.document.content)
        assertEquals(0, onlineCalls.get())

        val absent = resolveLyricsWithFallback(true, { CurrentResourceResult.Absent }, online)
        assertEquals("online", (absent as CurrentResourceResult.Ready).value.document.content)
        val failed = resolveLyricsWithFallback(
            true,
            { CurrentResourceResult.Failure(AppError.NotFound, retryable = true) },
            online,
        )
        assertEquals("online", (failed as CurrentResourceResult.Ready).value.document.content)
        val disabled = resolveLyricsWithFallback(
            false,
            { CurrentResourceResult.Absent },
            { error("must not search") },
        )
        assertEquals(CurrentResourceResult.Absent, disabled)
        assertEquals(2, onlineCalls.get())
    }

    @Test fun `lyrics fallback never swallows caller cancellation`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                resolveLyricsWithFallback(
                    onlineMatchingEnabled = true,
                    server = { CurrentResourceResult.Absent },
                    online = { throw CancellationException("track changed") },
                )
            }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                resolveLyricsWithFallback(
                    onlineMatchingEnabled = false,
                    server = { throw CancellationException("track changed") },
                    online = { error("must not be reached") },
                )
            }
        }
    }

    private fun currentLyrics(content: String) = CurrentLyrics(
        document = LyricDocument("lyric", content, false, 0L),
        syncedLyrics = null,
    )
}

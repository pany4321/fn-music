package com.fnmusic.tv.core.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class LrclibLyricsSourceTest {
    private val request = LyricsMatchRequest("id", "晴天", listOf("周杰伦"), "叶惠美", 270_000)

    @Before fun resetSharedState() = LrclibSharedState.resetForTest()

    @Test fun `search maps records and sends a single keyword request`() = runBlocking {
        val http = RecordingHttp(bodies = mapOf("api/search" to "[${record(id = 17788, duration = 270.0)}]"))
        val source = source(http)

        val candidates = source.search(LyricsSearchQuery("周杰伦 - 晴天", request))

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(LyricsSourceId.Lrclib, candidate.source)
        assertEquals("17788", candidate.remoteId)
        assertEquals("晴天", candidate.title)
        assertEquals(listOf("周杰伦"), candidate.artists)
        assertEquals("叶惠美", candidate.album)
        assertEquals(270_000L, candidate.durationMs)
        assertFalse(candidate.instrumental)
        assertEquals(1, http.calls.size)
        assertEquals("https://lrclib.net/api/search", http.calls.single().first)
        assertEquals("周杰伦 - 晴天", http.calls.single().second["q"])
    }

    @Test fun `fractional duration is rounded to milliseconds`() = runBlocking {
        val http = RecordingHttp(bodies = mapOf("api/search" to "[${record(id = 1, duration = 235.505031)}]"))
        val source = source(http)

        val candidate = source.search(LyricsSearchQuery("光年之外", request)).single()

        assertEquals(235_505L, candidate.durationMs)
    }

    @Test fun `records without synced lyrics never become candidates`() = runBlocking {
        // 用户要求：只取逐行同步歌词，纯文本记录直接跳过，连试都不用试
        val http = RecordingHttp(
            bodies = mapOf(
                "api/search" to """[
                    {"id":1,"trackName":"纯文本","artistName":"某人","plainLyrics":"只有文本"},
                    {"id":2,"trackName":"有逐行","artistName":"某人","syncedLyrics":"[00:01.00] 词"}
                ]""",
            ),
        )
        val source = source(http)

        val candidates = source.search(LyricsSearchQuery("x", request))

        assertEquals(listOf("2"), candidates.map(LyricsCandidate::remoteId))
    }

    @Test fun `records without an id or title are skipped`() = runBlocking {
        val http = RecordingHttp(
            bodies = mapOf(
                "api/search" to """[
                    {"id":1,"syncedLyrics":"[00:01.00] 无标题"},
                    {"id":2,"trackName":"有标题","syncedLyrics":"[00:01.00] 词"},
                    {"trackName":"无 id","syncedLyrics":"[00:01.00] 词"}
                ]""",
            ),
        )
        val source = source(http)

        assertEquals(listOf("2"), source.search(LyricsSearchQuery("x", request)).map(LyricsCandidate::remoteId))
    }

    @Test fun `fetch reuses the lyrics inlined by search without another request`() = runBlocking {
        val http = RecordingHttp(
            bodies = mapOf(
                "api/search" to """[{"id":17788,"trackName":"晴天","artistName":"周杰伦",
                    "syncedLyrics":"[00:29.36] 故事的小黃花\n[00:32.77] 從出生那年就飄著"}]""",
            ),
        )
        val source = source(http)

        val lyrics = source.fetch(source.search(LyricsSearchQuery("周杰伦 - 晴天", request)).single())

        assertEquals(2, lyrics.lines.size)
        assertEquals("故事的小黃花", lyrics.lines.first().lyricText())
        assertEquals(29_360, lyrics.lines.first().start)
        assertEquals(1, http.calls.size)
    }

    @Test fun `fetch falls back to the id endpoint when the inline copy is gone`() = runBlocking {
        val http = RecordingHttp(
            bodies = mapOf(
                "api/get/" to """{"id":5,"trackName":"晴天","artistName":"周杰伦",
                    "syncedLyrics":"[00:01.00] 词"}""",
            ),
        )
        val source = source(http)

        val lyrics = source.fetch(candidate(remoteId = "5"))

        assertEquals("词", lyrics.lines.single().lyricText())
        assertEquals("https://lrclib.net/api/get/5", http.calls.single().first)
    }

    @Test fun `plain only record is rejected on the id fallback path`() = runBlocking {
        val http = RecordingHttp(
            bodies = mapOf(
                "api/get/" to """{"id":5,"trackName":"Song","artistName":"Artist","plainLyrics":"just text"}""",
            ),
        )
        val source = source(http)

        try {
            source.fetch(candidate(remoteId = "5"))
            fail("expected LyricsPayloadException")
        } catch (expected: LyricsPayloadException) {
            assertTrue(expected.message.orEmpty().contains("no synced lyrics"))
        }
    }

    @Test fun `empty synced lyrics are rejected`() = runBlocking {
        val http = RecordingHttp(
            bodies = mapOf(
                "api/get/" to """{"id":6,"trackName":"Song","artistName":"Artist","syncedLyrics":"   "}""",
            ),
        )
        val source = source(http)

        try {
            source.fetch(candidate(remoteId = "6"))
            fail("expected LyricsPayloadException")
        } catch (expected: LyricsPayloadException) {
            assertTrue(expected.message.orEmpty().contains("no synced lyrics"))
        }
    }

    @Test fun `search without a title returns nothing and sends no request`() = runBlocking {
        val http = RecordingHttp()
        val source = source(http)

        assertTrue(source.search(LyricsSearchQuery("", request.copy(title = "   "))).isEmpty())
        assertTrue(http.calls.isEmpty())
    }

    @Test fun `keyword built locally when the coordinator passes none`() = runBlocking {
        val http = RecordingHttp(bodies = mapOf("api/search" to "[]"))
        val source = source(http)

        source.search(LyricsSearchQuery("", request))

        assertEquals("周杰伦 - 晴天", http.calls.single().second["q"])
    }

    @Test fun `rate limit sets a cooldown and further requests fail fast`() = runBlocking {
        var clock = 1_000_000L
        val http = RecordingHttp(
            bodies = mapOf("api/search" to "[${record(id = 1, duration = 270.0)}]"),
            errors = mapOf("api/search" to LyricsTransportException("Lyrics HTTP 429", status = 429, retryAfterMs = 30_000)),
        )
        val source = source(http) { clock }

        runCatching { source.search(LyricsSearchQuery("周杰伦 - 晴天", request)) }
        assertEquals(1, http.calls.size)

        // 冷却期内：直接失败，不再发请求
        runCatching { source.search(LyricsSearchQuery("周杰伦 - 晴天", request)) }
        assertEquals(1, http.calls.size)

        // 冷却到期后：恢复请求
        clock += 31_000
        runCatching { source.search(LyricsSearchQuery("周杰伦 - 晴天", request)) }
        assertEquals(2, http.calls.size)
    }

    @Test fun `cooldown is shared across instances`() = runBlocking {
        // 编排层历史上会持有多个本源实例（快源/深挖工厂各一）：一个实例吃到的 429 冷却
        // 必须对另一个实例同样生效，否则等于无视 LRCLIB 的 Retry-After 强制要求。
        var clock = 1_000_000L
        val http429 = RecordingHttp(
            errors = mapOf("api/search" to LyricsTransportException("Lyrics HTTP 429", status = 429, retryAfterMs = 30_000)),
        )
        val first = source(http429) { clock }
        runCatching { first.search(LyricsSearchQuery("周杰伦 - 晴天", request)) }
        assertEquals(1, http429.calls.size)

        val httpOk = RecordingHttp(bodies = mapOf("api/search" to "[${record(id = 2, duration = 270.0)}]"))
        val second = source(httpOk) { clock }
        runCatching { second.search(LyricsSearchQuery("周杰伦 - 晴天", request)) }
        assertTrue("另一实例在冷却期内不得发请求", httpOk.calls.isEmpty())

        clock += 31_000
        second.search(LyricsSearchQuery("周杰伦 - 晴天", request))
        assertEquals(1, httpOk.calls.size)
    }

    @Test fun `rate limit without retry after falls back to the default cooldown`() = runBlocking {
        var clock = 0L
        val http = RecordingHttp(
            bodies = mapOf("api/search" to "[${record(id = 1, duration = 270.0)}]"),
            errors = mapOf("api/search" to LyricsTransportException("Lyrics HTTP 429", status = 429)),
        )
        val source = source(http) { clock }

        runCatching { source.search(LyricsSearchQuery("周杰伦 - 晴天", request)) }
        assertEquals(1, http.calls.size)

        clock += 59_000
        runCatching { source.search(LyricsSearchQuery("周杰伦 - 晴天", request)) }
        assertEquals(1, http.calls.size)

        clock += 2_000
        runCatching { source.search(LyricsSearchQuery("周杰伦 - 晴天", request)) }
        assertEquals(2, http.calls.size)
    }

    @Test fun `instrumental flag is carried to the candidate`() = runBlocking {
        val http = RecordingHttp(
            bodies = mapOf("api/search" to "[${record(id = 7, duration = 200.0, instrumental = true)}]"),
        )
        val source = source(http)

        assertTrue(source.search(LyricsSearchQuery("周杰伦 - 晴天", request)).single().instrumental)
    }

    private fun source(http: LyricsHttpClient, now: (() -> Long)? = null) = if (now == null) {
        LrclibLyricsSource(http, interRequestDelayMs = 0)
    } else {
        LrclibLyricsSource(http, now = now, interRequestDelayMs = 0)
    }

    private fun candidate(remoteId: String) = LyricsCandidate(
        source = LyricsSourceId.Lrclib,
        remoteId = remoteId,
        title = "晴天",
        artists = listOf("周杰伦"),
    )

    private fun record(id: Long, duration: Double?, instrumental: Boolean? = null): String = buildString {
        append("""{"id":$id,"trackName":"晴天","artistName":"周杰伦","albumName":"叶惠美","syncedLyrics":"[00:01.00] 词"""")
        duration?.let { append(""","duration":$it""") }
        instrumental?.let { append(""","instrumental":$it""") }
        append("}")
    }

    private class RecordingHttp(
        private val bodies: Map<String, String> = emptyMap(),
        private val errors: Map<String, LyricsTransportException> = emptyMap(),
    ) : LyricsHttpClient {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun get(url: String, query: Map<String, String>, headers: Map<String, String>): String {
            calls += url to query
            errors.entries.firstOrNull { (key, _) -> url.contains(key) }?.let { throw it.value }
            return bodies.entries.firstOrNull { (key, _) -> url.contains(key) }?.value
                ?: throw LyricsTransportException("No fixture for $url", status = 404)
        }
    }
}

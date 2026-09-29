package com.fnmusic.tv.core.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLyricsSourcesTest {
    private val request = LyricsMatchRequest("id", "Song", listOf("Artist"), "Album", 180_000)

    @Test fun `qq requests native qrc before lrc fallback`() = kotlinx.coroutines.runBlocking {
        val original = encryptQrc(
            """<Lyric_1 LyricType="1" LyricContent="[1000,1000]你(1000,500)好(1500,500)"/>""",
        )
        val translation = encryptQrc("[00:01.00]Hello")
        val http = FixtureHttp(
            postFixtures = mapOf(
                "PlayLyricInfo" to """{"request":{"data":{"lyric":"$original","qrc_t":1,"lrc_t":0,"trans":"$translation","trans_t":1,"roma":""}}}""",
                "SearchCgiService" to searchFixture(),
            ),
        )
        val source = QqMusicLyricsSource(http)

        val lyrics = source.fetch(source.search(LyricsSearchQuery("Artist - Song", request)).single())

        val line = lyrics.lines.single() as KaraokeLine
        assertEquals("你好", line.lyricText())
        assertEquals("Hello", line.translation)
    }

    @Test fun `qq search sends the title only and maps the full metadata`() = kotlinx.coroutines.runBlocking {
        val http = FixtureHttp(postFixtures = mapOf("SearchCgiService" to searchFixture()))
        val source = QqMusicLyricsSource(http)

        val candidate = source.search(LyricsSearchQuery("Artist - Song", request)).single()

        // 只传标题：带「歌手 - 标题」整串会把合辑歌手标签（群星 / Various Artists）带成垃圾或 0 结果
        assertTrue(http.postBodies.single().contains("\"query\":\"Song\""))
        assertFalse(http.postBodies.single().contains("Artist - Song"))
        assertEquals("97773", candidate.remoteId)
        assertEquals("mid", candidate.mediaId)
        assertEquals("Song", candidate.title)
        assertEquals(listOf("Artist", "Second"), candidate.artists)
        assertEquals("Album", candidate.album)
        assertEquals(180_000L, candidate.durationMs)
    }

    @Test fun `qq search without a title sends nothing`() = kotlinx.coroutines.runBlocking {
        val http = FixtureHttp()
        val source = QqMusicLyricsSource(http)

        assertTrue(source.search(LyricsSearchQuery("", request.copy(title = "  "))).isEmpty())
        assertTrue(http.postBodies.isEmpty())
    }

    @Test fun `qq falls back to ordinary lrc when native qrc fails`() = kotlinx.coroutines.runBlocking {
        val http = FixtureHttp(
            getFixtures = mapOf(
                "fcg_query_lyric_new" to """{"lyric":"[00:01.00]Hello","trans":"[00:01.00]你好"}""",
            ),
            postFixtures = mapOf("SearchCgiService" to searchFixture()),
            postFailures = setOf("PlayLyricInfo"),
        )
        val source = QqMusicLyricsSource(http)

        val lyrics = source.fetch(source.search(LyricsSearchQuery("Song", request)).single())

        assertEquals("Hello", lyrics.lines.single().lyricText())
        assertEquals("你好", lyrics.lines.single().translationText())
    }

    @Test fun `netease prefers yrc and aligns translation`() = kotlinx.coroutines.runBlocking {
        val http = FixtureHttp(
            getFixtures = mapOf(
                "search/get/web" to """{"result":{"songs":[{"id":2,"name":"Song","duration":180000,"artists":[{"name":"Artist"}],"album":{"name":"Album"}}]}}""",
                "song/lyric" to """{"yrc":{"lyric":"[1000,1000](1000,500,0)Hel(1500,500,0)lo"},"lrc":{"lyric":"[00:01.00]Hello"},"tlyric":{"lyric":"[00:01.00]你好"}}""",
            ),
        )
        val source = NeteaseLyricsSource(http)

        val lyrics = source.fetch(source.search(LyricsSearchQuery("Song", request)).single())

        val line = lyrics.lines.single() as KaraokeLine
        assertEquals(1_000, line.syllables.first().start)
        assertEquals("你好", line.translation)
    }

    @Test fun `kugou decrypts native krc before parsing`() = kotlinx.coroutines.runBlocking {
        val content = encryptKrc("[0,1000]<0,500,0>Hel<500,500,0>lo")
        val http = FixtureHttp(
            getFixtures = mapOf(
                "song_search_v2" to """{"data":{"lists":[{"ID":"3","SongName":"Song","SingerName":"Artist","AlbumName":"Album","Duration":180,"FileHash":"hash"}]}}""",
                "lyrics.kugou.com/search" to """{"candidates":[{"id":"lyric","accesskey":"key","duration":180000,"score":60}]}""",
                "lyrics.kugou.com/download" to """{"contenttype":1,"content":"$content"}""",
            ),
        )
        val source = KugouLyricsSource(http)

        val lyrics = source.fetch(source.search(LyricsSearchQuery("Song", request)).single())

        assertEquals("Hello", lyrics.lines.single().lyricText())
        assertTrue(lyrics.lines.single() is KaraokeLine)
    }

    /** 桌面搜索接口：一次 15 条，字段齐全（时长、专辑、歌手数组）。 */
    private fun searchFixture() =
        """{"req":{"data":{"body":{"song":{"list":[""" +
            """{"id":97773,"mid":"mid","title":"Song","interval":180,"album":{"name":"Album"},""" +
            """"singer":[{"name":"Artist"},{"name":"Second"}]}""" +
            """]}}}}}"""

    /**
     * POST 的搜索 / QRC / 详情都走同一个 URL（musicu.fcg），所以按**请求体**里的模块名区分 fixture；
     * [postFailures] 里的键同样按请求体匹配。
     */
    private class FixtureHttp(
        private val getFixtures: Map<String, String> = emptyMap(),
        private val postFixtures: Map<String, String> = emptyMap(),
        private val postFailures: Set<String> = emptySet(),
    ) : LyricsHttpClient {
        var postCalls = 0
        val postBodies = mutableListOf<String>()

        override suspend fun get(url: String, query: Map<String, String>, headers: Map<String, String>): String =
            getFixtures.entries.firstOrNull { (key, _) -> url.contains(key) }?.value
                ?: error("No fixture for $url")

        override suspend fun post(url: String, body: String, headers: Map<String, String>): String {
            postCalls++
            postBodies += body
            if (postFailures.any(body::contains)) throw LyricsTransportException("POST disabled in this test")
            return postFixtures.entries.firstOrNull { (key, _) -> body.contains(key) }?.value
                ?: error("No fixture for $url")
        }
    }

    private companion object {
        val qrcKey = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)
        val krcKey = byteArrayOf(
            0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
            0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69,
        )

        fun encryptQrc(value: String): String {
            val compressed = compress(value)
            val padded = compressed.copyOf(((compressed.size + 7) / 8) * 8)
            val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(qrcKey, "DESede"))
            return cipher.doFinal(padded).joinToString("") { "%02x".format(it) }
        }

        fun encryptKrc(value: String): String {
            val compressed = compress(value)
            val encrypted = ByteArray(compressed.size) { index ->
                (compressed[index].toInt() xor krcKey[index % krcKey.size].toInt()).toByte()
            }
            val payload = byteArrayOf('k'.code.toByte(), 'r'.code.toByte(), 'c'.code.toByte(), '1'.code.toByte()) + encrypted
            return Base64.getEncoder().encodeToString(payload)
        }

        fun compress(value: String): ByteArray = ByteArrayOutputStream().also { output ->
            DeflaterOutputStream(output).use { it.write(value.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
    }
}

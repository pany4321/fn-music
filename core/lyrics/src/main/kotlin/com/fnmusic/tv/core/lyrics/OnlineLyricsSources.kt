// SPDX-License-Identifier: GPL-3.0-only
// Provider request, transport decoding, and fallback behavior is derived from LDDC.
// LRCLIB (https://lrclib.net/docs) is implemented from its public API documentation.
package com.fnmusic.tv.core.lyrics

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

object DefaultLyricsSources {
    fun create(client: OkHttpClient): List<LyricsSource> {
        val http = OkHttpLyricsHttpClient(client)
        return listOf(
            NeteaseLyricsSource(http),
            KugouLyricsSource(http),
            LrclibLyricsSource(http),
        )
    }
}

/**
 * QQ 音乐：**当前未注册**（见 [DefaultLyricsSources]）。
 *
 * 保留实现是因为它本身可用（逐字 QRC 覆盖最好），但它的搜索接口
 * `client_search_cp` 在真实网络上要 1.8–3.3 秒，已经顶到单源 1.8 秒预算：
 * 匹配编排会 `awaitAll` 等齐所有源，一个慢源会把**每首歌**的歌词上屏都拖到上限，
 * 而它自己往往还是超时被丢。若接入更快的查询通道，可以把它加回列表。
 */
class QqMusicLyricsSource(
    private val http: LyricsHttpClient,
) : LyricsSource {
    override val id = LyricsSourceId.QqMusic

    override suspend fun search(query: LyricsSearchQuery): List<LyricsCandidate> {
        val root = parseObject(
            http.get(
                "https://c.y.qq.com/soso/fcgi-bin/client_search_cp",
                mapOf("w" to query.keyword, "format" to "json", "p" to "1", "n" to "20", "cr" to "1", "g_tk" to "5381"),
                QQ_HEADERS,
            ),
        )
        return root.objectAt("data")?.objectAt("song")?.arrayAt("list").orEmpty().mapNotNull { item ->
            val song = item.asObject() ?: return@mapNotNull null
            val remoteId = song.stringAt("songid") ?: return@mapNotNull null
            val mediaId = song.stringAt("songmid") ?: return@mapNotNull null
            val title = decodeHtml(song.stringAt("songname").orEmpty()).takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            LyricsCandidate(
                source = id,
                remoteId = remoteId,
                mediaId = mediaId,
                title = title,
                artists = song.arrayAt("singer").orEmpty()
                    .mapNotNull { it.asObject()?.stringAt("name")?.let(::decodeHtml) },
                album = song.stringAt("albumname")?.let(::decodeHtml),
                durationMs = song.longAt("interval")?.times(1_000L),
                instrumental = song.longAt("pure") == 1L,
            )
        }
    }

    override suspend fun fetch(candidate: LyricsCandidate): SyncedLyrics = try {
        fetchNativeQrc(candidate)
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Exception) {
        fetchLrc(candidate)
    }

    private suspend fun fetchNativeQrc(candidate: LyricsCandidate): SyncedLyrics {
        val songId = candidate.remoteId.toLongOrNull()
            ?: throw LyricsPayloadException("QQ candidate has no numeric song ID")
        val body = buildJsonObject {
            put("comm", buildJsonObject {
                put("ct", 19)
                put("cv", 2111)
                put("uin", "0")
            })
            put("request", buildJsonObject {
                put("module", "music.musichallSong.PlayLyricInfo")
                put("method", "GetPlayLyricInfo")
                put("param", buildJsonObject {
                    put("albumName", encodeBase64(candidate.album.orEmpty()))
                    put("crypt", 1)
                    put("ct", 19)
                    put("cv", 2111)
                    put("interval", (candidate.durationMs ?: 0L) / 1_000L)
                    put("lrc_t", 0)
                    put("qrc", 1)
                    put("qrc_t", 0)
                    put("roma", 1)
                    put("roma_t", 0)
                    put("singerName", encodeBase64(candidate.artists.joinToString(" / ")))
                    put("songID", songId)
                    put("songName", encodeBase64(candidate.title))
                    put("trans", 1)
                    put("trans_t", 0)
                    put("type", 0)
                })
            })
        }.toString()
        val root = parseObject(http.post(QQ_MUSICU_URL, body, QQ_NATIVE_HEADERS))
        val data = root.objectAt("request")?.objectAt("data")
            ?: throw LyricsPayloadException("QQ QRC response has no data")
        val original = decodeQqNativeField(data.stringAt("lyric"))
        if (original.isBlank()) throw LyricsPayloadException("QQ QRC lyrics are empty")
        val translation = data.stringAt("trans")?.takeIf(String::isNotBlank)?.let { encrypted ->
            runCatching { decodeQqNativeField(encrypted) }.getOrNull()
        }
        val phonetic = data.stringAt("roma")?.takeIf(String::isNotBlank)?.let { encrypted ->
            runCatching { decodeQqNativeField(encrypted) }.getOrNull()
        }
        return parseLyrics(original, translation, phonetic).requireUsable("QQ QRC")
    }

    private suspend fun fetchLrc(candidate: LyricsCandidate): SyncedLyrics {
        val mediaId = candidate.mediaId ?: throw LyricsPayloadException("QQ candidate has no media ID")
        val root = parseObject(
            http.get(
                "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
                mapOf("songmid" to mediaId, "format" to "json", "nobase64" to "1", "g_tk" to "5381"),
                QQ_HEADERS,
            ),
        )
        val original = decodeHtml(decodeLyricField(root.stringAt("lyric")))
        val translation = decodeHtml(decodeLyricField(root.stringAt("trans")))
        return parseLyrics(original, translation).requireUsable("QQ LRC")
    }

    private companion object {
        const val QQ_MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        val QQ_HEADERS = mapOf("Referer" to "https://y.qq.com/")
        val QQ_NATIVE_HEADERS = mapOf(
            "Cookie" to "tmeLoginType=-1;",
            "User-Agent" to "okhttp/3.14.9",
        )
    }
}

class NeteaseLyricsSource(
    private val http: LyricsHttpClient,
) : LyricsSource {
    override val id = LyricsSourceId.Netease

    override suspend fun search(query: LyricsSearchQuery): List<LyricsCandidate> {
        val root = parseObject(
            http.get(
                "https://music.163.com/api/search/get/web",
                mapOf("s" to query.keyword, "type" to "1", "limit" to "20", "offset" to "0"),
                NETEASE_HEADERS,
            ),
        )
        return root.objectAt("result")?.arrayAt("songs").orEmpty().mapNotNull { item ->
            val song = item.asObject() ?: return@mapNotNull null
            val remoteId = song.stringAt("id") ?: return@mapNotNull null
            val title = song.stringAt("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val artists = (song.arrayAt("artists") ?: song.arrayAt("ar")).orEmpty()
                .mapNotNull { it.asObject()?.stringAt("name") }
            val album = song.objectAt("album") ?: song.objectAt("al")
            LyricsCandidate(
                source = id,
                remoteId = remoteId,
                title = title,
                artists = artists,
                album = album?.stringAt("name"),
                durationMs = song.longAt("duration") ?: song.longAt("dt"),
            )
        }
    }

    override suspend fun fetch(candidate: LyricsCandidate): SyncedLyrics {
        val root = parseObject(
            http.get(
                "https://music.163.com/api/song/lyric",
                mapOf("id" to candidate.remoteId, "lv" to "1", "kv" to "1", "tv" to "1", "yv" to "1", "rv" to "1"),
                NETEASE_HEADERS,
            ),
        )
        val yrc = root.objectAt("yrc")?.stringAt("lyric").orEmpty()
        val lrc = root.objectAt("lrc")?.stringAt("lyric").orEmpty()
        val original = yrc.ifBlank { lrc }
        val translation = root.objectAt("tlyric")?.stringAt("lyric")
        val phonetic = root.objectAt("romalrc")?.stringAt("lyric")
        return parseLyrics(original, translation, phonetic).requireUsable("Netease lyrics")
    }

    private companion object {
        val NETEASE_HEADERS = mapOf("Referer" to "https://music.163.com/")
    }
}

class KugouLyricsSource(
    private val http: LyricsHttpClient,
) : LyricsSource {
    override val id = LyricsSourceId.Kugou

    override suspend fun search(query: LyricsSearchQuery): List<LyricsCandidate> {
        val root = parseObject(
            http.get(
                "https://songsearch.kugou.com/song_search_v2",
                mapOf(
                    "keyword" to query.keyword,
                    "page" to "1",
                    "pagesize" to "20",
                    "platform" to "WebFilter",
                    "userid" to "-1",
                    "clientver" to "2000",
                ),
            ),
        )
        return root.objectAt("data")?.arrayAt("lists").orEmpty().mapNotNull { item ->
            val song = item.asObject() ?: return@mapNotNull null
            val remoteId = song.stringAt("ID") ?: return@mapNotNull null
            val title = song.stringAt("SongName")?.let(::stripMarkup)?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val durationSeconds = song.longAt("Duration") ?: song.longAt("SQDuration") ?: song.longAt("HQDuration")
            LyricsCandidate(
                source = id,
                remoteId = remoteId,
                title = title,
                artists = song.stringAt("SingerName").orEmpty().split("、", "/", "&")
                    .map(String::trim).filter(String::isNotBlank),
                album = song.stringAt("AlbumName")?.let(::stripMarkup),
                durationMs = durationSeconds?.times(1_000L),
                fileHash = song.stringAt("FileHash") ?: song.stringAt("SQFileHash") ?: song.stringAt("HQFileHash"),
            )
        }
    }

    override suspend fun fetch(candidate: LyricsCandidate): SyncedLyrics {
        val lyricCandidate = findLyricCandidate(candidate)
        return try {
            download(lyricCandidate, "krc").requireUsable("Kugou KRC")
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            download(lyricCandidate, "lrc").requireUsable("Kugou LRC")
        }
    }

    private suspend fun findLyricCandidate(candidate: LyricsCandidate): JsonObject {
        val search = parseObject(
            http.get(
                "https://lyrics.kugou.com/search",
                buildMap {
                    put("ver", "1")
                    put("man", "yes")
                    put("client", "pc")
                    put("keyword", (candidate.artists.firstOrNull()?.plus(" - ") ?: "") + candidate.title)
                    candidate.durationMs?.let { put("duration", it.toString()) }
                    candidate.fileHash?.let { put("hash", it) }
                },
            ),
        )
        return search.arrayAt("candidates").orEmpty()
            .mapNotNull(JsonElement::asObject)
            .filter { it.stringAt("id") != null && it.stringAt("accesskey") != null }
            .minWithOrNull(
                compareBy<JsonObject> { row ->
                    val duration = row.longAt("duration")
                    if (duration == null || candidate.durationMs == null) Long.MAX_VALUE / 2
                    else kotlin.math.abs(duration - candidate.durationMs)
                }.thenByDescending { it.longAt("score") ?: 0L },
            ) ?: throw LyricsPayloadException("Kugou returned no lyric candidates")
    }

    private suspend fun download(candidate: JsonObject, format: String): SyncedLyrics {
        val body = parseObject(
            http.get(
                "https://lyrics.kugou.com/download",
                mapOf(
                    "ver" to "1",
                    "client" to "pc",
                    "id" to candidate.stringAt("id")!!,
                    "accesskey" to candidate.stringAt("accesskey")!!,
                    "fmt" to format,
                    "charset" to "utf8",
                ),
            ),
        )
        val encoded = body.stringAt("content").orEmpty()
        if (encoded.isBlank()) throw LyricsPayloadException("Kugou lyrics are empty")
        val decoded = when {
            format == "krc" && body.longAt("contenttype") != 2L -> decryptKrc(encoded)
            else -> decodeBase64(encoded)
        }
        return parseLyrics(decoded)
    }
}

/**
 * LRCLIB（https://lrclib.net）：四家里唯一有公开文档与使用条款的歌词源，需要 `User-Agent`
 * 标明客户端，并且**必须**遵守 429 的 `Retry-After`（否则可能被临时封禁）。
 *
 * **只取逐行同步歌词**：检索响应里没有 `syncedLyrics` 的记录（只有纯文本歌词）直接不进候选，
 * 纯文本即使漏进来也会在解析阶段得到空歌词被上层跳过 —— 播放器只展示能滚动的逐行歌词。
 *
 * 只发一次请求：官方检索接口把歌词**内联**在响应里，所以 [search] 顺手把逐行歌词按 id 记到
 * 一个小缓存，[fetch] 直接取用，不再多发一次 `/api/get/{id}`（既省往返，也符合官方对客户端
 * 节制的期望）。缓存未命中（例如跨进程/被淘汰）时才回落到按 id 取词。
 *
 * 为什么不用 `/api/get` 精确接口作为首选：实测 60 首真实曲库样本里，精确接口（歌手+标题+
 * 专辑+时长±2s）单独只覆盖 5 首，而检索接口覆盖 10 首 —— 召回高一倍，体积在冷门曲目上也只有
 * 13KB 左右。（热门曲目的检索响应可达 181KB / 1.8–2.6s，会超出编排层单源预算而被取消，但那些
 * 曲子本来就有网易云/酷狗兜住。）
 */
class LrclibLyricsSource(
    private val http: LyricsHttpClient,
    private val now: () -> Long = System::currentTimeMillis,
    private val interRequestDelayMs: Long = INTER_REQUEST_DELAY_MS,
) : LyricsSource {
    override val id = LyricsSourceId.Lrclib

    /** 命中 429 后的冷却截止时刻：冷却期内不再发请求，避免把临时限流升级成封禁。 */
    @Volatile
    private var cooldownUntilMs = 0L
    private var lastRequestAtMs = 0L

    /** 检索响应内联的逐行歌词，按 record id 暂存给 [fetch] 用（先进先出，有上限）。 */
    private val inlineLyrics = LinkedHashMap<String, String>()
    private val inlineLock = Any()

    override suspend fun search(query: LyricsSearchQuery): List<LyricsCandidate> {
        val request = query.request
        val title = request.title.trim().takeIf(String::isNotBlank) ?: return emptyList()
        val keyword = query.keyword.trim().ifBlank { request.fallbackKeyword(title) }
        val records = parseArray(requestText(SEARCH_ENDPOINT, mapOf("q" to keyword)))
        val candidates = mutableListOf<LyricsCandidate>()
        records.forEach { element ->
            val record = element.asObject() ?: return@forEach
            val candidate = toCandidate(record) ?: return@forEach
            val synced = record.stringAt("syncedLyrics").orEmpty()
            if (synced.isBlank()) return@forEach
            candidates += candidate
            rememberInline(candidate.remoteId, synced)
        }
        return candidates
    }

    override suspend fun fetch(candidate: LyricsCandidate): SyncedLyrics {
        val synced = recallInline(candidate.remoteId) ?: parseObject(
            requestText("$GET_ENDPOINT/${candidate.remoteId}"),
        ).stringAt("syncedLyrics").orEmpty()
        if (synced.isBlank()) throw LyricsPayloadException("Lrclib record has no synced lyrics")
        return parseLyrics(synced).requireUsable("Lrclib")
    }

    private fun rememberInline(remoteId: String, syncedLyrics: String) = synchronized(inlineLock) {
        if (inlineLyrics.size >= MAX_INLINE_ENTRIES) {
            inlineLyrics.keys.firstOrNull()?.let(inlineLyrics::remove)
        }
        inlineLyrics[remoteId] = syncedLyrics
    }

    private fun recallInline(remoteId: String): String? = synchronized(inlineLock) {
        inlineLyrics[remoteId]
    }

    private suspend fun requestText(url: String, query: Map<String, String> = emptyMap()): String {
        if (now() < cooldownUntilMs) throw LyricsTransportException("Lrclib is cooling down after HTTP 429")
        pace()
        return try {
            http.get(url, query)
        } catch (cause: LyricsTransportException) {
            if (cause.status == HTTP_TOO_MANY_REQUESTS) {
                val waitMs = (cause.retryAfterMs ?: DEFAULT_COOLDOWN_MS).coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
                cooldownUntilMs = now() + waitMs
            }
            throw cause
        } finally {
            lastRequestAtMs = now()
        }
    }

    /** 官方要求客户端顺序发送、并在请求之间留出 200–500ms；这里只约束本源自己的相邻请求。 */
    private suspend fun pace() {
        if (interRequestDelayMs <= 0L || lastRequestAtMs <= 0L) return
        val remaining = interRequestDelayMs - (now() - lastRequestAtMs)
        if (remaining > 0L) delay(remaining)
    }

    private fun toCandidate(record: JsonObject): LyricsCandidate? {
        val remoteId = record.longAt("id")?.toString() ?: return null
        val title = record.stringAt("trackName").orEmpty().trim().takeIf(String::isNotBlank) ?: return null
        val artist = record.stringAt("artistName").orEmpty().trim()
        return LyricsCandidate(
            source = id,
            remoteId = remoteId,
            title = title,
            artists = listOf(artist).filter(String::isNotBlank),
            album = record.stringAt("albumName")?.trim()?.takeIf(String::isNotBlank),
            durationMs = record.doubleAt("duration")?.takeIf { it > 0 }?.let { (it * 1_000).roundToLong() },
            instrumental = record.booleanAt("instrumental") ?: false,
        )
    }

    private companion object {
        const val SEARCH_ENDPOINT = "https://lrclib.net/api/search"
        const val GET_ENDPOINT = "https://lrclib.net/api/get"
        const val ARTIST_JOINER = " - "
        const val INTER_REQUEST_DELAY_MS = 200L
        const val DEFAULT_COOLDOWN_MS = 60_000L
        const val MIN_COOLDOWN_MS = 1_000L
        const val MAX_COOLDOWN_MS = 10 * 60 * 1_000L
        const val MAX_INLINE_ENTRIES = 64
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

/** 编排层没给关键词时（例如直接调用本源）的兜底检索词：「歌手 - 标题」。 */
private fun LyricsMatchRequest.fallbackKeyword(title: String): String = artists
    .map(String::trim)
    .filter(String::isNotBlank)
    .joinToString(" - ")
    .takeIf(String::isNotBlank)
    ?.let { "$it - $title" }
    ?: title

private fun parseArray(value: String): JsonArray = try {
    providerJson.parseToJsonElement(value) as? JsonArray
        ?: throw LyricsPayloadException("Provider JSON is not an array")
} catch (cause: LyricsPayloadException) {
    throw cause
} catch (cause: Exception) {
    throw LyricsPayloadException("Invalid provider JSON", cause)
}

private val providerJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun parseObject(value: String): JsonObject = try {
    providerJson.parseToJsonElement(value).jsonObject
} catch (cause: Exception) {
    throw LyricsPayloadException("Invalid provider JSON", cause)
}

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
private fun JsonObject.objectAt(name: String): JsonObject? = get(name) as? JsonObject
private fun JsonObject.arrayAt(name: String): JsonArray? = get(name) as? JsonArray
private fun JsonObject.stringAt(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.longAt(name: String): Long? = (get(name) as? JsonPrimitive)?.longOrNull
private fun JsonObject.doubleAt(name: String): Double? = (get(name) as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.booleanAt(name: String): Boolean? = (get(name) as? JsonPrimitive)?.booleanOrNull

private fun SyncedLyrics.requireUsable(label: String): SyncedLyrics =
    takeIf(SyncedLyrics::hasUsableLines) ?: throw LyricsPayloadException("$label are empty")

private fun decodeLyricField(value: String?): String {
    val raw = value.orEmpty()
    if (raw.isBlank() || raw.contains('[')) return raw
    return runCatching { decodeBase64(raw) }.getOrDefault(raw)
}

private fun decodeQqNativeField(value: String?): String {
    val raw = value.orEmpty()
    if (raw.isBlank()) return ""
    return if (raw.length % 2 == 0 && raw.all(Char::isHexDigit)) decryptQrc(raw) else decodeLyricField(raw)
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun decryptQrc(value: String): String = try {
    val encrypted = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(QRC_KEY, "DESede"))
    inflate(cipher.doFinal(encrypted))
} catch (cause: Exception) {
    throw LyricsPayloadException("Invalid QQ QRC payload", cause)
}

private fun decryptKrc(value: String): String = try {
    val encoded = decodeBase64Bytes(value)
    if (encoded.size < KRC_HEADER.size || !encoded.copyOfRange(0, KRC_HEADER.size).contentEquals(KRC_HEADER)) {
        throw LyricsPayloadException("Invalid Kugou KRC header")
    }
    val encrypted = encoded.copyOfRange(KRC_HEADER.size, encoded.size)
    val decoded = ByteArray(encrypted.size) { index ->
        (encrypted[index].toInt() xor KRC_KEY[index % KRC_KEY.size].toInt()).toByte()
    }
    inflate(decoded)
} catch (cause: LyricsPayloadException) {
    throw cause
} catch (cause: Exception) {
    throw LyricsPayloadException("Invalid Kugou KRC payload", cause)
}

private fun inflate(value: ByteArray): String = InflaterInputStream(ByteArrayInputStream(value)).use { stream ->
    stream.readBytes().toString(Charsets.UTF_8)
}

private fun encodeBase64(value: String): String =
    Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

private fun decodeBase64(value: String): String = decodeBase64Bytes(value).toString(Charsets.UTF_8)

private fun decodeBase64Bytes(value: String): ByteArray = try {
    Base64.getDecoder().decode(value)
} catch (cause: IllegalArgumentException) {
    throw LyricsPayloadException("Invalid base64 lyric payload", cause)
}

private fun decodeHtml(value: String): String = value
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("&#(\\d+);")) { match ->
        match.groupValues[1].toIntOrNull()?.let { codePoint -> String(Character.toChars(codePoint)) }.orEmpty()
    }

private fun stripMarkup(value: String): String = decodeHtml(value.replace(Regex("<[^>]+>"), "")).trim()

private val QRC_KEY = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII)
private val KRC_HEADER = byteArrayOf('k'.code.toByte(), 'r'.code.toByte(), 'c'.code.toByte(), '1'.code.toByte())
private val KRC_KEY = byteArrayOf(
    0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
    0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69,
)

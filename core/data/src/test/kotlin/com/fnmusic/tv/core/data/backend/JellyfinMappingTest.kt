package com.fnmusic.tv.core.data.backend

import com.fnmusic.tv.core.data.api.ApiDecoder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jellyfin 适配器的纯函数部分：DTO 解码与领域映射、播放/封面 URL 构造。
 * 样本 JSON 取自本地实测（Jellyfin 10.10.7，见 .trellis/spec/backend/jellyfin-contracts.md）。
 */
class JellyfinMappingTest {

    private val api = JellyfinApi(
        origin = "http://jellyfin.local:8096".toHttpUrl(),
        client = OkHttpClient(),
        deviceId = "test-device",
        tokenProvider = { "TOKEN123" },
    )

    private val itemJson = """
        {"Id":"3121edc7c6868b7fafb4e54171910d54","Name":"#SELFIE (Caked Up Remix)",
         "Album":"世界百大DJ舞曲","AlbumId":"cdd67818cdf41727888925ad9e834893",
         "AlbumArtist":"Various Artists","Artists":["The Chainsmokers","Various Artists"],
         "RunTimeTicks":1950400000,"Container":"flac","IndexNumber":3,"ParentIndexNumber":1,
         "ImageTags":{"Primary":"abc"},"UserData":{"IsFavorite":true,"Played":true,
         "LastPlayedDate":"2026-08-26T12:41:11.6016328Z"},
         "MediaSources":[{"Id":"3121edc7c6868b7fafb4e54171910d54","Container":"flac",
         "Bitrate":1763987,"SupportsDirectPlay":true,"SupportsDirectStream":true,
         "SupportsTranscoding":true}]}
    """.trimIndent()

    @Test
    fun `audio item maps to domain track`() {
        val dto = ApiDecoder.json.decodeFromString<JellyfinItemDto>(itemJson)

        val track = dto.toTrack()

        assertEquals("3121edc7c6868b7fafb4e54171910d54", track.guid.value)
        assertEquals("#SELFIE (Caked Up Remix)", track.title)
        // 多艺人按与飞牛一致的 " / " 连接
        assertEquals("The Chainsmokers / Various Artists", track.artistName)
        assertEquals("世界百大DJ舞曲", track.albumName)
        // RunTimeTicks → 毫秒（1ms = 10_000 ticks）：1950400000 / 10000 = 195040
        assertEquals(195_040L, track.durationMs)
        // 有 ImageTags.Primary 才给 coverId（用条目 id 取图）
        assertEquals("3121edc7c6868b7fafb4e54171910d54", track.coverId)
        assertEquals("FLAC", track.audioFormat)
        assertTrue(track.isFavorite)
        // 飞牛语义字段对 Jellyfin 恒为 false/null
        assertEquals(false, track.isCue)
        assertNull(track.accessStatus)
    }

    @Test
    fun `item without primary image has no cover`() {
        val json = """{"Id":"abc","Name":"No Art","ImageTags":{}}"""

        val track = ApiDecoder.json.decodeFromString<JellyfinItemDto>(json).toTrack()

        assertNull(track.coverId)
        assertNull(track.albumName)
        assertEquals("No Art", track.title)
    }

    @Test
    fun `artist falls back to album artist when artists are absent`() {
        val json = """{"Id":"abc","Name":"Solo","AlbumArtist":"Someone"}"""

        val track = ApiDecoder.json.decodeFromString<JellyfinItemDto>(json).toTrack()

        assertEquals("Someone", track.artistName)
        assertNull(track.audioFormat)
    }

    @Test
    fun `jellyfin lyrics convert to lrc text in time order`() {
        val json = """{"Lyrics":[{"Text":"第二行","Start":15000000},
            {"Text":"第一行","Start":5000000},{"Text":"","Start":9000000}]}"""

        val doc = ApiDecoder.json.decodeFromString<JellyfinLyricsDto>(json)
            .toLyricDocument("t1")

        assertEquals(true, doc?.isLrc)
        // 空行被丢掉，行按时间排序，ticks 换算成 [mm:ss.xx]
        assertEquals("[00:00.50]第一行" + "\n" + "[00:01.50]第二行", doc?.content)
        assertEquals("t1", doc?.guid)
    }

    @Test
    fun `lyrics without start times produce no document`() {
        val json = """{"Lyrics":[{"Text":"无时间戳"}]}"""

        val doc = ApiDecoder.json.decodeFromString<JellyfinLyricsDto>(json)
            .toLyricDocument("t1")

        assertNull(doc)
    }

    @Test
    fun `album artist genre and playlist map to domain collections`() {
        val album = ApiDecoder.json.decodeFromString<JellyfinItemDto>(
            """{"Id":"al1","Name":"世界百大DJ","AlbumArtist":"Various Artists",
                "ImageTags":{"Primary":"x"},"RecursiveItemCount":12,"ProductionYear":2019}""",
        )
        val artist = ApiDecoder.json.decodeFromString<JellyfinItemDto>(
            """{"Id":"ar1","Name":"Diana Ross","ImageTags":{},"RecursiveItemCount":30,
                "AlbumCount":4}""",
        )
        val genre = ApiDecoder.json.decodeFromString<JellyfinItemDto>(
            """{"Id":"g1","Name":"Jazz","RecursiveItemCount":9}""",
        )
        val playlist = ApiDecoder.json.decodeFromString<JellyfinItemDto>(
            """{"Id":"pl1","Name":"我的歌单","RecursiveItemCount":7}""",
        )

        assertEquals("al1", album.toAlbum().guid.value)
        assertEquals("Various Artists", album.toAlbum().artistName)
        assertEquals(12, album.toAlbum().trackCount)
        assertEquals("2019", album.toAlbum().releaseDate)

        assertEquals(30, artist.toArtist().trackCount)
        assertEquals(4, artist.toArtist().albumCount)
        assertNull(artist.toArtist().coverId)

        assertEquals("Jazz", genre.toGenre().name)
        assertEquals(9, genre.toGenre().trackCount)

        // 歌单没有自己的封面 → coverId 为空，上层沿用“前三首拼排”
        assertNull(playlist.toPlaylist().coverId)
        assertEquals(7, playlist.toPlaylist().trackCount)
    }

    @Test
    fun `playlist track keeps its entry id for removal`() {
        val json = """{"Id":"track-1","Name":"Song","PlaylistItemId":"entry-9","ImageTags":{"Primary":"x"}}"""

        val track = ApiDecoder.json.decodeFromString<JellyfinItemDto>(json).toTrack()

        // 删歌单条目要用 PlaylistItemId（不是曲目 id）
        assertEquals("entry-9", track.playlistEntryId)
    }

    @Test
    fun `track outside a playlist has no entry id`() {
        val track = ApiDecoder.json.decodeFromString<JellyfinItemDto>(itemJson).toTrack()

        assertNull(track.playlistEntryId)
    }

    @Test
    fun `items response carries the total count used for paging`() {
        val json = """{"Items":[$itemJson],"TotalRecordCount":27577,"StartIndex":100}"""

        val response = ApiDecoder.json.decodeFromString<JellyfinItemsDto>(json)

        // 分页元信息来自响应体（仓库按 RawPage/DecodedPage 走，页号由请求侧决定）。
        assertEquals(27_577, response.TotalRecordCount)
        assertEquals(100, response.StartIndex)
        assertEquals("#SELFIE (Caked Up Remix)", response.Items.single().toTrack().title)
    }

    @Test
    fun `playlist items keep their entry id for removal`() {
        val json = """{"Id":"t1","Name":"Song","PlaylistItemId":"entry-9"}"""

        val dto = ApiDecoder.json.decodeFromString<JellyfinItemDto>(json)

        assertEquals("entry-9", dto.PlaylistItemId)
    }

    @Test
    fun `items page decodes totals`() {
        val json = """{"Items":[$itemJson],"TotalRecordCount":27577,"StartIndex":0}"""

        val page = ApiDecoder.json.decodeFromString<JellyfinItemsDto>(json)

        assertEquals(27_577, page.TotalRecordCount)
        assertEquals(1, page.Items.size)
    }

    @Test
    fun `image url uses fill width and height for square covers`() {
        val url = api.imageUrl("ITEM1", 400)

        assertTrue(url.contains("/Items/ITEM1/Images/Primary"))
        assertTrue(url.contains("fillWidth=400"))
        assertTrue(url.contains("fillHeight=400"))
    }

    @Test
    fun `image url without width keeps the original size`() {
        val url = api.imageUrl("ITEM1", null)

        assertTrue(url.endsWith("/Items/ITEM1/Images/Primary"))
    }

    @Test
    fun `direct stream url is static and carries no token`() {
        val url = api.directStreamUrl("ITEM1")

        assertTrue(url.contains("/Audio/ITEM1/stream"))
        assertTrue(url.contains("static=true"))
        // URL 会进播放队列快照（Room 未加密）与通知元数据，token 只能走请求头
        assertFalse(url.contains("api_key"))
        assertFalse(url.contains("TOKEN123"))
    }

    @Test
    fun `hls transcode url matches the verified parameter set`() {
        val url = api.hlsTranscodeUrl("ITEM1")

        // 实测：必须是 master.m3u8（/stream?...hls 返回裸 TS，HLS 解析器解不了）
        assertTrue(url.contains("/Audio/ITEM1/master.m3u8"))
        assertTrue(url.contains("MediaSourceId=ITEM1"))
        assertTrue(url.contains("transcodingProtocol=hls"))
        assertTrue(url.contains("audioCodec=aac"))
        assertTrue(url.contains("container=ts"))
        assertTrue(url.contains("maxAudioChannels=2"))
        // 同上：不带 token
        assertFalse(url.contains("api_key"))
        assertFalse(url.contains("TOKEN123"))
    }
}

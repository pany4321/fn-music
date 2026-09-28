package com.fnmusic.tv.core.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 飞牛源的"不可播打标"：飞牛没有转码兜底（实测 `track/stream` 原文件直出），
 * 客户端解不了的格式在映射期就打上原因，UI 直接说明，替代"点播放→失败"。
 * 判定用已知解不了的格式黑名单 —— 不认识的格式宁可放行让它试。
 */
class FnOsUnplayableReasonTest {

    @Test
    fun `dsd codecs are marked with a dsd reason regardless of bit order`() {
        assertEquals("DSD 格式暂不支持播放", fnOsUnplayableReason(container = "", codec = "dsd_msbf"))
        assertEquals("DSD 格式暂不支持播放", fnOsUnplayableReason(container = "", codec = "dsd_lsbf_planar"))
        // 就算容器字段写了名字，codec 是 DSD 也拦下
        assertEquals("DSD 格式暂不支持播放", fnOsUnplayableReason(container = "dsf", codec = "dsd_msbf"))
    }

    @Test
    fun `raw pcm without a container cannot be played`() {
        // 实测曲库里的 pcm_s16le：codec 有值、container 为空 —— 裸 PCM 流解不了
        assertEquals("该格式暂不支持播放", fnOsUnplayableReason(container = "", codec = "pcm_s16le"))
    }

    @Test
    fun `known unsupported codecs are marked`() {
        assertEquals("该格式暂不支持播放", fnOsUnplayableReason(container = "", codec = "ape"))
        assertEquals("该格式暂不支持播放", fnOsUnplayableReason(container = "", codec = "wma"))
    }

    @Test
    fun `regular containers stay playable`() {
        listOf(
            Pair("flac", ""),
            Pair("", "flac"),
            Pair("wav", "pcm_s16le"),
            Pair("mp4", "aac"),
            Pair("mka", ""),
        ).forEach { (container, codec) ->
            assertEquals(
                "container=$container codec=$codec 应可播",
                null, fnOsUnplayableReason(container = container, codec = codec),
            )
        }
    }

    @Test
    fun `blank spec cannot be judged and stays playable`() {
        assertEquals(null, fnOsUnplayableReason(container = "", codec = ""))
    }
}

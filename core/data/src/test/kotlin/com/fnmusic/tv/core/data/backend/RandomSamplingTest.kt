package com.fnmusic.tv.core.data.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 随机取样的两个纯函数：
 * 它们修的是"随机歌曲/随机专辑看起来总是前后连续"——分页数据本身是排好序的，
 * 取到页之后必须本地洗牌并把相邻同组的拆开。
 */
class RandomSamplingTest {

    @Test
    fun `random pages exclude the previous page when there is a choice`() {
        val pages = randomPages(total = 500, pageSize = 24, count = 2, exclude = 7)

        assertEquals(2, pages.size)
        assertEquals(2, pages.distinct().size)
        assertTrue("不能再选上一次那页", pages.none { it == 7 })
        assertTrue(pages.all { it in 1..21 })
    }

    @Test
    fun `random pages fall back to the excluded page when it is the only one`() {
        // 曲库只有一页：排除后没有候选，必须回退，而不是返回空列表
        val pages = randomPages(total = 10, pageSize = 24, count = 2, exclude = 1)

        assertEquals(listOf(1), pages)
    }

    @Test
    fun `random pages clamp the count to the available pages`() {
        val pages = randomPages(total = 100, pageSize = 24, count = 2, exclude = null)

        assertEquals(2, pages.size)
        assertTrue(pages.all { it in 1..5 })
    }

    @Test
    fun `random pages can draw three distinct pages for the wider sample window`() {
        // 随机歌曲/随机专辑都取三页：三页必须互不相同，且不选上一次用过的页
        val pages = randomPages(total = 10_000, pageSize = 24, count = 3, exclude = 42)

        assertEquals(3, pages.size)
        assertEquals(3, pages.distinct().size)
        assertTrue(pages.none { it == 42 })
        assertTrue(pages.all { it in 1..417 })
    }

    @Test
    fun `spread separates items that belong to the same group`() {
        // A A A B C：洗牌后的典型"连续三段"形态
        val tracks = listOf("A", "A", "A", "B", "C")

        val spread = tracks.spreadByKey { it }

        assertEquals(tracks.size, spread.size)
        assertEquals(tracks.sorted(), spread.sorted())
        assertEquals("相邻不能还是 A", false, spread[1] == "A")
    }

    @Test
    fun `spread keeps the list as is when everything is one group`() {
        val tracks = listOf("A", "A", "A")

        assertEquals(tracks, tracks.spreadByKey { it })
    }

    @Test
    fun `spread ignores blank and null groups`() {
        // albumName 可能缺失（单曲/无标签文件）：不能因为"都是 null"就误判成同组
        val tracks = listOf<String?>(null, null, "", "")

        assertEquals(tracks, tracks.spreadByKey { it })
    }

    @Test
    fun `spread does not touch short lists`() {
        val tracks = listOf("A", "A")

        assertEquals(tracks, tracks.spreadByKey { it })
    }

    @Test
    fun `random pages never repeat a page across consecutive draws`() {
        // 连续取样的页号不应总是同一页（它俩是"刷新两次结果一样"的直接原因）
        val first = randomPages(total = 10_000, pageSize = 24, count = 2, exclude = null)
        val second = randomPages(total = 10_000, pageSize = 24, count = 2, exclude = first.last())

        assertNotEquals(first, second)
    }
}

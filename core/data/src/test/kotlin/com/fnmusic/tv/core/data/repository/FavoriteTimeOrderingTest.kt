package com.fnmusic.tv.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏列表"最新收藏在前"（按最后一次收藏时间降序）的对账与切片逻辑（纯函数）。
 * 规则见 .trellis/spec/backend/android-client-contracts.md §Server-backed favorites：
 * 本 App 收藏动作时间 > 响应自带时间（favoriteAt/DateLastSaved） > 按服务端 desc 序分配的种子时间。
 */
class FavoriteTimeOrderingTest {
    private val now = 1_770_000_000_000L

    @Test fun `first sync without any times seeds server order newest first`() {
        // 服务端 desc 序：index 0 = 最新收藏
        val serverOrder = listOf("d", "c", "b", "a")

        val result = reconcileFavoriteTimes(serverOrder, emptyMap(), emptyMap(), now)

        // 降序显示 = d, c, b, a：最新收藏的在前
        assertEquals(listOf("d", "c", "b", "a"), serverOrder.sortedByDescending { result.times.getValue(it) })
        // 种子全部早于本次对账时刻且互不重叠；服务端序越靠前（越新）种子越大
        assertTrue(result.times.values.all { it in (now - 4_000L) until now })
        assertEquals(4, result.times.values.toSet().size)
        assertEquals(now - 1_000L, result.times.getValue("d"))
        assertEquals(now - 4_000L, result.times.getValue("a"))
        assertEquals(result.times, result.upserts)
        assertTrue(result.removals.isEmpty())
    }

    @Test fun `response provided favorite times are used as-is`() {
        // 飞牛 favoriteAt / Jellyfin DateLastSaved：真实收藏时间（旧→新）
        val serverOrder = listOf("new", "mid", "old")
        val serverTimes = mapOf("old" to now - 90_000L, "mid" to now - 50_000L, "new" to now - 10_000L)

        val result = reconcileFavoriteTimes(serverOrder, serverTimes, emptyMap(), now)

        assertEquals(listOf("new", "mid", "old"), serverOrder.sortedByDescending { result.times.getValue(it) })
        assertEquals(serverTimes, result.upserts)
        assertTrue(result.removals.isEmpty())
    }

    @Test fun `in-app recorded times are never overwritten by server values`() {
        // 本 App 收藏动作写入的时间是权威：重复收藏刚刷新过，服务器回包的旧值不能覆盖
        val localTime = now - 5_000L
        val serverTimes = mapOf("a" to now - 900_000L)

        val result = reconcileFavoriteTimes(listOf("a"), serverTimes, mapOf("a" to localTime), now)

        assertEquals(localTime, result.times.getValue("a"))
        assertTrue(result.upserts.isEmpty())
    }

    @Test fun `members removed on the server are dropped from the local table`() {
        val result = reconcileFavoriteTimes(listOf("a"), emptyMap(), mapOf("a" to 1, "gone" to 2), now)

        assertEquals(setOf("gone"), result.removals)
        assertTrue("gone" !in result.times)
        assertEquals(1L, result.times.getValue("a"))
    }

    @Test fun `server gone empty removes everything`() {
        val result = reconcileFavoriteTimes(emptyList(), emptyMap(), mapOf("a" to 1, "b" to 2), now)

        assertEquals(setOf("a", "b"), result.removals)
        assertTrue(result.times.isEmpty())
        assertTrue(result.upserts.isEmpty())
    }

    @Test fun `new members favorited elsewhere sort before existing favorites`() {
        // 第二次对账：本地已有旧种子，服务器新增 e（其他端刚收藏，响应不带时间）——
        // 最新收藏在前：e 的种子比所有现有时间都大，排在最前
        val existing = now - 60_000L

        val result = reconcileFavoriteTimes(listOf("e", "a"), emptyMap(), mapOf("a" to existing), now)

        assertEquals(now - 1_000L, result.times.getValue("e"))
        assertEquals(existing, result.times.getValue("a"))
        assertEquals(listOf("e", "a"), listOf("e", "a").sortedByDescending { result.times.getValue(it) })
        assertEquals(mapOf("e" to now - 1_000L), result.upserts)
    }

    @Test fun `slice pages preserve order total and local sort mark`() {
        val items = List(120) { "t$it" }

        val first = sliceFavoritePage(items, 1, 50)
        val third = sliceFavoritePage(items, 3, 50)

        assertEquals((0 until 50).map { "t$it" }, first.items)
        assertEquals(120, first.total)
        assertTrue(first.hasNext)
        assertEquals((100 until 120).map { "t$it" }, third.items)
        assertEquals(120, third.total)
        assertFalse(third.hasNext)
        assertEquals(FAVORITE_LOCAL_SORT, first.sort)
        assertEquals(0, sliceFavoritePage(emptyList<String>(), 1, 50).items.size)
    }
}

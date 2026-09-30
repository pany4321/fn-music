package com.fnmusic.tv.core.data.backend

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 搜索多源合并的纯函数测试（Jellyfin 音轨/专辑区的"飞牛式全字段匹配"）。
 * 查询编排依赖网络，合并/去重/排序/切窗的逻辑在这里验证。
 */
class JellyfinSearchMergeTest {
    private fun item(id: String, name: String) = JellyfinItemDto(Id = id, Name = name)

    @Test fun `duplicate ids across sources are merged once`() {
        val direct = JellyfinItemsDto(Items = listOf(item("1", "B"), item("2", "A")), TotalRecordCount = 2)
        val byAlbum = JellyfinItemsDto(Items = listOf(item("2", "A"), item("3", "C")), TotalRecordCount = 2)
        val byArtist = JellyfinItemsDto(Items = listOf(item("3", "C"), item("1", "B")), TotalRecordCount = 2)

        val merged = mergeSearchPages(listOf(direct, byAlbum, byArtist), page = 1, size = 20)

        assertEquals(listOf("2", "1", "3"), merged.Items.map { it.Id })
        assertEquals(3, merged.TotalRecordCount)
    }

    @Test fun `merged items are sorted by name case-insensitively`() {
        val direct = JellyfinItemsDto(Items = listOf(item("1", "banana"), item("2", "Apple")))
        val byAlbum = JellyfinItemsDto(Items = listOf(item("3", "cherry"), item("4", "apricot")))

        val merged = mergeSearchPages(listOf(direct, byAlbum), page = 1, size = 20)

        assertEquals(listOf("2", "4", "1", "3"), merged.Items.map { it.Id })
    }

    @Test fun `window is sliced for the requested page`() {
        val pages = listOf(
            JellyfinItemsDto(Items = (1..25).map { item("id-$it", "t-${it.toString().padStart(2, '0')}") }),
        )

        val page2 = mergeSearchPages(pages, page = 2, size = 20)

        assertEquals((21..25).map { "id-$it" }, page2.Items.map { it.Id })
        assertEquals(25, page2.TotalRecordCount)
    }

    @Test fun `window beyond the merged total is empty`() {
        val pages = listOf(JellyfinItemsDto(Items = listOf(item("1", "a")), TotalRecordCount = 1))

        val page3 = mergeSearchPages(pages, page = 3, size = 20)

        assertEquals(0, page3.Items.size)
        assertEquals(1, page3.TotalRecordCount)
    }

    @Test fun `empty sources yield an empty page`() {
        val merged = mergeSearchPages(listOf(JellyfinItemsDto(), JellyfinItemsDto()), page = 1, size = 20)

        assertEquals(0, merged.Items.size)
        assertEquals(0, merged.TotalRecordCount)
    }
}

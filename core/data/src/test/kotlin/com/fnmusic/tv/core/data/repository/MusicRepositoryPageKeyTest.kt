package com.fnmusic.tv.core.data.repository

import com.fnmusic.tv.core.data.backend.DecodedPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRepositoryPageKeyTest {
    @Test fun `page cache key isolates requested page sizes`() {
        assertEquals("artists:size=12", sizedPageSourceKey("artists", 12))
        assertNotEquals(sizedPageSourceKey("artists", 12), sizedPageSourceKey("artists", 50))
        assertThrows(IllegalArgumentException::class.java) { sizedPageSourceKey("albums", 0) }
    }

    /**
     * 列表缓存键必须带"数据契约版本"：改过请求字段（如给 Jellyfin 补 ImageTags）却没换键时，
     * 升级后会一直读到修复前的旧 payload —— 真机上表现就是"随机漫游有封面了、最近添加还是没有"。
     */
    @Test fun `page cache keys are namespaced by the data contract version`() {
        val key = contractVersionedKey("albums")

        assertNotEquals("albums", key)
        assertTrue(key, key.endsWith(":albums"))
        // 同一个源键要稳定映射到同一个版本化键（否则缓存永远不命中）
        assertEquals(key, contractVersionedKey("albums"))
        assertNotEquals(key, contractVersionedKey("artists"))
    }

    @Test fun `requested television page size controls continuation`() {
        val response = DecodedPage(
            items = List(12) { "artist-$it" },
            total = 345,
            sort = "",
        )

        val seventhPage = response.toDomainPage(page = 7, pageSize = 12)
        val lastPage = response.toDomainPage(page = 29, pageSize = 12)

        assertEquals(12, seventhPage.pageSize)
        assertTrue(seventhPage.hasNext)
        assertFalse(lastPage.hasNext)
    }
}

package com.fnmusic.tv.core.data.backend

import com.fnmusic.tv.core.data.api.ApiDecoder
import com.fnmusic.tv.core.data.api.TrackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 飞牛收藏列表映射：isFavorite 恒真 + favoriteAt 透传为排序候选种子。 */
class FnOsFavoriteMappingTest {
    @Test fun `favorite dto maps favorite time when present`() {
        val dto = ApiDecoder.json.decodeFromString<TrackDto>(
            """{"guid":"g","title":"t","favoriteAt":1770000000123}""",
        )

        val track = dto.toFavoriteDomain()

        assertTrue(track.isFavorite)
        assertEquals(1_770_000_000_123L, track.favoritedAt)
    }

    @Test fun `favorite dto keeps null favorite time when absent`() {
        val dto = ApiDecoder.json.decodeFromString<TrackDto>("""{"guid":"g","title":"t"}""")

        val track = dto.toFavoriteDomain()

        assertTrue(track.isFavorite)
        assertNull(track.favoritedAt)
    }
}

package com.fnmusic.tv.core.data.api

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `TrackDto.favoriteAt` 的宽容解析：服务端契约只承诺 `sort=favoriteAt,desc`，
 * 响应字段形态未定（毫秒/秒数值、数字字符串、ISO-8601、缺省），解析不出走种子回填。
 */
class FavoriteTimeParsingTest {
    @Test fun `parses epoch millis seconds and numeric strings`() {
        assertEquals(1_770_000_000_123L, JsonPrimitive(1_770_000_000_123L).favoriteTimeMillisOrNull())
        // 秒值（< 1e12）归一化为毫秒
        assertEquals(1_770_000_000_000L, JsonPrimitive(1_770_000_000L).favoriteTimeMillisOrNull())
        assertEquals(1_770_000_000_123L, JsonPrimitive("1770000000123").favoriteTimeMillisOrNull())
    }

    @Test fun `parses iso strings with zone and offset`() {
        assertEquals(1_767_225_600_000L, JsonPrimitive("2026-01-01T00:00:00Z").favoriteTimeMillisOrNull())
        assertEquals(1_767_225_600_000L, JsonPrimitive("2026-01-01T08:00:00+08:00").favoriteTimeMillisOrNull())
    }

    @Test fun `absent and unparsable values yield null`() {
        assertNull(null.favoriteTimeMillisOrNull())
        assertNull(JsonPrimitive("not-a-time").favoriteTimeMillisOrNull())
        assertNull(JsonPrimitive("").favoriteTimeMillisOrNull())
    }

    @Test fun `track dto tolerates missing favorite at field`() {
        val dto = ApiDecoder.json.decodeFromString<TrackDto>("""{"guid":"g","title":"t"}""")
        assertNull(dto.favoriteAt)
        assertEquals("g", dto.guid)
    }

    @Test fun `track dto decodes favorite at in numeric and string shapes`() {
        val numeric = ApiDecoder.json.decodeFromString<TrackDto>(
            """{"guid":"g","title":"t","favoriteAt":1770000000123}""",
        )
        assertEquals(1_770_000_000_123L, numeric.favoriteAt!!.favoriteTimeMillisOrNull())

        val textual = ApiDecoder.json.decodeFromString<TrackDto>(
            """{"guid":"g","title":"t","favoriteAt":"1770000000123"}""",
        )
        assertEquals(1_770_000_000_123L, textual.favoriteAt!!.favoriteTimeMillisOrNull())
    }
}

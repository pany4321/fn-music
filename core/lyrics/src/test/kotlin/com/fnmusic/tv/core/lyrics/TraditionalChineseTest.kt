package com.fnmusic.tv.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 繁→简映射表（OpenCC TSCharacters 单字 BMP 子集）。
 *
 * 这里守两类回归：
 * - 常用繁体字的映射正确（唱词/歌名里最常见的那批）；
 * - **代理对（非 BMP 字符）不得破坏表对齐** —— 表按「两条等长字符串下标对齐」压缩，
 *   一旦混入非 BMP 字符，后续所有映射整体错位（真实踩过：「叶」被转成「葒」）。
 */
class TraditionalChineseTest {
    @Test fun `common traditional characters convert to simplified`() {
        assertEquals("叶倩文", TraditionalChinese.toSimplified("葉倩文"))
        assertEquals("春风秋雨", TraditionalChinese.toSimplified("春風秋雨"))
        assertEquals("爱上一个不回家的人", TraditionalChinese.toSimplified("愛上一個不回家的人"))
        assertEquals("梦醒时分", TraditionalChinese.toSimplified("夢醒時分"))
        assertEquals("伤心的话留到明天再说", TraditionalChinese.toSimplified("傷心的話留到明天再說"))
    }

    @Test fun `simplified text is returned unchanged`() {
        val text = "春风秋雨"
        assertEquals(text, TraditionalChinese.toSimplified(text))
        assertFalse(TraditionalChinese.hasTraditional(text))
        assertTrue(TraditionalChinese.hasTraditional("春風秋雨"))
    }

    @Test fun `non bmp characters survive unchanged and do not shift the mapping`() {
        // 𠵾 是代理对（占两个 Char）：表里只收 BMP 字符，它原样保留，且相邻字符仍正确映射
        val text = "𠵾葉𠵾風𠵾"
        assertEquals("𠵾叶𠵾风𠵾", TraditionalChinese.toSimplified(text))
    }

    @Test fun `ascii punctuation and japanese pass through`() {
        assertEquals("Hello, 世界! 叶 (1991)", TraditionalChinese.toSimplified("Hello, 世界! 葉 (1991)"))
        assertEquals("TVサイズ", TraditionalChinese.toSimplified("TVサイズ"))
    }

    @Test fun `conversion is idempotent`() {
        val once = TraditionalChinese.toSimplified("葉倩文 - 春風秋雨（1991）")
        assertEquals("叶倩文 - 春风秋雨（1991）", once)
        assertEquals(once, TraditionalChinese.toSimplified(once))
    }

    @Test fun `table keeps the opencc single character bump subset`() {
        assertEquals(2965, TraditionalChinese.entryCount)
    }
}

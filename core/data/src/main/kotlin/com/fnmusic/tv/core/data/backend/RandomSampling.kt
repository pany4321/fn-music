package com.fnmusic.tv.core.data.backend

/**
 * 随机取样的公共工具。
 *
 * 背景：两个后端的"随机"都很容易退化成"成段连续"—— 飞牛只能按分页随机挑页，
 * 而页内是按添加时间排好序的（一页常常就是同一张专辑）；专辑列表按名称排序，同理。
 * 所以取到页之后本地还要洗牌，并把相邻的同专辑/同歌手条目拆开。
 */

/**
 * 随机页号：优先排除 [exclude]（上一次用过的那页），全被排除时再放开。
 * 只挑一页且不做这一步时，连续刷新很容易撞回同一页，结果与上次逐条相同。
 */
internal fun randomPages(total: Int, pageSize: Int, count: Int, exclude: Int?): List<Int> {
    val lastPage = ((total + pageSize - 1) / pageSize).coerceAtLeast(1)
    val all = (1..lastPage).toList()
    val pool = all.filter { it != exclude }.ifEmpty { all }
    return pool.shuffled().take(count.coerceAtMost(pool.size))
}

/**
 * 把相邻同组的条目拆开（[key] 相同视为同组，例如同一张专辑）。
 * 与后面第一个不同组的条目交换；找不到就保持原样——宁可不换，也不打乱其余顺序。
 */
internal fun <T> List<T>.spreadByKey(key: (T) -> String?): List<T> {
    if (size < 3) return this
    val result = toMutableList()
    for (index in 1 until result.size) {
        val group = key(result[index])?.takeIf(String::isNotBlank) ?: continue
        if (group != key(result[index - 1])) continue
        val swapAt = (index + 1 until result.size)
            .firstOrNull { key(result[it]) != group } ?: continue
        val moved = result[index]
        result[index] = result[swapAt]
        result[swapAt] = moved
    }
    return result
}

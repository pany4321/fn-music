package com.fnmusic.tv.core.data.backend

import com.fnmusic.tv.core.model.ServerIdentity
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.User

/**
 * 音乐服务器类型。新增后端时在这里加一项，并在 [MusicBackend] 提供实现。
 */
enum class ServerKind { FnOs, Jellyfin }

/**
 * 后端能力声明：把"某些服务器有、某些没有"的差异集中在这里，
 * 上层（UI/仓库）按能力决定走哪条路，而不是到处判断服务器类型。
 */
data class BackendCapabilities(
    /** 随机漫游由服务端维护前后节点（飞牛有；Jellyfin 没有，要靠客户端策略）。 */
    val serverSideRoam: Boolean,
    /** 服务端能提供歌词（Jellyfin 需 10.9+，没有时退回在线歌词源）。 */
    val serverSideLyrics: Boolean,
    /** 服务端记录播放历史（最近播放）。 */
    val serverSidePlayHistory: Boolean,
    /** 服务端可转码（Jellyfin 的 PlaybackInfo/stream 转码参数）。 */
    val transcoding: Boolean,
    /** 收藏有"收藏时间"排序（飞牛有；Jellyfin 只能按创建时间）。 */
    val favoriteTimeSort: Boolean,
)

/** 播放方式：直连原文件 / HLS 转码 / HTTP 转码流。 */
enum class StreamMode { Direct, Hls, HttpTranscode }

/** 一次播放的落地结果：交给 Media3 的 URL 与它的形态。 */
data class StreamPlan(val url: String, val mode: StreamMode)

/** 登录成功后的会话材料：身份、用户、以及后续请求要带的头。 */
data class AuthSession(
    val identity: ServerIdentity,
    val user: User,
    val headers: Map<String, String>,
)

/**
 * 音乐服务器后端。**只负责"把服务器的接口变成领域模型"**，
 * 缓存、分页保留、收藏状态、队列装配都由 `MusicRepository` 负责。
 *
 * 这是逐步抽取的第一批方法（播放地址 + 随机取歌 + 用户信息）；
 * 目录/歌单/收藏等方法会按同样的方式陆续搬进来，Jellyfin 实现也照此补齐。
 */
interface MusicBackend {
    val kind: ServerKind

    val capabilities: BackendCapabilities

    /** 当前登录用户（用于会话校验与"我的"页面展示）。 */
    suspend fun me(): User

    /** 封面图片的完整 URL；[com.fnmusic.tv.core.model.CoverVariant] 的宽度直接映射到各家参数。 */
    fun artworkUrl(coverId: String, variantWidth: Int?): String

    /**
     * 纯拼直连 URL（不联网）：批量装配队列时用，避免为每首歌发一次协商请求。
     */
    fun directStreamUrl(track: Track): String

    /**
     * 决定这首歌怎么放：能直连就直连，否则让服务端转码。
     * 返回的 [StreamMode] 告诉播放器要不要走 HLS 解析。
     */
    suspend fun streamPlan(track: Track): StreamPlan

    /** 随机取 [size] 首歌（首页卡片封面、随机行用），各后端用自己最省的方式实现。 */
    suspend fun randomTracks(size: Int): List<Track>
}

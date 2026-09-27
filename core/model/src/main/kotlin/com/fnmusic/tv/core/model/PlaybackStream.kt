package com.fnmusic.tv.core.model

/**
 * 播放方式：直连原文件 / HLS 转码 / HTTP 转码流。
 * 放在 :core:model 里是因为播放队列（[PlaybackTrack]）与后端都要用它。
 */
enum class StreamMode { Direct, Hls, HttpTranscode }

/** 一次播放的落地结果：交给 Media3 的 URL 与它的形态。 */
data class StreamPlan(val url: String, val mode: StreamMode)

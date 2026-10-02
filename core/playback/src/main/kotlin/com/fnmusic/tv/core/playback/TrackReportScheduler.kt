package com.fnmusic.tv.core.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 最近播放上报调度器：一首歌"成为当前曲目且正在播放"后延迟 [delayMs] 才真正上报，
 * 期间任何切歌/暂停都会取消挂起的上报——秒切的歌、解码失败被跳过的歌不算"播放过"，
 * 不污染最近播放（与飞牛官方客户端语义一致：累计真实播放 >0 才记；
 * Jellyfin 侧也避免了 PlayCount 被秒切抬高）。
 *
 * 每次满足条件都重新调度：单曲循环时 Media3 会以 REPEAT 原因再发 transition，
 * 循环重播同样刷新最近时间。上报失败由 [report] 实现方静默处理（不影响播放）。
 */
internal class TrackReportScheduler(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val report: suspend (trackGuid: String) -> Unit,
) {
    private var pending: Job? = null

    /** 当前曲目变化（transition）：符合播放条件就（重新）调度；清空队列/未在播放则取消挂起。 */
    fun onTrackBecameCurrent(trackGuid: String?, playWhenReady: Boolean) {
        if (trackGuid == null || !playWhenReady) {
            pending?.cancel()
            return
        }
        schedule(trackGuid)
    }

    /** 播放/暂停切换：暂停取消挂起（暂停内不算继续播放）；恢复播放重新调度当前曲目。 */
    fun onPlayWhenReadyChanged(playWhenReady: Boolean, currentTrackGuid: String?) {
        if (playWhenReady) {
            currentTrackGuid?.takeIf(String::isNotBlank)?.let(::schedule)
        } else {
            pending?.cancel()
        }
    }

    private fun schedule(trackGuid: String) {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            report(trackGuid)
        }
    }
}

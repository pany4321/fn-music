package com.fnmusic.tv.core.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackReportSchedulerTest {

    @Test
    fun `延迟后上报仍在播放的当前曲目`() = runTest {
        val reported = mutableListOf<String>()
        val scheduler = TrackReportScheduler(this, 3_000) { reported += it }

        scheduler.onTrackBecameCurrent("a", playWhenReady = true)
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(emptyList<String>(), reported)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("a"), reported)
    }

    @Test
    fun `3秒内切歌取消上一首的挂起上报`() = runTest {
        val reported = mutableListOf<String>()
        val scheduler = TrackReportScheduler(this, 3_000) { reported += it }

        scheduler.onTrackBecameCurrent("a", playWhenReady = true)
        advanceTimeBy(2_000)
        scheduler.onTrackBecameCurrent("b", playWhenReady = true)
        advanceTimeBy(3_000)
        runCurrent()

        // a 只播了 2 秒，不算"播放过"；b 播满 3 秒才记
        assertEquals(listOf("b"), reported)
    }

    @Test
    fun `暂停取消挂起上报_恢复播放重新调度`() = runTest {
        val reported = mutableListOf<String>()
        val scheduler = TrackReportScheduler(this, 3_000) { reported += it }

        scheduler.onTrackBecameCurrent("a", playWhenReady = true)
        advanceTimeBy(2_000)
        scheduler.onPlayWhenReadyChanged(playWhenReady = false, currentTrackGuid = "a")
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(emptyList<String>(), reported)

        scheduler.onPlayWhenReadyChanged(playWhenReady = true, currentTrackGuid = "a")
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf("a"), reported)
    }

    @Test
    fun `单曲循环的REPEAT切歌会再次上报刷新时间`() = runTest {
        val reported = mutableListOf<String>()
        val scheduler = TrackReportScheduler(this, 3_000) { reported += it }

        scheduler.onTrackBecameCurrent("a", playWhenReady = true)
        advanceTimeBy(3_000)
        runCurrent()
        scheduler.onTrackBecameCurrent("a", playWhenReady = true)
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(listOf("a", "a"), reported)
    }

    @Test
    fun `空曲目或未播放的切歌取消挂起上报`() = runTest {
        val reported = mutableListOf<String>()
        val scheduler = TrackReportScheduler(this, 3_000) { reported += it }

        scheduler.onTrackBecameCurrent("a", playWhenReady = true)
        scheduler.onTrackBecameCurrent(null, playWhenReady = true)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(emptyList<String>(), reported)

        scheduler.onTrackBecameCurrent("a", playWhenReady = true)
        scheduler.onTrackBecameCurrent("a", playWhenReady = false)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(emptyList<String>(), reported)
    }
}

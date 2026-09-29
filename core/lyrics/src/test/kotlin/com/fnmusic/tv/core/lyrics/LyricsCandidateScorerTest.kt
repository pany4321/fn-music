package com.fnmusic.tv.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsCandidateScorerTest {
    private val scorer = LyricsCandidateScorer()
    private val request = LyricsMatchRequest(
        localId = "local",
        title = "晴天",
        artists = listOf("周杰伦"),
        album = "叶惠美",
        durationMs = 269_000,
    )

    @Test fun `exact metadata is accepted and cross-source agreement adds confidence`() {
        val candidates = listOf(
            candidate(LyricsSourceId.QqMusic),
            candidate(LyricsSourceId.Netease, remoteId = "2"),
        )

        val scored = scorer.score(request, candidates)

        assertEquals(2, scored.size)
        assertEquals(2, scored.first().consensusCount)
        assertEquals(100.0, scored.first().score, 0.001)
    }

    @Test fun `consensus counts distinct sources rather than duplicate candidates`() {
        val candidates = listOf(
            candidate(LyricsSourceId.QqMusic),
            candidate(LyricsSourceId.Netease, remoteId = "2"),
            candidate(LyricsSourceId.Netease, remoteId = "3"),
        )

        val scored = scorer.score(request, candidates)

        assertEquals(3, scored.size)
        assertTrue(scored.all { it.consensusCount == 2 })
    }

    @Test fun `duration mismatch at five seconds is accepted`() {
        assertEquals(1, scorer.score(request, listOf(candidate(durationMs = 274_000))).size)
    }

    @Test fun `duration mismatch one millisecond over five seconds is rejected`() {
        assertTrue(scorer.score(request, listOf(candidate(durationMs = 274_001))).isEmpty())
    }

    @Test fun `instrumental and live conflicts are rejected`() {
        assertTrue(scorer.score(request, listOf(candidate(title = "晴天 (Instrumental)", instrumental = true))).isEmpty())
        assertTrue(scorer.score(request, listOf(candidate(title = "晴天 Live"))).isEmpty())
    }

    @Test fun `full width and artist separators normalize consistently`() {
        val local = request.copy(title = "ＡＢＣ", artists = listOf("A / B"), album = null)
        val remote = candidate(title = "ABC", artists = listOf("A、B"), album = null)

        assertTrue(scorer.score(local, listOf(remote)).single().score >= 99.0)
    }

    @Test fun `near identical title and duration rescue an artist mismatch`() {
        // 合辑 / 古典 / 民族器乐：本地歌手标签（群星、演奏者、不同拼写）与在线库对不上，
        // 但标题与时长几乎完全一致 —— 这才是这套曲库的第二大漏配原因。
        val local = request.copy(artists = listOf("群星"), album = "宝丽金情歌对唱 II")
        val remote = candidate(artists = listOf("周杰伦 张惠妹"), album = "另一个专辑", durationMs = 269_500)

        val scored = scorer.score(local, listOf(remote))

        assertEquals(1, scored.size)
        assertEquals(100.0, scored.single().titleScore, 0.001)
    }

    @Test fun `artist mismatch is still rejected when the duration is looser than two seconds`() {
        // 兜底闸门的时长要求更严：3 秒差不足以在歌手对不上时放行
        val local = request.copy(artists = listOf("群星"))
        val remote = candidate(artists = listOf("周杰伦"), durationMs = 272_000)

        assertTrue(scorer.score(local, listOf(remote)).isEmpty())
    }

    @Test fun `artist mismatch is not rescued when the title is not near identical`() {
        val local = request.copy(artists = listOf("群星"))
        val remote = candidate(title = "晴天啊", artists = listOf("周杰伦"))

        assertTrue(scorer.score(local, listOf(remote)).isEmpty())
    }

    @Test fun `fallback gate does not bypass the version conflict check`() {
        val local = request.copy(artists = listOf("群星"))
        val remote = candidate(title = "晴天 (Live)", artists = listOf("周杰伦"))

        assertTrue(scorer.score(local, listOf(remote)).isEmpty())
    }

    @Test fun `a single lead artist matches a multi artist remote`() {
        // 分母曾经用"较长一方"：本地 1 人 vs 在线 3 人 → 即使主唱完全一致也只得 33 分。
        // 这里把专辑置空，确保不是专辑项把分数救了回来。
        val local = request.copy(artists = listOf("周杰伦"), album = null)
        val remote = candidate(artists = listOf("周杰伦", "方文山", "黄俊郎"), album = null)

        val scored = scorer.score(local, listOf(remote))

        assertEquals(1, scored.size)
        assertEquals(100.0, scored.single().score, 0.001)
    }

    @Test fun `title outweighs a partially disagreeing artist list`() {
        // 标题完全一致、歌手只有一半对得上（Bob 与 Zed 无共同字符 → 歌手分 50）：
        // 常规闸门得 75 分仍被拒，但标题与时长都吻合，由 R3 兜底放行
        val local = request.copy(title = "Song", artists = listOf("Alice", "Bob"), album = null)
        val remote = candidate(title = "Song", artists = listOf("Alice", "Zed"), album = null)

        val scored = scorer.score(local, listOf(remote))

        assertEquals(1, scored.size)
        assertEquals(75.0, scored.single().score, 0.001)
    }

    @Test fun `long tracks keep the same absolute duration tolerance`() {
        // 相对容差（长曲按 1% 放宽）试过又撤回：长曲上几秒到几十秒的差几乎一定意味着不同版本，
        // 20 分钟的曲子若接受 12 秒偏差，内部时间轴可能早就是另一场演出了。这里守住绝对值。
        val long = request.copy(durationMs = 1_200_000)
        val near = candidate(durationMs = 1_204_000)
        val far = candidate(durationMs = 1_208_000)

        assertEquals(1, scorer.score(long, listOf(near)).size)
        assertTrue(scorer.score(long, listOf(far)).isEmpty())
    }

    @Test fun `short tracks keep the absolute five second tolerance`() {
        val short = request.copy(durationMs = 180_000)
        val remote = candidate(durationMs = 186_000)

        assertTrue(scorer.score(short, listOf(remote)).isEmpty())
    }

    private fun candidate(
        source: LyricsSourceId = LyricsSourceId.QqMusic,
        remoteId: String = "1",
        title: String = "晴天",
        artists: List<String> = listOf("周杰伦"),
        album: String? = "叶惠美",
        durationMs: Long = 269_000,
        instrumental: Boolean = false,
    ) = LyricsCandidate(source, remoteId, title, artists, album, durationMs, instrumental = instrumental)
}

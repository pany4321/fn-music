package com.fnmusic.tv.core.data.backend

import com.fnmusic.tv.core.data.api.TrackDto
import com.fnmusic.tv.core.data.repository.SessionRepository
import com.fnmusic.tv.core.model.CoverVariant
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.User
import kotlin.random.Random

/**
 * 飞牛音乐（fnOS）后端：把现有的 `TrimMusicApi` 调用与 DTO 映射收拢到这里。
 * 本类的行为与原 `MusicRepository` 内的实现逐字一致（抽取阶段不做任何行为改动）。
 */
internal class FnOsMusicBackend(
    private val session: SessionRepository,
) : MusicBackend {

    override val kind: ServerKind = ServerKind.FnOs

    override val capabilities: BackendCapabilities = BackendCapabilities(
        serverSideRoam = true,
        serverSideLyrics = true,
        serverSidePlayHistory = true,
        transcoding = false,
        favoriteTimeSort = true,
    )

    override suspend fun me(): User = session.authenticated { it.me() }.toDomain()

    override fun artworkUrl(coverId: String, variantWidth: Int?): String =
        session.requireApi().coverUrl(coverId, variantWidth).toString()

    override fun directStreamUrl(track: Track): String =
        session.requireApi().streamUrl(track.guid.value).toString()

    override suspend fun streamPlan(track: Track): StreamPlan =
        // 飞牛只做直连（无转码）：cue/受限曲目在上层已被拒绝。
        StreamPlan(url = directStreamUrl(track), mode = StreamMode.Direct)

    /**
     * 随机取歌：先探测总数，再随机挑一页。
     * 与原实现相同（沿用 `track/list` 的分页语义），只是不再走响应缓存。
     */
    override suspend fun randomTracks(size: Int): List<Track> {
        val pageSize = size.coerceAtLeast(1)
        val probe = session.authenticated { it.allTracks(page = 1, size = 1) }
        val total = probe.total
        if (total <= pageSize) {
            return session.authenticated { it.allTracks(page = 1, size = pageSize) }
                .list
                .map(TrackDto::toDomain)
        }
        val lastPage = (total + pageSize - 1) / pageSize
        val page = Random.nextInt(1, lastPage + 1)
        return session.authenticated { it.allTracks(page = page, size = pageSize) }
            .list
            .map(TrackDto::toDomain)
    }
}

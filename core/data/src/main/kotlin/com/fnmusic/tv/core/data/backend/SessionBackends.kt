package com.fnmusic.tv.core.data.backend

import com.fnmusic.tv.core.data.repository.SessionRepository
import com.fnmusic.tv.core.model.ServerKind

/**
 * 按会话的后端类型构造查询实现。
 *
 * 同一会话内**复用同一个实例**：
 * - Jellyfin 的客户端漫游靠后端持有的 prev/current/next 游标，跨调用必须是同一份状态；
 * - 换会话（kind 或 namespace 变了）时重建，避免把上一个账号的状态带过去；
 * - 连接换代（rehome 换地址，guid 不变）时也重建 —— 否则 Jellyfin 后端仍绑着旧 origin。
 *
 * 用拉取（而不是登录时写一个 var）是为了杜绝"会话已切换、后端还是旧的"这种竞态：
 * 每次取用都按当前会话解析。
 */
internal class SessionBackends(private val session: SessionRepository) {

    private var boundKey: Bound? = null
    private var boundBackend: MusicBackend? = null

    @Synchronized
    fun current(): MusicBackend {
        val requested = Bound(
            kind = session.serverKind,
            namespace = runCatching(session::cacheNamespace).getOrNull().orEmpty(),
            revision = session.connectionRevision,
        )
        boundBackend?.takeIf { boundKey == requested }?.let { return it }
        return create(requested.kind).also { backend ->
            boundKey = requested
            boundBackend = backend
        }
    }

    private fun create(kind: ServerKind): MusicBackend = when (kind) {
        ServerKind.FnOs -> FnOsMusicBackend(session)
        ServerKind.Jellyfin -> session.requireJellyfinSession().let { jellyfin ->
            JellyfinMusicBackend(session, jellyfin.api, jellyfin.userId)
        }
    }

    private data class Bound(val kind: ServerKind, val namespace: String, val revision: Long)
}

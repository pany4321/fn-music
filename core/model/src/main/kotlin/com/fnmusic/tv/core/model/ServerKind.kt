package com.fnmusic.tv.core.model

/**
 * 音乐服务器类型。新增后端时在这里加一项，并在 `MusicBackend` 提供实现。
 * 放在 :core:model 里是因为会话身份（[ServerIdentity]）与 UI 都要知道"连的是哪种服务器"。
 */
enum class ServerKind {
    /** 飞牛音乐（fnOS）。 */
    FnOs,

    /** Jellyfin。 */
    Jellyfin,
}

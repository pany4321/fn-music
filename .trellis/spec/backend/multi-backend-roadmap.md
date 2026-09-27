# 多后端改造路线图与设计决策

面向 `feature/multi-backend` 分支（目标 1.5.0）。已完成部分见 git 历史与
`.trellis/spec/backend/jellyfin-contracts.md`（Jellyfin 实测契约）。

## 已完成

1. **契约与实测**：Jellyfin 10.10.7 的认证、Items 查询、字段映射、图片/歌词端点、
   播放 URL 形态（PlaybackInfo 不返回 URL，客户端自建直连/HLS/HTTP 三种）。
2. **Step 1a**：`MusicBackend` 接口 + `FnOsMusicBackend`（用户信息、封面 URL、直连 URL、
   播放计划、随机取歌），`MusicRepository` 注入后端；真机回归（飞牛侧）全绿。
3. **Step 2a**：Jellyfin 的 DTO/映射（专辑/歌手/风格/歌单/曲目，含 `PlaylistItemId`）、
   统一 `items(...)` 查询、`Artists/AlbumArtists`、`MusicGenres`、`Playlists/{id}/Items`、
   收藏 `POST/DELETE`、歌词 → LRC（ticks→`[mm:ss.xx]`，404 = 无歌词 → 在线兜底），单测覆盖。

## 关键设计决策（Step 1b 必须遵守，否则会返工）

**问题**：`MusicRepository` 的分页缓存（`cachedPage`）缓存的是**原始 JSON**并持久化到 Room，
再由 `transform` 映射成领域模型。如果 `MusicBackend` 直接返回领域 `Page<T>`，
缓存层就没法复用它（拿不到可持久化的原始体），只能把缓存搬进每个后端——那是最坏的结果。

**决定**：`MusicBackend` 的**目录类查询返回"可缓存的原始页"**，映射留在仓库侧：

```kotlin
/** 后端产出的原始分页：rawJson 可直接进现有响应缓存/Room，元信息用于分页与漂移校验。 */
data class RawPage(
    val rawJson: String,
    val page: Int,
    val pageSize: Int,
    val total: Int?,
    val sort: String,
)

interface MusicBackend {
    // …已完成的五项…

    /** 目录类：按 source 取一页原始数据（source 用后端无关的描述，见下）。 */
    suspend fun catalogPage(source: CatalogSource, page: Int, size: Int): RawPage
}
```

- `CatalogSource` 用**后端无关的描述**（`Playlists` / `PlaylistTracks(id)` / `Artists` /
  `ArtistTracks(id)` / `ArtistAlbums(id)` / `Albums` / `AlbumTracks(id)` / `Genres` /
  `GenreTracks(id)` / `AllTracks(sort)` / `RecentlyAdded` / `RecentTracks` / `Favorites` /
  `Search(query, kind)`），飞牛侧映射成现有 endpoint + 排序串，Jellyfin 侧映射成
  `Items?IncludeItemTypes=…&SortBy=…`。
- 仓库的 `cachedPage` 改成：`fetch = { backend.catalogPage(source, page, size).rawJson }`，
  `transform` 由**后端提供**（`suspend fun decodePage(source, rawJson): List<T>`）——
  这样一份缓存同时服务两个后端，且 Room 持久化格式不变。
- 变更类操作（createPlaylist / addToPlaylist / removeFromPlaylist / setFavorite）保持
  **领域参数**（本就是命令语义，不需要缓存）。

## 剩余步骤

### Step 1b（飞牛侧抽取，行为不变）
1. 按上面的决策改 `cachedPage`/`cachedIndex` 的取数方式，加 `CatalogSource` 与
   `RawPage`、`decodePage`。
2. 把 `MusicRepository` 里各目录方法的 endpoint+排序串搬进 `FnOsMusicBackend`，
   映射函数（`Dto.kt` 里已有的 `toDomain()`）保持不动。
3. 拆 `ServerConnector`（`FnOsConnector` 收拢现有解析/安全码/FNID/登录/恢复），
   `PlaybackCredentials` → `PlaybackAuth(apiBase, headers, cacheNamespace, ownsUrl)`，
   `PlaybackService` 的头注入与 `PlaybackRehoming` 的前缀判定随之泛化。
4. 真机跑飞牛全流程回归（行为不变），合入 `main`。

### Step 2b（Jellyfin 接线）
5. `JellyfinConnector`：`/System/Info/Public` 识别 + 登录 + 令牌持久化
   （`SecureTokenStore` payload 加 `kind` 字段，兼容老数据）+ namespace = `ServerId:UserId`。
6. 登录页：提交时先 probe 识别后端；FNID/安全码仅飞牛显示；错误文案泛化。
7. `AppContainer`/会话按 `ServerKind` 选择并注入后端实现。
8. 客户端漫游 `LocalRoamStrategy`（`SortBy=Random` 或 `/Items/{id}/InstantMix` 取种子 →
   本地 prev/current/next 游标 → 合成 `RoamWindow`），由 `capabilities.serverSideRoam` 选择；
   播放内核零改动。
9. HLS 接通：`PlaybackTrack` 带 `StreamMode`，`PlaybackController` 用已有的
   `PlaybackSource.Hls` + `media3-exoplayer-hls` 建 HLS media item。
10. Jellyfin 的歌单/收藏/最近播放接到既有缓存与 `favoriteState` 状态机上
    （`PlaylistItemId` → `Track.playlistEntryId`）。

### 收尾
11. 真机双后端串行验收（同一台设备跑飞牛与 Jellyfin 全流程）→ 合入 `main` → 发 **1.5.0**。
12. `release/1.4` 维护分支保留 1.4.x 小修；改名/换包名（音乐坞 / `com.musicdock.tv`）
    单独一次 2.0.0 发布。

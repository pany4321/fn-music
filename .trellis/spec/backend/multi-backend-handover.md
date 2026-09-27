# 多后端改造 · 交接文档（新会话从这里开始）

> 目标：把只支持飞牛音乐（fnOS）的客户端扩展成**可连接多种音乐服务器**，第二阶段先支持 **Jellyfin**。
> 本文是唯一入口，配套两份细节文档：
> - `.trellis/spec/backend/jellyfin-contracts.md` —— Jellyfin **实测**接口契约（10.10.7）
> - `.trellis/spec/backend/multi-backend-roadmap.md` —— 路线图与**关键设计决策**
>
> 凭据只放在本地忽略目录 `.trellis/local/`（含 `test-credentials.properties`、Jellyfin 探测脚本），
> **任何情况下都不得写入会提交的文件**（历史上 fnOS 凭据泄露过两次、Git 历史重写过两次）。

---

## 1. 当前状态（分支 / 版本 / 远端）

| 分支 | 用途 | 状态 |
|---|---|---|
| `main` | 主线 | 1.4.3 + CI 触发规则（`on.push.branches: [main, "release/*"]`） |
| `release/1.4` | **上个发布版的维护分支**，1.4.x 小修在这里 bump 出包 | 已推送，tag `v1.4.3` 已存在 |
| `feature/multi-backend` | 多后端工作分支（本次改造） | 已推送，最新 `c11481a`，可编译、`:app` 门禁全绿 |

版本安排（已与用户确认）：
- **1.5.0** = 多后端这一整批（骨架 + Jellyfin 接入）；`1.4.x` 只留给上个发布版的小修。
- **2.0.0** = 改名「音乐坞」+ `applicationId` 改 `com.musicdock.tv`（破坏性，用户需重装）。

---

## 2. 已批准的总方案（要点）

**接口比对结论**：飞牛与 Jellyfin 在能力层面**基本一一匹配**，只有 3 处需要设计处理：
1. **歌单删曲目**：Jellyfin 要用 `PlaylistItemId`（`DELETE /Playlists/{id}/Items?entryIds=`）→ `Track` 加 `playlistEntryId`；
2. **随机漫游**：Jellyfin 没有服务端漫游 → 改**客户端漫游策略**（`SortBy=Random` 或 `/Items/{id}/InstantMix` 取种子 + 本地 prev/current/next 游标，仍合成 `RoamWindow`，**播放内核零改动**）；
3. **歌词**：Jellyfin 需 10.9+（`/Audio/{id}/Lyrics`，404 = 无歌词）→ 转 LRC，缺歌词时走已有的在线歌词源。

**架构分层**（新增 1 个包，不做大搬家）：
```
:core:model     领域模型（少量字段泛化）
:core:data/backend  MusicBackend 接口 + BackendCapabilities + FnOs/Jellyfin 两个实现
:core:data     缓存/本地库/会话（MusicRepository 变“门面 + 缓存 + 状态”，公开签名不变）
:core:playback  播放内核（仅接上 Hls/Transcode 与 backend 提供的 URL 判定）
:app            UI + 依赖装配（按会话的 ServerKind 选择后端）
```
**命名**：显示名改「**音乐坞（MusicDock）**」；`applicationId` 改 `com.musicdock.tv`，但 **Gradle `namespace` 与源码包路径保持 `com.fnmusic.tv` 不动**（避免全库移动目录、diff 爆炸）。

---

## 3. 已完成改动（按提交）

| commit | 内容 |
|---|---|
| `1f6630b` | `.gitignore` 增加 `.trellis/local/`；新增 Jellyfin **实测契约文档** |
| `a0f1c39` | **Step 1a**：`core/data/.../backend/`（`ServerKind`、`BackendCapabilities`、`AuthSession`、`StreamMode/StreamPlan`、`MusicBackend`）+ `FnOsMusicBackend`（用户信息、封面 URL、直连 URL、播放计划、随机取歌）；`MusicRepository` 注入 backend（默认飞牛），`prepare/prepareQueue` 走 `streamPlan/directStreamUrl/artworkUrl`，`randomTrackSample` 委托后端并保留收藏状态观察 |
| `3901968` | **Step 2a-1**：`JellyfinDtos`（DTO + 曲目映射）、`JellyfinApi`（公开信息/登录/Users/Me/Items/随机/单条目 + 三种 URL：直连 `?static=true`、HLS 转码、封面 `fillWidth/fillHeight`；错误按 HTTP 状态码映射）、`JellyfinMusicBackend`（能力声明：无服务端漫游/有转码/收藏无时间排序；播放计划按 `SupportsDirectPlay`+容器白名单决定直连或 HLS）、`JellyfinMappingTest` |
| `ee7dacd` | **Step 2a-2**：`PlaylistItemId` 入 DTO；专辑/歌手/风格/歌单映射；统一 `items(...)` 查询 + `Artists/AlbumArtists`、`MusicGenres`、`Playlists/{id}/Items`、收藏 `POST/DELETE`；单测补齐 |
| `e8dbedb` | **Step 2a-3**：歌词 → LRC（`ticks → [mm:ss.xx]`、按时间排序、丢空行、`isLrc=true`，与飞牛同形）；`JellyfinApi.lyrics()` 404 返回 null 交给在线歌词；单测覆盖 |
| `4b26c3c` | CI：允许从 `release/*` 维护分支出包（已存在的 tag 会跳过，不会重复发布） |
| `c11481a` | 路线图与 **Step 1b 关键设计决策**（见下） |

**真机回归（飞牛侧，Step 1a 之后）已通过**：登录 → 首页三卡封面 → 随机漫游播放（MediaSession 显示 PLAYING 且缓冲位远超前于播放位，证明后端产出的直连 URL 正常）→ 随机歌曲/最近添加封面 → 收藏页 ✕ 与清空 → logcat 无 FATAL。

---

## 4. 环境与命令（新会话直接用）

- **JDK**：`JAVA_HOME="D:/research/android/env/jdk-21.0.12.1+1"`（必须显式设置）
- **adb**：`D:/research/android/Sdk/platform-tools/adb.exe`；真机序列号 `3B1F5VEA9BBV3NFR`（debug 包 `com.fnmusic.tv.debug`）
- **门禁**：`./gradlew :app:testSideloadDebugUnitTest :app:lintSideloadDebug`（绿）
  　另：`./gradlew :core:data:testDebugUnitTest --tests "*JellyfinMappingTest*"`
  ⚠️ `core:data` 的两个 `AppDatabaseMigrationTest` 在 **Windows 上必然失败**（Robolectric 临时路径问题，与改动无关，CI/Linux 绿）
- **构建安装**：`./gradlew :app:assembleSideloadDebug` → `adb install -r app/build/outputs/apk/sideload/debug/fn-music-tv-<版本>-sideload-debug.apk`
- **真机登录**：debug 包每次重装都会退出登录，用 **D-pad 流程**最稳：
  `23(CENTER) → input text 地址 → 4(BACK) → 20(DOWN) → 23 → text 账号 → 4 → 20 → 23 → text 密码 → 4 → 20×3 → 23`
  （输入框在编辑态会吞方向键：**必须先 BACK 退出编辑**再按 DOWN；BACK 按多了会退出 App）
- **TV 模拟器**：AVD `tv_36`（Android TV API 36）存在，但本机启动后一直 `offline`（进程仅 12MB，疑似图形/加速问题），**不要依赖它**，真机为准。
- **Jellyfin 探测脚本**：`.trellis/local/probe_jellyfin.py`、`.trellis/local/probe_playback.py`（只读 + 一次登录，不改服务器数据）

---

## 5. 剩余任务（按顺序执行）

### Step 1b —— 飞牛侧抽取（行为不变，真机回归后合 main）
1. **按 `RawPage`/`CatalogSource` 决策改造取数**（见 `.trellis/spec/backend/multi-backend-roadmap.md` 第「关键设计决策」节）：
   `MusicBackend` 的目录查询返回 `RawPage(rawJson, page, pageSize, total, sort)`，`decodePage(source, rawJson)` 由后端提供；
   `MusicRepository.cachedPage` 改成 `fetch = backend.catalogPage(source,…).rawJson` —— **一份缓存同时服务两个后端，Room 持久化格式不变**。
   ⚠️ 这是本改造最关键的一步，不要改成"后端直接返回领域 Page"（那样缓存只能复制进每个后端）。
2. 把 `MusicRepository` 里各目录方法的 endpoint + 排序串搬进 `FnOsMusicBackend`（映射函数 `Dto.kt` 的 `toDomain()` 不动），逐个方法保持行为一致。
3. 拆 `ServerConnector`：`FnOsConnector` 收拢现有 `ConnectionResolver`/安全码/FNID/登录/恢复路径；
   `PlaybackCredentials` → `PlaybackAuth(apiBase, headers, cacheNamespace, ownsUrl)`；
   `PlaybackService` 头注入与 `PlaybackRehoming`（现在硬编码 `/music/api/v1/` 前缀）随之泛化。
4. 真机跑飞牛全流程回归（登录/首页四行/播放/漫游/随机行封面与红心/收藏页/歌单 CRUD/搜索/主题缩放）→ 合入 `main`。

### Step 2b —— Jellyfin 接线
5. `JellyfinConnector`：`GET /System/Info/Public` 识别 + `POST /Users/AuthenticateByName` 登录 +
   令牌持久化（`SecureTokenStore` payload 加 `kind` 字段，兼容老数据）+ namespace 用 `ServerId:UserId`。
6. 登录页：地址提交时先 probe 识别后端（命中 Public Info 即 Jellyfin，页面显示"已识别 Jellyfin 10.x"）；
   FNID/安全码字段仅飞牛显示；错误文案泛化（"NAS 暂时不可用"→"服务器暂时不可用"）。
7. `AppContainer`/会话按 `ServerKind` 构造并注入 `FnOsMusicBackend` 或 `JellyfinMusicBackend`。
8. **客户端漫游** `LocalRoamStrategy`：`SortBy=Random` 或 `/Items/{id}/InstantMix` 取种子 →
   本地 prev/current/next 游标 → 合成 `RoamWindow`；由 `capabilities.serverSideRoam` 选择实现，播放内核零改动。
9. **HLS 接通**：`PlaybackTrack` 带 `StreamMode`，`PlaybackController` 用已有的 `PlaybackSource.Hls` +
   `media3-exoplayer-hls`（依赖已在）建 HLS media item。
10. Jellyfin 的歌单/收藏/最近播放接到既有缓存与 `favoriteState` 状态机上
    （`PlaylistItemId` → `Track.playlistEntryId`；收藏列表用 `Filters=IsFavorite`，最近播放用 `Filters=IsPlayed&SortBy=DatePlayed`，均已实测）。

### 收尾
11. **真机双后端串行验收**（同一台设备先飞牛后 Jellyfin，跑全流程）→ 合入 `main` → 发 **1.5.0**（CHANGELOG + 版本号 +1 + push，CI 自动出 Release）。
12. 之后再单独做 **2.0.0**：改名「音乐坞」+ `applicationId com.musicdock.tv` + 同步 CI 产物名/更新清单/UA/README + 旧包最后一次发布里写明"请安装新版音乐坞"（换包名后旧版无法自更新）。

---

## 6. Jellyfin 手工验证要点（用本地测试服务器）

- 初始快照：27577 首 / 881 位专辑艺术家 / 79 个风格 / **0 歌单 / 0 收藏**
  → 验证歌单与收藏时会**新建测试歌单、收藏若干曲目**（可逆），验完清理并告知用户。
- 播放三种形态都实测可用：直连 `/Audio/{id}/stream?static=true&api_key=`（audio/flac）、
  HLS 转码（video/mp2t）、HTTP mp3 转码（audio/mpeg）；**PlaybackInfo 不返回 URL，必须客户端自建**。
- 歌词端点 404 = 该曲无歌词（不是错误）。

---

## 7. 风险与回滚

- **Step 1b 是行为不变重构**：真机回归必须全绿再合 main；出问题可直接回到 `a0f1c39` 之前的 `main`。
- **Jellyfin 版本差异**：删条目参数（`entryIds`）、InstantMix 参数、歌词端点可用性都要**以实测为准**（本地脚本已备好）。
- **换包名不可逆**：旧包用户需重装 → 单独一次 2.0.0 发布 + Release 说明。
- 若 Jellyfin 上线后出问题：可按 `capabilities` 在登录页隐藏 Jellyfin 入口，不必回滚代码。

---

## 8. 提交前必做

用本地凭据文件里的真实值去比对暂存区内容（**不要把任何明文凭据写进入库文件，包括这条命令本身**）：

```bash
# 取本地凭据里的值（地址/账号/密码）作为匹配模式，逐个比对暂存区
git diff --cached > /tmp/staged.patch
cut -d= -f2- .trellis/local/test-credentials.properties \
  | grep -vE '^\s*(#|$)' | while read -r secret; do
      [ -n "$secret" ] && grep -qiF -- "$secret" /tmp/staged.patch && echo "命中凭据：$secret"
  done
rm -f /tmp/staged.patch
```
输出为空才算干净。凭据历史上泄露过两次、Git 历史重写过两次，这条检查不要省。

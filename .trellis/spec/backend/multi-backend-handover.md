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
| `main` | 主线 | **1.5.0（多后端骨架）/ 1.5.1（Jellyfin 接入）已发布** + CI 触发规则（`on.push.branches: [main, "release/*"]`） |
| `release/1.4` | **上个发布版的维护分支**，1.4.x 小修在这里 bump 出包 | 已推送，tag `v1.4.3` 已存在 |
| `feature/multi-backend` | 多后端工作分支（本次改造） | 已推送并与 `main` 同步（`ff1976e`）；Step 2b 从这里继续 |

版本安排（已与用户确认）：
- **1.5.0** = 多后端（Multibackend）第一步：后端分层骨架（行为不变，已发布，见 §5）；
- **1.5.1** = Jellyfin 实际接入（登录识别 + 客户端漫游 + 转码/HLS），在 1.5.0 基础上升版；
- `1.4.x` 只留给上个发布版的维护分支小修。
- **1.7.0** = 显示名已改「**音乐坞（MusicDock）**」，`applicationId` 仍是 `com.fnmusic.tv`，
  老用户可直接覆盖升级。**2.0.0 只在真要换包名时才做**（`com.musicdock.tv`，破坏性、必须重装）。

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
**命名**：显示名「**音乐坞（MusicDock）**」——**1.7.0 已落地**（`app_name`、启动页、关于页、
更新提示、UA、README/文档一起改）；`applicationId` 仍是 `com.fnmusic.tv`。若将来真要换包名，
`namespace` 与源码包路径也应保持 `com.fnmusic.tv` 不动（避免全库移动目录）。

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
| `4086a86` | `.gitignore` 忽略 `.zcode/`（会话/计划草稿里会写测试服务器地址） |
| `c2bffa3` | **Step 1b**（详见 §3.1） |
| `9f8aef7`+`05cb0ef` | 版本改名 1.5.0（多后端第一步）+ 删除被取代的 v1.4.4 |
| `27a63a7` | **Step 2b**：Jellyfin 接线（会话层泛化 + 全量 Jellyfin 后端 + 客户端漫游 + HLS + 登录识别），详见 §3.2 |
| `c2bffa3` | **Step 1b**：飞牛侧抽取（行为不变）——`RawPage/RawIndex/DecodedPage` + `CatalogPageSource/CatalogIndexSource`（目录查询返回可缓存原始体、后端负责解码），`MusicRepository.cachedPage/cachedIndex` 改为吃原始体；`FnOsMusicBackend` 收拢全部飞牛 endpoint/排序串/DTO 映射；新增 `ServerConnector`+`FnOsConnector`；`PlaybackCredentials` → `PlaybackAuth(apiBase, headers, cacheNamespace, streamPathPrefix)`，`PlaybackRehoming` 前缀随之下发。详见 §3.1 |

### 3.1 Step 1b 落地细节（Step 2b 会用到）

- **缓存契约**：`MusicBackend.catalogPage/catalogIndex` 返回 `RawPage.rawJson`（飞牛侧就是 `ApiDecoder.json.encodeToString(DTO)`，
  与抽取前写进缓存的内容**逐字一致** → 老的 Room 行继续命中）；`decodePage/decodeIndex` 由后端把原始体解成领域模型。
  `CatalogPageSource` 的 `cacheKey` 与历史 sourceKey 相同，`sizeSensitive=true` 的来源（artists/albums/all-tracks/recently-added）
  仍按 `sizedPageSourceKey` 编页大小进键。
- **接口全量**：目录（分页/索引）、搜索、收藏列表/最近播放、歌单曲目数、收藏与歌单变更、歌词原始体+解码、漫游三件套、
  封面字节下载都在 `MusicBackend` 上；`MusicRepository` 里已无任何飞牛 endpoint 与 DTO。
- **`JellyfinMusicBackend` 目前是 pending 桩**（显式抛 `jellyfin_*_pending`，不会静默返回空数据）：
  `catalogPage/decodePage/catalogIndex/decodeIndex/searchTracks/searchArtists/searchAlbums/favoriteTracks/recentTracks/
  playlistTrackCounts/setFavorite/createPlaylist/addToPlaylist/removeFromPlaylist/lyricsRaw/decodeLyrics/startRoam/nextRoam/previousRoam`
  —— 这些都要在 Step 2b 补齐（现在会话只可能是飞牛，不会走到）。
- **`ServerConnection` 还带 `TrimMusicApi`**：Step 1b 只把"连接方式"抽到 `FnOsConnector`，会话状态机仍直接持有飞牛客户端。
  Step 2b 引入 `JellyfinConnector` 时要把它改成按 `ServerKind` 分叉的连接对象（各后端自带客户端类型），
  `SessionRepository` 的 `requireApi()/authenticated{}` 需同步泛化。
- **`streamPathPrefix`**：`PlaybackAuth` 里的字符串前缀（飞牛 `/music/api/v1/`，来自 `ServerUrlNormalizer.API_PATH_PREFIX`）
  是重挂判定的唯一依据（Media3 的 Bundle 传不了判定函数）；为空则完全不重写 URL。
- **`PlaybackAuth.headers` 由后端组装**：飞牛的 `Authorization`/Cookie(`music-token`+`mode=relay`)/`x-access-code` 就是
  原来的 `playbackRequestHeaders`（已搬到 `FnOsConnector.playbackHeaders`，`FnOsConnectorTest` 覆盖）；
  播放服务只用 `Bundle` 的两个平行数组搬运 Map（`PlaybackCommands.putRequestHeaders/requestHeaders`）。

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

### Step 1b —— 飞牛侧抽取 ✅ 已完成（`c2bffa3`，行为不变）
1. ✅ `RawPage/RawIndex/DecodedPage` + `CatalogPageSource/CatalogIndexSource` 落地，`cachedPage/cachedIndex`
   改成 `fetch = backend.catalogPage(...).rawJson`（**一份缓存同时服务两个后端，Room 格式与缓存键都没变**）。
2. ✅ 各目录方法的 endpoint + 排序串全部搬进 `FnOsMusicBackend`（含搜索/收藏/最近播放/歌单曲目数/歌词/漫游/封面字节/变更操作），
   `MusicRepository` 里已无飞牛接口与 DTO。
3. ✅ `ServerConnector` + `FnOsConnector` 拆出；`PlaybackCredentials` → `PlaybackAuth(apiBase, headers, cacheNamespace, streamPathPrefix)`；
   `PlaybackService` 的头注入与 `PlaybackRehoming` 前缀判定随之泛化（细节见 §3.1）。
4. ✅ **真机回归（飞牛侧）已全绿**：门禁（`:app` 单测 115 + lint 通过；`core:data` 130 中仅 Windows 必失败的
   `AppDatabaseMigrationTest` 两例；`core:playback` 47 全绿）。真机（3B1F5VEA9BBV3NFR，`1.4.4-debug (55)`）逐项验过：
   - **全新登录**：卸载重装（清数据）→ 走 D-pad 填地址/账号/密码 → 登录直达首页（`FnOsConnector.connect` + `login` + `me` 全链路）；
   - **首页四行**（歌单索引/随机专辑/随机歌曲/最近添加）与三张卡片封面、我的页（用户信息 + 歌手行 + 歌手详情页）；
   - **搜索**：无结果文案 + 命中歌手（Bandari 186 首）与歌曲行；**最近播放页** 7 首（`recentTracks`）；
   - **收藏读写**：播放页 ♡ 收藏（30 → 31，收藏页置顶可见）→ 收藏页 ✕ 取消（31 → 30，恢复原状）；
   - **歌单读写**：加到歌单（black 1 → 2 首）→ 歌单页 ✕ 删曲目（2 → 1 首，恢复原状）；**新建歌单** ZCode-Test-1.4.4（含当前曲目）；
   - **设置页**：界面缩放改「较大」立即生效 → 改回「自动」；缓存用量读数、关于页版本号正常；
   - **播放/漫游/歌词**：直连 URL + 头注入（MediaSession PLAYING、缓冲远超播放位、FLAC 徽标）、服务端漫游 start/next、无歌词曲目占位；全程无 FATAL。
5. ✅ 已合入 `main`；版本**按用户要求直接定为 `1.5.0`（Multibackend）= 多后端第一步**（原计划的 1.4.4 不再单独发）。
   CI Release `v1.5.0` 已确认（含 universal APK + sha256）；**已被取代的 `v1.4.4` Release 与 tag 已按用户要求删除**。
   Jellyfin 实际接入完成后在本基础上再升版（**1.5.1**）。

> ⚠️ **待清理（用户侧）**：验证新建歌单时留下一个测试歌单 **`ZCode-Test-1.4.4`**（含 1 首 Bandari 曲目）。
> App 没有删除歌单的接口，需要在 NAS/飞牛客户端里手动删掉；同页还有更早会话留下的 `ZCode-Test3`。
> 收藏与 `black` 歌单都已恢复到验证前的状态。


### 3.2 Step 2b 落地细节（Jellyfin 接入）

- **会话层**：`ServerKind` 移到 `:core:model`（`ServerIdentity` 带 kind）；登录档案加 `kind` 字段
  （字符串 + 默认值，`SecureSessionPayload.version` 仍是 1，老数据自动按飞牛解释）；
  `ServerConnection` 改成 sealed（FnOs 带 `TrimMusicApi`+安全码，Jellyfin 带 `JellyfinApi`+userId）；
  `ServerConnector` 增加 `probe/connect(ConnectRequest)/login/reLogin/me/logout`；
  `SessionBackends` 按"当前会话的 kind"解析后端并复用实例（Jellyfin 的漫游游标要跨调用保持）。
- **登录识别**：`SessionRepository.probeServer` 命中 `/System/Info/Public` 即 Jellyfin（FNID 与探测失败都按飞牛兜底）；
  探测客户端用 **3 秒超时**，避免在飞牛地址上拖慢登录。地址没写端口时补试 8096/8920。
- **Jellyfin 鉴权差异**：没有服务端密码哈希 → 免密重登只能靠 AccessToken（`reLogin` 返回 null，
  用户重新输密码）；播放流地址自带 `api_key`，另外注入 `MediaBrowser` 头。
- **目录映射**：`CatalogPageSource/CatalogIndexSource` → Items/Artists/AlbumArtists/MusicGenres/
  Playlists/{id}/Items/Views；每个来源固定排序键（后端不透明游标）。rawJson 仍是可缓存的原始响应。
- **客户端漫游**：`startRoam` 取一批 `SortBy=Random` 种子（30 首），本地 prev/current/next 游标；
  越界时**换一批并排除当前曲目**（否则 roamId 不变会让播放内核报 `CollectionChanged`）。
- **HLS**：`PlaybackTrack.streamMode` → Media3 显式 `setMimeType(application/x-mpegURL)`；
  快照持久化流形态（冷启动续播不丢 HLS 标记）；队列装填走 `queueStreamPlan`
  （Jellyfin 按条目已知容器白光名单决定直连/HLS，不逐首协商）。

#### 实测踩坑（Jellyfin 10.10.7，务必按这个来）

| 坑 | 现象 | 正确做法 |
|---|---|---|
| HLS 端点选错 | `/Audio/{id}/stream?...transcodingProtocol=hls` 返回**裸 TS 流**（`video/mp2t`，0x47 开头），ExoPlayer 报 "Input does not start with the #EXTM3U header" | 打 **`/Audio/{id}/master.m3u8`**，带 `MediaSourceId`（缺了直接 400）+ 转码参数；鉴权走请求头，**不要** `api_key` |
| **空 userId 拼出 `/Users//…`** | 令牌恢复/跨源切换建起来的连接是"先探测、后凭令牌"的，那时还没有 userId：`/Items?userId=` 照样能用（服务端按令牌解析用户），但**路径式**接口 `/Users//FavoriteItems/{id}` 直接 **404** —— 真机表现就是"点收藏提示收藏失败" | `JellyfinApi` 统一兜底：空 userId 先问 **`/Users/Me`** 并缓存（登录响应里的 id 也缓存），所有带 userId 的接口过 `uid()`；`JellyfinConnector.me()` 顺手把解析到的 id 回填到连接上 |
| 新建歌单返回体 | `POST /Playlists` 只回 `{"Id":"…"}`，按完整条目解会得到空名字 | 名字用调用方传进去的那个，别解返回体 |
| 歌手/风格没有计数 | `/Artists/AlbumArtists`、`/MusicGenres` 不返回 `ChildCount/RecursiveItemCount` | 计数缺失时 UI 不显示（别显示"0 首歌曲"） |
| 歌单混着视频 | `IncludeItemTypes=Playlist` 会把"电影/电视剧"这类视频歌单也列出来 | 按条目 `MediaType == Audio` 过滤 |
| 删歌单条目 | `DELETE /Playlists/{id}/Items?entryIds=` 要的是 **PlaylistItemId** 不是曲目 id | `Track.playlistEntryId`（歌单页取回时自带；缺失则回源查一次） |

#### Step 2b 验证状态

- **门禁**：`:app` 单测 + lint 通过；`core:data` 132（仅 Windows 必失败的迁移两例）；`core:playback` 49 全绿。
- **1.5.2（空封面兜底）视觉核对待补**：首页三张卡片空数据时改画主题插画（收藏=心形、最近播放=时钟、
  随机漫游=黑胶），歌单卡片/详情页改用「歌单」拼贴；**只跑了编译与门禁，没截图核对**（手机息屏锁定 + debug 包未装回）。
- **真机（Jellyfin 侧，已验证）**：地址识别（回显"已识别 Jellyfin 10.10.7"）+ 全新登录 + 重装后会话恢复；
  首页三卡片与歌单/随机专辑/随机歌曲/最近添加四行；我的页（服务器名、歌手行、风格行）；搜索（专辑/歌曲）；
  直连播放（FLAC 徽标、缓冲正常）；**客户端漫游 start/next/previous**（含边界换批）；歌词（服务端/在线，含双语）；
  收藏页空态。**接口层验证**（真实服务器，跑完清理干净）：收藏 create/read/delete、歌单 create/add/删条目/删歌单、
  歌单批量计数、`/Audio/{id}/master.m3u8` 返回合法播放列表。
- **真机（Jellyfin 侧，未验证）**：HLS 修复后的**端到端**播放（修复前的尝试已经证明播放器走了 HLS 解析器，
  修复本身用真实服务器 + 单测锁定）；收藏 ♡ 与歌单新建在 App 内的点击链路（控制器/弹窗的 D-pad 命中率问题，
  与飞牛侧踩的是同一个坑）。
- **真机（飞牛侧，本版后未复验）**：Step 2b 改了会话层与后端选择，飞牛侧代码路径是"形状变化"（连接器抽取、
  按 kind 取后端、`PlaybackAuth`、`streamMode=Direct`），单测（`SessionRepositoryTest`/`FnOsConnectorTest`/
  `MusicRepository*Test`）全绿，但**登录一件与全流程回归没在真机跑完**——下次真机验收要补。
- **观察（未定因）**：飞牛档案在某次 `adb install -r` 后从登录历史里消失（登录页回到空表单），
  同期 Jellyfin 档案跨多次安装都保持；没找到确定原因，下次真机验收留意是否复现。

#### 1.5.2 空封面兜底（已实现 + 真机截图核对）

- 首页「收藏 / 最近播放 / 随机漫游」在**没有可摊封面**时，摊位上画对应主题插画
  （心形 / 时钟 / 黑胶）——实现点在 `FeatureCoverDeck`（`app/.../ui/AuthenticatedApp.kt`），
  `deckPlacements(kind, artwork.size.coerceAtLeast(1))` + `item == null` 分支。
- 歌单卡片与歌单详情页：既没有自身封面、也拼不出派生封面组时用「歌单」拼贴插画
  （`CollectionArtworkFallback.PlaylistGrid`）；「最近播放」详情页兜底改用
  `CollectionArtworkFallback.Recent`（时钟）。
- **真机核对（1.5.2，Jellyfin 侧 0 收藏）**：收藏卡显示心形插画 ✓、空歌单卡片显示拼贴插画 ✓
  （截图已核对，临时截图不入库）。发布：v1.5.2（CI Release 含 universal APK）。
- 顺带核实：**飞牛地址的 `/System/Info/Public` 返回 404** → 登录页探测会立刻按飞牛兜底 ✓；
  Jellyfin 服务器上我早前用 API 建的 3 个空测试歌单**已删除**（`DELETE /Items/{id}` 才是正确接口，
  `DELETE /Playlists/{id}` 返回 405），服务器歌单回到用户自己的状态。
- ⚠️ 注意：Jellyfin 的 `IncludeItemTypes=Playlist` 在 `Recursive=false` 时会返回**媒体库目录**
  （CollectionFolder，如「电影/电视剧」），**必须 `Recursive=true`** 才是真歌单（当前实现是对的）。

#### 仍未在真机验证（本轮受自动化限制）

- **飞牛登录 + 全流程回归**：登录页在 adb 下难以走完 —— 输入框 IME 与 BACK 的组合会让页面直接退回
  （`input text` 之后按 BACK 有时被当成"退出页面"而不是"收起键盘"）。人工点按没问题；
  真机确认时用真手或按"点字段→输入→点下一个字段"的方式逐步截图确认。
- **HLS 端到端**：URL 修复已用真实服务器验证 + 单测锁定；端到端播放（dsf 曲目）还没在 1.5.1/1.5.2 上复跑。
- **App 内收藏 ♡ / 歌单新建的点击链路**：`JellyfinApi.setFavorite` 与歌单读写已在真实服务器上
  用 App 的查询形状验证；播放器控制条在 adb 下点不中（自动隐藏 + 无 UI Automator 语义），
  人工点一次即可确认。

### 3.3 多音乐源（1.6.0）与一轮批判性审核

#### 多源管理（用户要求的形态）

- **术语**：用户界面统一叫「**音乐源**」（不是"播放源"）——后者容易被理解成"同一首歌的直连/转码线路"，
  而这个概念管的是"音乐从哪台服务器来"。代码里仍是 `StoredLoginProfile` / `LoginHistoryEntry`。
- **首次启动/添加源**：登录页顶部先选类型（飞牛音乐 / Jellyfin）→ 再填地址、账号、密码（安全码/FNID 提示仅飞牛显示）
  → 登录即完成添加，并成为当前源。类型由用户显式选择后**不再探测**，失败时探一次并提示"这台服务器看起来是 X"。
- **切换源**：「我的」页「切换音乐源」→ `SourcePickerDialog`（`app/.../ui/SourcePicker.kt`）弹出来源列表，
  点一行即 `switchAccountWithHistory`（飞牛用保存的密码哈希、Jellyfin 用访问令牌续期，都不用重新输密码）；
  可删除单个源；「＋添加音乐源」复用登录页表单。
- **设置页**：「音乐源（N）」区块 + 「管理与切换」/「＋添加」（同一个弹窗复用）。
- **数据**：`StoredLoginProfile` 增加 `serverName`（登录成功后回填服务器自报名，源列表用它做标题，
  缺省时回退到类型名）；`LoginHistoryEntry` 带上 `kind`/`serverName`；`SessionRepository.activeProfileId`
  暴露给 UI 标记"当前"。同一个服务器可以存多个账号（档案按 server+username+kind 去重）。

#### 批判性审核发现并修掉的问题

| 严重度 | 问题 | 处理 |
|---|---|---|
| 高 | **跨源切换必然失败**：`loginWithHistory` 的令牌续期路径用 `memoryToken`（当前会话的令牌）去验目标源 → 401 → 弹回登录页 | 临时把 `memoryToken` 换成目标源的令牌再验 `me()`，验不过还原（当前会话不受影响）。真机验证双向切换 ✓ |
| 中 | **访问令牌进 URL**：Jellyfin 的直连/HLS/封面 URL 都带 `api_key`，而 URL 会被**持久化进播放队列快照（Room 未加密）**与系统通知元数据 | 去掉所有 `api_key`，改走 `Authorization` 头（实测图片/流/播放列表都认头 ✓）；真机复验直连与 HLS 播放 0 错误 ✓ |
| 中 | **列表页拉了用不上的重字段**：每页 50 条都取 `MediaSources`（KB 级/条） | 列表查询改用 `LIST_ITEM_FIELDS`（不含 MediaSources），单条目查询用 `FULL_ITEM_FIELDS` |
| 低 | 每次登录重复探测服务器类型 | `probeServer` 结果按 (useHttps, 地址) 缓存；显式选类型时根本不探 |
| 低 | 歌单/歌手没有计数时 UI 显示"0 首歌曲" | 计数缺失即留空（1.5.2 已修） |
| 低 | 「切换音乐源」按钮宽度不够（5 字被截断） | 加宽到 134dp |
| — | 快照兼容性（新增 `streamMode` 字段） | 已核实：`PlaybackSnapshotCodec` 用 `opt*` 读键、版本号仅校验结构，新老快照互通 ✓ |

#### 1.6.0 真机走测（两后端）

- **Jellyfin**：首次添加源（选类型→参数→登录）✓、切换源 ✓、设置页入口 ✓、首页三卡片与四行 ✓、搜索 ✓、
  播放直连（PLAYING、0 错误）✓、**HLS（DSF 曲目 → master.m3u8，PLAYING、0 解析错误）**✓、收藏空态兜底 ✓。
- **飞牛**：用切换音乐源切过去 ✓、首页（收藏/最近播放行）✓、播放 ✓。
- 全程无 FATAL；仅系统通知取封面时 401（既有行为，非本次引入）。

#### 真机环境坑（接续会话必读）

- **手机锁屏时 `adb install` 必然失败**（`Failure [-99]`，ColorOS 要弹安装确认但锁屏下弹不出来）。
  实测：`mWakefulness=Dozing` / 锁屏 → 一律 -99；先 `input keyevent 26` 唤醒并**解锁**再装。
  `adb shell svc power stayon true` 可以让屏幕在充电时不熄灭，减少踩这个坑。
- **当前设备状态（2026-09-28 00:30）**：为了拿到"干净登录页"做了卸载重装，卸载成功但重装被锁屏拦住，
  所以 **debug 包 `com.fnmusic.tv.debug` 目前不在手机上**（release 包 `com.fnmusic.tv` 未动）。
  解锁手机后在仓库根执行：
  `adb install app/build/outputs/apk/sideload/debug/fn-music-tv-1.5.1-sideload-debug.apk`
  （或直接下 GitHub Release 的 1.5.1 universal APK）即可恢复。
- **登录页 D-pad 两个坑**（这次在飞牛登录上卡了很久）：① 输入框不在编辑态时按 BACK 会**直接退出登录页**；
  ② 登录按钮在 `credentialsReady=false` 时是 disabled，此时从 HTTPS 按 DOWN **不会移动焦点**，
  再按 CENTER 会把 HTTPS 开关打开。填完表单后建议先截图确认焦点在「登录」再按确认。

### Step 2b —— Jellyfin 接线 ✅ 已完成（`27a63a7` + HLS/文案修复）
6. ✅ `JellyfinConnector`：识别 + 登录 + 令牌持久化都做了（`kind` 字段兼容老数据；namespace 仍用
   `serverGUID:userGUID`，Jellyfin 侧 serverGUID 取 `Public Info` 的 `Id`）；差异见 §3.2。
7. ✅ 登录页提交时先 probe 并回显"已识别 Jellyfin x.y.z"；识别为 Jellyfin 时隐藏安全码栏、抬头改为 Jellyfin；
   文案泛化（NAS → 服务器）。
8. ✅ 没走"AppContainer 按 kind 构造"这条路：改成 `SessionBackends` 按当前会话解析后端（`MusicRepository.backend`
   变成按会话拉取），避免"会话切了后端还是旧的"竞态，App 侧零改动。
9. ✅ 客户端漫游落在 `JellyfinMusicBackend` 里（随机种子 + 本地游标 + 越界换批），仍合成 `RoamWindow`；
   用的是 `SortBy=Random`（`InstantMix` 留作后续可选优化）。
10. ✅ HLS 接通（`PlaybackTrack.streamMode` + 显式 m3u8 MIME + 快照持久化）；端点必须用 `master.m3u8`（见 §3.2 坑表）。
11. ✅ 歌单/收藏/最近播放都接到既有缓存与 `favoriteState` 上；`Track.playlistEntryId` 落地并用于删条目。
12. ✅ 已落地：`probe` 在 `SessionConnector` 层实现（`SessionRepository.probeServer`）、`Track.playlistEntryId`、
   `ServerConnection` 按 kind 分叉。提醒：
    `MusicBackend` 增加 `probe(origin): ServerIdentity?`（登录页识别类型/版本用）；
    `Track` 增加 `playlistEntryId: String?`；`ServerConnection` 改成按 `ServerKind` 分叉
    （现在直接带 `TrimMusicApi`），`SessionRepository.requireApi()/authenticated{}` 同步泛化；
    `QueueSource.sort` 按"后端不透明游标"的语义写注释（飞牛沿用现有排序串，Jellyfin 用自家键）。

### 收尾
13. ⏳ **真机双后端串行验收**：Jellyfin 侧已跑（见 §3.2 验证状态），飞牛侧在本版改动后**只差登录 + 全流程复验**
   （已知踩点在 §3.2：登录页 BACK 会退出页面、登录按钮 disabled 时焦点不移动）。发版：**1.5.1**。
14. ✅ 显示名已在 **1.7.0** 改成「音乐坞」且包名不变；CI 产物名 `fn-music-tv-*`、更新清单包名校验、
    签名别名都保持原样，所以升级路径不变。**2.0.0**（换包名 `com.musicdock.tv`）仅在确有需要时再做，
    届时要同步 CI 产物名/更新清单/UA/README，并在旧包最后一次发布里写明"请安装新版音乐坞"。

### 3.4 用户反馈的两个缺陷（1.6.2）

| 现象 | 根因 | 修复 |
|---|---|---|
| **点收藏提示"收藏失败"**（Jellyfin；浏览/播放都正常） | 连接是"先探测、后凭令牌"建的（重装/重启后的令牌恢复、跨源切换都是这条路径），那时 `userId` 还是空：`/Items?userId=` 照样能用（服务端按令牌认人），但**路径式**接口 `/Users//FavoriteItems/{id}` 直接 404。日志一行锁定：`HTTP 404 POST /Users//FavoriteItems/{id}` | `JellyfinApi.uid()`：空 userId 先 `/Users/Me` 再缓存（登录响应里的 id 也缓存），所有带 userId 的接口统一过它；`JellyfinConnector.me()` 顺手回填连接上的 userId。**真机验证**：令牌恢复会话下点收藏 → 服务端 `IsFavorite` False→True、无 404，再点一次 → True→False（服务端状态已还原） |
| **音乐源窗口按钮文字"跑到按钮外"**（手机上） | 源行按钮是胶囊形（`LoginActionButton` 默认 `RoundedCornerShape(50)`，80dp 高 = 40dp 圆角），而行内文字**没有横向内边距**：首字正好压在圆角的弧线上，视觉上像出界 | 源行内容加 `padding(horizontal = 22.dp)`。设置页那两个按钮（12sp 标签）本身没问题，一并核对过 ✓ |

### 3.5 1.7.0：音乐源管理增强 / 封面与随机修复 / 改名

| 主题 | 内容 |
|---|---|
| **Jellyfin 四处缺封面** | 根因是 `LIST_ITEM_FIELDS` **从没请求过 `ImageTags`**，而所有 `coverId` 只由 `ImageTags.Primary` 决定 → 随机漫游 / 全部歌单 / 随机歌曲 / 最近添加全都没有封面，歌单的"前三首拼排"也因为曲目没有 coverId 而永远拼不出来。现在列表字段带上 `ImageTags`（体积可忽略），单条目字段自动继承。 |
| **随机不够随机** | 飞牛只能按分页随机挑页，而页内是按添加时间排好序的（一页 24 首常常是同一张专辑），连续刷新还容易撞回同一页。现在：飞牛取**两个不同随机页**合并后洗牌；随机专辑同理（并避开上一次的页号）；两侧都加 `spreadByKey` 把相邻同专辑/同歌手的条目拆开；首页"随机漫游/全部歌单"卡片的封面 deck 也从"只在缓存为空时采样一次"改成**冷启动先用上次那组出画、随后重采样上屏**。工具函数在 `core/data/.../backend/RandomSampling.kt`。 |
| **音乐源连通性测试** | 新增 `SessionRepository.testSource(profileId)`：用**该源自己的令牌**临时造 connector 与 API，`connect()` 验地址/类型/安全码、`me()` 验令牌，飞牛令牌过期但存了密码哈希时用 `reLogin` 复验；**全程不碰当前会话**（不改 memoryToken/connection/state），6 秒总超时。返回 `SourceTestResult`（Ok / CredentialsExpired / Failed）→ 弹窗里行下方的状态行 + Toast。 |
| **连接音乐源页文案与提示** | 抬头从「登录」改成「连接音乐源」、按钮改成「连接/正在连接」（同一页既要服务首次安装，也要服务应用内新增）。连接成功弹 Toast「已连接：X」并**显式回退该页**（原来靠 `user.guid` 变化重置路由，重新连同一个账号时页面不会关）；失败除了页内状态行再多一个 Toast。顺带修掉 `switchAccountTo` **丢掉 `kind` 参数**的真 bug（显式选的类型曾被自动探测覆盖）。 |
| **登录页「已有音乐源」弹窗** | 原来的自绘弹窗不会自适应宽度、也没有滚动（条目一多就移不动）。现在与「我的」/设置页共用 `SourcePickerDialog`（自适应宽度 + 滚动 + 删除 + **测试**）；1.4.x 遗留的"最近用过的地址"仍以次级列表保留（点一条只回填地址）。交互契约与 `LoginHistorySmokeTest` 已同步。 |
| **静态兜底图** | 卡片空态从 Canvas 插画改为静态图：`app/src/main/res/drawable-nodpi/cover_fallback_{roam,favorites,recent,playlist}.png`（1024×1024，用户提供，`HomeFeatureArtwork` 里走 `painterResource` + `ContentScale.Crop`；`Collection` 黑胶仍用 Canvas 画）。 |
| **改名** | 显示名改「音乐坞（MusicDock）」：`app_name`、启动页、关于页、更新提示、播放器/歌词 UA、README 与文档、`rootProject.name`。**不动**：`applicationId`/`namespace`、签名别名与目录、CI 产物名、更新清单包名校验、`sourceKindLabel(FnOs)="飞牛音乐"`（那是服务器类型名）。 |

#### 已知问题（本轮未修，与改动无关）

- `AppDatabaseMigrationTest` 的两条迁移测试会**间歇性失败**：`SupportSQLiteDriver` 报
  "configured to open a database named 'migration-test.db' but '<绝对路径>' was requested"。
  证据：干净 HEAD（1.6.1 发布版本）上同样失败、单独跑该类的同样失败、room/sqlite 无版本混用；
  同一天早些时候同一命令曾全绿 → 环境性 flake（Robolectric + Room `MigrationTestHelper`）。
  CI 只跑 `:app:assembleSideloadRelease`，不跑单测，所以不影响发布。下次真机/单测验收时若仍是红的，
  值得单独查（候选：给该测试加 `robolectric.sqliteMode` 配置）。

---

## 6. Jellyfin 手工验证要点（用本地测试服务器）

- 初始快照：27577 首 / 881 位专辑艺术家 / 79 个风格 / **0 歌单 / 0 收藏**
  → 验证歌单与收藏时会**新建测试歌单、收藏若干曲目**（可逆），验完清理并告知用户。
- 播放三种形态都实测可用：直连 `/Audio/{id}/stream?static=true&api_key=`（audio/flac）、
  HLS 转码（video/mp2t）、HTTP mp3 转码（audio/mpeg）；**PlaybackInfo 不返回 URL，必须客户端自建**。
- 歌词端点 404 = 该曲无歌词（不是错误）。

---

## 7. 风险与回滚

- **Step 1b 是行为不变重构**：真机回归（主要通路）已通过；出问题可直接 `git revert c2bffa3`（或回到 `e1530ec`）。
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

---

## 9. 既定规则与限制条件（必读，违反会返工或出事）

### 9.1 仓库与发布规则（项目硬性约定）
- **每个改动批次结束必须升版 + 发布**：`version.properties` 的 `VERSION_NAME`（patch+1）与 `VERSION_CODE`（+1）；
  提交 `chore: prepare <版本> release`；push `main` 后 CI 自动出 Release。
- **CI 只在 tag `v<VERSION_NAME>` 不存在时才发布**；**已发布过的版本号绝不复用**。
- `CHANGELOG.md` 必须同步写条目（版式：`## 版本 - 日期` + `### 新增 / 优化 / 修复 / 说明`，中文、面向用户）。
- **版本号语义**：patch = 修补与内部重构；minor = 用户可见的新能力（本次多后端 = **1.5.0**）；
  major = 破坏性变更（改包名/改名 = **2.0.0**）。
- 分支策略：`main` 主线；`release/1.4` 维护历史发布版（1.4.x 小修在这里 bump 出包）；功能开发走 `feature/*` 分支后合入 `main`。
- 项目由 **Trellis** 管理：写代码前先读 `.trellis/spec/`，工作流见 `.trellis/workflow.md`；
  `.trellis/tasks/`（任务）与 `.trellis/local/`、`.trellis/tmp/` 都在 `.gitignore` 里，**不要往里提交任何东西**。

### 9.2 安全与凭据（用户多次强调，历史泄露过两次）
- NAS 与 Jellyfin 的**地址、账号、密码永不出现在任何入库文件**：文档、注释、测试代码、CI 配置、README 一律不行；
  只允许放在 `.trellis/local/test-credentials.properties`。
- **连"校验命令/示例"都不能含明文片段**（本项目曾把密码片段写进文档，必须改写提交并 force-push 清理）。
- 截图、抓包、临时脚本不得入库（曾误提交 `.trellis/*.png`、`*.sh`，已清理）。
- CI 的 FN Connect 密钥走 GitHub Secrets（`FN_CONNECT_API_KEY` / `FN_CONNECT_AUTHX_PREFIX`），与本地测试凭据无关。
- 万一泄露：立即改密码 + 重写 Git 历史（流程参照 `.trellis` 内既有记录）。

### 9.3 代码铁律（改 UI/播放/缓存时必须遵守）
1. **焦点**（用户拷问最多的点）：左右键只在**行内**移动、上下键跨行、**不跳过、不逃逸**；
   行首/行尾用 `FocusRequester.Cancel` 明确取消；跨行用显式 `FocusRequester` 或 `getOrNull(...) ?: Cancel`。
   - **不要把 Lazy 容器（LazyRow/LazyColumn/LazyVerticalGrid）里会被回收的 item 当作 `focusProperties` 的目标**：
     目标被回收后 requester 会变成未挂载，方向键会**静默失效（焦点冻结）**。
     现有首页行、媒体带、网格都已改成非 lazy（`Row + horizontalScroll` / `Column + verticalScroll`）——改回 lazy 前先想清楚焦点。
   - 所有 `requestFocus()` 必须 `runCatching` 包住（未挂载会抛 `IllegalStateException`）。
   - 弹窗（Dialog）关闭后要显式把焦点还给触发它的按钮（见 `AddToPlaylistDialog` 的 `dialogWasVisible` 写法）。
2. **配色**：UI 层（`app/.../ui/` 除 `Theme.kt` 外）**不允许出现任何颜色字面量**；
   一律用 `FnColors` 的主题 token（插画用 `FnArt`）。新增颜色必须：加 `ThemeColors` 字段 → 在**每套主题**里派生（现在 12 套）→ 加 `FnColors` 字段 → 在 `applyTheme` 里赋值。
   - **色值必须写 8 位 ARGB**（`Color(0xFFRRGGBB)`）；写成 6 位会被 Compose 当作 alpha=0（全透明），
     本项目曾因此让"卡片底色/描边/面板底色"在六套主题下全部画不出来。
3. **尺寸与缩放**：全局缩放已实现（`LocalDensity` 覆盖，设置页 标准 1.35 / 较大 1.5 / 更大 1.75；自动档：车机 1.25、电视与手机 1.0）。
   新 UI 直接写普通 dp/sp 即可被统一缩放；**不要再写"按屏幕密度分支"的尺寸逻辑**。
   10 英尺 UI：可点元素 ≥ 48dp、正文字号 ≥ 14sp 起步（本项目已在 1.4.x 统一放大过一轮）。
4. **Compose lint 规则**：`Modifier` 必须是第一个可选参数；公共函数不要暴露 `internal` 类型；改了签名要跑 lint。
5. **播放内核语义**：`mediaId` = 曲目 id；`QueueSource.sort` 是**队列/快照身份的一部分**（改动必须按后端隔离，快照已按 namespace 隔离）；
   漫游是内核的一等状态（`QueueKind.Roam`、`RoamWindow`、`PlaybackTransportOwnership.Roam`），换后端时**只换数据来源，别动状态机**。
6. **缓存语义（Step 1b 的关键约束）**：`cachedPage` 缓存**原始 JSON**并持久化到 Room；
   `invalidateSource(namespace, businessKey)` 的键是 `(namespace, kind, businessKey)` 三段——
   本项目曾把 `kind`（"index"）当 `businessKey`（"playlists"）传，导致**歌单索引缓存没被清掉、#新建歌单后列表不刷新**。
   改缓存相关代码前先读 `SerializedResponseCache` 的键定义。
7. **服务端是家用设备，后端要克制**：封面并发 4、列表取样单页、卡片封面写穿复用、随机取歌一次成片；
   不要引入"一次发几十个请求"的实现（本项目为此专门优化过一轮）。
8. **老设备（Android 6 电视，堆 96–192MB）**：不要把大量条目一次性铺开（搜索已改 `LazyColumn`）；
   图片内存缓存按堆封顶（`min(40MB, heap/8)`）、小堆时并发解码 3→2 且跳过渐进加载；Manifest 已开 `largeHeap`；
   解码要能扛 `OutOfMemoryError`（失败退化成占位图，不许崩）。

### 9.4 环境与工具限制
- **JDK 必须显式设置**：`JAVA_HOME="D:/research/android/env/jdk-21.0.12.1+1"`（默认环境的 JDK 版本不对）。
- **不要用模拟器**（早期用户明确要求：会影响其他项目的模拟器测试）；TV AVD `tv_36` 在本机起不来（进程仅 12MB、长期 offline），
  如需模拟器验证请先与用户确认。**以真机为准**。
- `heredoc` 写长脚本会被截断 → **长脚本/长补丁用 Write 工具写文件再执行**（本项目多次踩坑）。
- 截图用 `adb exec-out screencap -p`（不要 `adb shell cat`，CRLF 会破坏二进制）；`uiautomator dump` 不稳，优先截图 + `input keyevent`。
- 真机登录（debug 包每次重装都退出登录）：CENTER → 输入 → **BACK 退出编辑** → DOWN 到下一个字段 ……（编辑态会吞方向键；BACK 多按一次会退出 App）。
- Windows 上 `core:data` 的 `AppDatabaseMigrationTest` **必然失败**（Robolectric 临时路径），与改动无关，CI/Linux 为绿。

### 9.5 与用户协作的约定
- **先方案后动手**：涉及 UI/命名/大重构，先把方案讲清楚等确认（用户会说"待我批准再修改"）。
- **改完必须自测**：UI 改动要真机截图核对；**动到用户数据前先想清楚**（验证歌单/收藏这类写操作要用可逆操作并清理，验完告知）。
- 回复用中文；文档与说明"越详细越好"。
- 每次发布后要确认 CI 真的产出了 Release（`gh release list`），而不是只看 push 成功。

---

## 10. 已踩过的坑（不要重复）

| 坑 | 症状 | 正确做法 |
|---|---|---|
| 6 位色值 | 卡片/描边/面板在部分主题下"消失" | 一律 8 位 ARGB |
| 缓存失效键 | 新建歌单后首页/弹窗不刷新 | 用 `businessKey`（如 `playlists`），不是 `kind` |
| Lazy item 作焦点目标 | 方向键静默失效、焦点冻死 | 容器改非 lazy，或只把 requester 挂在稳定节点 |
| effect 自己改自己的 key | 历史胶囊点击被自我取消、结果被清空 | 拆成两个互不影响的 effect |
| 输入框吞方向键 | 遥控器下不到结果区/历史胶囊 | 输入框显式 `onPreviewKeyEvent` 接管上下键 |
| 页面/列表一次性铺开 | 老电视 OOM 崩退 | `LazyColumn` + 按堆封顶的图片缓存 + OOM 兜底 |
| `Modifier` 位置 | lint 报 `ModifierParameter` | 参数顺序：必填 → `modifier` → 其它可选 |
| 公开函数暴露 internal 类型 | 编译报错 | 类型改成 public 或函数改 internal（`applyTheme` 就是这么处理的） |
| 文档里写明文片段 | 等于泄露 | 校验命令从 `.trellis/local/` 读匹配模式 |
| 只 push 不看 CI | 以为发了版其实没出 Release | `gh release list` 确认 |
| 真机上误点「切换账号」再返回 | 之后播放报 `Source error`（HTTP 401，日志里 DataSource 401） | **不是回归**：进「切换账号」页会 `clearPlaybackSession()`（清空播放会话 + `ClearAuth` 清掉注入的头），而头只在会话状态再次发 `SignedIn` 时才重新注入 → 重启 App 即恢复。改动播放鉴权时别被它带偏 |

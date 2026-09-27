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
| `main` | 主线 | **1.4.4 已发布**（Step 1b 合入 `ff1976e`，CI 已出 Release）+ CI 触发规则（`on.push.branches: [main, "release/*"]`） |
| `release/1.4` | **上个发布版的维护分支**，1.4.x 小修在这里 bump 出包 | 已推送，tag `v1.4.3` 已存在 |
| `feature/multi-backend` | 多后端工作分支（本次改造） | 已推送并与 `main` 同步（`ff1976e`）；Step 2b 从这里继续 |

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
| `4086a86` | `.gitignore` 忽略 `.zcode/`（会话/计划草稿里会写测试服务器地址） |
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
5. ✅ 已合入 `main` 并发布 **1.4.4**（`ff1976e`，CI 出 Release 已用 `gh release list` 确认）；
   下个版本 **1.5.0** 留给 Step 2b（Jellyfin 接入）。

> ⚠️ **待清理（用户侧）**：验证新建歌单时留下一个测试歌单 **`ZCode-Test-1.4.4`**（含 1 首 Bandari 曲目）。
> App 没有删除歌单的接口，需要在 NAS/飞牛客户端里手动删掉；同页还有更早会话留下的 `ZCode-Test3`。
> 收藏与 `black` 歌单都已恢复到验证前的状态。

### Step 2b —— Jellyfin 接线
6. `JellyfinConnector`：`GET /System/Info/Public` 识别 + `POST /Users/AuthenticateByName` 登录 +
   令牌持久化（`SecureTokenStore` payload 加 `kind` 字段，兼容老数据）+ namespace 用 `ServerId:UserId`。
7. 登录页：地址提交时先 probe 识别后端（命中 Public Info 即 Jellyfin，页面显示"已识别 Jellyfin 10.x"）；
   FNID/安全码字段仅飞牛显示；错误文案泛化（"NAS 暂时不可用"→"服务器暂时不可用"）。
8. `AppContainer`/会话按 `ServerKind` 构造并注入 `FnOsMusicBackend` 或 `JellyfinMusicBackend`。
9. **客户端漫游** `LocalRoamStrategy`：`SortBy=Random` 或 `/Items/{id}/InstantMix` 取种子 →
   本地 prev/current/next 游标 → 合成 `RoamWindow`；由 `capabilities.serverSideRoam` 选择实现，播放内核零改动。
10. **HLS 接通**：`PlaybackTrack` 带 `StreamMode`，`PlaybackController` 用已有的 `PlaybackSource.Hls` +
   `media3-exoplayer-hls`（依赖已在）建 HLS media item。
11. Jellyfin 的歌单/收藏/最近播放接到既有缓存与 `favoriteState` 状态机上
    （`PlaylistItemId` → `Track.playlistEntryId`；收藏列表用 `Filters=IsFavorite`，最近播放用 `Filters=IsPlayed&SortBy=DatePlayed`，均已实测）。
12. 计划文档（`.zcode/plans/…`）里还剩这几处没落地，别漏：
    `MusicBackend` 增加 `probe(origin): ServerIdentity?`（登录页识别类型/版本用）；
    `Track` 增加 `playlistEntryId: String?`；`ServerConnection` 改成按 `ServerKind` 分叉
    （现在直接带 `TrimMusicApi`），`SessionRepository.requireApi()/authenticated{}` 同步泛化；
    `QueueSource.sort` 按"后端不透明游标"的语义写注释（飞牛沿用现有排序串，Jellyfin 用自家键）。

### 收尾
13. **真机双后端串行验收**（同一台设备先飞牛后 Jellyfin，跑全流程）→ 合入 `main` → 发 **1.5.0**（CHANGELOG + 版本号 +1 + push，CI 自动出 Release）。
14. 之后再单独做 **2.0.0**：改名「音乐坞」+ `applicationId com.musicdock.tv` + 同步 CI 产物名/更新清单/UA/README + 旧包最后一次发布里写明"请安装新版音乐坞"（换包名后旧版无法自更新）。

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

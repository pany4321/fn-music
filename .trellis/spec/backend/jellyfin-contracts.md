# Jellyfin 音乐接口契约（实测）

本文件记录**实测**得到的 Jellyfin 音乐接口形态，作为 `JellyfinBackend` 适配器的实现依据。
测试环境：Jellyfin **10.10.7**（局域网自建，账号凭据只保存在 `.trellis/local/`，不入库）。
所有结论均以本地只读探测为准（`GET /System/Info/Public`、登录一次、若干 GET + 一次 `stream` 头探测）。

## 1. 服务器识别与版本（免登录）

```
GET /System/Info/Public
→ 200 {"ServerName":"jellyfin","Version":"10.10.7","Id":"<serverId>",
       "ProductName":"Jellyfin Server","OperatingSystem":""}
```
用途：登录页**自动识别服务器类型**（命中该端点即 Jellyfin），同时拿到 `Id`（作为 `ServerIdentity.guid`）与 `Version`（决定歌词端点等能力）。

## 2. 认证

```
POST /Users/AuthenticateByName
Header: X-Emby-Authorization: MediaBrowser Client="…", Device="…", DeviceId="…", Version="…"
Body:   {"Username":"…","Pw":"<明文>"}          // 10.x 用 Pw（明文，走 TLS 或局域网直连）
→ 200 {"AccessToken":"…","ServerId":"…","User":{"Id":"…","Name":"…"}}
```
后续请求头：

```
Authorization: MediaBrowser Token="<AccessToken>", Client="…", Device="…", DeviceId="…", Version="…"
```
- 会话校验 `GET /Users/Me`；登出 `POST /Sessions/Logout`。
- 与飞牛的差异：飞牛是 `Authorization: <raw token>` + `x-access-code`，Jellyfin 是上面这种带参数的 `MediaBrowser` 头 → 抽象层用统一的 `headers: Map<String,String>` 承载。

## 3. 条目查询（所有音乐目录共用）

```
GET /Items?userId=<uid>&Recursive=true
    &IncludeItemTypes=Audio|MusicAlbum|MusicArtist|MusicGenre|Playlist
    &StartIndex=<(page-1)*size>&Limit=<size>
    &SortBy=<SortName|DateCreated|DatePlayed|Random|ParentIndexNumber,IndexNumber>
    &SortOrder=Ascending|Descending
    &Fields=MediaSources,MediaStreams,DateCreated,UserData,RunTimeTicks,Container,
            AlbumArtist,Artists,IndexNumber,ParentIndexNumber,ChildCount,RecursiveItemCount
    &SearchTerm=<q>          // 搜索
    &Filters=IsFavorite|IsPlayed
    &GenreIds=/&AlbumIds=/&ArtistIds=/&ParentId=
→ 200 {"Items":[…],"TotalRecordCount":N,"StartIndex":i}
```

字段映射（→ 我们的领域模型）：

| Jellyfin | 我们 |
|---|---|
| `Id` | `TrackGuid` / `CollectionGuid` |
| `Name` | `title` / `name` |
| `Album` / `AlbumId` | `albumName` / 专辑 guid |
| `Artists` / `AlbumArtist` | `artistName`（多值用 ` / ` 连接，与飞牛一致） |
| `RunTimeTicks` | `durationMs = ticks / 10_000` |
| `IndexNumber` / `ParentIndexNumber` | 音轨号 / 碟号（排序用） |
| `Container` + `MediaSources[0].{Container,BitRate,MediaStreams}` | `audioFormat`（大写，如 `FLAC`） |
| `ImageTags.Primary` 存在与否 | 是否有封面（决定是否走占位图） |
| `UserData.{IsFavorite,Played,LastPlayedDate}` | 收藏态 / 最近播放 |
| `ChildCount` / `RecursiveItemCount` | 歌单曲目数（飞牛的 `trackCount`） |

分页/排序映射：`page,size,sort` ↔ `StartIndex=(page-1)*size, Limit=size, SortBy+SortOrder`：

| 飞牛 sort | Jellyfin |
|---|---|
| `createdAt,desc` | `SortBy=DateCreated&SortOrder=Descending` |
| `title,asc` | `SortBy=SortName&SortOrder=Ascending` |
| `trackNo,asc` | `SortBy=ParentIndexNumber,IndexNumber` |
| `trackCount,desc`（歌手） | `SortBy=SortName`（Jellyfin 无曲目数排序，用名称） |
| 收藏 `favoriteAt,desc` | `SortBy=DateCreated&SortOrder=Descending`（无收藏时间排序） |

## 4. 各目录端点（实测均 200）

| 能力 | 端点 | 实测 |
|---|---|---|
| 全部歌曲 | `/Items?IncludeItemTypes=Audio&Recursive=true` | 27577 首 |
| 歌手（专辑艺术家） | `/Artists/AlbumArtists?userId&Recursive=true&SortBy=SortName` | 881 位 |
| 风格 | `/MusicGenres?userId&Limit&SortBy=SortName` | 79 个 |
| 专辑 | `/Items?IncludeItemTypes=MusicAlbum&Recursive=true&SortBy=SortName` | ✅ |
| 专辑/歌手/风格下属歌曲 | `/Items?AlbumIds=` / `ArtistIds=` / `GenreIds=` + `IncludeItemTypes=Audio` | ✅（用 `/Items?ParentId=<albumId>` 亦可） |
| 最近添加 | `/Items/Latest?Limit=` 或 `SortBy=DateCreated&SortOrder=Descending` | ✅ |
| 最近播放 | `/Items?Filters=IsPlayed&SortBy=DatePlayed&SortOrder=Descending` | ✅ 57 首（`UserData.LastPlayedDate` 可读） |
| 随机 | `/Items?SortBy=Random&Limit=N` | ✅ 一次成片（比飞牛的"探测+随机页"更省） |
| 搜索 | `/Items?SearchTerm=李&IncludeItemTypes=Audio\|MusicAlbum\|MusicArtist` | ✅ 4 / 2 / 0 |
| 收藏 | `POST`/`DELETE /Users/{userId}/FavoriteItems/{itemId}`；列表 `/Items?Filters=IsFavorite` | 端点 ✅（测试库当前 0 条） |
| 歌单列表 | `/Items?IncludeItemTypes=Playlist&Fields=ChildCount,RecursiveItemCount` | 端点 ✅（测试库当前 0 个） |
| 歌单曲目 | `/Playlists/{id}/Items?userId&StartIndex&Limit` → `Items[].PlaylistItemId` | ⚠️ 删条目要用 `PlaylistItemId`（需在 `Track` 上承载） |
| 新建歌单 | `POST /Playlists` body `{Name, Ids, UserId, MediaType:"Audio"}` | 待真机验证 |
| 加/删歌 | `POST /Playlists/{id}/Items?ids=<itemIds>`、`DELETE /Playlists/{id}/Items?entryIds=<PlaylistItemId>` | 待真机验证 |
| 封面 | `/Items/{id}/Images/Primary?fillWidth=400&fillHeight=400&quality=90` | ✅ 200 image/jpeg（51 KB） |
| 歌词 | `/Audio/{id}/Lyrics` | 404 = 该曲无歌词（10.10 端点存在）；→ 返回 null，交给已有在线歌词源兜底 |

## 5. 播放（重点：PlaybackInfo 不返回 URL，要客户端自建）

实测：`GET/POST /Items/{id}/PlaybackInfo?userId=…`（含 `DeviceProfile`）返回的
`MediaSources[0]` 里 **`DirectStreamUrl` 与 `TranscodingUrl` 均为 null**，只有能力位与规格：
`{Id, Container:"flac", Bitrate:1763987, SupportsDirectPlay:true, SupportsDirectStream:true, SupportsTranscoding:true}`。
→ 客户端自己拼 URL（官方客户端也是这么做的）。

| 模式 | URL | 实测 |
|---|---|---|
| 直连（原文件） | `GET /Audio/{id}/stream?static=true&api_key=<token>` | ✅ 200 `audio/flac`（43 MB） |
| HLS 转码 | `GET /Audio/{id}/stream?api_key=&container=ts&audioCodec=aac&transcodingContainer=ts&transcodingProtocol=hls&maxAudioChannels=2&maxStreamingBitrate=192000&transcodeReasons=ContainerNotSupported` | ✅ 200 `video/mp2t`（HLS 分片，可拖动） |
| HTTP 转码 | 同上但 `audioCodec=mp3&transcodingContainer=mp3&transcodingProtocol=http` | ✅ 200 `audio/mpeg`（ID3 流） |

决策：**能直连就直连**（`SupportsDirectPlay` 且容器/编码在客户端白名单内，如 flac/mp3/aac/m4a/ogg/opus/wav）；
否则若 `SupportsTranscoding` → **优先 HLS**（可拖动），HTTP-mp3 作为兜底开关。
→ 正好接通仓库里已有但未接线的 `PlaybackSource.Hls` 与 `media3-exoplayer-hls` 依赖。

## 6. 错误模型

Jellyfin 用 HTTP 状态码 + 结构化错误体（`{"type","title","status","traceId"}`），没有飞牛那种业务 code：
| 状态 | 映射到 `AppError` |
|---|---|
| 401 | `Unauthenticated`（清会话、回登录） |
| 403 | `Unknown("forbidden")`（提示无权限） |
| 404 | `NotFound`（歌词端点 404 视为"无歌词"） |
| 5xx / 超时 / IO | `NetworkUnavailable(retryable=true)` |

## 7. 测试库初始快照（便于回归对比）

27577 首歌曲 / 881 位专辑艺术家 / 79 个风格 / 0 个歌单 / 0 首收藏。
验证歌单与收藏流程时会在该服务器上新建测试歌单、收藏若干曲目（可逆操作，验证完清理）。

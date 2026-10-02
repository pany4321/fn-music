package com.fnmusic.tv.core.data.backend

import android.util.Log
import com.fnmusic.tv.core.data.api.ApiDecoder
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.AppException
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Jellyfin 音乐接口封装。
 * 只做"发请求 + 解 JSON"，映射与播放策略在 [JellyfinMusicBackend]；
 * 形态依据实测契约（.trellis/spec/backend/jellyfin-contracts.md，10.10.7）。
 */
private const val TAG = "JellyfinApi"

internal class JellyfinApi(
    private val origin: HttpUrl,
    private val client: OkHttpClient,
    private val deviceId: String,
    private val clientName: String = DEFAULT_CLIENT_NAME,
    private val clientVersion: String = DEFAULT_CLIENT_VERSION,
    private val tokenProvider: () -> String? = { null },
) {

    private fun authorizationHeader(token: String?): String =
        jellyfinAuthorizationHeader(token, deviceId, clientName, clientVersion)

    /** [currentUserId] 的缓存：一次会话内用户不会变。 */
    @Volatile
    private var cachedUserId: String? = null

    /** 免登录的服务器信息：用于自动识别服务器类型与版本。 */
    suspend fun publicInfo(): JellyfinPublicInfoDto =
        request(Request.Builder().url(url("System/Info/Public")).get(), token = null)

    /** 登录：返回访问令牌与用户。 */
    suspend fun authenticate(username: String, password: String): JellyfinAuthResultDto {
        val body = ApiDecoder.json.encodeToString(
            JellyfinAuthRequest(Username = username, Pw = password),
        )
        val result: JellyfinAuthResultDto = request(
            Request.Builder()
                .url(url("Users/AuthenticateByName"))
                .post(body.toRequestBody("application/json".toMediaType())),
            token = null,
        )
        cachedUserId = result.User.Id
        return result
    }

    /** [token] 非空时用它鉴权（令牌续期场景），否则读全局令牌提供者。 */
    suspend fun me(token: String? = null): JellyfinUserDto =
        request<JellyfinUserDto>(
            Request.Builder().url(url("Users/Me")).get(),
            token = token?.takeIf(String::isNotBlank) ?: requireToken(),
        ).also { cachedUserId = it.Id }

    /**
     * 当前用户 id：登录响应里有，但"令牌恢复/跨源切换"时连接是先探测、后凭令牌用的，
     * 那条路径上 userId 永远是空 —— 向服务端问一次（`/Users/Me`）并缓存。
     */
    private suspend fun currentUserId(): String =
        cachedUserId ?: me().Id.takeIf(String::isNotBlank)
            ?: throw AppException(AppError.Unauthenticated)

    /**
     * 空的 userId 一律解析成当前用户：
     * 否则会拼出 `/Users//FavoriteItems/{id}` 这类必然 404 的地址（实测收藏就这样失败）。
     */
    private suspend fun uid(userId: String): String =
        userId.takeIf(String::isNotBlank) ?: currentUserId()

    /** 登出：作废当前访问令牌（失败不阻塞本地清理，由调用方决定）。 */
    suspend fun logout() {
        execute(
            Request.Builder()
                .url(url("Sessions/Logout"))
                .post(EMPTY_BODY)
                .header("Accept", "application/json")
                .header("Authorization", authorizationHeader(requireToken()))
                .build(),
        )
    }

    /** 随机取歌：`SortBy=Random` 一次成片（比飞牛的"探测 + 随机页"更省）。 */
    suspend fun randomTracks(userId: String, limit: Int): List<JellyfinItemDto> =
        items(
            Request.Builder().url(
                url("Items").newBuilder()
                    .addQueryParameter("userId", uid(userId))
                    .addQueryParameter("IncludeItemTypes", "Audio")
                    .addQueryParameter("Recursive", "true")
                    .addQueryParameter("SortBy", "Random")
                    .addQueryParameter("Limit", limit.toString())
                    .addQueryParameter("Fields", LIST_ITEM_FIELDS)
                    .build(),
            ).get(),
        ).Items

    /**
     * 统一的条目查询：各目录/搜索/收藏/最近都用它，参数按需组合。
     * 分页用 Jellyfin 的 StartIndex/Limit（与我们的 page/size 是 1:1 换算）。
     */
    suspend fun items(
        userId: String,
        includeItemTypes: String? = null,
        parentId: String? = null,
        albumIds: String? = null,
        artistIds: String? = null,
        albumArtistIds: String? = null,
        genreIds: String? = null,
        searchTerm: String? = null,
        ids: String? = null,
        filters: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        startIndex: Int = 0,
        limit: Int = 50,
        fields: String = LIST_ITEM_FIELDS,
    ): JellyfinItemsDto = items(
        Request.Builder().url(
            url("Items").newBuilder().apply {
                addQueryParameter("userId", uid(userId))
                addQueryParameter("Recursive", "true")
                includeItemTypes?.let { addQueryParameter("IncludeItemTypes", it) }
                parentId?.let { addQueryParameter("ParentId", it) }
                albumIds?.let { addQueryParameter("AlbumIds", it) }
                artistIds?.let { addQueryParameter("ArtistIds", it) }
                albumArtistIds?.let { addQueryParameter("AlbumArtistIds", it) }
                genreIds?.let { addQueryParameter("GenreIds", it) }
                searchTerm?.let { addQueryParameter("SearchTerm", it) }
                ids?.let { addQueryParameter("Ids", it) }
                filters?.let { addQueryParameter("Filters", it) }
                sortBy?.let { addQueryParameter("SortBy", it) }
                sortOrder?.let { addQueryParameter("SortOrder", it) }
                addQueryParameter("StartIndex", startIndex.toString())
                addQueryParameter("Limit", limit.toString())
                addQueryParameter("Fields", fields)
            }.build(),
        ).get(),
    )

    /** 歌手列表走专门的端点（Jellyfin 的"专辑艺术家"）。[searchTerm] 供全局搜索使用。 */
    suspend fun albumArtists(userId: String, startIndex: Int, limit: Int, searchTerm: String? = null): JellyfinItemsDto = items(
        Request.Builder().url(
            url("Artists/AlbumArtists").newBuilder()
                .addQueryParameter("userId", uid(userId))
                .addQueryParameter("Recursive", "true")
                .addQueryParameter("SortBy", "SortName")
                .apply { searchTerm?.let { addQueryParameter("searchTerm", it) } }
                .addQueryParameter("StartIndex", startIndex.toString())
                .addQueryParameter("Limit", limit.toString())
                .addQueryParameter("Fields", LIST_ITEM_FIELDS)
                .build(),
        ).get(),
    )

    /** 风格列表走专门的端点。 */
    suspend fun musicGenres(userId: String, startIndex: Int, limit: Int): JellyfinItemsDto = items(
        Request.Builder().url(
            url("MusicGenres").newBuilder()
                .addQueryParameter("userId", uid(userId))
                .addQueryParameter("SortBy", "SortName")
                .addQueryParameter("StartIndex", startIndex.toString())
                .addQueryParameter("Limit", limit.toString())
                .addQueryParameter("Fields", LIST_ITEM_FIELDS)
                .build(),
        ).get(),
    )

    /** 歌单内曲目：条目上带 PlaylistItemId（删条目时要用）。 */
    suspend fun playlistItems(
        playlistId: String,
        userId: String,
        startIndex: Int,
        limit: Int,
    ): JellyfinItemsDto = items(
        Request.Builder().url(
            url("Playlists/$playlistId/Items").newBuilder()
                .addQueryParameter("userId", uid(userId))
                .addQueryParameter("StartIndex", startIndex.toString())
                .addQueryParameter("Limit", limit.toString())
                .addQueryParameter("Fields", LIST_ITEM_FIELDS)
                .build(),
        ).get(),
    )

    /** 收藏/取消收藏（`POST`/`DELETE /Users/{userId}/FavoriteItems/{itemId}`）。 */
    suspend fun setFavorite(userId: String, itemId: String, favorite: Boolean) {
        val request = Request.Builder()
            .url(url("Users/${uid(userId)}/FavoriteItems/$itemId"))
            .method(if (favorite) "POST" else "DELETE", EMPTY_BODY)
            .build()
        execute(
            request.newBuilder()
                .header("Accept", "application/json")
                .header("Authorization", authorizationHeader(requireToken()))
                .build(),
        )
    }

    /** 按 id 取单个条目（拿 MediaSources 判断能不能直连）。 */
    suspend fun item(itemId: String, userId: String): JellyfinItemDto? =
        items(
            Request.Builder().url(
                url("Items").newBuilder()
                    .addQueryParameter("userId", uid(userId))
                    .addQueryParameter("Ids", itemId)
                    .addQueryParameter("Fields", FULL_ITEM_FIELDS)
                    .build(),
            ).get(),
        ).Items.firstOrNull()

    /** 单个条目请求（`Ids=` 查询与新建歌单的返回体都能解出条目）。 */
    private suspend fun item(builder: Request.Builder): JellyfinItemDto {
        val body = execute(withAuthorization(builder.build()))
        return try {
            ApiDecoder.json.decodeFromString<JellyfinItemDto>(body)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            throw AppException(AppError.Unknown("jellyfin_invalid_json"), cause)
        }
    }

    private fun withAuthorization(request: Request): Request = request.newBuilder()
        .header("Accept", "application/json")
        .header("Authorization", authorizationHeader(requireToken()))
        .build()

    /**
     * 服务端歌词（Jellyfin 10.9+）。
     * 实测：该曲没有歌词时端点返回 404 —— 这不是错误，返回 null 让在线的歌词源接管。
     */
    suspend fun lyrics(itemId: String): JellyfinLyricsDto? {
        val request = Request.Builder()
            .url(url("Audio/$itemId/Lyrics"))
            .header("Accept", "application/json")
            .header("Authorization", authorizationHeader(requireToken()))
            .get()
            .build()
        val body = try {
            execute(request)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: AppException) {
            if (cause.error == AppError.NotFound) return null
            throw cause
        }
        return try {
            ApiDecoder.json.decodeFromString<JellyfinLyricsDto>(body)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            throw AppException(AppError.Unknown("jellyfin_invalid_json"), cause)
        }
    }

    /** 封面原始字节：图片是二进制，不能走 `execute`（字符串化会破坏内容）。 */
    suspend fun imageBytes(itemId: String, width: Int?): ByteArray =
        executeBytes(
            Request.Builder()
                .url(imageUrl(itemId, width))
                .header("Accept", "image/*")
                .header("Authorization", authorizationHeader(requireToken()))
                .get()
                .build(),
        )

    /** 新建歌单：`POST /Playlists`（`Ids` 留空＝先建空歌单，再由上层把当前曲目加进去）。 */
    suspend fun createPlaylist(name: String, userId: String): JellyfinItemDto {
        val body = ApiDecoder.json.encodeToString(
            JellyfinCreatePlaylistRequest(Name = name, UserId = uid(userId), MediaType = "Audio"),
        )
        return item(
            Request.Builder()
                .url(url("Playlists"))
                .post(body.toRequestBody("application/json".toMediaType())),
        )
    }

    /** 往歌单加条目（`POST /Playlists/{id}/Items?ids=`）。 */
    suspend fun addToPlaylist(playlistId: String, itemIds: List<String>, userId: String) {
        val request = Request.Builder()
            .url(
                url("Playlists/$playlistId/Items").newBuilder()
                    .addQueryParameter("ids", itemIds.joinToString(","))
                    .addQueryParameter("userId", uid(userId))
                    .build(),
            )
            .post(EMPTY_BODY)
            .build()
        execute(withAuthorization(request))
    }

    /** 删除歌单本身：`DELETE /Playlists/{id}`（Jellyfin 标准端点）。 */
    suspend fun deletePlaylist(playlistId: String) {
        val request = Request.Builder()
            .url(url("Playlists/$playlistId"))
            .delete()
            .build()
        execute(withAuthorization(request))
    }

    /** 从歌单删条目：必须用 `entryIds`（歌单条目的 PlaylistItemId，不是曲目 id）。 */
    suspend fun removeFromPlaylist(playlistId: String, entryIds: List<String>) {
        val request = Request.Builder()
            .url(
                url("Playlists/$playlistId/Items").newBuilder()
                    .addQueryParameter("entryIds", entryIds.joinToString(","))
                    .build(),
            )
            .delete()
            .build()
        execute(withAuthorization(request))
    }

    /** 媒体库列表（`/Users/{id}/Views`）；当前 UI 没用到，接口先留着。 */
    suspend fun views(userId: String): JellyfinItemsDto = items(
        Request.Builder().url(
            url("Users/${uid(userId)}/Views").newBuilder()
                .addQueryParameter("userId", uid(userId))
                .build(),
        ).get(),
    )

    // ---- URL 构造（实测：PlaybackInfo 不返回 URL，客户端自己拼） ----

    /**
     * 封面：Jellyfin 按条目 id 取图；宽度给 fillWidth/fillHeight 得到方形裁切。
     * **不带 api_key**：实测图片/流/播放列表都认 `Authorization` 头，
     * 而 URL 会进播放队列快照（Room 未加密）与通知元数据 —— token 不该跟着落地。
     */
    fun imageUrl(itemId: String, width: Int?): String =
        url("Items/$itemId/Images/Primary").newBuilder().apply {
            if (width != null) {
                addQueryParameter("fillWidth", width.toString())
                addQueryParameter("fillHeight", width.toString())
                addQueryParameter("quality", "80")
            }
        }.build().toString()

    /** 直连原文件（不带 token：播放器统一用后端注入的请求头）。 */
    fun directStreamUrl(itemId: String): String =
        url("Audio/$itemId/stream").newBuilder()
            .addQueryParameter("static", "true")
            .build().toString()

    /**
     * HLS 转码（可拖动）。
     *
     * ⚠️ 实测（10.10.7）：必须打 **`/Audio/{id}/master.m3u8`** ——
     * `/Audio/{id}/stream?...transcodingProtocol=hls` 返回的是裸 TS 流（`video/mp2t`，
     * 以 0x47 同步字节开头），HLS 解析器会报 "Input does not start with the #EXTM3U header"。
     * `MediaSourceId` 是必需的（缺了直接 400）；不带 `api_key`，鉴权靠播放器注入的请求头。
     */
    fun hlsTranscodeUrl(itemId: String, maxStreamingBitrate: Int = 192_000): String =
        url("Audio/$itemId/master.m3u8").newBuilder()
            .addQueryParameter("MediaSourceId", itemId)
            .addQueryParameter("DeviceId", deviceId)
            .addQueryParameter("container", "ts")
            .addQueryParameter("audioCodec", "aac")
            .addQueryParameter("transcodingContainer", "ts")
            .addQueryParameter("transcodingProtocol", "hls")
            .addQueryParameter("maxAudioChannels", "2")
            .addQueryParameter("maxStreamingBitrate", maxStreamingBitrate.toString())
            .addQueryParameter("transcodeReasons", "ContainerNotSupported")
            .build().toString()

    private fun url(path: String): HttpUrl =
        origin.newBuilder().addPathSegments(path).build()

    private fun requireToken(): String =
        tokenProvider() ?: throw AppException(AppError.Unauthenticated)

    private suspend inline fun <reified T> request(builder: Request.Builder, token: String?): T {
        val request = builder
            .header("Accept", "application/json")
            .header("Authorization", authorizationHeader(token))
            .build()
        val body = execute(request)
        return try {
            ApiDecoder.json.decodeFromString<T>(body)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            throw AppException(AppError.Unknown("jellyfin_invalid_json"), cause)
        }
    }

    private suspend fun items(builder: Request.Builder): JellyfinItemsDto {
        val body = execute(
            builder
                .header("Accept", "application/json")
                .header("Authorization", authorizationHeader(requireToken()))
                .build(),
        )
        return try {
            ApiDecoder.json.decodeFromString<JellyfinItemsDto>(body)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            throw AppException(AppError.Unknown("jellyfin_invalid_json"), cause)
        }
    }

    private suspend fun execute(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "IO ${call.request().method} ${call.request().url.encodedPath}: ${e.javaClass.simpleName} ${e.message}")
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(
                        AppException(AppError.NetworkUnavailable),
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val text = it.body?.string().orEmpty()
                        if (it.isSuccessful) {
                            continuation.resume(text)
                        } else {
                            // 只记路径与状态，不记查询串（避免把令牌写进日志）。
                            Log.w(
                                TAG,
                                "HTTP ${it.code} ${call.request().method} ${call.request().url.encodedPath}: " +
                                    text.take(200),
                            )
                            continuation.resumeWithException(
                                AppException(classify(it.code, text)),
                            )
                        }
                    }
                }
            })
        }

    private suspend fun executeBytes(request: Request): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "IO ${call.request().method} ${call.request().url.encodedPath}: ${e.javaClass.simpleName} ${e.message}")
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(
                        AppException(AppError.NetworkUnavailable),
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw AppException(classify(it.code, ""))
                            it.body?.bytes()?.takeIf(ByteArray::isNotEmpty)
                                ?: throw AppException(AppError.Empty)
                        }
                    }
                    if (!continuation.isActive) return
                    result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }

    /** Jellyfin 用 HTTP 状态码而非业务 code（实测错误体是 RFC9110 的 problem JSON）。 */
    private fun classify(status: Int, body: String): AppError = when (status) {
        400 -> AppError.Unknown("jellyfin_bad_request")
        401 -> AppError.Unauthenticated
        403 -> AppError.Unknown("jellyfin_forbidden")
        404 -> AppError.NotFound
        in 500..599 -> AppError.NetworkUnavailable
        else -> AppError.Unknown("jellyfin_http_$status")
    }

    private companion object {
        val EMPTY_BODY = ByteArray(0).toRequestBody(null, 0, 0)
    }
}

/**
 * 列表/分页用的字段：**不含 `MediaSources`** —— 单个条目的 MediaSources 能到 KB 级，
 * 一页 50 条就是几十 KB 的无用负载。列表只需要能判断"能不能直连"的 `Container`。
 *
 * `ImageTags` 必须带上：所有 coverId 都由 `ImageTags.Primary` 决定
 * （实测漏掉它时，随机漫游/全部歌单/随机歌曲/最近添加这些列表界面全都没有封面，
 * 歌单的"前三首拼排"也因为曲目没有 coverId 而永远拼不出来）。
 */
internal const val LIST_ITEM_FIELDS =
    "DateCreated,UserData,RunTimeTicks,Container,AlbumArtist,Artists,IndexNumber," +
        "ParentIndexNumber,ChildCount,RecursiveItemCount,ProductionYear,AlbumCount,MediaType," +
        "ImageTags,AlbumId"

/** 单个条目用完整字段（`streamPlan` 要 MediaSources 判断直连还是转码）。 */
internal const val FULL_ITEM_FIELDS = "MediaSources,$LIST_ITEM_FIELDS"

@kotlinx.serialization.Serializable
internal data class JellyfinAuthRequest(val Username: String, val Pw: String)

internal const val DEFAULT_CLIENT_NAME = "MusicDock"
internal const val DEFAULT_CLIENT_VERSION = "0.1.0"

/**
 * Jellyfin 的鉴权头：`MediaBrowser Token="…", Client="…", Device="Android", DeviceId="…", Version="…"`。
 * 接口调用与播放器请求头共用这一份格式（见 `.trellis/spec/backend/jellyfin-contracts.md` §2）。
 */
internal fun jellyfinAuthorizationHeader(
    token: String?,
    deviceId: String,
    clientName: String = DEFAULT_CLIENT_NAME,
    clientVersion: String = DEFAULT_CLIENT_VERSION,
): String = buildString {
    append("MediaBrowser ")
    if (!token.isNullOrBlank()) append("Token=\"").append(token).append("\", ")
    append("Client=\"").append(clientName).append("\", ")
    append("Device=\"Android\", ")
    append("DeviceId=\"").append(deviceId).append("\", ")
    append("Version=\"").append(clientVersion).append("\"")
}

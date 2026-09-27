package com.fnmusic.tv.core.data.backend

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
internal class JellyfinApi(
    private val origin: HttpUrl,
    private val client: OkHttpClient,
    private val deviceId: String,
    private val clientName: String = "MusicDock",
    private val clientVersion: String = "0.1.0",
    private val tokenProvider: () -> String? = { null },
) {

    private fun authorizationHeader(token: String?): String = buildString {
        append("MediaBrowser ")
        if (!token.isNullOrBlank()) append("Token=\"").append(token).append("\", ")
        append("Client=\"").append(clientName).append("\", ")
        append("Device=\"Android\", ")
        append("DeviceId=\"").append(deviceId).append("\", ")
        append("Version=\"").append(clientVersion).append("\"")
    }

    /** 免登录的服务器信息：用于自动识别服务器类型与版本。 */
    suspend fun publicInfo(): JellyfinPublicInfoDto =
        request(Request.Builder().url(url("System/Info/Public")).get(), token = null)

    /** 登录：返回访问令牌与用户。 */
    suspend fun authenticate(username: String, password: String): JellyfinAuthResultDto {
        val body = ApiDecoder.json.encodeToString(
            JellyfinAuthRequest(Username = username, Pw = password),
        )
        return request(
            Request.Builder()
                .url(url("Users/AuthenticateByName"))
                .post(body.toRequestBody("application/json".toMediaType())),
            token = null,
        )
    }

    suspend fun me(): JellyfinUserDto =
        request(Request.Builder().url(url("Users/Me")).get(), token = requireToken())

    /** 随机取歌：`SortBy=Random` 一次成片（比飞牛的"探测 + 随机页"更省）。 */
    suspend fun randomTracks(userId: String, limit: Int): List<JellyfinItemDto> =
        items(
            Request.Builder().url(
                url("Items").newBuilder()
                    .addQueryParameter("userId", userId)
                    .addQueryParameter("IncludeItemTypes", "Audio")
                    .addQueryParameter("Recursive", "true")
                    .addQueryParameter("SortBy", "Random")
                    .addQueryParameter("Limit", limit.toString())
                    .addQueryParameter("Fields", ITEM_FIELDS)
                    .build(),
            ).get(),
        ).Items

    /** 按 id 取单个条目（拿 MediaSources 判断能不能直连）。 */
    suspend fun item(itemId: String, userId: String): JellyfinItemDto? =
        items(
            Request.Builder().url(
                url("Items").newBuilder()
                    .addQueryParameter("userId", userId)
                    .addQueryParameter("Ids", itemId)
                    .addQueryParameter("Fields", ITEM_FIELDS)
                    .build(),
            ).get(),
        ).Items.firstOrNull()

    // ---- URL 构造（实测：PlaybackInfo 不返回 URL，客户端自己拼） ----

    /** 封面：Jellyfin 按条目 id 取图；宽度给 fillWidth/fillHeight 得到方形裁切。 */
    fun imageUrl(itemId: String, width: Int?): String =
        url("Items/$itemId/Images/Primary").newBuilder().apply {
            if (width != null) {
                addQueryParameter("fillWidth", width.toString())
                addQueryParameter("fillHeight", width.toString())
                addQueryParameter("quality", "90")
            }
        }.build().toString()

    /** 直连原文件。 */
    fun directStreamUrl(itemId: String): String =
        url("Audio/$itemId/stream").newBuilder()
            .addQueryParameter("static", "true")
            .addQueryParameter("api_key", requireToken())
            .build().toString()

    /** HLS 转码（可拖动）；参数来自实测可用的那一组。 */
    fun hlsTranscodeUrl(itemId: String, maxStreamingBitrate: Int = 192_000): String =
        url("Audio/$itemId/stream").newBuilder()
            .addQueryParameter("api_key", requireToken())
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
                            continuation.resumeWithException(
                                AppException(classify(it.code, text)),
                            )
                        }
                    }
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
        const val ITEM_FIELDS =
            "MediaSources,DateCreated,UserData,RunTimeTicks,Container,AlbumArtist,Artists," +
                "IndexNumber,ParentIndexNumber,ChildCount,RecursiveItemCount"
    }
}

@kotlinx.serialization.Serializable
internal data class JellyfinAuthRequest(val Username: String, val Pw: String)

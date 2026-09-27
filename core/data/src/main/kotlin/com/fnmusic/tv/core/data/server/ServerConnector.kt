package com.fnmusic.tv.core.data.server

import com.fnmusic.tv.core.data.api.LoginResultDto
import com.fnmusic.tv.core.data.api.PasswordHash
import com.fnmusic.tv.core.data.api.TrimMusicApi
import com.fnmusic.tv.core.data.backend.ServerKind
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.AppException
import com.fnmusic.tv.core.model.ServerIdentity
import com.fnmusic.tv.core.model.User
import java.io.IOException
import okhttp3.OkHttpClient

/**
 * 一次成功连接的材料：规范化地址、服务器身份、API 客户端与会话访问码。
 *
 * 抽取阶段这里放的还是飞牛的 `TrimMusicApi`；接入 Jellyfin 时会改成按 [ServerKind]
 * 分叉的连接对象（各后端自带自己的客户端类型），会话状态机不需要跟着改。
 */
internal data class ServerConnection(
    val kind: ServerKind,
    val normalized: NormalizedServer,
    val identity: ServerIdentity,
    val api: TrimMusicApi,
    val access: ConnectionAccess,
)

/**
 * 各后端的"怎么连上服务器"：地址解析（含 FNID 中继）、安全码校验、登录、播放请求材料。
 * 会话状态机（内存令牌、账号档案、状态流转）留在 `SessionRepository`，
 * 与具体服务器形态相关的部分收在这里。
 */
internal interface ServerConnector {
    val kind: ServerKind

    /** 播放重挂判定的路径前缀：只重写落在它下面的流地址。 */
    val streamPathPrefix: String

    /** 播放器要带的请求头（各后端的鉴权差异都在这里消化）。 */
    fun playbackHeaders(token: String?, encodedAccessCode: String?, relayMode: Boolean): Map<String, String>

    /**
     * 解析用户输入并校验安全码：[restoredRelayMode] 非空表示"用已知的中继模式直接重连"
     * （恢复会话时不再做一遍候选探测）。
     */
    suspend fun connect(
        input: String,
        useHttps: Boolean,
        accessCode: String,
        restoredRelayMode: Boolean? = null,
    ): ServerConnection

    /** 登录并返回结果（令牌由调用方保管，连接对象只用它拼请求头）。 */
    suspend fun login(
        connection: ServerConnection,
        username: String,
        password: PasswordHash,
        deviceId: String,
    ): LoginResultDto

    /** 当前登录用户（会话校验用）。 */
    suspend fun me(connection: ServerConnection): User

    suspend fun logout(connection: ServerConnection)
}

/**
 * 飞牛连接器：收拢原有的 FNID 解析、安全码校验、`sys/config` 探测与登录调用。
 * 行为与抽取前 `SessionRepository` 内的实现逐字一致。
 */
internal class FnOsConnector(
    private val clientFactory: () -> OkHttpClient,
    private val tokenProvider: () -> String?,
) : ServerConnector {

    override val kind: ServerKind = ServerKind.FnOs

    /** 飞牛的流地址都挂在 `/music/api/v1/` 下面（重挂时的重写范围）。 */
    override val streamPathPrefix: String = ServerUrlNormalizer.API_PATH_PREFIX

    override fun playbackHeaders(
        token: String?,
        encodedAccessCode: String?,
        relayMode: Boolean,
    ): Map<String, String> = ConnectionAccess(encodedAccessCode, relayMode).headers(token)

    override suspend fun connect(
        input: String,
        useHttps: Boolean,
        accessCode: String,
        restoredRelayMode: Boolean?,
    ): ServerConnection {
        try {
            val client = clientFactory()
            val resolver = ConnectionResolver(client)
            val target = if (restoredRelayMode == null) {
                resolver.resolve(input, useHttps)
            } else {
                val normalized = (ServerUrlNormalizer.normalize(input, useHttps) as? ServerUrlResult.Valid)?.server
                    ?: throw AppException(AppError.Unknown("invalid_server"))
                ConnectionTarget(normalized, restoredRelayMode)
            }
            val access = resolver.verifyAccessCode(target, accessCode)
            val candidate = TrimMusicApi(target.server, client, tokenProvider, access)
            return ServerConnection(
                kind = kind,
                normalized = target.server,
                identity = candidate.systemConfig().toDomain(),
                api = candidate,
                access = access,
            )
        } catch (cause: IOException) {
            throw AppException(AppError.NetworkUnavailable, cause)
        }
    }

    override suspend fun login(
        connection: ServerConnection,
        username: String,
        password: PasswordHash,
        deviceId: String,
    ): LoginResultDto = connection.api.login(username, password, deviceId)

    override suspend fun me(connection: ServerConnection): User = connection.api.me().toDomain()

    override suspend fun logout(connection: ServerConnection) {
        connection.api.logout()
    }
}

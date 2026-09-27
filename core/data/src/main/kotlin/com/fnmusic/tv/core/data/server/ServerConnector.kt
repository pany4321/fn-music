package com.fnmusic.tv.core.data.server

import com.fnmusic.tv.core.data.api.PasswordHash
import com.fnmusic.tv.core.data.api.TrimMusicApi
import com.fnmusic.tv.core.data.backend.JellyfinApi
import com.fnmusic.tv.core.data.backend.jellyfinAuthorizationHeader
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.AppException
import com.fnmusic.tv.core.model.ServerGuid
import com.fnmusic.tv.core.model.ServerIdentity
import com.fnmusic.tv.core.model.ServerKind
import com.fnmusic.tv.core.model.User
import com.fnmusic.tv.core.model.UserGuid
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * 一次成功连接的材料：按后端分叉，各自带自己的客户端类型。
 * 会话状态机（内存令牌、账号档案、状态流转）只认这个接口，不认具体后端。
 */
internal sealed interface ServerConnection {
    val kind: ServerKind
    val normalized: NormalizedServer
    val identity: ServerIdentity

    /** 记进"最近服务器"/登录档案的地址：飞牛带接口前缀，Jellyfin 只有源站。 */
    val persistentServer: String

    /** 播放器与重挂用的服务基址。 */
    val apiBase: String

    /** 飞牛：`TrimMusicApi` + 安全码/中继。 */
    class FnOs(
        override val normalized: NormalizedServer,
        override val identity: ServerIdentity,
        val api: TrimMusicApi,
        val access: ConnectionAccess,
    ) : ServerConnection {
        override val kind: ServerKind get() = ServerKind.FnOs
        override val persistentServer: String get() = normalized.persistentApiBase()
        override val apiBase: String get() = api.apiBase()
    }

    /** Jellyfin：`JellyfinApi` + 用户 id（条目查询都要带 userId）。 */
    class Jellyfin(
        override val normalized: NormalizedServer,
        override val identity: ServerIdentity,
        val api: JellyfinApi,
        val userId: String,
    ) : ServerConnection {
        override val kind: ServerKind get() = ServerKind.Jellyfin
        override val persistentServer: String get() = normalized.origin.toString()
        override val apiBase: String get() = normalized.origin.toString()
    }
}

/** 连接参数：[accessCode]/[relayMode] 只有飞牛用得到（Jellyfin 忽略）。 */
internal data class ConnectRequest(
    val input: String,
    val useHttps: Boolean,
    val accessCode: String = "",
    /** 非空表示"用已知的中继模式直接重连"（飞牛恢复会话时不再做候选探测）。 */
    val relayMode: Boolean? = null,
)

/** 登录结果：令牌 + 用户 +（可能被补全的）连接。 */
internal class ServerLoginResult(
    val token: String,
    val user: User,
    val connection: ServerConnection,
)

/**
 * 各后端的"怎么连上服务器"：免登录识别、地址解析（含 FNID 中继）、安全码校验、登录、
 * 播放请求材料。会话状态机留在 `SessionRepository`，与服务器形态相关的部分收在这里。
 */
internal interface ServerConnector {
    val kind: ServerKind

    /** 播放重挂判定的路径前缀：只重写落在它下面的流地址。 */
    val streamPathPrefix: String

    /** 播放器要带的请求头（各后端的鉴权差异都在这里消化）。 */
    fun playbackHeaders(token: String?, encodedAccessCode: String?, relayMode: Boolean): Map<String, String>

    /**
     * 免登录识别这台服务器的身份（登录页判断类型与版本用）。
     * 返回 null 表示"这个地址不是我这种后端"——飞牛是兜底选择，所以直接返回 null。
     */
    suspend fun probe(input: String, useHttps: Boolean): ServerIdentity?

    /** 解析用户输入并建立连接（Jellyfin 此时还没有 userId，登录后由 [login] 补全）。 */
    suspend fun connect(request: ConnectRequest): ServerConnection

    /** 用明文密码登录（登录页输入的那次）。 */
    suspend fun login(
        connection: ServerConnection,
        username: String,
        password: String,
        deviceId: String,
    ): ServerLoginResult

    /**
     * 只用"已保存的密码哈希"免密重登（飞牛：支持）。
     * 返回 null 表示这个后端做不到（Jellyfin 无法从哈希还原密码），调用方改用令牌或要求重新输入。
     */
    suspend fun reLogin(
        connection: ServerConnection,
        username: String,
        savedHash: PasswordHash?,
        deviceId: String,
    ): ServerLoginResult?

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

    /** 飞牛不做识别探测：探测失败就是飞牛（登录页的兜底分支）。 */
    override suspend fun probe(input: String, useHttps: Boolean): ServerIdentity? = null

    override suspend fun connect(request: ConnectRequest): ServerConnection {
        try {
            val client = clientFactory()
            val resolver = ConnectionResolver(client)
            val target = if (request.relayMode == null) {
                resolver.resolve(request.input, request.useHttps)
            } else {
                val normalized = (ServerUrlNormalizer.normalize(request.input, request.useHttps) as? ServerUrlResult.Valid)?.server
                    ?: throw AppException(AppError.Unknown("invalid_server"))
                ConnectionTarget(normalized, request.relayMode)
            }
            val access = resolver.verifyAccessCode(target, request.accessCode)
            val candidate = TrimMusicApi(target.server, client, tokenProvider, access)
            return ServerConnection.FnOs(
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
        password: String,
        deviceId: String,
    ): ServerLoginResult {
        val fnOs = connection as? ServerConnection.FnOs
            ?: throw AppException(AppError.Unauthenticated)
        val chars = password.toCharArray()
        val result = try {
            fnOs.api.login(username, PasswordHash.fromPlaintext(chars), deviceId)
        } finally {
            chars.fill('\u0000')
        }
        return ServerLoginResult(result.userToken, result.user.toDomain(), fnOs)
    }

    override suspend fun reLogin(
        connection: ServerConnection,
        username: String,
        savedHash: PasswordHash?,
        deviceId: String,
    ): ServerLoginResult? {
        val fnOs = connection as? ServerConnection.FnOs ?: return null
        val hash = savedHash ?: return null
        val result = fnOs.api.login(username, hash, deviceId)
        return ServerLoginResult(result.userToken, result.user.toDomain(), fnOs)
    }

    override suspend fun me(connection: ServerConnection): User {
        val fnOs = connection as? ServerConnection.FnOs
            ?: throw AppException(AppError.Unauthenticated)
        return fnOs.api.me().toDomain()
    }

    override suspend fun logout(connection: ServerConnection) {
        (connection as? ServerConnection.FnOs)?.api?.logout()
    }
}

/**
 * Jellyfin 连接器：`/System/Info/Public` 免登录识别 + `/Users/AuthenticateByName` 登录。
 *
 * 与飞牛的两处关键差异：
 * 1. **没有服务端密码哈希**：Jellyfin 要明文密码，所以我们没法像飞牛那样"存哈希免密重登"，
 *    免密续期只能靠 AccessToken（[reLogin] 返回 null）。
 * 2. **没有安全码/中继**：局域网直连即可，播放令牌直接拼进流地址（`api_key=`）。
 */
internal class JellyfinConnector(
    private val clientFactory: () -> OkHttpClient,
    private val tokenProvider: () -> String?,
    private val deviceId: String,
) : ServerConnector {

    override val kind: ServerKind = ServerKind.Jellyfin

    /** Jellyfin 的流地址都在 `/Audio/` 下（`/Audio/{id}/stream`）。 */
    override val streamPathPrefix: String = "/Audio/"

    override fun playbackHeaders(
        token: String?,
        encodedAccessCode: String?,
        relayMode: Boolean,
    ): Map<String, String> = token
        ?.takeIf(String::isNotBlank)
        ?.let { mapOf("Authorization" to jellyfinAuthorizationHeader(it, deviceId)) }
        .orEmpty()

    /**
     * 命中 `/System/Info/Public` 即 Jellyfin。
     * 地址没写端口时补试一次 Jellyfin 默认端口（8096/8920）——用户常常只输 IP。
     */
    override suspend fun probe(input: String, useHttps: Boolean): ServerIdentity? {
        // 探测失败要"快速失败"：飞牛地址上这个端点通常 404，超时也不该拖住登录页。
        val client = clientFactory().newBuilder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        for (origin in probeOrigins(input, useHttps)) {
            val identity = runCatching { publicIdentity(origin, client) }.getOrNull()
            if (identity != null) return identity
        }
        return null
    }

    override suspend fun connect(request: ConnectRequest): ServerConnection {
        val client = clientFactory()
        val normalized = (ServerUrlNormalizer.normalize(request.input, request.useHttps) as? ServerUrlResult.Valid)?.server
            ?: throw AppException(AppError.Unknown("invalid_server"))
        val api = JellyfinApi(normalized.origin, client, deviceId, tokenProvider = tokenProvider)
        val identity = publicIdentity(normalized.origin, client)
            ?: throw AppException(AppError.Unknown("not_jellyfin"))
        return ServerConnection.Jellyfin(normalized, identity, api, userId = "")
    }

    override suspend fun login(
        connection: ServerConnection,
        username: String,
        password: String,
        deviceId: String,
    ): ServerLoginResult {
        val jellyfin = connection as? ServerConnection.Jellyfin
            ?: throw AppException(AppError.Unauthenticated)
        val result = jellyfin.api.authenticate(username, password)
        val user = User(UserGuid(result.User.Id), result.User.Name, null)
        return ServerLoginResult(
            token = result.AccessToken,
            user = user,
            connection = ServerConnection.Jellyfin(
                normalized = jellyfin.normalized,
                identity = jellyfin.identity,
                api = jellyfin.api,
                userId = result.User.Id,
            ),
        )
    }

    /** Jellyfin 不能用保存的哈希重登（服务端只认明文）；续期靠 AccessToken。 */
    override suspend fun reLogin(
        connection: ServerConnection,
        username: String,
        savedHash: PasswordHash?,
        deviceId: String,
    ): ServerLoginResult? = null

    override suspend fun me(connection: ServerConnection): User {
        val jellyfin = connection as? ServerConnection.Jellyfin
            ?: throw AppException(AppError.Unauthenticated)
        val me = jellyfin.api.me()
        return User(UserGuid(me.Id), me.Name, null)
    }

    override suspend fun logout(connection: ServerConnection) {
        (connection as? ServerConnection.Jellyfin)?.api?.logout()
    }

    private suspend fun publicIdentity(origin: HttpUrl, client: OkHttpClient): ServerIdentity? {
        val info = JellyfinApi(origin, client, deviceId).publicInfo()
        val id = info.Id?.takeIf(String::isNotBlank) ?: return null
        val version = info.Version?.takeIf(String::isNotBlank) ?: return null
        return ServerIdentity(
            guid = ServerGuid(id),
            name = info.ServerName?.takeIf(String::isNotBlank) ?: "Jellyfin",
            serverVersion = version,
            mediaServerVersion = "",
            kind = ServerKind.Jellyfin,
        )
    }

    private fun probeOrigins(input: String, useHttps: Boolean): List<HttpUrl> {
        val normalized = (ServerUrlNormalizer.normalize(input, useHttps) as? ServerUrlResult.Valid)?.server
            ?: return emptyList()
        val origins = mutableListOf(normalized.origin)
        if (!ServerUrlNormalizer.hasExplicitPort(input)) {
            origins += normalized.origin.newBuilder()
                .port(if (useHttps) JELLYFIN_HTTPS_PORT else JELLYFIN_HTTP_PORT)
                .build()
        }
        return origins
    }

    private companion object {
        const val JELLYFIN_HTTP_PORT = 8096
        const val JELLYFIN_HTTPS_PORT = 8920
        const val PROBE_TIMEOUT_SECONDS = 3L
    }
}

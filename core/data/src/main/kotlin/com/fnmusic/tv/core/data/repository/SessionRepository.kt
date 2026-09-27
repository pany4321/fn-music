package com.fnmusic.tv.core.data.repository

import android.content.Context
import com.fnmusic.tv.core.data.api.PasswordHash
import com.fnmusic.tv.core.data.api.TrimMusicApi
import com.fnmusic.tv.core.data.api.isRetryableRequestFailure
import com.fnmusic.tv.core.data.security.SecureSessionPayload
import com.fnmusic.tv.core.data.security.SecureSessionRead
import com.fnmusic.tv.core.data.security.SecureTokenStore
import com.fnmusic.tv.core.data.security.StoredLoginProfile
import com.fnmusic.tv.core.data.security.TokenStore
import com.fnmusic.tv.core.data.server.ConnectionAccess
import com.fnmusic.tv.core.data.server.FnOsConnector
import com.fnmusic.tv.core.data.server.ServerConnection
import com.fnmusic.tv.core.data.server.ServerConnector
import com.fnmusic.tv.core.data.server.ServerUrlNormalizer
import com.fnmusic.tv.core.model.AppError
import com.fnmusic.tv.core.model.AppException
import com.fnmusic.tv.core.model.PlaybackAuth
import com.fnmusic.tv.core.model.ServerIdentity
import com.fnmusic.tv.core.model.User
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

data class LoginHistoryEntry(
    val id: String,
    val server: String,
    val username: String,
    val useHttps: Boolean,
)

/** Remembered playback credentials for media-button resume; read-only projection. */
data class PersistedPlaybackAuth(
    val headers: Map<String, String>,
)

data class LoginDraft(
    val profileId: String,
    val server: String,
    val username: String,
    val useHttps: Boolean,
    val accessCode: String,
    val hasSavedPassword: Boolean = true,
)

sealed interface SessionState {
    data object Loading : SessionState
    data class Recovering(
        val server: String,
        val username: String?,
        val attempt: Int,
        val error: AppError,
    ) : SessionState
    data class SignedOut(
        val savedServer: String = "",
        val recentServers: List<String> = emptyList(),
        val loginHistory: List<LoginHistoryEntry> = emptyList(),
        val selectedProfileId: String? = null,
        val error: AppError? = null,
    ) : SessionState
    data class SignedIn(val server: ServerIdentity, val user: User) : SessionState
}

class SessionRepository internal constructor(
    context: Context,
    private val tokenStore: TokenStore,
    private val clientFactory: () -> OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryDelaysMillis: LongArray = DEFAULT_RETRY_DELAYS,
) {
    constructor(context: Context) : this(context, SecureTokenStore(context), TrimMusicApi::client)

    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private var memoryToken: String? = null
    private var memoryAccessCode: String? = null
    private var memoryProfileId: String? = null
    private var rememberedCredentialsLoaded = false
    private var secureSessionUnreadable = false
    @Volatile private var securePayload = SecureSessionPayload()
    private var connectionAccess = ConnectionAccess()
    private var connection: ServerConnection? = null

    /**
     * 与具体服务器形态相关的一切（地址解析、安全码、FNID 中继、登录、播放请求头）都在
     * connector 里；接入 Jellyfin 时换成按会话类型选择的实现即可。
     */
    private val connector: ServerConnector = FnOsConnector(clientFactory) { memoryToken }

    private val authVerification = Mutex()
    private val credentialsMutex = Mutex()

    val savedServer: String get() = preferences.getString(SERVER, "").orEmpty()
    val recentServers: List<String>
        get() = (0 until MAX_LOGIN_HISTORY).mapNotNull { index ->
            preferences.getString("$RECENT_SERVER_PREFIX$index", null)
        }
    val deviceId: String by lazy {
        preferences.getString(DEVICE, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(DEVICE, it).apply()
        }
    }

    suspend fun restore() {
        loadRememberedCredentials()
        val profile = activeProfile()
        val targetServer = profile?.server ?: savedServer
        if (targetServer.isBlank() || (memoryToken == null && profile == null)) {
            _state.value = signedOut(
                error = AppError.Unknown("secure_session_unreadable").takeIf { secureSessionUnreadable },
            )
            return
        }

        var attempt = 1
        while (true) {
            try {
                restoreAttempt(targetServer, profile)
                return
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                connection = null
                val error = (cause as? AppException)?.error ?: AppError.Unknown()
                if (!cause.isRetryableRestoreFailure()) {
                    if (error == AppError.Unauthenticated || error == AppError.AccountDisabled) {
                        clearCurrentToken()
                    }
                    _state.value = signedOut(error, profile?.id)
                    return
                }
                _state.value = SessionState.Recovering(
                    server = ServerUrlNormalizer.editableInput(targetServer, false).address,
                    username = profile?.username,
                    attempt = attempt,
                    error = error,
                )
                delay(retryDelaysMillis[(attempt - 1).coerceAtMost(retryDelaysMillis.lastIndex)])
                attempt += 1
            }
        }
    }

    suspend fun login(
        serverInput: String,
        useHttps: Boolean,
        username: String,
        password: CharArray,
        remember: Boolean,
        accessCode: CharArray = charArrayOf(),
    ) {
        try {
            loadRememberedCredentials()
            val passwordHash = PasswordHash.fromPlaintext(password)
            loginWithCredential(
                serverInput = serverInput,
                useHttps = useHttps,
                username = username,
                passwordHash = passwordHash,
                remember = remember,
                accessCode = accessCode.concatToString(),
            )
        } finally {
            password.fill('\u0000')
            accessCode.fill('\u0000')
        }
    }

    suspend fun loginWithHistory(
        profileId: String,
        accessCode: CharArray? = null,
        remember: Boolean = true,
    ) {
        try {
            loadRememberedCredentials()
            val profile = securePayload.profiles.firstOrNull { it.id == profileId }
                ?: throw AppException(AppError.Unauthenticated)
            val passwordHash = PasswordHash.parse(profile.passwordSha256)
                ?: throw AppException(AppError.Unknown("invalid_saved_password"))
            loginWithCredential(
                serverInput = profile.server,
                useHttps = false,
                username = profile.username,
                passwordHash = passwordHash,
                remember = remember,
                accessCode = accessCode?.concatToString() ?: profile.accessCode.orEmpty(),
                restoredRelayMode = profile.relayMode,
                existingProfileId = profile.id,
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            val error = (cause as? AppException)?.error ?: AppError.Unknown()
            _state.value = signedOut(error, profileId)
            throw cause
        } finally {
            accessCode?.fill('\u0000')
        }
    }

    fun loginDraft(profileId: String): LoginDraft? {
        val profile = securePayload.profiles.firstOrNull { it.id == profileId } ?: return null
        val editable = ServerUrlNormalizer.editableInput(profile.server, false)
        return LoginDraft(
            profileId = profile.id,
            server = editable.address,
            username = profile.username,
            useHttps = editable.useHttps,
            accessCode = profile.accessCode.orEmpty(),
            hasSavedPassword = PasswordHash.parse(profile.passwordSha256) != null,
        )
    }

    suspend fun deleteLoginHistory(profileId: String) {
        val signedIn = _state.value is SessionState.SignedIn
        updateSecurePayload { payload ->
            val profiles = payload.profiles.filterNot { it.id == profileId }
            payload.copy(
                activeProfileId = payload.activeProfileId.takeUnless { it == profileId },
                profiles = profiles,
            )
        }
        if (memoryProfileId == profileId && !signedIn) {
            memoryProfileId = null
            memoryToken = null
            memoryAccessCode = null
        }
        if (!signedIn) _state.value = signedOut(selectedProfileId = securePayload.activeProfileId)
    }

    suspend fun clearLoginHistory() {
        val signedIn = _state.value is SessionState.SignedIn
        updateSecurePayload { SecureSessionPayload() }
        preferences.edit().apply {
            remove(SERVER)
            remove(RELAY_MODE)
            repeat(MAX_LOGIN_HISTORY) { remove("$RECENT_SERVER_PREFIX$it") }
        }.apply()
        if (!signedIn) {
            memoryProfileId = null
            memoryToken = null
            memoryAccessCode = null
        }
        if (!signedIn) _state.value = signedOut()
    }

    fun showLogin() {
        connection = null
        _state.value = signedOut(selectedProfileId = memoryProfileId)
    }

    suspend fun logout() {
        try {
            connection?.let { connector.logout(it) }
        } finally {
            clearCurrentToken()
            connection = null
            _state.value = signedOut(selectedProfileId = memoryProfileId)
        }
    }

    /**
     * 播放器要用的会话材料：地址、请求头（由 connector 按后端组装）、缓存命名空间
     * 与重挂判定的路径前缀。
     */
    fun playbackAuth(): PlaybackAuth {
        val current = requireSignedIn()
        val token = memoryToken ?: throw AppException(AppError.Unauthenticated)
        return PlaybackAuth(
            apiBase = current.api.apiBase(),
            headers = connector.playbackHeaders(
                token = token,
                encodedAccessCode = connectionAccess.encodedAccessCode,
                relayMode = connectionAccess.relayMode,
            ),
            cacheNamespace = cacheNamespace(),
            streamPathPrefix = connector.streamPathPrefix,
        )
    }

    private fun requireSignedIn(): ServerConnection =
        connection ?: throw AppException(AppError.Unauthenticated)

    internal fun requireApi(): TrimMusicApi = connection?.api ?: throw AppException(AppError.Unauthenticated)

    fun cacheNamespace(): String {
        val current = _state.value as? SessionState.SignedIn ?: throw AppException(AppError.Unauthenticated)
        return "${current.server.guid.value}:${current.user.guid.value}"
    }

    /** 已保存账号列表，供应用内“切换账号”界面使用（与 SignedOut 状态同源）。 */
    suspend fun savedLoginEntries(): List<LoginHistoryEntry> {
        loadRememberedCredentials()
        return securePayload.profiles.map { profile ->
            val editable = ServerUrlNormalizer.editableInput(profile.server, false)
            LoginHistoryEntry(profile.id, editable.address, profile.username, editable.useHttps)
        }
    }

    internal suspend fun <T> authenticated(block: suspend (TrimMusicApi) -> T): T {
        val current = requireApi()
        return try {
            block(current)
        } catch (cause: AppException) {
            when (cause.error) {
                AppError.AccountDisabled -> invalidateSession(AppError.AccountDisabled)
                AppError.Unauthenticated -> verifyCurrentSession(current)
                else -> Unit
            }
            throw cause
        }
    }

    suspend fun verifyCurrentSession(): Boolean {
        val current = connection?.api ?: return false
        return verifyCurrentSession(current)
    }

    /**
     * Re-runs server discovery for the signed-in account and rebinds the API
     * connection (and, via the coordinator, playback credentials) to a reachable
     * candidate — the car scenario of switching from a home LAN IP onto the
     * relay. Returns false, without disturbing the current binding, when the
     * session is not signed in or no candidate is reachable.
     */
    suspend fun rehomeConnection(): Boolean {
        if (_state.value !is SessionState.SignedIn) return false
        loadRememberedCredentials()
        val profile = activeProfile()
        val targetServer = profile?.server ?: savedServer
        if (targetServer.isBlank()) return false
        return try {
            restoreAttempt(targetServer, profile)
            true
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Best-effort persisted playback credentials for cold-start media-button
     * resume. Reads encrypted storage at most once and never changes session state.
     */
    suspend fun persistedPlaybackAuth(): PersistedPlaybackAuth? {
        loadRememberedCredentials()
        val token = memoryToken ?: return null
        val profile = activeProfile()
        val accessCodeHeader = connectionAccess.encodedAccessCode
            ?: profile?.accessCode?.takeIf(String::isNotBlank)?.let { code ->
                ConnectionAccess.from(code, relayMode = false).encodedAccessCode
            }
        val relayMode = profile?.relayMode ?: savedRelayMode
        return PersistedPlaybackAuth(
            headers = connector.playbackHeaders(token, accessCodeHeader, relayMode),
        )
    }

    private suspend fun verifyCurrentSession(candidate: TrimMusicApi): Boolean = authVerification.withLock {
        if (connection?.api !== candidate) return@withLock connection != null
        try {
            candidate.me()
            true
        } catch (cause: AppException) {
            if (cause.error == AppError.Unauthenticated || cause.error == AppError.AccountDisabled) {
                invalidateSession(cause.error)
                false
            } else {
                true
            }
        }
    }

    private suspend fun restoreAttempt(targetServer: String, profile: StoredLoginProfile?) {
        val accessCode = profile?.accessCode.orEmpty().ifBlank { memoryAccessCode.orEmpty() }
        val connected = connector.connect(
            input = targetServer,
            useHttps = false,
            accessCode = accessCode,
            restoredRelayMode = profile?.relayMode ?: savedRelayMode,
        )
        val token = memoryToken
        if (token != null) {
            try {
                completeSignIn(connected, connector.me(connected))
                return
            } catch (cause: AppException) {
                if (cause.error != AppError.Unauthenticated || profile == null) throw cause
                clearCurrentToken()
            }
        }

        val savedProfile = profile ?: throw AppException(AppError.Unauthenticated)
        val passwordHash = PasswordHash.parse(savedProfile.passwordSha256)
            ?: throw AppException(AppError.Unknown("invalid_saved_password"))
        val result = connected.api.login(savedProfile.username, passwordHash, deviceId)
        memoryToken = result.userToken
        memoryAccessCode = savedProfile.accessCode
        memoryProfileId = savedProfile.id
        persistProfile(savedProfile.copy(userToken = result.userToken, lastUsedAt = clock()), makeActive = true)
        completeSignIn(connected, result.user.toDomain())
    }

    private suspend fun loginWithCredential(
        serverInput: String,
        useHttps: Boolean,
        username: String,
        passwordHash: PasswordHash,
        remember: Boolean,
        accessCode: String,
        restoredRelayMode: Boolean? = null,
        existingProfileId: String? = null,
    ) {
        val connected = connector.connect(serverInput, useHttps, accessCode, restoredRelayMode)
        val result = connector.login(connected, username, passwordHash, deviceId)
        memoryToken = result.userToken
        memoryAccessCode = accessCode.takeIf(String::isNotBlank)
        rememberedCredentialsLoaded = true
        val canonicalServer = connected.normalized.persistentApiBase()
        val matching = securePayload.profiles.firstOrNull {
            it.server == canonicalServer && it.username == username
        }
        if (remember) {
            val profile = StoredLoginProfile(
                id = existingProfileId ?: matching?.id ?: UUID.randomUUID().toString(),
                server = canonicalServer,
                relayMode = connected.access.relayMode,
                username = username,
                passwordSha256 = passwordHash.encoded,
                accessCode = accessCode.takeIf(String::isNotBlank),
                userToken = result.userToken,
                lastUsedAt = clock(),
            )
            memoryProfileId = profile.id
            persistProfile(profile, makeActive = true)
            clearLegacyCredentials()
        } else {
            memoryProfileId = null
            removeMatchingProfile(canonicalServer, username)
            clearLegacyCredentials()
        }
        recordServer(canonicalServer, connected.access.relayMode)
        completeSignIn(connected, result.user.toDomain())
    }

    private fun completeSignIn(connected: ServerConnection, user: User) {
        connection = connected
        connectionAccess = connected.access
        _state.value = SessionState.SignedIn(connected.identity, user)
    }

    private suspend fun invalidateSession(error: AppError) {
        clearCurrentToken()
        connection = null
        _state.value = signedOut(error, memoryProfileId)
    }

    private suspend fun clearCurrentToken() {
        memoryToken = null
        connectionAccess = ConnectionAccess()
        val profileId = memoryProfileId
        if (profileId != null && securePayload.profiles.any { it.id == profileId }) {
            updateSecurePayload { payload ->
                payload.copy(profiles = payload.profiles.map { profile ->
                    if (profile.id == profileId) profile.copy(userToken = null) else profile
                })
            }
        } else {
            clearLegacyCredentials()
        }
    }

    private suspend fun persistProfile(profile: StoredLoginProfile, makeActive: Boolean) {
        updateSecurePayload { payload ->
            val profiles = (listOf(profile) + payload.profiles.filterNot { it.id == profile.id })
                .sortedByDescending(StoredLoginProfile::lastUsedAt)
                .take(MAX_LOGIN_HISTORY)
            payload.copy(
                activeProfileId = if (makeActive) profile.id else payload.activeProfileId,
                profiles = profiles,
            )
        }
    }

    private suspend fun removeMatchingProfile(server: String, username: String) {
        updateSecurePayload { payload ->
            val removedIds = payload.profiles.filter { it.server == server && it.username == username }.map { it.id }.toSet()
            payload.copy(
                activeProfileId = payload.activeProfileId.takeUnless(removedIds::contains),
                profiles = payload.profiles.filterNot { it.id in removedIds },
            )
        }
    }

    private suspend fun updateSecurePayload(
        transform: (SecureSessionPayload) -> SecureSessionPayload,
    ): SecureSessionPayload = credentialsMutex.withLock {
        val updated = transform(securePayload)
        withContext(Dispatchers.IO) {
            if (updated.profiles.isEmpty()) tokenStore.clearSession() else tokenStore.writeSession(updated)
        }
        securePayload = updated
        updated
    }

    private suspend fun clearLegacyCredentials() = withContext(Dispatchers.IO) {
        tokenStore.clear()
        tokenStore.clearAccessCode()
    }

    private fun recordServer(server: String, relayMode: Boolean) {
        val updated = (listOf(server) + recentServers).distinct().take(MAX_LOGIN_HISTORY)
        preferences.edit().apply {
            putString(SERVER, server)
            putBoolean(RELAY_MODE, relayMode)
            repeat(MAX_LOGIN_HISTORY) { remove("$RECENT_SERVER_PREFIX$it") }
            updated.forEachIndexed { index, value -> putString("$RECENT_SERVER_PREFIX$index", value) }
        }.apply()
    }

    private fun signedOut(
        error: AppError? = null,
        selectedProfileId: String? = securePayload.activeProfileId,
    ) = SessionState.SignedOut(
        savedServer = selectedProfileId?.let(::loginDraft)?.server ?: savedServer,
        recentServers = recentServers,
        loginHistory = securePayload.profiles.map { profile ->
            val editable = ServerUrlNormalizer.editableInput(profile.server, false)
            LoginHistoryEntry(profile.id, editable.address, profile.username, editable.useHttps)
        },
        selectedProfileId = selectedProfileId,
        error = error,
    )

    private suspend fun loadRememberedCredentials() = credentialsMutex.withLock {
        if (rememberedCredentialsLoaded) return@withLock
        val (session, legacyToken, legacyAccessCode) = withContext(Dispatchers.IO) {
            Triple(tokenStore.readSession(), tokenStore.read(), tokenStore.readAccessCode())
        }
        when (session) {
            SecureSessionRead.Missing -> Unit
            is SecureSessionRead.Ready -> securePayload = session.payload
            SecureSessionRead.Unreadable -> secureSessionUnreadable = true
        }
        val active = activeProfile()
        memoryProfileId = active?.id
        memoryToken = active?.userToken ?: legacyToken
        memoryAccessCode = active?.accessCode ?: legacyAccessCode
        rememberedCredentialsLoaded = true
    }

    private fun activeProfile(): StoredLoginProfile? = securePayload.activeProfileId?.let { activeId ->
        securePayload.profiles.firstOrNull { it.id == activeId }
    }

    private fun Throwable.isRetryableRestoreFailure(): Boolean {
        val exception = this as? AppException ?: return false
        if (exception.error != AppError.NetworkUnavailable) return false
        return exception.isRetryableRequestFailure || exception.cause is IOException
    }

    private companion object {
        const val SERVER = "server"
        const val DEVICE = "device_id"
        const val RELAY_MODE = "relay_mode"
        const val RECENT_SERVER_PREFIX = "recent_server_"
        const val MAX_LOGIN_HISTORY = 5
        val DEFAULT_RETRY_DELAYS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000, 30_000)
    }

    private val savedRelayMode: Boolean get() = preferences.getBoolean(RELAY_MODE, false)
}

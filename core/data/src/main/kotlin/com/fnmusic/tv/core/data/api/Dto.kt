package com.fnmusic.tv.core.data.api

import com.fnmusic.tv.core.model.Genre
import com.fnmusic.tv.core.model.CollectionGuid
import com.fnmusic.tv.core.model.Album
import com.fnmusic.tv.core.model.Artist
import com.fnmusic.tv.core.model.LyricDocument
import com.fnmusic.tv.core.model.Playlist
import com.fnmusic.tv.core.model.RoamNode
import com.fnmusic.tv.core.model.RoamWindow
import com.fnmusic.tv.core.model.ServerGuid
import com.fnmusic.tv.core.model.ServerIdentity
import com.fnmusic.tv.core.model.SharedLibrary
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.TrackGuid
import com.fnmusic.tv.core.model.User
import com.fnmusic.tv.core.model.UserGuid
import kotlinx.serialization.Serializable

@Serializable
data class SystemConfigDto(
    val serverGUID: String,
    val serverName: String,
    val serverVersion: String,
    val mediasrvVersion: String,
) {
    fun toDomain() = ServerIdentity(ServerGuid(serverGUID), serverName, serverVersion, mediasrvVersion)
}

@Serializable data class PasswordLoginRequest(val username: String, val password: String, val deviceId: String)
@Serializable data class FavoriteTrackRequest(val trackGUID: String)

@Serializable data class PlaylistAddTrackRequest(val guid: String, val trackGUIDs: List<String>)

/** 新建歌单请求：coverId 用服务端约定的 playlist_<32位hex> 形式。 */
@Serializable data class PlaylistCreateRequest(val coverId: String, val name: String)

/** 新建歌单返回：不同版本服务端把新 guid 放在不同层级，逐层兜底解析。 */
@Serializable data class PlaylistCreateResultDto(
    val guid: String? = null,
    val playlistGUID: String? = null,
    val playlistGuid: String? = null,
    val playlist: PlaylistGuidRefDto? = null,
) {
    fun guidOrNull(): String? = guid ?: playlistGUID ?: playlistGuid ?: playlist?.guid
}

@Serializable data class PlaylistGuidRefDto(val guid: String? = null)
@Serializable data class LoginResultDto(val userToken: String, val user: UserDto)

@Serializable
data class UserDto(val guid: String, val name: String) {
    fun toDomain() = User(UserGuid(guid), name, null)
}

@Serializable
data class PlaylistDto(val guid: String, val name: String, val coverId: String? = null) {
    fun toDomain() = Playlist(CollectionGuid(guid), name, coverId)
}

@Serializable data class PlaylistDetailDto(
    val guid: String,
    val name: String,
    val coverId: String? = null,
    val trackCount: Int = 0,
) {
    fun toDomain() = Playlist(CollectionGuid(guid), name, coverId, trackCount)
}

@Serializable data class GenreDto(
    val guid: String,
    val name: String,
    val coverId: String? = null,
    val trackCount: Int = 0,
)

fun GenreDto.toDomain() = Genre(CollectionGuid(guid), name, coverId, trackCount)


@Serializable
data class ArtistDto(
    val guid: String,
    val name: String,
    val coverId: String? = null,
    val trackCount: Int? = null,
    val albumCount: Int? = null,
) {
    fun toDomain() = Artist(CollectionGuid(guid), name, coverId, trackCount, albumCount)
}

@Serializable
data class AlbumDto(
    val guid: String,
    val name: String,
    val coverId: String? = null,
    val releaseDate: String? = null,
    val artists: List<ArtistDto> = emptyList(),
    val trackCount: Int? = null,
) {
    fun toDomain() = Album(
        CollectionGuid(guid),
        name,
        artists.joinToString(" / ") { it.name }.ifBlank { null },
        coverId,
        trackCount,
        releaseDate,
    )
}

@Serializable
data class AudioSpecDto(
    val codec: String = "",
    val container: String = "",
    val duration: Long = 0,
    val bitrate: Long = 0,
) {
    fun displayFormat(): String? = container.ifBlank { codec }.trim().uppercase().takeIf(String::isNotBlank)
}

@Serializable
data class TrackDto(
    val guid: String,
    val title: String,
    val coverId: String? = null,
    val duration: Long = 0,
    val isCue: Boolean = false,
    val album: AlbumDto? = null,
    val artists: List<ArtistDto> = emptyList(),
    val audioSpec: AudioSpecDto = AudioSpecDto(),
    val accessStatus: Int? = null,
    val isFavorite: Boolean = false,
) {
    fun toDomain(sourceAudioSpec: AudioSpecDto = audioSpec) = Track(
        guid = TrackGuid(guid),
        title = title,
        artistName = artists.joinToString(" / ") { it.name }.ifBlank { null },
        albumName = album?.name,
        coverId = coverId,
        durationMs = duration.takeIf { it > 0 },
        isCue = isCue,
        accessStatus = accessStatus,
        audioFormat = sourceAudioSpec.displayFormat(),
        isFavorite = isFavorite,
        unplayableReason = fnOsUnplayableReason(
            container = sourceAudioSpec.container,
            codec = sourceAudioSpec.codec,
        ),
    )
}

/**
 * 飞牛源没有转码兜底（`track/stream` 实测为原文件直出，见交接文档"飞牛转码能力实测"）：
 * 客户端解码器放不了的格式在映射期就打上"不可播"，列表里直接说明原因，
 * 替代"点播放 → 转圈 → 播放失败"。判定用已知解不了的格式清单（黑名单），
 * 不认识的格式宁可放行让它试 —— 黑名单只会漏报不会误伤。
 */
internal fun fnOsUnplayableReason(container: String, codec: String): String? {
    val containerLower = container.trim().lowercase()
    val codecLower = codec.trim().lowercase()
    val format = containerLower.ifBlank { codecLower }
    if (format.isBlank()) return null
    return when {
        // DSD（dsf/dff 两种打包、msbf/lsbf 两种位序）ExoPlayer 一律解不了：
        // codec 与容器都要看（实测曲库里 codec=dsd_msbf 而容器写 dsf 的都有）
        "dsd" in codecLower || "dsd" in format -> "DSD 格式暂不支持播放"
        // 已知 ExoPlayer 核心不带的音频编码
        format in setOf("ape", "wma", "wv", "tak") || codecLower in setOf("ape", "wma", "wv", "tak") ->
            "该格式暂不支持播放"
        // 裸 PCM 流（没有 WAV 等容器包着）无法直接喂给解码器
        containerLower.isBlank() && codecLower.startsWith("pcm_") -> "该格式暂不支持播放"
        else -> null
    }
}

@Serializable data class SortedPageListDto<T>(val list: List<T> = emptyList(), val total: Int = list.size, val sort: String = "")

@Serializable
data class SharedLibraryDto(
    val guid: String,
    val name: String,
    val accessStatus: Int = -1,
    val updatedAt: Long = 0,
) {
    fun toDomain() = SharedLibrary(CollectionGuid(guid), name, accessStatus, updatedAt)
}

@Serializable data class ListDto<T>(val list: List<T> = emptyList())

@Serializable
data class TrackMetadataDto(val track: TrackDto, val audioSpec: AudioSpecDto = AudioSpecDto()) {
    fun toDomain(): Track = track.toDomain(audioSpec.takeIf { it.displayFormat() != null } ?: track.audioSpec)
}

@Serializable
data class LyricDto(
    val guid: String,
    val content: String,
    val isLRC: Boolean,
    val offset: Long = 0,
) {
    fun toDomain() = LyricDocument(guid, content, isLRC, offset)
}

@Serializable data class LyricListDto(val list: List<LyricDto> = emptyList(), val preferred: String? = null)

@Serializable
data class RoamNodeDto(val roamId: String, val track: TrackDto) {
    fun toDomain() = RoamNode(roamId, track.toDomain())
}

@Serializable data class RoamStartDto(val current: RoamNodeDto, val next: RoamNodeDto? = null)
@Serializable data class RoamWindowDto(val previous: RoamNodeDto? = null, val current: RoamNodeDto, val next: RoamNodeDto? = null) {
    fun toDomain() = RoamWindow(previous?.toDomain(), current.toDomain(), next?.toDomain())
}

@Serializable data class TempTokenRequest(val usage: String, val scopes: List<String>, val resourceGUID: String, val ttlSeconds: Int)
@Serializable data class TempTokenDto(val token: String, val expiredAt: Long)

@Serializable data class PageListDto<T>(val list: List<T> = emptyList(), val total: Int = list.size)

@Serializable
data class TranscodeResultDto(
    val status: String? = null,
    val url: String? = null,
)

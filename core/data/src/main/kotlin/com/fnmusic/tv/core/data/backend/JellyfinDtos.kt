package com.fnmusic.tv.core.data.backend

import com.fnmusic.tv.core.model.Album
import com.fnmusic.tv.core.model.Artist
import com.fnmusic.tv.core.model.CollectionGuid
import com.fnmusic.tv.core.model.Genre
import com.fnmusic.tv.core.model.Page
import com.fnmusic.tv.core.model.Playlist
import com.fnmusic.tv.core.model.Track
import com.fnmusic.tv.core.model.TrackGuid
import kotlinx.serialization.Serializable

/**
 * Jellyfin 的 DTO 与领域映射。
 * 字段形态以本地实测为准（见 .trellis/spec/backend/jellyfin-contracts.md，Jellyfin 10.10.7）。
 */
@Serializable
internal data class JellyfinPublicInfoDto(
    val Id: String? = null,
    val ServerName: String? = null,
    val Version: String? = null,
    val ProductName: String? = null,
)

@Serializable
internal data class JellyfinUserDto(val Id: String, val Name: String = "")

@Serializable
internal data class JellyfinAuthResultDto(
    val AccessToken: String,
    val ServerId: String? = null,
    val User: JellyfinUserDto,
)

@Serializable
internal data class JellyfinItemsDto(
    val Items: List<JellyfinItemDto> = emptyList(),
    val TotalRecordCount: Int = 0,
    val StartIndex: Int = 0,
)

@Serializable
internal data class JellyfinUserDataDto(
    val IsFavorite: Boolean = false,
    val Played: Boolean = false,
    val LastPlayedDate: String? = null,
)

@Serializable
internal data class JellyfinMediaSourceDto(
    val Id: String? = null,
    val Container: String? = null,
    val Bitrate: Long? = null,
    val SupportsDirectPlay: Boolean = false,
    val SupportsDirectStream: Boolean = false,
    val SupportsTranscoding: Boolean = false,
)

@Serializable
internal data class JellyfinItemDto(
    val Id: String,
    val Name: String = "",
    val Album: String? = null,
    val AlbumId: String? = null,
    val AlbumArtist: String? = null,
    val Artists: List<String> = emptyList(),
    val RunTimeTicks: Long? = null,
    val Container: String? = null,
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val ImageTags: Map<String, String> = emptyMap(),
    /** 歌单内条目的 id：Jellyfin 从歌单移除曲目要用它（`DELETE ...?entryIds=`）。 */
    val PlaylistItemId: String? = null,
    val UserData: JellyfinUserDataDto? = null,
    val ChildCount: Int? = null,
    val RecursiveItemCount: Int? = null,
    val ProductionYear: Int? = null,
    val AlbumCount: Int? = null,
    val MediaSources: List<JellyfinMediaSourceDto> = emptyList(),
)

/** Jellyfin 的时间单位：1 毫秒 = 10 000 ticks。 */
internal const val TICKS_PER_MS = 10_000L

/**
 * 音频条目 → 领域模型。
 * - 艺人优先用 `Artists` 多值（与飞牛一样用 " / " 连接），退回 `AlbumArtist`
 * - 封面：Jellyfin 按条目 id 取图，只有带 `ImageTags.Primary` 的才有图 → 没图时置空走占位图
 * - `isCue`/`accessStatus` 是飞牛语义，这里恒为 false/null（UI 的 isTrackPlayable 天然成立）
 */
internal fun JellyfinItemDto.toTrack(): Track = Track(
    guid = TrackGuid(Id),
    title = Name,
    artistName = Artists.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: AlbumArtist,
    albumName = Album,
    coverId = Id.takeIf { ImageTags.containsKey("Primary") },
    durationMs = RunTimeTicks?.takeIf { it > 0 }?.div(TICKS_PER_MS),
    isCue = false,
    audioFormat = (Container ?: MediaSources.firstOrNull()?.Container)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.uppercase(),
    isFavorite = UserData?.IsFavorite == true,
)

/** 专辑条目 → 领域模型：Jellyfin 的专辑也是 Item，曲目数在 `ChildCount`/`RecursiveItemCount`。 */
internal fun JellyfinItemDto.toAlbum(): Album = Album(
    guid = CollectionGuid(Id),
    name = Name,
    artistName = AlbumArtist ?: Artists.firstOrNull(),
    coverId = Id.takeIf { ImageTags.containsKey("Primary") },
    trackCount = RecursiveItemCount ?: ChildCount,
    releaseDate = ProductionYear?.toString(),
)

/** 歌手条目 → 领域模型（Jellyfin 的"专辑艺术家"）。 */
internal fun JellyfinItemDto.toArtist(): Artist = Artist(
    guid = CollectionGuid(Id),
    name = Name,
    coverId = Id.takeIf { ImageTags.containsKey("Primary") },
    trackCount = RecursiveItemCount ?: ChildCount,
    albumCount = AlbumCount,
)

/** 风格条目 → 领域模型。 */
internal fun JellyfinItemDto.toGenre(): Genre = Genre(
    guid = CollectionGuid(Id),
    name = Name,
    coverId = Id.takeIf { ImageTags.containsKey("Primary") },
    trackCount = RecursiveItemCount ?: ChildCount,
)

/**
 * 歌单条目 → 领域模型。
 * Jellyfin 的歌单通常没有自己的封面（ImageTags 里没有 Primary），
 * 此时 coverId 为空，上层沿用"用前三首歌拼排"的既有逻辑。
 */
internal fun JellyfinItemDto.toPlaylist(): Playlist = Playlist(
    guid = CollectionGuid(Id),
    name = Name,
    coverId = Id.takeIf { ImageTags.containsKey("Primary") },
    trackCount = RecursiveItemCount ?: ChildCount,
)

/** Items 响应 → 统一分页模型（page/size 是 1-based 页号与每页条数）。 */
internal fun <T> JellyfinItemsDto.toPage(page: Int, size: Int, map: (JellyfinItemDto) -> T): Page<T> =
    Page(
        items = Items.map(map),
        page = page,
        pageSize = size,
        total = TotalRecordCount.takeIf { it > 0 } ?: Items.size,
        sort = "",
    )

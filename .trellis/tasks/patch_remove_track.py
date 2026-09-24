import re

p = "app/src/main/java/com/fnmusic/tv/ui/AuthenticatedApp.kt"
s = open(p, encoding="utf-8").read()
applied = []

def sub(old, new, tag):
    global s
    if new in s:
        applied.append(tag + " (already)")
        return
    assert old in s, f"{tag} anchor missing"
    s = s.replace(old, new, 1)
    applied.append(tag)

# ---- A. API: removeFromPlaylist ----
p2 = "core/data/src/main/kotlin/com/fnmusic/tv/core/data/api/TrimMusicApi.kt"
s2 = open(p2, encoding="utf-8").read()
old = """    suspend fun addToPlaylist(guid: String, trackGUIDs: List<String>) {"""
new = """    suspend fun removeFromPlaylist(guid: String, trackGUIDs: List<String>) {
        postUnit("playlist/remove-track", PlaylistAddTrackRequest(guid, trackGUIDs))
    }

    suspend fun addToPlaylist(guid: String, trackGUIDs: List<String>) {"""
if "removeFromPlaylist" not in s2:
    assert old in s2
    s2 = s2.replace(old, new, 1)
    open(p2, "w", encoding="utf-8", newline="\n").write(s2)
    applied.append("api removeFromPlaylist")

# ---- B. Repository: removeFromPlaylist ----
p3 = "core/data/src/main/kotlin/com/fnmusic/tv/core/data/repository/MusicRepository.kt"
s3 = open(p3, encoding="utf-8").read()
old = """    suspend fun addToPlaylist(playlistGuid: String, trackGuid: String) {
        session.authenticated { it.addToPlaylist(playlistGuid, listOf(trackGuid)) }
    }"""
new = old + """

    suspend fun removeFromPlaylist(playlistGuid: String, trackGuid: String) {
        session.authenticated { it.removeFromPlaylist(playlistGuid, listOf(trackGuid)) }
    }"""
if "removeFromPlaylist" not in s3:
    assert old in s3
    s3 = s3.replace(old, new, 1)
    open(p3, "w", encoding="utf-8", newline="\n").write(s3)
    applied.append("repo removeFromPlaylist")

open(p, "w", encoding="utf-8", newline="\n").write(s)
print("applied:", applied)

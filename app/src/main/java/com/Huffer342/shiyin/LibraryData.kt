package com.Huffer342.shiyin

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

fun isLocalAudioReference(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "content" && (
        (uri.authority == "media" && uri.path.matches(Regex("/[^/]+/audio/media/[0-9]+"))) ||
        (uri.authority == "com.android.externalstorage.documents" &&
            uri.rawPath.startsWith("/tree/") && uri.rawPath.contains("/document/"))
    )
}.getOrDefault(false)

data class MusicPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songs: List<Song> = emptyList()
) {
    init {
        require(name.isNotBlank() && name.length <= 64) { "名称须为 1–64 个字符" }
        require(id.isNotBlank())
        require(songs.map { it.uri }.distinct().size == songs.size) { "播放列表有重复歌曲" }
        require(songs.all { isLocalAudioReference(it.uri) })
    }
    fun adding(values: List<Song>) = copy(songs = (songs + values).distinctBy { it.uri })
    fun removing(uri: String) = copy(songs = songs.filterNot { it.uri == uri })
    fun moving(from: Int, to: Int): MusicPlaylist {
        require(from in songs.indices && to in songs.indices)
        return copy(songs = songs.toMutableList().apply { add(to, removeAt(from)) })
    }
}

data class WatchedFolder(val uri: String, val name: String) {
    init {
        val parsed = URI(uri)
        require(parsed.scheme == "content" && parsed.authority == "com.android.externalstorage.documents" &&
            parsed.rawPath.startsWith("/tree/")) { "请选择手机或存储卡上的本地目录" }
        require(name.isNotBlank())
    }
}

object LibraryCodec {
    fun playlists(values: List<MusicPlaylist>): String = JSONArray().apply {
        values.forEach { list -> put(JSONObject().apply {
            put("id", list.id); put("name", list.name)
            put("songs", JSONArray().apply { list.songs.forEach { song -> put(JSONObject().apply {
                put("uri", song.uri); put("title", song.title); put("artist", song.artist); put("duration", song.duration)
            }) } })
        }) }
    }.toString()

    fun readPlaylists(text: String): List<MusicPlaylist> {
        val array = JSONArray(text)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val songs = o.getJSONArray("songs")
            MusicPlaylist(o.getString("id"), o.getString("name"), (0 until songs.length()).map { j ->
                val song = songs.getJSONObject(j)
                Song(song.getString("uri"), song.getString("title"), song.getString("artist"), song.getLong("duration"))
            })
        }.also { require(it.map { list -> list.id }.distinct().size == it.size) }
    }

    fun folders(values: List<WatchedFolder>): String = JSONArray().apply {
        values.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name)) }
    }.toString()

    fun readFolders(text: String): List<WatchedFolder> {
        val array = JSONArray(text)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            WatchedFolder(o.getString("uri"), o.getString("name"))
        }.distinctBy { it.uri }
    }
}

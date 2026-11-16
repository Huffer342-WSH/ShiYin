package com.Huffer342.shiyin

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.util.ArrayDeque

data class Song(val uri: String, val title: String, val artist: String, val duration: Long) {
    fun mediaItem(): MediaItem = MediaItem.Builder().setMediaId(uri).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build()).build()
}

data class FolderEntry(val id: String, val parentId: String, val name: String, val song: Song? = null)
data class FolderScan(val folder: WatchedFolder, val songs: List<Song>, val error: String = "", val entries: List<FolderEntry> = emptyList())
data class LibraryScan(val songs: List<Song>, val folders: List<FolderScan>, val watchUris: Set<Uri> = emptySet())

object MusicLibrary {
    val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun canRead(context: Context, value: String): Boolean {
        if (!isLocalAudioReference(value)) return false
        val uri = Uri.parse(value)
        if (uri.authority == "media") return hasPermission(context)
        return runCatching {
            context.contentResolver.persistedUriPermissions.any {
                it.isReadPermission && DocumentsContract.isTreeUri(it.uri) && it.uri.authority == uri.authority &&
                    DocumentsContract.getTreeDocumentId(it.uri) == DocumentsContract.getTreeDocumentId(uri)
            }
        }.getOrDefault(false)
    }

    fun scan(context: Context): List<Song> {
        check(hasPermission(context)) { "请允许读取本地音频" }
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        val songs = mutableListOf<Song>()
        val cursor = context.contentResolver.query(collection, projection,
            "${MediaStore.Audio.Media.DURATION} > 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")
            ?: error("无法读取系统音乐库")
        cursor.use {
            while (it.moveToNext()) songs += Song(ContentUris.withAppendedId(collection, it.getLong(0)).toString(),
                it.getString(1)?.takeIf(String::isNotBlank) ?: "未命名音频",
                it.getString(2)?.takeUnless { artist -> artist == "<unknown>" } ?: "未知艺术家", it.getLong(3))
        }
        return songs
    }

    fun folder(context: Context, uri: Uri): WatchedFolder {
        require(uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isTreeUri(uri)) {
            "请选择手机或存储卡上的本地目录"
        }
        val root = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        val cursor = context.contentResolver.query(root, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?: error("目录不可访问")
        return cursor.use {
            check(it.moveToFirst()) { "目录不存在" }
            WatchedFolder(uri.toString(), it.getString(0))
        }
    }

    private data class CachedSong(val size: Long, val modified: Long, val song: Song)
    private val metadata = mutableMapOf<String, CachedSong>()
    private val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "opus", "aac", "amr", "aiff", "alac")

    @Synchronized
    fun scanFolders(context: Context, folders: List<WatchedFolder>): LibraryScan {
        val seen = mutableSetOf<String>()
        val watch = mutableSetOf<Uri>()
        val results = folders.map { folder ->
            val songs = mutableListOf<Song>()
            val entries = mutableListOf<FolderEntry>()
            var unreadable = 0
            try {
                val tree = Uri.parse(folder.uri)
                val pending = ArrayDeque<String>()
                val visited = mutableSetOf<String>()
                pending.add(DocumentsContract.getTreeDocumentId(tree))
                while (pending.isNotEmpty()) {
                    val parentId = pending.removeFirst()
                    if (!visited.add(parentId)) continue
                    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
                    watch += children
                    val cursor = context.contentResolver.query(children, arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)
                        ?: error("无法读取目录内容")
                    cursor.use {
                        it.notificationUri?.let(watch::add)
                        while (it.moveToNext()) {
                            val id = it.getString(0)
                            val name = it.getString(1).orEmpty()
                            val mime = it.getString(2).orEmpty()
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                entries += FolderEntry(id, parentId, name)
                                pending.add(id); continue
                            }
                            if (!mime.startsWith("audio/") && name.substringAfterLast('.').lowercase() !in audioExtensions) continue
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            val key = uri.toString()
                            seen += key
                            val size = it.getLong(3)
                            val modified = it.getLong(4)
                            val cached = metadata[key]
                            if (cached != null && cached.size == size && cached.modified == modified) {
                                songs += cached.song
                                entries += FolderEntry(id, parentId, name, cached.song)
                                continue
                            }
                            try {
                                val retriever = MediaMetadataRetriever()
                                val song = try {
                                    retriever.setDataSource(context, uri)
                                    Song(key,
                                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)
                                            ?: name.substringBeforeLast('.').ifBlank { "未命名音频" },
                                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank) ?: "未知艺术家",
                                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: 0)
                                } finally { retriever.release() }
                                metadata[key] = CachedSong(size, modified, song)
                                songs += song
                                entries += FolderEntry(id, parentId, name, song)
                            } catch (_: Exception) { unreadable++ }
                        }
                    }
                }
                FolderScan(folder, songs.sortedBy { it.title.lowercase() },
                    if (unreadable > 0) "$unreadable 个音频无法读取" else "", entries)
            } catch (e: Exception) {
                FolderScan(folder, songs, if (e is SecurityException) "授权失效，请重新选择目录" else e.message ?: "目录不可访问", entries)
            }
        }
        metadata.keys.retainAll(seen)
        val all = results.flatMap { it.songs }.distinctBy {
            DocumentsContract.getDocumentId(Uri.parse(it.uri))
        }.sortedBy { it.title.lowercase() }
        return LibraryScan(all, results, watch)
    }


}

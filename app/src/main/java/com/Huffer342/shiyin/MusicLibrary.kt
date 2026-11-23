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
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

data class Song(val uri: String, val title: String, val artist: String, val duration: Long) {
    fun mediaItem(): MediaItem = MediaItem.Builder().setMediaId(uri).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build()).build()
}

data class FolderEntry(val id: String, val parentId: String, val name: String, val song: Song? = null)
data class FolderScan(val folder: WatchedFolder, val songs: List<Song>, val error: String = "", val entries: List<FolderEntry> = emptyList())
data class LibraryScan(
    val songs: List<Song>,
    val folders: List<FolderScan>,
    val watchUris: Set<Uri> = emptySet(),
    val songKeys: Map<String, String> = emptyMap()
)
data class MediaStoreDirectory(
    val key: String,
    val volumeName: String,
    val path: String,
    val songCount: Int,
    val included: Boolean
)
data class MediaStoreScan(
    val songs: List<Song> = emptyList(),
    val directories: List<MediaStoreDirectory> = emptyList(),
    val songKeys: Map<String, String> = emptyMap(),
    val error: String = ""
)

internal fun normalizeMediaDirectoryPath(value: String): String {
    val path = value.replace('\\', '/').trim().trim('/')
    return if (path.isEmpty()) "其他/" else "$path/"
}

internal fun mediaDirectoryKey(volumeName: String, path: String): String =
    "${volumeName.lowercase(Locale.ROOT)}|${normalizeMediaDirectoryPath(path).lowercase(Locale.ROOT)}"

internal fun defaultMediaDirectoryIncluded(path: String): Boolean =
    normalizeMediaDirectoryPath(path).trim('/').split('/').none { it.equals("Recordings", ignoreCase = true) }

internal fun mediaDirectoryIncluded(
    key: String,
    path: String,
    overrides: Map<String, Boolean>
): Boolean = overrides[key] ?: defaultMediaDirectoryIncluded(path)

internal fun mergeLibrarySongs(system: MediaStoreScan, custom: LibraryScan): List<Song> {
    val merged = linkedMapOf<String, Song>()
    custom.songs.forEach { song -> merged.putIfAbsent(custom.songKeys[song.uri] ?: song.uri, song) }
    system.songs.forEach { song -> merged.putIfAbsent(system.songKeys[song.uri] ?: song.uri, song) }
    return merged.values.sortedBy { it.title.lowercase() }
}

private fun mediaStorageKey(volumeName: String, path: String, displayName: String): String =
    "${mediaDirectoryKey(volumeName, path)}${displayName.lowercase(Locale.ROOT)}"

private fun documentStorageKey(documentId: String): String {
    val volume = documentId.substringBefore(':', missingDelimiterValue = "document")
        .let { if (it.equals("primary", ignoreCase = true)) "external_primary" else it }
    val path = documentId.substringAfter(':', missingDelimiterValue = documentId)
    return "$volume|$path".lowercase(Locale.ROOT)
}

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

    fun scan(context: Context): List<Song> = scanMediaStore(context, emptyMap()).songs

    fun scanMediaStore(context: Context, overrides: Map<String, Boolean>): MediaStoreScan {
        check(hasPermission(context)) { "请允许读取本地音频" }
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val modern = Build.VERSION.SDK_INT >= 29
        val projection = if (modern) arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME
        ) else arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val songs = mutableListOf<Song>()
        val songKeys = mutableMapOf<String, String>()
        data class DirectoryCount(val volume: String, val path: String, var count: Int = 0)
        val counts = linkedMapOf<String, DirectoryCount>()
        val cursor = context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.DURATION} > 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        ) ?: error("无法读取系统音乐库")
        cursor.use {
            while (it.moveToNext()) {
                val volume: String
                val path: String
                val displayName: String
                val storageKey: String
                if (modern) {
                    volume = it.getString(4) ?: MediaStore.VOLUME_EXTERNAL
                    path = normalizeMediaDirectoryPath(it.getString(5).orEmpty())
                    displayName = it.getString(6).orEmpty()
                    storageKey = mediaStorageKey(volume, path, displayName)
                } else {
                    @Suppress("DEPRECATION")
                    val data = it.getString(4).orEmpty()
                    volume = "legacy"
                    path = normalizeMediaDirectoryPath(File(data).parent.orEmpty())
                    displayName = it.getString(5).orEmpty()
                    storageKey = "legacy|${data.lowercase(Locale.ROOT)}"
                }
                val directoryKey = mediaDirectoryKey(volume, path)
                counts.getOrPut(directoryKey) { DirectoryCount(volume, path) }.count++
                if (!mediaDirectoryIncluded(directoryKey, path, overrides)) continue
                val uri = ContentUris.withAppendedId(collection, it.getLong(0)).toString()
                songs += Song(
                    uri,
                    it.getString(1)?.takeIf(String::isNotBlank)
                        ?: displayName.substringBeforeLast('.').ifBlank { "未命名音频" },
                    it.getString(2)?.takeUnless { artist -> artist == "<unknown>" } ?: "未知艺术家",
                    it.getLong(3)
                )
                songKeys[uri] = storageKey
            }
        }
        val directories = counts.map { (key, value) ->
            MediaStoreDirectory(
                key,
                value.volume,
                value.path,
                value.count,
                mediaDirectoryIncluded(key, value.path, overrides)
            )
        }.sortedWith(compareBy<MediaStoreDirectory> { it.volumeName }.thenBy { it.path.lowercase() })
        return MediaStoreScan(songs, directories, songKeys)
    }

    fun folder(context: Context, uri: Uri): WatchedFolder {
        require(uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isTreeUri(uri)) {
            "请选择手机或存储卡上的本地目录"
        }
        val root = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        val cursor = context.contentResolver.query(
            root,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        ) ?: error("目录不可访问")
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
        val songKeys = mutableMapOf<String, String>()
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
                    val cursor = context.contentResolver.query(
                        children,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED
                        ),
                        null,
                        null,
                        null
                    ) ?: error("无法读取目录内容")
                    cursor.use {
                        it.notificationUri?.let(watch::add)
                        while (it.moveToNext()) {
                            val id = it.getString(0)
                            val name = it.getString(1).orEmpty()
                            val mime = it.getString(2).orEmpty()
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                entries += FolderEntry(id, parentId, name)
                                pending.add(id)
                                continue
                            }
                            if (!mime.startsWith("audio/") &&
                                name.substringAfterLast('.').lowercase() !in audioExtensions) continue
                            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            val key = uri.toString()
                            seen += key
                            songKeys[key] = documentStorageKey(id)
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
                                    Song(
                                        key,
                                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                                            ?.takeIf(String::isNotBlank)
                                            ?: name.substringBeforeLast('.').ifBlank { "未命名音频" },
                                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                            ?.takeIf(String::isNotBlank) ?: "未知艺术家",
                                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                            ?.toLongOrNull()?.coerceAtLeast(0) ?: 0
                                    )
                                } finally {
                                    retriever.release()
                                }
                                metadata[key] = CachedSong(size, modified, song)
                                songs += song
                                entries += FolderEntry(id, parentId, name, song)
                            } catch (_: Exception) {
                                unreadable++
                            }
                        }
                    }
                }
                FolderScan(
                    folder,
                    songs.sortedBy { it.title.lowercase() },
                    if (unreadable > 0) "$unreadable 个音频无法读取" else "",
                    entries
                )
            } catch (e: Exception) {
                FolderScan(
                    folder,
                    songs,
                    if (e is SecurityException) "授权失效，请重新选择目录" else e.message ?: "目录不可访问",
                    entries
                )
            }
        }
        metadata.keys.retainAll(seen)
        val unique = linkedMapOf<String, Song>()
        results.flatMap { it.songs }.forEach { song ->
            unique.putIfAbsent(songKeys[song.uri] ?: song.uri, song)
        }
        return LibraryScan(unique.values.sortedBy { it.title.lowercase() }, results, watch, songKeys)
    }
}

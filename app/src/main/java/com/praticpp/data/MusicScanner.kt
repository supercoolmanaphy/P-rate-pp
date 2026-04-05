package com.praticpp.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.praticpp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicScanner(private val context: Context) {

    suspend fun scanMp3s(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val albumId = c.getLong(albumIdCol)
                    val contentUri = Uri.withAppendedPath(collection, id.toString())
                    val albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")

                    songs.add(
                        Song(
                            id = id,
                            title = c.getString(titleCol) ?: context.getString(R.string.unknown_title),
                            artist = c.getString(artistCol) ?: context.getString(R.string.unknown_artist),
                            album = c.getString(albumCol) ?: context.getString(R.string.unknown_album),
                            duration = c.getLong(durationCol),
                            uri = contentUri,
                            albumArtUri = albumArtUri,
                            dateAdded = c.getLong(dateAddedCol)
                        )
                    )
                }
            }
        } finally {
            cursor?.close()
        }

        songs
    }
}

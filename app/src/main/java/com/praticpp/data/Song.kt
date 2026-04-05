package com.praticpp.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,       // milliseconds
    val uri: Uri,
    val albumArtUri: Uri?,
    val dateAdded: Long       // seconds since epoch
)

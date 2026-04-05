package com.praticpp.data

import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: MutableList<Long> = mutableListOf()
)

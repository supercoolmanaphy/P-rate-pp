package com.praticpp.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlaylistRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getPlaylists(): MutableList<Playlist> {
        val json = prefs.getString(KEY_PLAYLISTS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Playlist>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    fun savePlaylists(playlists: List<Playlist>) {
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(playlists)).apply()
    }

    fun createPlaylist(name: String): Playlist {
        val playlists = getPlaylists()
        val playlist = Playlist(name = name)
        playlists.add(playlist)
        savePlaylists(playlists)
        return playlist
    }

    fun renamePlaylist(id: String, newName: String) {
        val playlists = getPlaylists()
        val index = playlists.indexOfFirst { it.id == id }
        if (index != -1) {
            playlists[index] = playlists[index].copy(name = newName)
            savePlaylists(playlists)
        }
    }

    fun deletePlaylist(id: String) {
        val playlists = getPlaylists().filter { it.id != id }
        savePlaylists(playlists)
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        val playlists = getPlaylists()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1 && !playlists[index].songIds.contains(songId)) {
            playlists[index].songIds.add(songId)
            savePlaylists(playlists)
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        val playlists = getPlaylists()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            playlists[index].songIds.remove(songId)
            savePlaylists(playlists)
        }
    }

    companion object {
        private const val PREFS_NAME = "praticpp_playlists"
        private const val KEY_PLAYLISTS = "playlists"
    }
}

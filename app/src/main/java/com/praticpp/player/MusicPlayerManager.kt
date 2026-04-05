package com.praticpp.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.praticpp.data.Song

class MusicPlayerManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    val player: ExoPlayer = ExoPlayer.Builder(appContext).build()

    private var currentQueue: List<Song> = emptyList()

    val currentSong: Song?
        get() {
            val idx = player.currentMediaItemIndex
            return if (idx in currentQueue.indices) currentQueue[idx] else null
        }

    val isPlaying: Boolean
        get() = player.isPlaying

    fun loadQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        currentQueue = songs
        val clampedIndex = startIndex.coerceIn(0, songs.lastIndex)
        val mediaItems = songs.map { MediaItem.fromUri(it.uri) }
        player.setMediaItems(mediaItems, clampedIndex, 0L)
        player.prepare()
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        val index = queue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        loadQueue(queue, index)
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekNext() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun seekPrevious() {
        if (player.currentPosition > REWIND_THRESHOLD_MS) {
            player.seekTo(0)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun release() {
        player.release()
    }

    fun addListener(listener: Player.Listener) {
        player.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        player.removeListener(listener)
    }

    companion object {
        private const val REWIND_THRESHOLD_MS = 3_000L

        @Volatile
        private var INSTANCE: MusicPlayerManager? = null

        fun getInstance(context: Context): MusicPlayerManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicPlayerManager(context).also { INSTANCE = it }
            }
    }
}

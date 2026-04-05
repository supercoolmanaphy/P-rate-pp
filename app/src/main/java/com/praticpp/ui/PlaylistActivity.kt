package com.praticpp.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.praticpp.R
import com.praticpp.data.MusicScanner
import com.praticpp.data.Playlist
import com.praticpp.data.PlaylistRepository
import com.praticpp.data.Song
import com.praticpp.databinding.ActivityPlaylistBinding
import com.praticpp.player.MusicPlayerManager
import kotlinx.coroutines.launch

class PlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var musicScanner: MusicScanner
    private lateinit var playerManager: MusicPlayerManager

    private var playlistId: String = ""
    private var playlist: Playlist? = null
    private var allSongs: List<Song> = emptyList()

    private val playlistSongsAdapter = PlaylistSongsAdapter(
        onSongClick = { song, queue -> playSong(song, queue) },
        onRemove = { song -> removeSongFromPlaylist(song) }
    )

    private val allSongsAdapter = SongAdapter(
        onSongClick = { song, queue -> playSong(song, queue) },
        onAddToPlaylist = { song -> addSongToPlaylist(song) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID) ?: run {
            finish()
            return
        }

        playlistRepo = PlaylistRepository(this)
        musicScanner = MusicScanner(this)
        playerManager = MusicPlayerManager.getInstance(this)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.rvPlaylistSongs.adapter = playlistSongsAdapter
        binding.rvAllSongs.adapter = allSongsAdapter

        binding.btnAddSongs.setOnClickListener {
            toggleAddSongsPanel()
        }

        ThemeManager.load(this)
        ThemeManager.applyToPlaylistBinding(binding)

        loadData()
    }

    override fun onResume() {
        super.onResume()
        ThemeManager.load(this)
        ThemeManager.applyToPlaylistBinding(binding)
        refreshPlaylist()
        playlistSongsAdapter.notifyDataSetChanged()
        allSongsAdapter.notifyDataSetChanged()
    }

    private fun loadData() {
        lifecycleScope.launch {
            allSongs = musicScanner.scanMp3s()
            allSongsAdapter.submitList(allSongs)
            refreshPlaylist()
        }
    }

    private fun refreshPlaylist() {
        playlist = playlistRepo.getPlaylists().find { it.id == playlistId }
        if (playlist == null) {
            finish()
            return
        }
        supportActionBar?.title = playlist!!.name
        binding.tvPlaylistName.text = playlist!!.name

        val songs = allSongs.filter { it.id in playlist!!.songIds }
        playlistSongsAdapter.submitList(songs)
        binding.tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleAddSongsPanel() {
        if (binding.addSongsPanel.visibility == View.VISIBLE) {
            binding.addSongsPanel.visibility = View.GONE
            binding.btnAddSongs.text = "+ ADD SONGS"
        } else {
            binding.addSongsPanel.visibility = View.VISIBLE
            binding.btnAddSongs.text = "▲ HIDE"
        }
    }

    private fun addSongToPlaylist(song: Song) {
        val pl = playlist ?: return
        playlistRepo.addSongToPlaylist(pl.id, song.id)
        refreshPlaylist()
        Toast.makeText(this, "Added: ${song.title}", Toast.LENGTH_SHORT).show()
    }

    private fun removeSongFromPlaylist(song: Song) {
        val pl = playlist ?: return
        playlistRepo.removeSongFromPlaylist(pl.id, song.id)
        refreshPlaylist()
    }

    private fun playSong(song: Song, queue: List<Song>) {
        playerManager.playSong(song, queue)
        Toast.makeText(this, "▶ ${song.title}", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
    }
}

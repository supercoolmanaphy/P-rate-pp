package com.praticpp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import com.praticpp.R
import com.praticpp.data.MusicScanner
import com.praticpp.data.Playlist
import com.praticpp.data.PlaylistRepository
import com.praticpp.data.Song
import com.praticpp.databinding.ActivityMainBinding
import com.praticpp.databinding.DialogColorPickerBinding
import com.praticpp.player.MusicPlayerManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var playerManager: MusicPlayerManager
    private lateinit var musicScanner: MusicScanner
    private lateinit var playlistRepo: PlaylistRepository

    private val songAdapter = SongAdapter(
        onSongClick = { song, queue -> playSong(song, queue) },
        onAddToPlaylist = { song -> showAddToPlaylistDialog(song) }
    )
    private val playlistAdapter = PlaylistAdapter(
        onPlaylistClick = { playlist -> openPlaylist(playlist) },
        onRename = { playlist -> showRenameDialog(playlist) },
        onDelete = { playlist -> confirmDeletePlaylist(playlist) }
    )

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private var audioVisualizer: Visualizer? = null

    private var allSongs: List<Song> = emptyList()
    private var playlists: MutableList<Playlist> = mutableListOf()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val storageGranted = results[Manifest.permission.READ_MEDIA_AUDIO]
            ?: results[Manifest.permission.READ_EXTERNAL_STORAGE]
            ?: false
        if (storageGranted) {
            loadMusic()
        } else {
            Toast.makeText(this, "Storage permission required to find music", Toast.LENGTH_LONG).show()
        }
        if (results[Manifest.permission.RECORD_AUDIO] != true) {
            Toast.makeText(this, "Visualizer permission denied (no audio will be recorded)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playerManager = MusicPlayerManager.getInstance(this)
        musicScanner = MusicScanner(this)
        playlistRepo = PlaylistRepository(this)

        ThemeManager.load(this)
        setupRecyclerViews()
        setupPlayerControls()
        setupCreatePlaylistButton()
        setupColorPicker()
        setupThemePicker()
        setupVisualizerToggle()
        ThemeManager.applyToMainBinding(binding)
        applyBackgroundTheme(ThemeManager.currentBgTheme)
        checkPermissionAndLoad()
    }

    override fun onResume() {
        super.onResume()
        refreshPlaylists()
        updateNowPlayingBar()
        if (playerManager.isPlaying) {
            handler.post(updateProgressRunnable)
            attachVisualizer()
        }
        currentMirrorView()?.resumeSensor()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateProgressRunnable)
        detachVisualizer()
        currentMirrorView()?.pauseSensor()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseVisualizer()
    }

    // ── Permission ──────────────────────────────────────────────────────────

    private fun checkPermissionAndLoad() {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val permissionsToRequest = arrayOf(storagePermission, Manifest.permission.RECORD_AUDIO)

        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            loadMusic()
            return
        }

        if (permissionsToRequest.any { shouldShowRequestPermissionRationale(it) }) {
            Toast.makeText(this, "Music access and visualizer permission needed", Toast.LENGTH_SHORT).show()
        }
        permissionLauncher.launch(permissionsToRequest)
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    private fun loadMusic() {
        lifecycleScope.launch {
            binding.progressLoadingRecentSongs.visibility = View.VISIBLE
            allSongs = musicScanner.scanMp3s()
            binding.progressLoadingRecentSongs.visibility = View.GONE

            val recent = allSongs.take(20)
            songAdapter.submitList(recent)

            if (recent.isEmpty()) {
                binding.tvNoSongs.visibility = View.VISIBLE
            } else {
                binding.tvNoSongs.visibility = View.GONE
            }
        }
    }

    private fun refreshPlaylists() {
        playlists = playlistRepo.getPlaylists()
        playlistAdapter.submitList(playlists.toList())
        binding.tvNoPlaylists.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── Setup ────────────────────────────────────────────────────────────────

    private fun setupRecyclerViews() {
        binding.rvRecentSongs.adapter = songAdapter
        binding.rvPlaylists.adapter = playlistAdapter
    }

    private fun setupPlayerControls() {
        binding.btnPlayPause.setOnClickListener {
            playerManager.togglePlayPause()
            updatePlayPauseIcon()
        }

        binding.btnNext.setOnClickListener { playerManager.seekNext() }
        binding.btnPrev.setOnClickListener { playerManager.seekPrevious() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) playerManager.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        playerManager.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                runOnUiThread {
                    updatePlayPauseIcon()
                    if (isPlaying) {
                        handler.post(updateProgressRunnable)
                        attachVisualizer()
                    } else {
                        handler.removeCallbacks(updateProgressRunnable)
                        detachVisualizer()
                    }
                }
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                runOnUiThread { updateNowPlayingBar() }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                runOnUiThread { updateNowPlayingBar() }
            }
        })
    }

    private fun setupCreatePlaylistButton() {
        binding.btnCreatePlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun setupColorPicker() {
        binding.btnColorPicker.setOnClickListener {
            showColorPickerDialog()
        }
    }

    private fun setupThemePicker() {
        binding.btnThemePicker.setOnClickListener {
            showThemePickerDialog()
        }
    }

    private fun setupVisualizerToggle() {
        binding.btnVizMode.setOnClickListener {
            binding.visualizerView.mode = binding.visualizerView.mode.next()
        }
    }

    private fun showThemePickerDialog() {
        val themes = BgTheme.entries.toTypedArray()
        val names = themes.map { it.displayName }.toTypedArray()
        val current = themes.indexOf(ThemeManager.currentBgTheme).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this, R.style.Y2kDialog)
            .setTitle("BACKGROUND THEME")
            .setSingleChoiceItems(names, current) { dialog, which ->
                val chosen = themes[which]
                ThemeManager.saveBgTheme(this, chosen)
                applyBackgroundTheme(chosen)
                dialog.dismiss()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun applyBackgroundTheme(theme: BgTheme) {
        binding.flBackground.removeAllViews()
        binding.vBgScrim.visibility = if (theme == BgTheme.NONE) View.GONE else View.VISIBLE

        when (theme) {
            BgTheme.NONE -> { /* nothing */ }
            BgTheme.MATRIX -> {
                val view = MatrixRainView(this)
                view.accentColor = ThemeManager.accentColor()
                binding.flBackground.addView(view)
            }
            BgTheme.PLASMA -> {
                val view = PlasmaWaveView(this)
                view.accentHue = ThemeManager.currentHue
                binding.flBackground.addView(view)
            }
            BgTheme.STARFIELD -> {
                val view = StarfieldView(this)
                view.accentColor = ThemeManager.accentColor()
                binding.flBackground.addView(view)
            }
            BgTheme.CHROME_GRID -> {
                val view = ChromeGridView(this)
                view.accentColor = ThemeManager.accentColor()
                binding.flBackground.addView(view)
            }
            BgTheme.MIRROR_MODE -> {
                val view = MirrorModeView(this)
                view.accentHue = ThemeManager.currentHue
                binding.flBackground.addView(view)
            }
        }
    }

    private fun currentMirrorView(): MirrorModeView? =
        binding.flBackground.getChildAt(0) as? MirrorModeView

    private fun showColorPickerDialog() {
        val pickerBinding = DialogColorPickerBinding.inflate(LayoutInflater.from(this))
        pickerBinding.hueWheel.setHue(ThemeManager.currentHue)
        pickerBinding.tvHueValue.text = "HUE: ${ThemeManager.currentHue.toInt()}°"

        var pendingHue = ThemeManager.currentHue
        pickerBinding.hueWheel.onHueChanged = { hue ->
            pendingHue = hue
            pickerBinding.tvHueValue.text = "HUE: ${hue.toInt()}°"
        }

        android.app.AlertDialog.Builder(this, R.style.Y2kDialog)
            .setTitle("ACCENT COLOR")
            .setView(pickerBinding.root)
            .setPositiveButton("APPLY") { _, _ ->
                ThemeManager.save(this, pendingHue)
                ThemeManager.applyToMainBinding(binding)
                applyBackgroundTheme(ThemeManager.currentBgTheme)
                songAdapter.notifyDataSetChanged()
                playlistAdapter.notifyDataSetChanged()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun playSong(song: Song, queue: List<Song>) {
        playerManager.playSong(song, queue)
        updateNowPlayingBar()
        handler.post(updateProgressRunnable)
    }

    private fun updateNowPlayingBar() {
        val song = playerManager.currentSong
        if (song != null) {
            binding.nowPlayingBar.visibility = View.VISIBLE
            binding.tvNowPlayingTitle.text = song.title
            binding.tvNowPlayingArtist.text = song.artist
            binding.seekBar.max = playerManager.player.duration.coerceAtLeast(1).toInt()
            Glide.with(this)
                .load(song.albumArtUri)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .into(binding.ivNowPlayingArt)
            updatePlayPauseIcon()
        } else {
            binding.nowPlayingBar.visibility = View.GONE
        }
    }

    private fun updatePlayPauseIcon() {
        val icon = if (playerManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        binding.btnPlayPause.setImageResource(icon)
    }

    private fun updateProgress() {
        val player = playerManager.player
        val pos = player.currentPosition
        val dur = player.duration.coerceAtLeast(1)
        binding.seekBar.max = dur.toInt()
        binding.seekBar.progress = pos.toInt()
        binding.tvCurrentTime.text = formatMs(pos)
        binding.tvTotalTime.text = formatMs(dur)
    }

    // ── Visualizer ────────────────────────────────────────────────────────────

    /**
     * Creates (or recreates) an [android.media.audiofx.Visualizer] attached to
     * the ExoPlayer audio session and starts feeding data into [AudioVisualizerView].
     *
     * Requires RECORD_AUDIO permission. Fails silently if the system doesn't
     * support audio effects (e.g. emulators without audio).
     */
    private fun attachVisualizer() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return

        releaseVisualizer()
        try {
            val sessionId = playerManager.player.audioSessionId
            audioVisualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer, waveform: ByteArray, samplingRate: Int
                        ) {
                            binding.visualizerView.updateWaveform(waveform)
                        }

                        override fun onFftDataCapture(
                            v: Visualizer, fft: ByteArray, samplingRate: Int
                        ) {
                            binding.visualizerView.updateFft(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    /* waveform = */ true,
                    /* fft = */ true,
                )
                enabled = true
            }
        } catch (_: Exception) {
            // Audio effects not supported on this device/emulator — visualizer stays idle.
        }
    }

    /** Disables (but does not release) the visualizer so the CPU isn't wasted while paused. */
    private fun detachVisualizer() {
        try { audioVisualizer?.enabled = false } catch (_: Exception) {}
        binding.visualizerView.clearData()
    }

    /** Fully releases the native Visualizer resource. */
    private fun releaseVisualizer() {
        try { audioVisualizer?.release() } catch (_: Exception) {}
        audioVisualizer = null
    }

    // ── Playlist dialogs ─────────────────────────────────────────────────────

    private fun showCreatePlaylistDialog() {
        val dialog = android.app.AlertDialog.Builder(this, R.style.Y2kDialog)
            .setTitle("NEW PLAYLIST")
            .setView(R.layout.dialog_input)
            .setPositiveButton("CREATE") { d, _ ->
                val et = (d as android.app.AlertDialog)
                    .findViewById<android.widget.EditText>(R.id.et_input)
                val name = et?.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    playlistRepo.createPlaylist(name)
                    refreshPlaylists()
                }
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
    }

    private fun showRenameDialog(playlist: Playlist) {
        val dialog = android.app.AlertDialog.Builder(this, R.style.Y2kDialog)
            .setTitle("RENAME PLAYLIST")
            .setView(R.layout.dialog_input)
            .setPositiveButton("RENAME") { d, _ ->
                val et = (d as android.app.AlertDialog)
                    .findViewById<android.widget.EditText>(R.id.et_input)
                val name = et?.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    playlistRepo.renamePlaylist(playlist.id, name)
                    refreshPlaylists()
                }
            }
            .setNegativeButton("CANCEL", null)
            .create()
        dialog.show()
    }

    private fun confirmDeletePlaylist(playlist: Playlist) {
        android.app.AlertDialog.Builder(this, R.style.Y2kDialog)
            .setTitle("DELETE PLAYLIST")
            .setMessage("Delete \"${playlist.name}\"?")
            .setPositiveButton("DELETE") { _, _ ->
                playlistRepo.deletePlaylist(playlist.id)
                refreshPlaylists()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showAddToPlaylistDialog(song: Song) {
        val refreshed = playlistRepo.getPlaylists()
        if (refreshed.isEmpty()) {
            Toast.makeText(this, "Create a playlist first", Toast.LENGTH_SHORT).show()
            return
        }
        val names = refreshed.map { it.name }.toTypedArray()
        android.app.AlertDialog.Builder(this, R.style.Y2kDialog)
            .setTitle("ADD TO PLAYLIST")
            .setItems(names) { _, i ->
                playlistRepo.addSongToPlaylist(refreshed[i].id, song.id)
                Toast.makeText(this, "Added to ${refreshed[i].name}", Toast.LENGTH_SHORT).show()
                refreshPlaylists()
            }
            .show()
    }

    private fun openPlaylist(playlist: Playlist) {
        val intent = Intent(this, PlaylistActivity::class.java).apply {
            putExtra(PlaylistActivity.EXTRA_PLAYLIST_ID, playlist.id)
        }
        startActivity(intent)
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private fun formatMs(ms: Long): String {
        val min = ms / 60000
        val sec = (ms % 60000) / 1000
        return "%d:%02d".format(min, sec)
    }
}

package com.praticpp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.praticpp.R
import com.praticpp.data.Song
import java.util.concurrent.TimeUnit

class SongAdapter(
    private val onSongClick: (Song, List<Song>) -> Unit,
    private val onAddToPlaylist: ((Song) -> Unit)? = null
) : ListAdapter<Song, SongAdapter.SongViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), currentList)
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val albumArt: ImageView = itemView.findViewById(R.id.iv_album_art)
        private val title: TextView = itemView.findViewById(R.id.tv_song_title)
        private val artist: TextView = itemView.findViewById(R.id.tv_song_artist)
        private val duration: TextView = itemView.findViewById(R.id.tv_song_duration)
        private val addBtn: ImageView = itemView.findViewById(R.id.iv_add_to_playlist)

        fun bind(song: Song, allSongs: List<Song>) {
            title.text = song.title
            artist.text = song.artist
            duration.text = formatDuration(song.duration)

            Glide.with(itemView.context)
                .load(song.albumArtUri)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .into(albumArt)

            itemView.setOnClickListener { onSongClick(song, allSongs) }

            if (onAddToPlaylist != null) {
                addBtn.visibility = View.VISIBLE
                addBtn.setOnClickListener { onAddToPlaylist.invoke(song) }
            } else {
                addBtn.visibility = View.GONE
            }
        }

        private fun formatDuration(ms: Long): String {
            val min = TimeUnit.MILLISECONDS.toMinutes(ms)
            val sec = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
            return "%d:%02d".format(min, sec)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(a: Song, b: Song) = a.id == b.id
            override fun areContentsTheSame(a: Song, b: Song) = a == b
        }
    }
}

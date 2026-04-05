package com.praticpp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.praticpp.R
import com.praticpp.data.Playlist

class PlaylistAdapter(
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onRename: (Playlist) -> Unit,
    private val onDelete: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.PlaylistViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tv_playlist_name)
        private val count: TextView = itemView.findViewById(R.id.tv_song_count)
        private val renameBtn: ImageButton = itemView.findViewById(R.id.btn_rename)
        private val deleteBtn: ImageButton = itemView.findViewById(R.id.btn_delete)

        fun bind(playlist: Playlist) {
            name.text = playlist.name
            val c = playlist.songIds.size
            count.text = "$c ${if (c == 1) "song" else "songs"}"
            itemView.setOnClickListener { onPlaylistClick(playlist) }
            renameBtn.setOnClickListener { onRename(playlist) }
            deleteBtn.setOnClickListener { onDelete(playlist) }
            ThemeManager.applyToPlaylistItem(name, renameBtn, deleteBtn)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Playlist>() {
            override fun areItemsTheSame(a: Playlist, b: Playlist) = a.id == b.id
            override fun areContentsTheSame(a: Playlist, b: Playlist) =
                a.name == b.name && a.songIds == b.songIds
        }
    }
}

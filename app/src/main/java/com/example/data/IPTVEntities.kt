package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class IPTVPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "M3U" or "XTREAM"
    val playlistUrl: String = "",
    val username: String = "",
    val password: String = "",
    val serverUrl: String = "",
    val lastUpdated: Long = 0,
    val isSelected: Boolean = false
)

@Entity(tableName = "categories")
data class IPTVCategory(
    @PrimaryKey val id: String, // playlistId + "_" + categoryId + "_" + type
    val playlistId: Int,
    val categoryId: String,
    val name: String,
    val type: String // "LIVE", "MOVIE", "SERIES"
)

@Entity(tableName = "channels")
data class IPTVChannel(
    @PrimaryKey val id: String, // playlistId + "_" + type + "_" + streamId/url
    val playlistId: Int,
    val categoryId: String,
    val streamId: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val type: String, // "LIVE", "MOVIE", "SERIES"
    val num: Int = 0,
    val containerExtension: String = "mp4",
    val description: String = "",
    val isFavorite: Boolean = false
)

@Entity(tableName = "epg_programs")
data class EPGProgram(
    @PrimaryKey val id: String, // playlistId + "_" + channelId + "_" + startTimestamp
    val playlistId: Int,
    val channelId: String, // matches tvg-id or channel name / stream_id
    val title: String,
    val description: String,
    val startTimestamp: Long, // Epoch ms
    val endTimestamp: Long     // Epoch ms
)

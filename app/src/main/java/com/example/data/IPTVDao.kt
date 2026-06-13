package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IPTVDao {

    // --- Playlist Queries ---
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylistsFlow(): Flow<List<IPTVPlaylist>>

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylists(): List<IPTVPlaylist>

    @Query("SELECT * FROM playlists WHERE isSelected = 1 LIMIT 1")
    fun getSelectedPlaylistFlow(): Flow<IPTVPlaylist?>

    @Query("SELECT * FROM playlists WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedPlaylist(): IPTVPlaylist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: IPTVPlaylist): Long

    @Update
    suspend fun updatePlaylist(playlist: IPTVPlaylist)

    @Delete
    suspend fun deletePlaylist(playlist: IPTVPlaylist)

    @Query("UPDATE playlists SET isSelected = 0")
    suspend fun deselectAllPlaylists()

    @Query("UPDATE playlists SET isSelected = 1 WHERE id = :playlistId")
    suspend fun selectPlaylistById(playlistId: Int)

    @Transaction
    suspend fun setSelectedPlaylist(playlistId: Int) {
        deselectAllPlaylists()
        selectPlaylistById(playlistId)
    }

    // --- Category Queries ---
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type ORDER BY name ASC")
    fun getCategoriesFlow(playlistId: Int, type: String): Flow<List<IPTVCategory>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type ORDER BY name ASC")
    suspend fun getCategories(playlistId: Int, type: String): List<IPTVCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<IPTVCategory>)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteCategoriesByPlaylist(playlistId: Int)

    // --- Channel Queries ---
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND type = :type AND categoryId = :categoryId ORDER BY num ASC, name ASC")
    fun getChannelsFlow(playlistId: Int, type: String, categoryId: String): Flow<List<IPTVChannel>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND type = :type AND categoryId = :categoryId ORDER BY num ASC, name ASC")
    suspend fun getChannels(playlistId: Int, type: String, categoryId: String): List<IPTVChannel>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND type = :type AND (name LIKE :query OR description LIKE :query) ORDER BY name ASC")
    fun searchChannelsFlow(playlistId: Int, type: String, query: String): Flow<List<IPTVChannel>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY type ASC, name ASC")
    fun getFavoritesFlow(playlistId: Int): Flow<List<IPTVChannel>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY type ASC, name ASC")
    suspend fun getFavorites(playlistId: Int): List<IPTVChannel>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: String): IPTVChannel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<IPTVChannel>)

    @Update
    suspend fun updateChannel(channel: IPTVChannel)

    @Query("UPDATE channels SET isFavorite = :isFav WHERE id = :channelId")
    suspend fun updateFavoriteStatus(channelId: String, isFav: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Int)

    // --- EPG Program Queries ---
    @Query("SELECT * FROM epg_programs WHERE playlistId = :playlistId AND channelId = :channelId AND endTimestamp > :currentTime ORDER BY startTimestamp ASC")
    fun getEPGForChannelFlow(playlistId: Int, channelId: String, currentTime: Long): Flow<List<EPGProgram>>

    @Query("SELECT * FROM epg_programs WHERE playlistId = :playlistId AND channelId = :channelId AND startTimestamp <= :currentTime AND endTimestamp >= :currentTime LIMIT 1")
    suspend fun getActiveProgramForChannel(playlistId: Int, channelId: String, currentTime: Long): EPGProgram?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEPGPrograms(programs: List<EPGProgram>)

    @Query("DELETE FROM epg_programs WHERE playlistId = :playlistId")
    suspend fun deleteEPGByPlaylist(playlistId: Int)

    @Query("DELETE FROM epg_programs WHERE endTimestamp < :currentTime")
    suspend fun deleteExpiredEPG(currentTime: Long)
}

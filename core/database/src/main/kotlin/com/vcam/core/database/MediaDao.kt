package com.vcam.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE mediaType = :type ORDER BY createdAt DESC")
    fun observeByType(type: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun findById(id: Long): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE mediaType = :type ORDER BY createdAt DESC")
    suspend fun listByType(type: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE displayName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<MediaItemEntity>>

    @Insert
    suspend fun insert(item: MediaItemEntity): Long

    @Update
    suspend fun update(item: MediaItemEntity)

    @Delete
    suspend fun delete(item: MediaItemEntity)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE media_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE media_items SET displayName = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("SELECT COUNT(*) FROM media_items WHERE mediaType = :type")
    suspend fun countByType(type: String): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM media_items")
    suspend fun totalBytes(): Long

    @Query("SELECT COUNT(*) FROM media_items WHERE isFavorite = 1")
    suspend fun countFavorites(): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM media_items WHERE mediaType = :type")
    suspend fun totalBytesByType(type: String): Long
}

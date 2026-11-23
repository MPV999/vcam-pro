package com.vcam.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** Aggregated library numbers shown on the dashboard. These are read from the database,
 * never hard-coded. */
data class LibraryStats(
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val totalBytes: Long = 0,
    val favoriteCount: Int = 0,
) {
    val itemCount: Int get() = photoCount + videoCount
}

/**
 * Single source of truth for the media library. Deleting an item also deletes the backing
 * file; renaming only changes the display name and keeps the file path stable so an active
 * playback session never breaks.
 */
class MediaRepository(
    private val mediaDao: MediaDao,
    private val testSessionDao: TestSessionDao,
) {

    fun observeAll(): Flow<List<MediaItemEntity>> = mediaDao.observeAll()

    fun observeVideos(): Flow<List<MediaItemEntity>> = mediaDao.observeByType(MediaTypes.VIDEO)

    fun observePhotos(): Flow<List<MediaItemEntity>> = mediaDao.observeByType(MediaTypes.PHOTO)

    fun observeRecent(limit: Int = 8): Flow<List<MediaItemEntity>> = mediaDao.observeRecent(limit)

    fun observeFavorites(): Flow<List<MediaItemEntity>> =
        mediaDao.observeAll().map { items -> items.filter { it.isFavorite } }

    fun search(query: String): Flow<List<MediaItemEntity>> = mediaDao.search(query)

    fun observeTestSessions(limit: Int = 5): Flow<List<TestSessionEntity>> =
        testSessionDao.observeRecent(limit)

    suspend fun stats(): LibraryStats = LibraryStats(
        photoCount = mediaDao.countByType(MediaTypes.PHOTO),
        videoCount = mediaDao.countByType(MediaTypes.VIDEO),
        totalBytes = mediaDao.totalBytes(),
        favoriteCount = mediaDao.countFavorites(),
    )

    suspend fun findById(id: Long): MediaItemEntity? = mediaDao.findById(id)

    suspend fun videos(): List<MediaItemEntity> = mediaDao.listByType(MediaTypes.VIDEO)

    suspend fun photos(): List<MediaItemEntity> = mediaDao.listByType(MediaTypes.PHOTO)

    suspend fun add(item: MediaItemEntity): Long = mediaDao.insert(item)

    suspend fun rename(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        mediaDao.rename(id, trimmed)
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = mediaDao.setFavorite(id, favorite)

    /** Removes the row and the backing file. Returns true when the file is gone. */
    suspend fun delete(item: MediaItemEntity): Boolean {
        mediaDao.deleteById(item.id)
        val file = File(item.filePath)
        return if (file.exists()) file.delete() else true
    }

    suspend fun latestTestSession(): TestSessionEntity? = testSessionDao.latest()

    suspend fun recordTestSession(session: TestSessionEntity): Long = testSessionDao.insert(session)

    suspend fun testSessionCount(): Int = testSessionDao.count()

    suspend fun clearTestSessions() = testSessionDao.clear()
}

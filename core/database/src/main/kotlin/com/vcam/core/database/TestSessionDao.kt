package com.vcam.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TestSessionDao {

    @Insert
    suspend fun insert(session: TestSessionEntity): Long

    @Query("SELECT * FROM test_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TestSessionEntity>>

    @Query("SELECT * FROM test_sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun latest(): TestSessionEntity?

    @Query("SELECT COUNT(*) FROM test_sessions")
    suspend fun count(): Int

    @Query("DELETE FROM test_sessions")
    suspend fun clear()
}

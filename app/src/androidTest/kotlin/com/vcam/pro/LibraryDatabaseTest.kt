package com.vcam.pro

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vcam.core.database.MediaItemEntity
import com.vcam.core.database.MediaOrigins
import com.vcam.core.database.MediaRepository
import com.vcam.core.database.MediaTypes
import com.vcam.core.database.TestSessionEntity
import com.vcam.core.database.VcamDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the library database. Requires a connected device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {

    private lateinit var database: VcamDatabase
    private lateinit var repository: MediaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VcamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MediaRepository(database.mediaDao(), database.testSessionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun item(name: String, type: String) = MediaItemEntity(
        displayName = name,
        filePath = "/data/user/0/com.vcam.pro/files/media/$name",
        mimeType = if (type == MediaTypes.VIDEO) "video/mp4" else "image/jpeg",
        mediaType = type,
        sizeBytes = 1024,
        width = 1280,
        height = 720,
        durationMs = if (type == MediaTypes.VIDEO) 4_000 else 0,
        createdAt = System.currentTimeMillis(),
        origin = MediaOrigins.CAPTURE,
    )

    @Test
    fun insertAndQuery() = runTest {
        repository.add(item("a.jpg", MediaTypes.PHOTO))
        repository.add(item("b.mp4", MediaTypes.VIDEO))

        val all = repository.observeAll().first()
        assertEquals(2, all.size)

        val stats = repository.stats()
        assertEquals(1, stats.photoCount)
        assertEquals(1, stats.videoCount)
        assertEquals(2048, stats.totalBytes)
    }

    @Test
    fun renameAndFavorite() = runTest {
        val id = repository.add(item("c.jpg", MediaTypes.PHOTO))
        repository.rename(id, "renamed.jpg")
        repository.setFavorite(id, true)

        val stored = repository.findById(id)
        assertNotNull(stored)
        assertEquals("renamed.jpg", stored!!.displayName)
        assertTrue(stored.isFavorite)
    }

    @Test
    fun deleteRemovesRow() = runTest {
        val id = repository.add(item("d.jpg", MediaTypes.PHOTO))
        val stored = repository.findById(id)!!
        repository.delete(stored)
        assertEquals(0, repository.observeAll().first().size)
    }

    @Test
    fun testSessionsAreRecorded() = runTest {
        repository.recordTestSession(
            TestSessionEntity(
                sourceLabel = "clip.mp4",
                sourceKind = "VIDEO_FILE",
                startedAt = 1_000,
                endedAt = 2_000,
                framesDelivered = 300,
                framesDropped = 4,
                averageFps = 29.8f,
                width = 1280,
                height = 720,
                outcome = TestSessionEntity.OUTCOME_SUCCESS,
            ),
        )
        val latest = repository.latestTestSession()
        assertNotNull(latest)
        assertEquals(300, latest!!.framesDelivered)
    }
}

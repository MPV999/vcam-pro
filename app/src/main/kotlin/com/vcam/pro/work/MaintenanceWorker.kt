package com.vcam.pro.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vcam.core.common.VcamLog
import com.vcam.pro.VcamApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Removes library files that no database row points at and older than one hour, so a crash
 * during capture cannot silently grow the app's storage forever.
 *
 * Runs on WorkManager, i.e. only when the system decides it is a good moment, and never on
 * the main thread.
 */
class MaintenanceWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val container = (applicationContext as VcamApplication).container
            val known = container.mediaRepository.observeAll().first()
                .mapTo(HashSet()) { it.filePath }

            val root = File(applicationContext.filesDir, "media")
            if (!root.exists()) return@withContext Result.success()

            val cutoff = System.currentTimeMillis() - ONE_HOUR_MS
            var removed = 0
            var bytes = 0L
            root.walkTopDown()
                .filter { it.isFile }
                .filter { it.absolutePath !in known && it.lastModified() < cutoff }
                .forEach { file ->
                    val length = file.length()
                    if (file.delete()) {
                        removed++
                        bytes += length
                    }
                }

            VcamLog.i(TAG, "Maintenance removed $removed orphan files ($bytes bytes)")
            Result.success(workDataOf(KEY_REMOVED to removed, KEY_BYTES to bytes))
        } catch (throwable: Throwable) {
            VcamLog.e(TAG, "Maintenance failed", throwable)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "vcam-maintenance"
        const val KEY_REMOVED = "removed"
        const val KEY_BYTES = "bytes"
        private const val TAG = "Maintenance"
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
    }
}

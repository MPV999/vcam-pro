package com.vcam.pro

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vcam.pro.work.MaintenanceWorker
import java.util.concurrent.TimeUnit

class VcamApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleMaintenance()
    }

    /**
     * Daily housekeeping: removes library files that no database row references (a crash
     * mid-capture leaves those behind) and keeps storage accounting honest.
     */
    private fun scheduleMaintenance() {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MaintenanceWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

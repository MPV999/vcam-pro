package com.vcam.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A virtual camera test run: which source was used, how many frames actually reached the
 * consumer, and what the pipeline reported. Written by the Virtual Camera Studio.
 */
@Entity(tableName = "test_sessions", indices = [Index("startedAt")])
data class TestSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sourceLabel") val sourceLabel: String,
    @ColumnInfo(name = "sourceKind") val sourceKind: String,
    @ColumnInfo(name = "startedAt") val startedAt: Long,
    @ColumnInfo(name = "endedAt") val endedAt: Long,
    @ColumnInfo(name = "framesDelivered") val framesDelivered: Long,
    @ColumnInfo(name = "framesDropped") val framesDropped: Long,
    @ColumnInfo(name = "averageFps") val averageFps: Float,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "detail") val detail: String = "",
) {
    companion object {
        const val OUTCOME_SUCCESS = "SUCCESS"
        const val OUTCOME_ERROR = "ERROR"
        const val OUTCOME_STOPPED = "STOPPED"
    }
}

package com.vcam.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object MediaTypes {
    const val PHOTO = "PHOTO"
    const val VIDEO = "VIDEO"
}

object MediaOrigins {
    const val CAPTURE = "CAPTURE"
    const val IMPORT = "IMPORT"
}

/**
 * One item in the VCAM library. Only metadata is stored here; the bytes live in the app's
 * scoped internal storage, never in a shared folder unless the user exports explicitly.
 */
@Entity(
    tableName = "media_items",
    indices = [Index("mediaType"), Index("createdAt"), Index("isFavorite")],
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "filePath") val filePath: String,
    @ColumnInfo(name = "mimeType") val mimeType: String,
    @ColumnInfo(name = "mediaType") val mediaType: String,
    @ColumnInfo(name = "sizeBytes") val sizeBytes: Long,
    @ColumnInfo(name = "width") val width: Int,
    @ColumnInfo(name = "height") val height: Int,
    @ColumnInfo(name = "durationMs") val durationMs: Long,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "isFavorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "origin") val origin: String = MediaOrigins.CAPTURE,
) {
    val isVideo: Boolean get() = mediaType == MediaTypes.VIDEO
}

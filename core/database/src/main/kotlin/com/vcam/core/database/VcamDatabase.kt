package com.vcam.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MediaItemEntity::class, TestSessionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class VcamDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao

    abstract fun testSessionDao(): TestSessionDao

    companion object {
        private const val NAME = "vcam.db"

        @Volatile
        private var instance: VcamDatabase? = null

        fun get(context: Context): VcamDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): VcamDatabase =
            Room.databaseBuilder(context, VcamDatabase::class.java, NAME)
                // No destructive migration: the schema is versioned and exported for
                // explicit migrations once the app ships.
                .build()
    }
}

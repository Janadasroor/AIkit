package com.jnd.aikit.database.vexdb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * VexDB - Vector Database using Room
 *
 * A custom vector database implementation for Android that provides
 * efficient storage and similarity search for AI embeddings.
 */
@Database(
    entities = [VectorEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VexDatabase : RoomDatabase() {

    abstract fun vectorDao(): VectorDao

    companion object {
        private const val DATABASE_NAME = "vexdb_vectors.db"

        @Volatile
        private var INSTANCE: VexDatabase? = null

        fun getInstance(context: Context): VexDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VexDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun destroyInstance() {
            INSTANCE = null
        }
    }
}

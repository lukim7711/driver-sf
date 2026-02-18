package com.driversfpoc.screenreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.driversfpoc.screenreader.data.model.CaptureRecord

/**
 * Room Database untuk app PoC Screen Reader.
 * Hanya punya 1 tabel: captures.
 */
@Database(entities = [CaptureRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun captureDao(): CaptureDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screen_reader_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

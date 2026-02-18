package com.driversfpoc.screenreader.data

import android.content.Context
import androidx.lifecycle.LiveData
import com.driversfpoc.screenreader.data.model.CaptureRecord
import java.util.concurrent.Executors

/**
 * Repository pattern untuk akses data captures.
 * Semua operasi write dijalankan di background thread via Executor.
 */
class CaptureRepository private constructor(context: Context) {

    private val dao: CaptureDao = AppDatabase.getInstance(context).captureDao()
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        @Volatile
        private var INSTANCE: CaptureRepository? = null

        fun getInstance(context: Context): CaptureRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = CaptureRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /** Insert record di background thread */
    fun insert(record: CaptureRecord) {
        executor.execute {
            dao.insert(record)
        }
    }

    /** LiveData: semua tangkapan (urut terbaru) — observe dari UI */
    fun getAllDesc(): LiveData<List<CaptureRecord>> {
        return dao.getAllDesc()
    }

    /** Ambil 1 record by ID (blocking, panggil dari background thread) */
    fun getById(id: Long): CaptureRecord? {
        return dao.getById(id)
    }

    /** Hitung tangkapan hari ini (blocking, panggil dari background thread) */
    fun getCountSince(sinceTimestamp: String): Int {
        return dao.getCountSince(sinceTimestamp)
    }

    /** LiveData: total count — observe dari UI */
    fun getTotalCount(): LiveData<Int> {
        return dao.getTotalCount()
    }

    /** Hapus semua di background thread */
    fun deleteAll() {
        executor.execute {
            dao.deleteAll()
        }
    }

    /** Auto-purge records lama di background thread */
    fun deleteOlderThan(beforeTimestamp: String) {
        executor.execute {
            dao.deleteOlderThan(beforeTimestamp)
        }
    }
}

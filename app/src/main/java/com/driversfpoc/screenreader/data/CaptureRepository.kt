package com.driversfpoc.screenreader.data

import android.content.Context
import androidx.lifecycle.LiveData
import com.driversfpoc.screenreader.data.model.CaptureRecord
import java.util.concurrent.Executors

/**
 * Repository pattern untuk akses data captures.
 * Semua operasi write dijalankan di background thread via Executor.
 *
 * v2: Ditambahkan method untuk filter, search, tag, star, dan update.
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
        executor.execute { dao.insert(record) }
    }

    /** Update record di background thread (untuk tag, note, star) */
    fun update(record: CaptureRecord) {
        executor.execute { dao.update(record) }
    }

    /** LiveData: semua tangkapan (urut terbaru) */
    fun getAllDesc(): LiveData<List<CaptureRecord>> = dao.getAllDesc()

    /** Ambil 1 record by ID (blocking) */
    fun getById(id: Long): CaptureRecord? = dao.getById(id)

    /** LiveData: filter berdasarkan event type */
    fun getByEventType(eventType: String): LiveData<List<CaptureRecord>> =
        dao.getByEventType(eventType)

    /** LiveData: search teks di plain_text */
    fun searchByText(keyword: String): LiveData<List<CaptureRecord>> =
        dao.searchByText(keyword)

    /** LiveData: search teks + filter event type */
    fun searchByTextAndType(keyword: String, eventType: String): LiveData<List<CaptureRecord>> =
        dao.searchByTextAndType(keyword, eventType)

    /** LiveData: hanya record yang di-star */
    fun getStarred(): LiveData<List<CaptureRecord>> = dao.getStarred()

    /** LiveData: filter berdasarkan tag */
    fun getByTag(tag: String): LiveData<List<CaptureRecord>> = dao.getByTag(tag)

    /** LiveData: semua tag yang pernah dipakai */
    fun getAllTags(): LiveData<List<String>> = dao.getAllTags()

    /** Hitung tangkapan hari ini (blocking) */
    fun getCountSince(sinceTimestamp: String): Int = dao.getCountSince(sinceTimestamp)

    /** LiveData: total count */
    fun getTotalCount(): LiveData<Int> = dao.getTotalCount()

    /** Hapus semua di background thread */
    fun deleteAll() {
        executor.execute { dao.deleteAll() }
    }

    /** Auto-purge records lama di background thread */
    fun deleteOlderThan(beforeTimestamp: String) {
        executor.execute { dao.deleteOlderThan(beforeTimestamp) }
    }
}

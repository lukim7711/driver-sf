package com.driversfpoc.screenreader.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import com.driversfpoc.screenreader.data.model.CaptureRecord
import java.util.concurrent.Executors

/**
 * Repository untuk akses data CaptureRecord.
 *
 * Semua write operations dan blocking read operations dijalankan di
 * internal executor thread. UI layer TIDAK perlu membuat executor sendiri.
 *
 * Pattern:
 * - Write (insert, update, delete): fire-and-forget via executor
 * - Read reactive (LiveData): langsung return, Room handle background query
 * - Read blocking (getById, getCountSince): via getByIdAsync() dengan callback
 *   yang dipanggil di main thread
 */
class CaptureRepository private constructor(context: Context) {

    private val dao: CaptureDao = AppDatabase.getInstance(context).captureDao()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

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

    // ──────────────────────────────────────────────
    // Write Operations (fire-and-forget)
    // ──────────────────────────────────────────────

    fun insert(record: CaptureRecord) {
        executor.execute { dao.insert(record) }
    }

    fun update(record: CaptureRecord) {
        executor.execute { dao.update(record) }
    }

    fun deleteById(id: Long) {
        executor.execute { dao.deleteById(id) }
    }

    fun deleteAll() {
        executor.execute { dao.deleteAll() }
    }

    fun deleteOlderThan(beforeTimestamp: String) {
        executor.execute { dao.deleteOlderThan(beforeTimestamp) }
    }

    // ──────────────────────────────────────────────
    // Read Operations — Reactive (LiveData)
    // ──────────────────────────────────────────────

    fun getAllDesc(): LiveData<List<CaptureRecord>> = dao.getAllDesc()

    fun getByEventType(eventType: String): LiveData<List<CaptureRecord>> =
        dao.getByEventType(eventType)

    /** Snapshot = PAGE + UPDATE */
    fun getSnapshots(): LiveData<List<CaptureRecord>> = dao.getSnapshots()

    fun searchByText(keyword: String): LiveData<List<CaptureRecord>> =
        dao.searchByText(keyword)

    fun searchByTextAndType(keyword: String, eventType: String): LiveData<List<CaptureRecord>> =
        dao.searchByTextAndType(keyword, eventType)

    /** Search di dalam snapshot saja */
    fun searchSnapshotsByText(keyword: String): LiveData<List<CaptureRecord>> =
        dao.searchSnapshotsByText(keyword)

    fun getStarred(): LiveData<List<CaptureRecord>> = dao.getStarred()

    fun getByTag(tag: String): LiveData<List<CaptureRecord>> = dao.getByTag(tag)

    fun getAllTags(): LiveData<List<String>> = dao.getAllTags()

    fun getTotalCount(): LiveData<Int> = dao.getTotalCount()

    // ──────────────────────────────────────────────
    // Read Operations — Async with Callback
    // ──────────────────────────────────────────────

    /**
     * Ambil CaptureRecord by ID secara async.
     * Callback dipanggil di main thread agar aman untuk update UI.
     */
    fun getByIdAsync(id: Long, onResult: (CaptureRecord?) -> Unit) {
        executor.execute {
            val record = dao.getById(id)
            mainHandler.post { onResult(record) }
        }
    }

    // ──────────────────────────────────────────────
    // Read Operations — Blocking (background thread only)
    // ──────────────────────────────────────────────

    /**
     * Blocking version — HANYA untuk dipanggil dari background thread.
     * Digunakan oleh ScreenReaderService yang sudah berjalan di executor-nya sendiri.
     */
    fun getCountSince(sinceTimestamp: String): Int = dao.getCountSince(sinceTimestamp)

    /**
     * Cek apakah konten dengan hash yang sama sudah pernah tersimpan di DB.
     * Blocking call — hanya panggil dari background thread.
     *
     * Digunakan oleh ScreenReaderService untuk dedup lintas sesi.
     * Index pada content_hash memastikan query ini cepat (O(1) lookup).
     */
    fun hasContentHash(hash: Int): Boolean = dao.countByHash(hash) > 0
}

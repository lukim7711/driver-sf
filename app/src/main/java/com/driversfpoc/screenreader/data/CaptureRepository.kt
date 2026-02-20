package com.driversfpoc.screenreader.data

import android.content.Context
import androidx.lifecycle.LiveData
import com.driversfpoc.screenreader.data.model.CaptureRecord
import java.util.concurrent.Executors

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

    fun insert(record: CaptureRecord) {
        executor.execute { dao.insert(record) }
    }

    fun update(record: CaptureRecord) {
        executor.execute { dao.update(record) }
    }

    fun deleteById(id: Long) {
        executor.execute { dao.deleteById(id) }
    }

    fun getAllDesc(): LiveData<List<CaptureRecord>> = dao.getAllDesc()

    fun getById(id: Long): CaptureRecord? = dao.getById(id)

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

    fun getCountSince(sinceTimestamp: String): Int = dao.getCountSince(sinceTimestamp)

    fun getTotalCount(): LiveData<Int> = dao.getTotalCount()

    fun deleteAll() {
        executor.execute { dao.deleteAll() }
    }

    fun deleteOlderThan(beforeTimestamp: String) {
        executor.execute { dao.deleteOlderThan(beforeTimestamp) }
    }
}

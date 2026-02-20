package com.driversfpoc.screenreader.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.driversfpoc.screenreader.data.model.CaptureRecord

@Dao
interface CaptureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: CaptureRecord): Long

    @Update
    fun update(record: CaptureRecord)

    @Query("SELECT * FROM captures ORDER BY id DESC")
    fun getAllDesc(): LiveData<List<CaptureRecord>>

    @Query("SELECT * FROM captures WHERE id = :id")
    fun getById(id: Long): CaptureRecord?

    @Query("DELETE FROM captures WHERE id = :id")
    fun deleteById(id: Long)

    @Query("SELECT * FROM captures WHERE event_type = :eventType ORDER BY id DESC")
    fun getByEventType(eventType: String): LiveData<List<CaptureRecord>>

    /** Snapshot = PAGE + UPDATE (kedua event type ini sama-sama full screen snapshot) */
    @Query("SELECT * FROM captures WHERE event_type IN ('WINDOW_STATE_CHANGED', 'WINDOW_CONTENT_CHANGED') ORDER BY id DESC")
    fun getSnapshots(): LiveData<List<CaptureRecord>>

    @Query("SELECT * FROM captures WHERE plain_text LIKE '%' || :keyword || '%' ORDER BY id DESC")
    fun searchByText(keyword: String): LiveData<List<CaptureRecord>>

    @Query("SELECT * FROM captures WHERE event_type = :eventType AND plain_text LIKE '%' || :keyword || '%' ORDER BY id DESC")
    fun searchByTextAndType(keyword: String, eventType: String): LiveData<List<CaptureRecord>>

    /** Search di dalam snapshot saja (PAGE + UPDATE) */
    @Query("SELECT * FROM captures WHERE event_type IN ('WINDOW_STATE_CHANGED', 'WINDOW_CONTENT_CHANGED') AND plain_text LIKE '%' || :keyword || '%' ORDER BY id DESC")
    fun searchSnapshotsByText(keyword: String): LiveData<List<CaptureRecord>>

    @Query("SELECT * FROM captures WHERE is_starred = 1 ORDER BY id DESC")
    fun getStarred(): LiveData<List<CaptureRecord>>

    @Query("SELECT * FROM captures WHERE tag = :tag ORDER BY id DESC")
    fun getByTag(tag: String): LiveData<List<CaptureRecord>>

    @Query("SELECT DISTINCT tag FROM captures WHERE tag != '' ORDER BY tag")
    fun getAllTags(): LiveData<List<String>>

    @Query("SELECT COUNT(*) FROM captures WHERE timestamp >= :sinceTimestamp")
    fun getCountSince(sinceTimestamp: String): Int

    @Query("SELECT COUNT(*) FROM captures")
    fun getTotalCount(): LiveData<Int>

    @Query("DELETE FROM captures")
    fun deleteAll()

    @Query("DELETE FROM captures WHERE timestamp < :beforeTimestamp")
    fun deleteOlderThan(beforeTimestamp: String): Int
}

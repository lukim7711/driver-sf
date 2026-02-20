package com.driversfpoc.screenreader.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.driversfpoc.screenreader.data.model.CaptureRecord

/**
 * Data Access Object untuk tabel captures.
 *
 * v2: Ditambahkan query untuk filter, search, tag, dan star.
 */
@Dao
interface CaptureDao {

    /** Insert 1 tangkapan baru */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: CaptureRecord): Long

    /** Update record (untuk tag, note, star) */
    @Update
    fun update(record: CaptureRecord)

    /** Ambil semua tangkapan, urut dari terbaru */
    @Query("SELECT * FROM captures ORDER BY id DESC")
    fun getAllDesc(): LiveData<List<CaptureRecord>>

    /** Ambil 1 tangkapan berdasarkan ID */
    @Query("SELECT * FROM captures WHERE id = :id")
    fun getById(id: Long): CaptureRecord?

    /** Filter berdasarkan event type */
    @Query("SELECT * FROM captures WHERE event_type = :eventType ORDER BY id DESC")
    fun getByEventType(eventType: String): LiveData<List<CaptureRecord>>

    /** Search teks di plain_text */
    @Query("SELECT * FROM captures WHERE plain_text LIKE '%' || :keyword || '%' ORDER BY id DESC")
    fun searchByText(keyword: String): LiveData<List<CaptureRecord>>

    /** Search teks + filter event type */
    @Query("SELECT * FROM captures WHERE event_type = :eventType AND plain_text LIKE '%' || :keyword || '%' ORDER BY id DESC")
    fun searchByTextAndType(keyword: String, eventType: String): LiveData<List<CaptureRecord>>

    /** Ambil yang di-star saja */
    @Query("SELECT * FROM captures WHERE is_starred = 1 ORDER BY id DESC")
    fun getStarred(): LiveData<List<CaptureRecord>>

    /** Filter berdasarkan tag */
    @Query("SELECT * FROM captures WHERE tag = :tag ORDER BY id DESC")
    fun getByTag(tag: String): LiveData<List<CaptureRecord>>

    /** Ambil semua tag yang pernah dipakai */
    @Query("SELECT DISTINCT tag FROM captures WHERE tag != '' ORDER BY tag")
    fun getAllTags(): LiveData<List<String>>

    /** Hitung jumlah tangkapan sejak waktu tertentu (untuk counter hari ini) */
    @Query("SELECT COUNT(*) FROM captures WHERE timestamp >= :sinceTimestamp")
    fun getCountSince(sinceTimestamp: String): Int

    /** Hitung total semua tangkapan */
    @Query("SELECT COUNT(*) FROM captures")
    fun getTotalCount(): LiveData<Int>

    /** Hapus semua tangkapan */
    @Query("DELETE FROM captures")
    fun deleteAll()

    /** Hapus tangkapan yang lebih lama dari waktu tertentu (untuk auto-purge) */
    @Query("DELETE FROM captures WHERE timestamp < :beforeTimestamp")
    fun deleteOlderThan(beforeTimestamp: String): Int
}

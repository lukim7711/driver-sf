package com.driversfpoc.screenreader.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.driversfpoc.screenreader.data.model.CaptureRecord

/**
 * Data Access Object untuk tabel captures.
 */
@Dao
interface CaptureDao {

    /** Insert 1 tangkapan baru */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: CaptureRecord): Long

    /** Ambil semua tangkapan, urut dari terbaru */
    @Query("SELECT * FROM captures ORDER BY id DESC")
    fun getAllDesc(): LiveData<List<CaptureRecord>>

    /** Ambil 1 tangkapan berdasarkan ID */
    @Query("SELECT * FROM captures WHERE id = :id")
    fun getById(id: Long): CaptureRecord?

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

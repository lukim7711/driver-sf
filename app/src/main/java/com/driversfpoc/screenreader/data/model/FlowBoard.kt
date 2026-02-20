package com.driversfpoc.screenreader.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Flow Board — koleksi log yang dikurasi dan diurutkan manual oleh user.
 *
 * Setiap flow board merepresentasikan 1 skenario flow yang sedang diriset.
 * Misal: "Order Lifecycle SPX", "Bid Manual Flow", "Pembatalan Order".
 *
 * Log asli di tabel captures TIDAK dihapus — flow board hanya menyimpan
 * referensi (capture_id) via tabel flow_board_items.
 */
@Entity(tableName = "flow_boards")
data class FlowBoard(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Nama flow board, misal: "Order Lifecycle SPX" */
    @ColumnInfo(name = "name")
    val name: String,

    /** Deskripsi opsional */
    @ColumnInfo(name = "description", defaultValue = "")
    val description: String = "",

    /** Waktu dibuat (ISO 8601) */
    @ColumnInfo(name = "created_at")
    val createdAt: String,

    /** Jumlah item (denormalized untuk performa di list) */
    @ColumnInfo(name = "item_count", defaultValue = "0")
    val itemCount: Int = 0
)

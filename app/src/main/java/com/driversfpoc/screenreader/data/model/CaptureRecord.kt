package com.driversfpoc.screenreader.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity Room untuk menyimpan 1 tangkapan layar.
 *
 * Setiap kali layar ShopeeFood Driver berubah (pindah halaman, scroll, dll),
 * 1 record CaptureRecord disimpan ke database.
 */
@Entity(tableName = "captures")
data class CaptureRecord(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Waktu tangkapan dalam format ISO 8601 */
    @ColumnInfo(name = "timestamp")
    val timestamp: String,

    /** Gabungan semua teks di layar, urut dari atas ke bawah */
    @ColumnInfo(name = "plain_text")
    val plainText: String,

    /** Struktur lengkap node tree dalam format JSON */
    @ColumnInfo(name = "node_tree_json")
    val nodeTreeJson: String,

    /** Tipe event: WINDOW_STATE_CHANGED atau WINDOW_CONTENT_CHANGED */
    @ColumnInfo(name = "event_type")
    val eventType: String,

    /** Jumlah karakter plain_text */
    @ColumnInfo(name = "text_length")
    val textLength: Int
)

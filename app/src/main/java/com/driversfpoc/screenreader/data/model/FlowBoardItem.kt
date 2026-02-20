package com.driversfpoc.screenreader.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Item di dalam Flow Board — referensi ke 1 capture record.
 *
 * position menentukan urutan tampil (drag & drop mengubah position).
 * note opsional untuk catatan per-item di dalam context flow.
 */
@Entity(
    tableName = "flow_board_items",
    foreignKeys = [
        ForeignKey(
            entity = FlowBoard::class,
            parentColumns = ["id"],
            childColumns = ["flow_board_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["flow_board_id"]),
        Index(value = ["capture_id"])
    ]
)
data class FlowBoardItem(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** FK ke flow_boards.id */
    @ColumnInfo(name = "flow_board_id")
    val flowBoardId: Long,

    /** FK ke captures.id (referensi, bukan copy) */
    @ColumnInfo(name = "capture_id")
    val captureId: Long,

    /** Urutan tampil di flow board (0-based) */
    @ColumnInfo(name = "position")
    val position: Int,

    /** Catatan opsional per item dalam context flow */
    @ColumnInfo(name = "note", defaultValue = "")
    val note: String = ""
)

package com.driversfpoc.screenreader.data.model

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Relasi FlowBoardItem + CaptureRecord.
 * Digunakan untuk menampilkan item flow board beserta data capture-nya.
 */
data class FlowBoardItemWithCapture(
    @Embedded
    val item: FlowBoardItem,

    @Relation(
        parentColumn = "capture_id",
        entityColumn = "id"
    )
    val capture: CaptureRecord
)

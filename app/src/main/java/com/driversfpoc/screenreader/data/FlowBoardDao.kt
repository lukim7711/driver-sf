package com.driversfpoc.screenreader.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.driversfpoc.screenreader.data.model.FlowBoard
import com.driversfpoc.screenreader.data.model.FlowBoardItem
import com.driversfpoc.screenreader.data.model.FlowBoardItemWithCapture

@Dao
interface FlowBoardDao {

    // ===== Flow Board CRUD =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBoard(board: FlowBoard): Long

    @Update
    fun updateBoard(board: FlowBoard)

    @Query("DELETE FROM flow_boards WHERE id = :boardId")
    fun deleteBoard(boardId: Long)

    /** Semua flow boards (LiveData untuk observe) */
    @Query("SELECT * FROM flow_boards ORDER BY created_at DESC")
    fun getAllBoards(): LiveData<List<FlowBoard>>

    /** Semua flow boards (blocking, untuk dialog) */
    @Query("SELECT * FROM flow_boards ORDER BY created_at DESC")
    fun getAllBoardsSync(): List<FlowBoard>

    @Query("SELECT * FROM flow_boards WHERE id = :boardId")
    fun getBoardById(boardId: Long): FlowBoard?

    // ===== Flow Board Items =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: FlowBoardItem): Long

    @Update
    fun updateItem(item: FlowBoardItem)

    @Query("DELETE FROM flow_board_items WHERE id = :itemId")
    fun deleteItem(itemId: Long)

    @Transaction
    @Query("SELECT * FROM flow_board_items WHERE flow_board_id = :boardId ORDER BY position ASC")
    fun getItemsWithCapture(boardId: Long): LiveData<List<FlowBoardItemWithCapture>>

    @Query("SELECT COUNT(*) FROM flow_board_items WHERE flow_board_id = :boardId")
    fun getItemCount(boardId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM flow_board_items WHERE flow_board_id = :boardId")
    fun getMaxPosition(boardId: Long): Int

    @Query("UPDATE flow_board_items SET position = :newPosition WHERE id = :itemId")
    fun updatePosition(itemId: Long, newPosition: Int)

    @Query("SELECT COUNT(*) FROM flow_board_items WHERE flow_board_id = :boardId AND capture_id = :captureId")
    fun isCaptureInBoard(boardId: Long, captureId: Long): Int

    @Query("UPDATE flow_boards SET item_count = (SELECT COUNT(*) FROM flow_board_items WHERE flow_board_id = :boardId) WHERE id = :boardId")
    fun refreshItemCount(boardId: Long)
}

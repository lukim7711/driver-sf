package com.driversfpoc.screenreader.data

import android.content.Context
import androidx.lifecycle.LiveData
import com.driversfpoc.screenreader.data.model.FlowBoard
import com.driversfpoc.screenreader.data.model.FlowBoardItem
import com.driversfpoc.screenreader.data.model.FlowBoardItemWithCapture
import java.util.concurrent.Executors

/**
 * Repository untuk Flow Board.
 * Semua operasi write di background thread.
 */
class FlowBoardRepository private constructor(context: Context) {

    private val dao: FlowBoardDao = AppDatabase.getInstance(context).flowBoardDao()
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        @Volatile
        private var INSTANCE: FlowBoardRepository? = null

        fun getInstance(context: Context): FlowBoardRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = FlowBoardRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // ===== Board CRUD =====

    fun insertBoard(board: FlowBoard, onResult: ((Long) -> Unit)? = null) {
        executor.execute {
            val id = dao.insertBoard(board)
            onResult?.invoke(id)
        }
    }

    fun updateBoard(board: FlowBoard) {
        executor.execute { dao.updateBoard(board) }
    }

    fun deleteBoard(boardId: Long) {
        executor.execute { dao.deleteBoard(boardId) }
    }

    fun getAllBoards(): LiveData<List<FlowBoard>> = dao.getAllBoards()

    fun getBoardById(boardId: Long): FlowBoard? = dao.getBoardById(boardId)

    // ===== Items =====

    /** Tambah capture ke flow board (append di akhir) */
    fun addCaptureToBoard(boardId: Long, captureId: Long, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            // Cek duplikat
            val exists = dao.isCaptureInBoard(boardId, captureId) > 0
            if (exists) {
                onResult?.invoke(false)
                return@execute
            }

            val maxPos = dao.getMaxPosition(boardId)
            val item = FlowBoardItem(
                flowBoardId = boardId,
                captureId = captureId,
                position = maxPos + 1
            )
            dao.insertItem(item)
            dao.refreshItemCount(boardId)
            onResult?.invoke(true)
        }
    }

    fun deleteItem(itemId: Long, boardId: Long) {
        executor.execute {
            dao.deleteItem(itemId)
            dao.refreshItemCount(boardId)
        }
    }

    fun getItemsWithCapture(boardId: Long): LiveData<List<FlowBoardItemWithCapture>> =
        dao.getItemsWithCapture(boardId)

    /** Reorder: swap posisi semua items sesuai list baru */
    fun reorderItems(items: List<FlowBoardItemWithCapture>) {
        executor.execute {
            items.forEachIndexed { index, itemWithCapture ->
                dao.updatePosition(itemWithCapture.item.id, index)
            }
        }
    }

    fun updateItemNote(itemId: Long, note: String) {
        executor.execute {
            // Using raw query via DAO would be cleaner, but for now:
            // We'll update via the item itself
        }
    }
}

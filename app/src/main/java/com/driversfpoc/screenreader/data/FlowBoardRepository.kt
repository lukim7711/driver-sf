package com.driversfpoc.screenreader.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import com.driversfpoc.screenreader.data.model.FlowBoard
import com.driversfpoc.screenreader.data.model.FlowBoardItem
import com.driversfpoc.screenreader.data.model.FlowBoardItemWithCapture
import java.util.concurrent.Executors

/**
 * Repository untuk akses data FlowBoard dan FlowBoardItem.
 *
 * Semua write operations dan blocking read operations dijalankan di
 * internal executor thread. UI layer TIDAK perlu membuat executor sendiri.
 *
 * Pattern:
 * - Write: fire-and-forget via executor, dengan optional callback di main thread
 * - Read reactive (LiveData): langsung return
 * - Read blocking: via async method dengan callback di main thread
 */
class FlowBoardRepository private constructor(context: Context) {

    private val dao: FlowBoardDao = AppDatabase.getInstance(context).flowBoardDao()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

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
            onResult?.let { callback -> mainHandler.post { callback(id) } }
        }
    }

    fun updateBoard(board: FlowBoard) {
        executor.execute { dao.updateBoard(board) }
    }

    fun deleteBoard(boardId: Long) {
        executor.execute { dao.deleteBoard(boardId) }
    }

    fun getAllBoards(): LiveData<List<FlowBoard>> = dao.getAllBoards()

    /**
     * Ambil semua boards secara async.
     * Callback dipanggil di main thread.
     *
     * Menggantikan getAllBoardsSync() blocking yang membutuhkan
     * caller untuk membuat executor sendiri.
     */
    fun getAllBoardsAsync(onResult: (List<FlowBoard>) -> Unit) {
        executor.execute {
            val boards = dao.getAllBoardsSync()
            mainHandler.post { onResult(boards) }
        }
    }

    fun getBoardByIdAsync(boardId: Long, onResult: (FlowBoard?) -> Unit) {
        executor.execute {
            val board = dao.getBoardById(boardId)
            mainHandler.post { onResult(board) }
        }
    }

    // ===== Items =====

    /** Tambah capture ke flow board (append di posisi terakhir) */
    fun addCaptureToBoard(boardId: Long, captureId: Long, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            val exists = dao.isCaptureInBoard(boardId, captureId) > 0
            if (exists) {
                onResult?.let { callback -> mainHandler.post { callback(false) } }
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
            onResult?.let { callback -> mainHandler.post { callback(true) } }
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

    /** Simpan urutan baru setelah drag & drop */
    fun reorderItems(items: List<FlowBoardItemWithCapture>) {
        executor.execute {
            items.forEachIndexed { index, itemWithCapture ->
                dao.updatePosition(itemWithCapture.item.id, index)
            }
        }
    }
}

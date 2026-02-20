package com.driversfpoc.screenreader.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.driversfpoc.screenreader.R
import com.driversfpoc.screenreader.data.FlowBoardRepository
import com.driversfpoc.screenreader.data.model.FlowBoardItemWithCapture
import com.driversfpoc.screenreader.databinding.ActivityFlowBoardDetailBinding
import java.util.concurrent.Executors

/**
 * Halaman detail Flow Board.
 *
 * Menampilkan item-item yang sudah dikurasi, bisa:
 * - Drag & drop untuk reorder (long press atau drag handle)
 * - Tap item untuk lihat detail capture
 * - Remove item dari flow board (tombol \u2715)
 * - Hapus seluruh flow board
 */
class FlowBoardDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOARD_ID = "extra_board_id"
    }

    private lateinit var binding: ActivityFlowBoardDetailBinding
    private lateinit var repository: FlowBoardRepository
    private lateinit var adapter: FlowBoardItemAdapter
    private val executor = Executors.newSingleThreadExecutor()

    private var boardId: Long = -1
    private var currentItems: MutableList<FlowBoardItemWithCapture> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlowBoardDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FlowBoardRepository.getInstance(applicationContext)

        boardId = intent.getLongExtra(EXTRA_BOARD_ID, -1)
        if (boardId == -1L) {
            finish()
            return
        }

        setupRecyclerView()
        setupDragAndDrop()
        setupButtons()
        loadBoard()
        observeItems()
    }

    private fun setupRecyclerView() {
        adapter = FlowBoardItemAdapter(
            onRemoveClick = { item ->
                repository.deleteItem(item.item.id, boardId)
            },
            onItemClick = { item ->
                val intent = Intent(this, CaptureDetailActivity::class.java).apply {
                    putExtra(CaptureDetailActivity.EXTRA_CAPTURE_ID, item.capture.id)
                }
                startActivity(intent)
            }
        )
        binding.recyclerItems.layoutManager = LinearLayoutManager(this)
        binding.recyclerItems.adapter = adapter
    }

    /**
     * Setup drag & drop menggunakan ItemTouchHelper bawaan Android.
     * Long press pada item untuk mulai drag.
     * Setelah drop, posisi baru disimpan ke database.
     */
    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition

                val item = currentItems.removeAt(from)
                currentItems.add(to, item)
                adapter.notifyItemMoved(from, to)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // Simpan urutan baru ke database
                repository.reorderItems(currentItems)
                // Update nomor posisi di UI
                adapter.submitList(currentItems.toList())
            }

            override fun isLongPressDragEnabled(): Boolean = true
        }

        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerItems)
    }

    private fun setupButtons() {
        binding.btnDeleteBoard.setOnClickListener {
            showDeleteConfirmation()
        }
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadBoard() {
        executor.execute {
            val board = repository.getBoardById(boardId) ?: return@execute
            runOnUiThread {
                binding.tvBoardName.text = board.name
                if (board.description.isNotBlank()) {
                    binding.tvBoardDescription.text = board.description
                    binding.tvBoardDescription.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun observeItems() {
        repository.getItemsWithCapture(boardId).observe(this) { items ->
            currentItems = items.toMutableList()
            adapter.submitList(items)

            val count = items.size
            binding.tvItemCount.text = getString(R.string.flow_board_item_count, count)

            if (items.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recyclerItems.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.recyclerItems.visibility = View.VISIBLE
            }
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_delete_board_title))
            .setMessage(getString(R.string.dialog_delete_board_message))
            .setPositiveButton(getString(R.string.confirm_yes)) { _, _ ->
                repository.deleteBoard(boardId)
                Toast.makeText(this, "Flow board dihapus", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(getString(R.string.confirm_no), null)
            .show()
    }
}

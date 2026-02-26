package com.driversfpoc.screenreader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.driversfpoc.screenreader.R
import com.driversfpoc.screenreader.data.FlowBoardRepository
import com.driversfpoc.screenreader.data.model.FlowBoardItemWithCapture
import com.driversfpoc.screenreader.databinding.ActivityFlowBoardDetailBinding
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Detail screen untuk melihat dan mengelola satu FlowBoard.
 *
 * Semua DB operations didelegasikan ke FlowBoardRepository yang
 * mengelola executor sendiri. Activity ini TIDAK membuat executor.
 */
class FlowBoardDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOARD_ID = "extra_board_id"
    }

    private lateinit var binding: ActivityFlowBoardDetailBinding
    private lateinit var repository: FlowBoardRepository
    private lateinit var adapter: FlowBoardItemAdapter

    private var boardId: Long = -1
    private var currentItems: MutableList<FlowBoardItemWithCapture> = mutableListOf()
    private var boardName: String = ""
    private var boardDescription: String = ""
    private var boardCreatedAt: String = ""
    private var pendingExportJson: String = ""

    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { writeJsonToUri(it) }
    }

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
                repository.reorderItems(currentItems)
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
        binding.btnExport.setOnClickListener {
            exportFlowBoard()
        }
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    /**
     * Load board menggunakan repository async method.
     * Tidak perlu executor sendiri.
     */
    private fun loadBoard() {
        repository.getBoardByIdAsync(boardId) { board ->
            if (board == null) return@getBoardByIdAsync
            boardName = board.name
            boardDescription = board.description
            boardCreatedAt = board.createdAt

            binding.tvBoardName.text = board.name
            if (board.description.isNotBlank()) {
                binding.tvBoardDescription.text = board.description
                binding.tvBoardDescription.visibility = View.VISIBLE
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

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Export
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private fun exportFlowBoard() {
        if (currentItems.isEmpty()) {
            Toast.makeText(this, getString(R.string.export_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val json = buildExportJson()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.export_title))
            .setItems(arrayOf(
                getString(R.string.export_share),
                getString(R.string.export_copy),
                getString(R.string.export_save)
            )) { _, which ->
                when (which) {
                    0 -> shareJson(json)
                    1 -> copyToClipboard(json)
                    2 -> saveAsFile(json)
                }
            }
            .show()
    }

    /**
     * Build export JSON. Setiap step hanya berisi node_tree (raw data).
     * Plain text TIDAK di-include karena itu cuma turunan dari node_tree.
     */
    private fun buildExportJson(): String {
        val timeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())

        val exportObj = JSONObject().apply {
            put("flow_board", JSONObject().apply {
                put("name", boardName)
                put("description", boardDescription)
                put("exported_at", Instant.now().toString())
                put("total_steps", currentItems.size)
            })

            put("steps", JSONArray().apply {
                currentItems.forEachIndexed { index, item ->
                    val capture = item.capture
                    val readableTime = try {
                        timeFormatter.format(Instant.parse(capture.timestamp))
                    } catch (e: Exception) {
                        capture.timestamp
                    }

                    val nodeTree = try {
                        if (capture.nodeTreeJson.isNotBlank()) {
                            JSONArray(capture.nodeTreeJson)
                        } else {
                            JSONArray()
                        }
                    } catch (e: Exception) {
                        JSONArray()
                    }

                    put(JSONObject().apply {
                        put("position", index + 1)
                        put("capture_id", capture.id)
                        put("event_type", when (capture.eventType) {
                            "WINDOW_STATE_CHANGED" -> "PAGE"
                            "WINDOW_CONTENT_CHANGED" -> "CONTENT_UPDATE"
                            "VIEW_CLICKED" -> "USER_CLICK"
                            "VIEW_SELECTED" -> "USER_SELECT"
                            else -> capture.eventType
                        })
                        put("timestamp", readableTime)
                        put("note", item.item.note)
                        put("tag", capture.tag)
                        put("is_starred", capture.isStarred)
                        put("node_tree", nodeTree)
                    })
                }
            })
        }

        return exportObj.toString(2)
    }

    private fun shareJson(json: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Flow Board: $boardName")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.export_share_chooser)))
    }

    private fun copyToClipboard(json: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Flow Board: $boardName", json)
        clipboard.setPrimaryClip(clip)

        val sizeKb = json.length / 1024
        val sizeText = if (sizeKb > 0) "${sizeKb}KB" else "${json.length} chars"
        Toast.makeText(this, getString(R.string.export_copied, sizeText), Toast.LENGTH_SHORT).show()
    }

    private fun saveAsFile(json: String) {
        pendingExportJson = json
        val safeName = boardName
            .replace("[^a-zA-Z0-9 ]".toRegex(), "")
            .replace(" ", "-")
            .lowercase()
            .take(50)
        saveFileLauncher.launch("flow-$safeName.json")
    }

    private fun writeJsonToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(pendingExportJson.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.export_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Delete
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

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

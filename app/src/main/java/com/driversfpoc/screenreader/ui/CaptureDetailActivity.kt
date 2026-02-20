package com.driversfpoc.screenreader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.driversfpoc.screenreader.R
import com.driversfpoc.screenreader.data.CaptureRepository
import com.driversfpoc.screenreader.data.FlowBoardRepository
import com.driversfpoc.screenreader.data.model.CaptureRecord
import com.driversfpoc.screenreader.data.model.FlowBoard
import com.driversfpoc.screenreader.databinding.ActivityCaptureDetailBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

class CaptureDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CAPTURE_ID = "extra_capture_id"
    }

    private lateinit var binding: ActivityCaptureDetailBinding
    private lateinit var repository: CaptureRepository
    private lateinit var flowBoardRepository: FlowBoardRepository
    private val executor = Executors.newSingleThreadExecutor()

    private val dateTimeFormatter = DateTimeFormatter
        .ofPattern("HH:mm:ss \u2014 dd MMM yyyy", Locale("id", "ID"))
        .withZone(ZoneId.systemDefault())

    private var currentRecord: CaptureRecord? = null
    private var isNodeTreeVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CaptureRepository.getInstance(applicationContext)
        flowBoardRepository = FlowBoardRepository.getInstance(applicationContext)

        val captureId = intent.getLongExtra(EXTRA_CAPTURE_ID, -1)
        if (captureId == -1L) {
            finish()
            return
        }

        setupButtons()
        loadCapture(captureId)
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Setup
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private fun setupButtons() {
        binding.btnCopyText.setOnClickListener {
            currentRecord?.let { copyToClipboard(it.plainText) }
        }
        binding.btnBack.setOnClickListener {
            finish()
        }
        binding.btnToggleNodeTree.setOnClickListener {
            isNodeTreeVisible = !isNodeTreeVisible
            binding.cardNodeTree.visibility = if (isNodeTreeVisible) View.VISIBLE else View.GONE
        }
        binding.btnStar.setOnClickListener {
            toggleStar()
        }
        binding.btnTag.setOnClickListener {
            showTagDialog()
        }
        binding.btnNote.setOnClickListener {
            showNoteDialog()
        }
        binding.btnAddToFlow.setOnClickListener {
            showAddToFlowDialog()
        }
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Load Data
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private fun loadCapture(captureId: Long) {
        executor.execute {
            val record = repository.getById(captureId)
            if (record == null) {
                runOnUiThread { finish() }
                return@execute
            }
            currentRecord = record
            runOnUiThread { displayRecord(record) }
        }
    }

    private fun displayRecord(record: CaptureRecord) {
        binding.tvTitle.text = getString(R.string.detail_title, record.id)

        val formattedTime = try {
            val instant = Instant.parse(record.timestamp)
            dateTimeFormatter.format(instant)
        } catch (e: Exception) {
            record.timestamp
        }
        binding.tvTimestamp.text = getString(R.string.detail_time, formattedTime)
        binding.tvCharCount.text = getString(R.string.detail_chars, record.textLength)
        binding.tvEventType.text = record.eventType

        updateStarUI(record.isStarred)

        if (record.tag.isNotBlank()) {
            binding.tvCurrentTag.text = record.tag
            binding.tvCurrentTag.visibility = View.VISIBLE
        } else {
            binding.tvCurrentTag.visibility = View.GONE
        }

        if (record.note.isNotBlank()) {
            binding.tvNote.text = record.note
            binding.cardNote.visibility = View.VISIBLE
        } else {
            binding.cardNote.visibility = View.GONE
        }

        binding.tvPlainText.text = record.plainText

        if (record.nodeTreeJson.isBlank()) {
            binding.btnToggleNodeTree.visibility = View.GONE
            binding.cardNodeTree.visibility = View.GONE
        } else {
            binding.btnToggleNodeTree.visibility = View.VISIBLE
            binding.tvNodeTree.text = record.nodeTreeJson
        }
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Star / Tag / Note
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private fun toggleStar() {
        val record = currentRecord ?: return
        val updated = record.copy(isStarred = !record.isStarred)
        repository.update(updated)
        currentRecord = updated
        updateStarUI(updated.isStarred)
        val msg = if (updated.isStarred) "\u2B50 Ditandai penting" else "Tanda bintang dihapus"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun updateStarUI(isStarred: Boolean) {
        binding.btnStar.text = if (isStarred) "\u2B50" else "\u2606"
    }

    private fun showTagDialog() {
        val record = currentRecord ?: return
        val commonTags = arrayOf(
            "bid-auto", "bid-manual", "order-list", "order-detail",
            "kerja-bagus", "riwayat", "insentif", "dibatalkan", "(ketik sendiri)"
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_tag_title))
            .setItems(commonTags) { _, which ->
                if (which == commonTags.size - 1) {
                    showCustomTagDialog()
                } else {
                    applyTag(commonTags[which])
                }
            }
            .setNeutralButton(getString(R.string.dialog_clear_tag)) { _, _ -> applyTag("") }
            .setNegativeButton(getString(R.string.confirm_no), null)
            .show()
    }

    private fun showCustomTagDialog() {
        val editText = EditText(this).apply {
            hint = "Contoh: pickup-screen"
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Ketik Tag")
            .setView(editText)
            .setPositiveButton("Simpan") { _, _ ->
                val tag = editText.text.toString().trim().lowercase().replace(" ", "-")
                if (tag.isNotBlank()) applyTag(tag)
            }
            .setNegativeButton(getString(R.string.confirm_no), null)
            .show()
    }

    private fun applyTag(tag: String) {
        val record = currentRecord ?: return
        val updated = record.copy(tag = tag)
        repository.update(updated)
        currentRecord = updated
        if (tag.isNotBlank()) {
            binding.tvCurrentTag.text = tag
            binding.tvCurrentTag.visibility = View.VISIBLE
            Toast.makeText(this, "Tag: $tag", Toast.LENGTH_SHORT).show()
        } else {
            binding.tvCurrentTag.visibility = View.GONE
            Toast.makeText(this, "Tag dihapus", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNoteDialog() {
        val record = currentRecord ?: return
        val editText = EditText(this).apply {
            hint = "Tulis catatan riset..."
            setText(record.note)
            minLines = 3
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_note_title))
            .setView(editText)
            .setPositiveButton("Simpan") { _, _ ->
                val note = editText.text.toString().trim()
                val updated = record.copy(note = note)
                repository.update(updated)
                currentRecord = updated
                if (note.isNotBlank()) {
                    binding.tvNote.text = note
                    binding.cardNote.visibility = View.VISIBLE
                } else {
                    binding.cardNote.visibility = View.GONE
                }
                Toast.makeText(this, "Catatan disimpan", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Hapus") { _, _ ->
                val updated = record.copy(note = "")
                repository.update(updated)
                currentRecord = updated
                binding.cardNote.visibility = View.GONE
            }
            .setNegativeButton(getString(R.string.confirm_no), null)
            .show()
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Flow Board
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    /**
     * Dialog untuk menambahkan capture ini ke Flow Board.
     * - Jika sudah ada board: tampilkan list pilihan + opsi buat baru
     * - Jika belum ada board: langsung tawarkan buat baru
     */
    private fun showAddToFlowDialog() {
        val record = currentRecord ?: return

        executor.execute {
            val boards = flowBoardRepository.getAllBoardsSync()

            runOnUiThread {
                if (boards.isEmpty()) {
                    showCreateBoardAndAddDialog(record.id)
                } else {
                    val options = boards.map { it.name }.toTypedArray() + "(Buat flow board baru)"

                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_add_to_flow_title))
                        .setItems(options) { _, which ->
                            if (which == options.size - 1) {
                                showCreateBoardAndAddDialog(record.id)
                            } else {
                                val board = boards[which]
                                addCaptureToBoard(board, record.id)
                            }
                        }
                        .setNegativeButton(getString(R.string.confirm_no), null)
                        .show()
                }
            }
        }
    }

    private fun showCreateBoardAndAddDialog(captureId: Long) {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val nameInput = EditText(this).apply {
            hint = "Nama flow, misal: Order Lifecycle SPX"
            requestFocus()
        }
        val descInput = EditText(this).apply {
            hint = "Deskripsi (opsional)"
        }
        layout.addView(nameInput)
        layout.addView(descInput)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_create_board_title))
            .setView(layout)
            .setPositiveButton("Buat & Tambahkan") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val board = FlowBoard(
                    name = name,
                    description = descInput.text.toString().trim(),
                    createdAt = Instant.now().toString()
                )
                flowBoardRepository.insertBoard(board) { boardId ->
                    flowBoardRepository.addCaptureToBoard(boardId, captureId) { success ->
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this, "Ditambahkan ke \"$name\"", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.confirm_no), null)
            .show()
    }

    private fun addCaptureToBoard(board: FlowBoard, captureId: Long) {
        flowBoardRepository.addCaptureToBoard(board.id, captureId) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Ditambahkan ke \"${board.name}\"", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Sudah ada di flow board ini", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Helpers
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Captured Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.text_copied), Toast.LENGTH_SHORT).show()
    }
}

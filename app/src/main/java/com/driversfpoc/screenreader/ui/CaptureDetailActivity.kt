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
import com.driversfpoc.screenreader.data.model.CaptureRecord
import com.driversfpoc.screenreader.databinding.ActivityCaptureDetailBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Halaman 2 \u2014 Detail Tangkapan
 *
 * Menampilkan:
 * - Metadata: nomor, waktu, jumlah karakter, event type
 * - Star toggle (tandai penting)
 * - Tag selector (pilih dari preset atau ketik sendiri)
 * - Note card (catatan riset)
 * - Plain text lengkap
 * - Node tree JSON (toggle show/hide, hidden for click events)
 * - Tombol: Salin Teks, Catatan, Kembali
 */
class CaptureDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CAPTURE_ID = "extra_capture_id"
    }

    private lateinit var binding: ActivityCaptureDetailBinding
    private lateinit var repository: CaptureRepository
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
        // Salin Teks
        binding.btnCopyText.setOnClickListener {
            currentRecord?.let { copyToClipboard(it.plainText) }
        }

        // Kembali
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Toggle Node Tree
        binding.btnToggleNodeTree.setOnClickListener {
            isNodeTreeVisible = !isNodeTreeVisible
            binding.cardNodeTree.visibility = if (isNodeTreeVisible) View.VISIBLE else View.GONE
        }

        // Star
        binding.btnStar.setOnClickListener {
            toggleStar()
        }

        // Tag
        binding.btnTag.setOnClickListener {
            showTagDialog()
        }

        // Note
        binding.btnNote.setOnClickListener {
            showNoteDialog()
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

            runOnUiThread {
                displayRecord(record)
            }
        }
    }

    private fun displayRecord(record: CaptureRecord) {
        // Header
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

        // Star
        updateStarUI(record.isStarred)

        // Tag
        if (record.tag.isNotBlank()) {
            binding.tvCurrentTag.text = record.tag
            binding.tvCurrentTag.visibility = View.VISIBLE
        } else {
            binding.tvCurrentTag.visibility = View.GONE
        }

        // Note
        if (record.note.isNotBlank()) {
            binding.tvNote.text = record.note
            binding.cardNote.visibility = View.VISIBLE
        } else {
            binding.cardNote.visibility = View.GONE
        }

        // Plain text
        binding.tvPlainText.text = record.plainText

        // Node tree JSON (hide for click events since they don't have it)
        if (record.nodeTreeJson.isBlank()) {
            binding.btnToggleNodeTree.visibility = View.GONE
            binding.cardNodeTree.visibility = View.GONE
        } else {
            binding.btnToggleNodeTree.visibility = View.VISIBLE
            binding.tvNodeTree.text = record.nodeTreeJson
        }
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Star / Tag / Note Actions
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
            .setNeutralButton(getString(R.string.dialog_clear_tag)) { _, _ ->
                applyTag("")
            }
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
                val tag = editText.text.toString().trim()
                    .lowercase().replace(" ", "-")
                if (tag.isNotBlank()) {
                    applyTag(tag)
                }
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
    // Helpers
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Captured Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.text_copied), Toast.LENGTH_SHORT).show()
    }
}

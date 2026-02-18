package com.driversfpoc.screenreader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.driversfpoc.screenreader.R
import com.driversfpoc.screenreader.data.CaptureRepository
import com.driversfpoc.screenreader.databinding.ActivityCaptureDetailBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Halaman 2 — Detail Tangkapan
 *
 * Menampilkan:
 * - Metadata: nomor, waktu, jumlah karakter, package app
 * - Plain text lengkap (semua teks yang tertangkap dari layar)
 * - Node tree JSON (toggle show/hide, untuk developer)
 * - Tombol: Salin Teks, Kembali
 */
class CaptureDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CAPTURE_ID = "extra_capture_id"
        private const val TARGET_PACKAGE = "com.shopee.foody.driver.id"
    }

    private lateinit var binding: ActivityCaptureDetailBinding
    private val executor = Executors.newSingleThreadExecutor()

    private val dateTimeFormatter = DateTimeFormatter
        .ofPattern("HH:mm:ss \u2014 dd MMM yyyy", Locale("id", "ID"))
        .withZone(ZoneId.systemDefault())

    private var plainText: String = ""
    private var isNodeTreeVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val captureId = intent.getLongExtra(EXTRA_CAPTURE_ID, -1)
        if (captureId == -1L) {
            finish()
            return
        }

        setupButtons()
        loadCapture(captureId)
    }

    // ──────────────────────────────────────────────
    // Setup
    // ──────────────────────────────────────────────

    private fun setupButtons() {
        // Salin Teks
        binding.btnCopyText.setOnClickListener {
            copyToClipboard(plainText)
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
    }

    // ──────────────────────────────────────────────
    // Load Data
    // ──────────────────────────────────────────────

    private fun loadCapture(captureId: Long) {
        val repository = CaptureRepository.getInstance(applicationContext)

        executor.execute {
            val record = repository.getById(captureId)

            if (record == null) {
                runOnUiThread { finish() }
                return@execute
            }

            plainText = record.plainText

            runOnUiThread {
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
                binding.tvAppPackage.text = getString(R.string.detail_app, TARGET_PACKAGE)

                // Plain text
                binding.tvPlainText.text = record.plainText

                // Node tree JSON
                binding.tvNodeTree.text = record.nodeTreeJson

                // Event type
                binding.tvEventType.text = "Event: ${record.eventType}"
            }
        }
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Captured Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.text_copied), Toast.LENGTH_SHORT).show()
    }
}

package com.driversfpoc.screenreader.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.driversfpoc.screenreader.R
import com.driversfpoc.screenreader.data.CaptureRepository
import com.driversfpoc.screenreader.databinding.ActivityMainBinding
import com.driversfpoc.screenreader.service.ScreenReaderService

/**
 * Halaman 1 — Daftar Tangkapan
 *
 * Menampilkan:
 * - Status service (aktif/tidak aktif)
 * - Jumlah tangkapan hari ini
 * - RecyclerView daftar semua tangkapan (terbaru di atas)
 * - Tombol: Aktifkan/Nonaktifkan, Hapus Semua Log
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CaptureRepository
    private lateinit var adapter: CaptureAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CaptureRepository.getInstance(applicationContext)

        setupRecyclerView()
        setupButtons()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    // ──────────────────────────────────────────────
    // Setup
    // ──────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = CaptureAdapter { record ->
            // Tap item → buka detail
            val intent = Intent(this, CaptureDetailActivity::class.java).apply {
                putExtra(CaptureDetailActivity.EXTRA_CAPTURE_ID, record.id)
            }
            startActivity(intent)
        }
        binding.recyclerCaptures.layoutManager = LinearLayoutManager(this)
        binding.recyclerCaptures.adapter = adapter
    }

    private fun setupButtons() {
        // Tombol Aktifkan → buka Accessibility Settings
        binding.btnEnable.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Tombol Nonaktifkan → buka Accessibility Settings (user matikan manual)
        binding.btnDisable.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Tombol Hapus Semua Log
        binding.btnClearLogs.setOnClickListener {
            showClearConfirmation()
        }
    }

    // ──────────────────────────────────────────────
    // Observe Data
    // ──────────────────────────────────────────────

    private fun observeData() {
        // Observe daftar tangkapan (LiveData → auto-update RecyclerView)
        repository.getAllDesc().observe(this) { captures ->
            adapter.submitList(captures)

            // Show/hide empty state
            if (captures.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recyclerCaptures.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.recyclerCaptures.visibility = View.VISIBLE
            }
        }

        // Observe total count
        repository.getTotalCount().observe(this) { count ->
            binding.tvCaptureCount.text = getString(R.string.capture_today, count ?: 0)
        }
    }

    // ──────────────────────────────────────────────
    // UI Updates
    // ──────────────────────────────────────────────

    private fun updateServiceStatus() {
        val isActive = ScreenReaderService.isRunning

        if (isActive) {
            binding.tvStatus.text = getString(R.string.status_active)
            binding.btnEnable.visibility = View.GONE
            binding.bottomButtons.visibility = View.VISIBLE
        } else {
            binding.tvStatus.text = getString(R.string.status_inactive)
            binding.btnEnable.visibility = View.VISIBLE
            binding.bottomButtons.visibility = View.VISIBLE
        }
    }

    private fun showClearConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_clear_title))
            .setMessage(getString(R.string.confirm_clear_message))
            .setPositiveButton(getString(R.string.confirm_yes)) { _, _ ->
                repository.deleteAll()
            }
            .setNegativeButton(getString(R.string.confirm_no), null)
            .show()
    }
}

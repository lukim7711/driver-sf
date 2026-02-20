package com.driversfpoc.screenreader.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.driversfpoc.screenreader.R
import com.driversfpoc.screenreader.data.CaptureRepository
import com.driversfpoc.screenreader.data.model.CaptureRecord
import com.driversfpoc.screenreader.databinding.ActivityMainBinding
import com.driversfpoc.screenreader.service.ScreenReaderService

/**
 * Halaman 1 \u2014 Dashboard utama
 *
 * Menampilkan:
 * - Status service (aktif/tidak)
 * - Search bar untuk pencarian teks di log
 * - Filter chips: Semua, Page, Click, Update, Starred
 * - Daftar tangkapan terbaru (RecyclerView)
 * - Tombol: Hapus Log, Nonaktifkan
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CaptureRepository
    private lateinit var adapter: CaptureAdapter

    // Filter state
    private var activeFilter: String? = null // null = semua
    private var searchKeyword: String = ""

    // Observer tracking agar bisa di-remove saat filter berubah
    private var currentLiveData: LiveData<List<CaptureRecord>>? = null
    private var currentObserver: Observer<List<CaptureRecord>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CaptureRepository.getInstance(applicationContext)

        setupRecyclerView()
        setupButtons()
        setupFilterChips()
        setupSearch()
        applyFilter()
        observeTotalCount()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Setup
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private fun setupRecyclerView() {
        adapter = CaptureAdapter { record ->
            val intent = Intent(this, CaptureDetailActivity::class.java).apply {
                putExtra(CaptureDetailActivity.EXTRA_CAPTURE_ID, record.id)
            }
            startActivity(intent)
        }
        binding.recyclerCaptures.layoutManager = LinearLayoutManager(this)
        binding.recyclerCaptures.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnEnable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnDisable.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnClearLogs.setOnClickListener {
            showClearConfirmation()
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener {
            activeFilter = null
            applyFilter()
        }
        binding.chipPage.setOnClickListener {
            activeFilter = "WINDOW_STATE_CHANGED"
            applyFilter()
        }
        binding.chipClick.setOnClickListener {
            activeFilter = "VIEW_CLICKED"
            applyFilter()
        }
        binding.chipUpdate.setOnClickListener {
            activeFilter = "WINDOW_CONTENT_CHANGED"
            applyFilter()
        }
        binding.chipStarred.setOnClickListener {
            activeFilter = "STARRED"
            applyFilter()
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchKeyword = s?.toString()?.trim() ?: ""
                applyFilter()
            }
        })
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // Data Observation
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private fun observeTotalCount() {
        repository.getTotalCount().observe(this) { count ->
            binding.tvCaptureCount.text = getString(R.string.capture_today, count ?: 0)
        }
    }

    /**
     * Menerapkan filter aktif + keyword pencarian.
     * Melepas observer lama dan memasang observer baru sesuai kombinasi filter.
     */
    private fun applyFilter() {
        // Remove previous observer
        currentLiveData?.let { ld ->
            currentObserver?.let { obs -> ld.removeObserver(obs) }
        }

        // Tentukan LiveData berdasarkan kombinasi filter + search
        val liveData: LiveData<List<CaptureRecord>> = when {
            activeFilter == "STARRED" -> {
                repository.getStarred()
            }
            searchKeyword.isNotBlank() && activeFilter != null -> {
                repository.searchByTextAndType(searchKeyword, activeFilter!!)
            }
            searchKeyword.isNotBlank() -> {
                repository.searchByText(searchKeyword)
            }
            activeFilter != null -> {
                repository.getByEventType(activeFilter!!)
            }
            else -> {
                repository.getAllDesc()
            }
        }

        val observer = Observer<List<CaptureRecord>> { captures ->
            adapter.submitList(captures)
            if (captures.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recyclerCaptures.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.recyclerCaptures.visibility = View.VISIBLE
            }
        }

        liveData.observe(this, observer)
        currentLiveData = liveData
        currentObserver = observer
    }

    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    // UI Updates
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

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

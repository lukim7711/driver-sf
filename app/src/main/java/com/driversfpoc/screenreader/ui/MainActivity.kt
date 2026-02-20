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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.driversfpoc.screenreader.R
import com.driversfpoc.screenreader.data.CaptureRepository
import com.driversfpoc.screenreader.data.model.CaptureRecord
import com.driversfpoc.screenreader.databinding.ActivityMainBinding
import com.driversfpoc.screenreader.service.ScreenReaderService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: CaptureRepository
    private lateinit var adapter: CaptureAdapter

    private var activeFilter: String? = null
    private var searchKeyword: String = ""
    private var currentLiveData: LiveData<List<CaptureRecord>>? = null
    private var currentObserver: Observer<List<CaptureRecord>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CaptureRepository.getInstance(applicationContext)

        setupRecyclerView()
        setupSwipeToDelete()
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

    /**
     * Swipe ke kiri untuk hapus log.
     * Menampilkan dialog konfirmasi sebelum hapus.
     * Jika batal, item dikembalikan ke posisi semula.
     */
    private fun setupSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val record = adapter.currentList[position]

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Hapus Log #${record.id}?")
                    .setMessage("Log ini akan dihapus permanen.")
                    .setPositiveButton(getString(R.string.confirm_yes)) { _, _ ->
                        repository.deleteById(record.id)
                    }
                    .setNegativeButton(getString(R.string.confirm_no)) { _, _ ->
                        adapter.notifyItemChanged(position)
                    }
                    .setOnCancelListener {
                        adapter.notifyItemChanged(position)
                    }
                    .show()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerCaptures)
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
        binding.btnFlowBoards.setOnClickListener {
            startActivity(Intent(this, FlowBoardListActivity::class.java))
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
    // Data
    // \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500

    private fun observeTotalCount() {
        repository.getTotalCount().observe(this) { count ->
            binding.tvCaptureCount.text = getString(R.string.capture_today, count ?: 0)
        }
    }

    private fun applyFilter() {
        currentLiveData?.let { ld ->
            currentObserver?.let { obs -> ld.removeObserver(obs) }
        }

        val liveData: LiveData<List<CaptureRecord>> = when {
            activeFilter == "STARRED" -> repository.getStarred()
            searchKeyword.isNotBlank() && activeFilter != null ->
                repository.searchByTextAndType(searchKeyword, activeFilter!!)
            searchKeyword.isNotBlank() -> repository.searchByText(searchKeyword)
            activeFilter != null -> repository.getByEventType(activeFilter!!)
            else -> repository.getAllDesc()
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
    // UI
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

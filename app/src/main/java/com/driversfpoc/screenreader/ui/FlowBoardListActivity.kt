package com.driversfpoc.screenreader.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.driversfpoc.screenreader.R
import com.driversfpoc.screenreader.data.FlowBoardRepository
import com.driversfpoc.screenreader.data.model.FlowBoard
import com.driversfpoc.screenreader.databinding.ActivityFlowBoardListBinding
import java.time.Instant

/**
 * Halaman daftar Flow Boards.
 * User bisa melihat semua flow board, membuat baru, atau masuk ke detail.
 */
class FlowBoardListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFlowBoardListBinding
    private lateinit var repository: FlowBoardRepository
    private lateinit var adapter: FlowBoardListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlowBoardListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = FlowBoardRepository.getInstance(applicationContext)

        setupRecyclerView()
        setupButtons()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = FlowBoardListAdapter { board ->
            val intent = Intent(this, FlowBoardDetailActivity::class.java).apply {
                putExtra(FlowBoardDetailActivity.EXTRA_BOARD_ID, board.id)
            }
            startActivity(intent)
        }
        binding.recyclerFlowBoards.layoutManager = LinearLayoutManager(this)
        binding.recyclerFlowBoards.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnCreateBoard.setOnClickListener {
            showCreateBoardDialog()
        }
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun observeData() {
        repository.getAllBoards().observe(this) { boards ->
            adapter.submitList(boards)
            if (boards.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.recyclerFlowBoards.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.recyclerFlowBoards.visibility = View.VISIBLE
            }
        }
    }

    private fun showCreateBoardDialog() {
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
            .setPositiveButton("Buat") { _, _ ->
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
                repository.insertBoard(board)
                Toast.makeText(this, "Flow board \"$name\" dibuat", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.confirm_no), null)
            .show()
    }
}

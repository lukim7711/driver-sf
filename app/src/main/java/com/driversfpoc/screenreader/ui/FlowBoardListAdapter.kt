package com.driversfpoc.screenreader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.driversfpoc.screenreader.data.model.FlowBoard
import com.driversfpoc.screenreader.databinding.ItemFlowBoardBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class FlowBoardListAdapter(
    private val onItemClick: (FlowBoard) -> Unit
) : ListAdapter<FlowBoard, FlowBoardListAdapter.ViewHolder>(DiffCallback) {

    private val dateFormatter = DateTimeFormatter
        .ofPattern("dd MMM yyyy", Locale("id", "ID"))
        .withZone(ZoneId.systemDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFlowBoardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemFlowBoardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(board: FlowBoard) {
            binding.tvBoardName.text = "\uD83D\uDCCB ${board.name}"

            if (board.description.isNotBlank()) {
                binding.tvBoardDescription.text = board.description
                binding.tvBoardDescription.visibility = View.VISIBLE
            } else {
                binding.tvBoardDescription.visibility = View.GONE
            }

            binding.tvItemCount.text = "${board.itemCount} langkah"

            val date = try {
                val instant = Instant.parse(board.createdAt)
                dateFormatter.format(instant)
            } catch (e: Exception) {
                ""
            }
            binding.tvCreatedAt.text = date

            binding.root.setOnClickListener {
                onItemClick(board)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FlowBoard>() {
        override fun areItemsTheSame(old: FlowBoard, new: FlowBoard) = old.id == new.id
        override fun areContentsTheSame(old: FlowBoard, new: FlowBoard) = old == new
    }
}

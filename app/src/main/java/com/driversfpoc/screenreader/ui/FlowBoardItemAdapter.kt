package com.driversfpoc.screenreader.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.driversfpoc.screenreader.data.model.FlowBoardItemWithCapture
import com.driversfpoc.screenreader.databinding.ItemFlowBoardEntryBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FlowBoardItemAdapter(
    private val onRemoveClick: (FlowBoardItemWithCapture) -> Unit,
    private val onItemClick: (FlowBoardItemWithCapture) -> Unit
) : ListAdapter<FlowBoardItemWithCapture, FlowBoardItemAdapter.ViewHolder>(DiffCallback) {

    private val timeFormatter = DateTimeFormatter
        .ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFlowBoardEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(
        private val binding: ItemFlowBoardEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FlowBoardItemWithCapture, position: Int) {
            val capture = item.capture

            binding.tvPosition.text = "${position + 1}."

            // Event badge: PAGE + UPDATE digabung jadi SNAPSHOT
            val (badgeText, badgeColor) = when (capture.eventType) {
                "VIEW_CLICKED" -> "\uD83D\uDD8B CLICK" to "#FF6B35"
                "VIEW_SELECTED" -> "\uD83D\uDD18 SELECT" to "#9C27B0"
                "WINDOW_STATE_CHANGED",
                "WINDOW_CONTENT_CHANGED" -> "\uD83D\uDCF8 SNAPSHOT" to "#4CAF50"
                else -> "\u2753 OTHER" to "#757575"
            }
            binding.tvEventBadge.text = badgeText
            val badgeBg = GradientDrawable().apply {
                setColor(Color.parseColor(badgeColor))
                cornerRadius = 12f
            }
            binding.tvEventBadge.background = badgeBg

            val time = try {
                val instant = Instant.parse(capture.timestamp)
                timeFormatter.format(instant)
            } catch (e: Exception) {
                "--:--:--"
            }
            binding.tvTime.text = time
            binding.tvCaptureId.text = "#${capture.id}"

            val preview = if (capture.eventType in listOf("VIEW_CLICKED", "VIEW_SELECTED")) {
                capture.plainText.lines().firstOrNull()?.take(100) ?: ""
            } else {
                capture.plainText
                    .lines()
                    .filter { it.length > 3 }
                    .take(3)
                    .joinToString(" \u2022 ")
                    .take(100)
            }
            binding.tvPreview.text = preview

            binding.btnRemove.setOnClickListener {
                onRemoveClick(item)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FlowBoardItemWithCapture>() {
        override fun areItemsTheSame(old: FlowBoardItemWithCapture, new: FlowBoardItemWithCapture) =
            old.item.id == new.item.id
        override fun areContentsTheSame(old: FlowBoardItemWithCapture, new: FlowBoardItemWithCapture) =
            old == new
    }
}

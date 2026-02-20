package com.driversfpoc.screenreader.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.driversfpoc.screenreader.data.model.CaptureRecord
import com.driversfpoc.screenreader.databinding.ItemCaptureBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CaptureAdapter(
    private val onItemClick: (CaptureRecord) -> Unit
) : ListAdapter<CaptureRecord, CaptureAdapter.ViewHolder>(DiffCallback) {

    private val timeFormatter = DateTimeFormatter
        .ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCaptureBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemCaptureBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: CaptureRecord) {
            // Event badge with color
            val (badgeText, badgeColor) = when (record.eventType) {
                "VIEW_CLICKED" -> "\uD83D\uDD8B CLICK" to "#FF6B35"
                "VIEW_SELECTED" -> "\uD83D\uDD18 SELECT" to "#9C27B0"
                "WINDOW_STATE_CHANGED" -> "\uD83D\uDCF1 PAGE" to "#4CAF50"
                "WINDOW_CONTENT_CHANGED" -> "\uD83D\uDCDD UPDATE" to "#2196F3"
                else -> "\u2753 OTHER" to "#757575"
            }
            binding.tvEventBadge.text = badgeText
            val badgeBg = GradientDrawable().apply {
                setColor(Color.parseColor(badgeColor))
                cornerRadius = 12f
            }
            binding.tvEventBadge.background = badgeBg

            // Time
            val time = try {
                val instant = Instant.parse(record.timestamp)
                timeFormatter.format(instant)
            } catch (e: Exception) {
                "--:--:--"
            }
            binding.tvTime.text = time
            binding.tvCaptureNumber.text = "#${record.id}"

            // Star
            binding.tvStar.visibility = if (record.isStarred) View.VISIBLE else View.GONE

            // Tag
            if (record.tag.isNotBlank()) {
                binding.tvTag.text = record.tag
                binding.tvTag.visibility = View.VISIBLE
                val tagBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#E8F5E9"))
                    cornerRadius = 8f
                    setStroke(1, Color.parseColor("#4CAF50"))
                }
                binding.tvTag.background = tagBg
            } else {
                binding.tvTag.visibility = View.GONE
            }

            // Smart preview: 3 baris bermakna untuk snapshot, info langsung untuk klik
            val preview = if (record.eventType in listOf("VIEW_CLICKED", "VIEW_SELECTED")) {
                // Klik: tampilkan info klik langsung
                record.plainText.take(150)
            } else {
                // Snapshot: gabung 3 baris bermakna dengan bullet
                record.plainText
                    .lines()
                    .filter { it.length > 3 }
                    .take(3)
                    .joinToString(" \u2022 ")
                    .take(150)
            }
            binding.tvPreview.text = preview

            binding.tvCharCount.text = "${record.textLength} chars \u2022 \u2190 swipe hapus"

            binding.root.setOnClickListener {
                onItemClick(record)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CaptureRecord>() {
        override fun areItemsTheSame(old: CaptureRecord, new: CaptureRecord) = old.id == new.id
        override fun areContentsTheSame(old: CaptureRecord, new: CaptureRecord) = old == new
    }
}

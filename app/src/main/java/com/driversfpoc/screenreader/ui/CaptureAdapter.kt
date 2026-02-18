package com.driversfpoc.screenreader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.driversfpoc.screenreader.data.model.CaptureRecord
import com.driversfpoc.screenreader.databinding.ItemCaptureBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RecyclerView Adapter untuk daftar tangkapan.
 * Menggunakan ListAdapter + DiffUtil untuk performa optimal.
 */
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
            // Time
            val time = try {
                val instant = Instant.parse(record.timestamp)
                "[${timeFormatter.format(instant)}]"
            } catch (e: Exception) {
                "[--:--:--]"
            }
            binding.tvTime.text = time

            // Capture number
            binding.tvCaptureNumber.text = "Tangkapan #${record.id}"

            // Preview (first 100 chars, max 2 lines)
            val preview = record.plainText
                .replace("\n", " \u2014 ")
                .take(100)
            binding.tvPreview.text = "\"$preview\""

            // Character count
            binding.tvCharCount.text = "Teks: ${record.textLength} karakter"

            // Event type (shortened)
            val shortType = record.eventType
                .replace("WINDOW_", "")
            binding.tvEventType.text = shortType

            // Click listener
            binding.root.setOnClickListener {
                onItemClick(record)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CaptureRecord>() {
        override fun areItemsTheSame(old: CaptureRecord, new: CaptureRecord) =
            old.id == new.id

        override fun areContentsTheSame(old: CaptureRecord, new: CaptureRecord) =
            old == new
    }
}

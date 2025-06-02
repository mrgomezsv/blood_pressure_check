package com.mrgomez.bloodpressurecheck

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mrgomez.bloodpressurecheck.databinding.ItemBloodPressureBinding
import com.mrgomez.bloodpressurecheck.model.BloodPressureRecord
import java.text.SimpleDateFormat
import java.util.Locale

class BloodPressureAdapter : ListAdapter<BloodPressureRecord, BloodPressureAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBloodPressureBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemBloodPressureBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun bind(record: BloodPressureRecord) {
            binding.apply {
                tvSystolic.text = record.systolic.toString()
                tvDiastolic.text = record.diastolic.toString()
                tvPulse.text = record.pulse.toString()
                tvNotes.text = record.notes
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<BloodPressureRecord>() {
        override fun areItemsTheSame(oldItem: BloodPressureRecord, newItem: BloodPressureRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BloodPressureRecord, newItem: BloodPressureRecord): Boolean {
            return oldItem == newItem
        }
    }
} 
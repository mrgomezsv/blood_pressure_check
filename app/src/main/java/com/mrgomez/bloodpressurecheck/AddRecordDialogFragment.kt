package com.mrgomez.bloodpressurecheck

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mrgomez.bloodpressurecheck.databinding.DialogAddRecordBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddRecordDialogFragment(
    private val onSave: (systolic: Int, diastolic: Int, pulse: Int?, notes: String, timestamp: Date) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogAddRecordBinding? = null
    private val binding get() = _binding!!

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = DialogAddRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar campos de fecha y hora con la hora actual
        updateDateTimeFields()

        binding.etDate.setOnClickListener {
            showDatePicker()
        }
        binding.etTime.setOnClickListener {
            showTimePicker()
        }

        binding.btnSave.setOnClickListener {
            val systolic = binding.etSystolic.text.toString().toIntOrNull()
            val diastolic = binding.etDiastolic.text.toString().toIntOrNull()
            val pulse = binding.etPulse.text.toString().toIntOrNull()
            val notes = binding.etNotes.text.toString()
            val timestamp = calendar.time

            if (systolic == null || diastolic == null) {
                Toast.makeText(requireContext(), "Por favor ingrese valores válidos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            onSave(systolic, diastolic, pulse, notes, timestamp)
            dismiss()
        }
    }

    private fun updateDateTimeFields() {
        binding.etDate.setText(dateFormat.format(calendar.time))
        binding.etTime.setText(timeFormat.format(calendar.time))
    }

    private fun showDatePicker() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(requireContext(), { _, y, m, d ->
            calendar.set(Calendar.YEAR, y)
            calendar.set(Calendar.MONTH, m)
            calendar.set(Calendar.DAY_OF_MONTH, d)
            updateDateTimeFields()
        }, year, month, day).show()
    }

    private fun showTimePicker() {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        TimePickerDialog(requireContext(), { _, h, m ->
            calendar.set(Calendar.HOUR_OF_DAY, h)
            calendar.set(Calendar.MINUTE, m)
            updateDateTimeFields()
        }, hour, minute, true).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(onSave: (systolic: Int, diastolic: Int, pulse: Int?, notes: String, timestamp: Date) -> Unit): AddRecordDialogFragment {
            return AddRecordDialogFragment(onSave)
        }
    }
} 
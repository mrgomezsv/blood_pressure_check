package com.mrgomez.bloodpressurecheck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mrgomez.bloodpressurecheck.databinding.DialogCommentDetailBinding
import androidx.fragment.app.DialogFragment
import java.text.SimpleDateFormat
import java.util.Locale

class CommentDetailDialogFragment : DialogFragment() {
    private var _binding: DialogCommentDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = DialogCommentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val systolic = arguments?.getInt("systolic") ?: 0
        val diastolic = arguments?.getInt("diastolic") ?: 0
        val pulse = arguments?.getInt("pulse") ?: 0
        val timestamp = arguments?.getLong("timestamp") ?: 0L
        val notes = arguments?.getString("notes") ?: "-- Sin Comentarios --"

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(java.util.Date(timestamp))

        binding.tvSystolic.text = systolic.toString()
        binding.tvDiastolic.text = diastolic.toString()
        binding.tvPulse.text = pulse.toString()
        binding.tvDate.text = dateStr
        binding.tvComment.text = notes
        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            systolic: Int,
            diastolic: Int,
            pulse: Int,
            timestamp: Long,
            notes: String
        ): CommentDetailDialogFragment {
            val fragment = CommentDetailDialogFragment()
            val args = Bundle()
            args.putInt("systolic", systolic)
            args.putInt("diastolic", diastolic)
            args.putInt("pulse", pulse)
            args.putLong("timestamp", timestamp)
            args.putString("notes", notes)
            fragment.arguments = args
            return fragment
        }
    }
} 
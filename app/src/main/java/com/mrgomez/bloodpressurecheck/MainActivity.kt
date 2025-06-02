package com.mrgomez.bloodpressurecheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.mrgomez.bloodpressurecheck.databinding.ActivityMainBinding
import com.mrgomez.bloodpressurecheck.model.BloodPressureRecord
import com.mrgomez.bloodpressurecheck.model.BloodPressureCategory
import java.util.Date
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import android.graphics.Typeface

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: BloodPressureAdapter
    private lateinit var lineChart: LineChart
    private var records: List<BloodPressureRecord> = emptyList()

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        lineChart = binding.lineChart
        setupLineChart()

        // Verificar si el usuario está autenticado
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "Usuario no autenticado")
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        Log.d(TAG, "Usuario autenticado: ${currentUser.uid}")
        setupRecyclerView()
        setupClickListeners()
        loadRecords()
    }

    private fun setupRecyclerView() {
        adapter = BloodPressureAdapter { record ->
            val dialog = CommentDetailDialogFragment.newInstance(
                record.systolic,
                record.diastolic,
                record.pulse,
                record.timestamp.time,
                record.notes
            )
            dialog.show(supportFragmentManager, "CommentDetailDialog")
        }
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupLineChart() {
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.axisRight.isEnabled = false
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.legend.isEnabled = true
        lineChart.setNoDataText("Por favor registra datos para iniciar tu seguimiento!")
        lineChart.setNoDataTextColor(0xFF1976D2.toInt())
        lineChart.setNoDataTextTypeface(Typeface.DEFAULT_BOLD)
    }

    private fun updateLineChart() {
        if (records.isEmpty()) {
            lineChart.clear()
            return
        }
        val entriesSystolic = records.mapIndexed { index, record -> Entry(index.toFloat(), record.systolic.toFloat()) }
        val entriesDiastolic = records.mapIndexed { index, record -> Entry(index.toFloat(), record.diastolic.toFloat()) }
        val systolicSet = LineDataSet(entriesSystolic, "Sistólica").apply { color = 0xFFE53935.toInt(); setCircleColor(0xFFE53935.toInt()) }
        val diastolicSet = LineDataSet(entriesDiastolic, "Diastólica").apply { color = 0xFF1E88E5.toInt(); setCircleColor(0xFF1E88E5.toInt()) }
        val data = LineData(systolicSet, diastolicSet)
        lineChart.data = data
        lineChart.invalidate()
    }

    private fun setupClickListeners() {
        binding.fabAdd.setOnClickListener {
            val dialog = AddRecordDialogFragment.newInstance { systolic, diastolic, pulse, notes, timestamp ->
                saveRecord(systolic, diastolic, pulse, notes, timestamp)
            }
            dialog.show(supportFragmentManager, "AddRecordDialog")
        }
    }

    private fun saveRecord(systolic: Int, diastolic: Int, pulse: Int?, notes: String, timestamp: Date) {
        val userId = auth.currentUser?.uid ?: return
        val record = BloodPressureRecord(
            userId = userId,
            systolic = systolic,
            diastolic = diastolic,
            pulse = pulse ?: 0,
            notes = notes,
            timestamp = timestamp
        )

        db.collection("registro_medico_usuarios")
            .document(userId)
            .collection("registros")
            .add(record)
            .addOnSuccessListener { documentRef ->
                Log.d(TAG, "Registro guardado con ID: ${documentRef.id}")
                Toast.makeText(this, "Registro guardado exitosamente", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar registro", e)
                Toast.makeText(this, "Error al guardar registro: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadRecords() {
        val userId = auth.currentUser?.uid ?: return
        Log.d(TAG, "Cargando registros para usuario: $userId")
        db.collection("registro_medico_usuarios")
            .document(userId)
            .collection("registros")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error al cargar registros", e)
                    Toast.makeText(this, "Error al cargar registros: ${e.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    Log.d(TAG, "No hay registros disponibles")
                    return@addSnapshotListener
                }

                val newRecords = snapshot.documents.mapNotNull { doc ->
                    val systolic = doc.getLong("systolic")?.toInt() ?: return@mapNotNull null
                    val diastolic = doc.getLong("diastolic")?.toInt() ?: return@mapNotNull null
                    val pulse = doc.getLong("pulse")?.toInt() ?: 0
                    val notes = doc.getString("notes") ?: "-- Sin Comentarios --"
                    val timestamp = (doc.get("timestamp") as? Timestamp)?.toDate() ?: Date()
                    BloodPressureRecord(
                        id = doc.id,
                        userId = userId,
                        systolic = systolic,
                        diastolic = diastolic,
                        pulse = pulse,
                        notes = notes,
                        timestamp = timestamp
                    )
                }
                records = newRecords
                adapter.submitList(newRecords)
                updateLineChart()
                updateStatusSummary()
            }
    }

    private fun updateStatusSummary() {
        if (records.isEmpty()) {
            binding.tvStatusSummary.text = "No hay registros disponibles."
            return
        }
        val latest = records.first()
        val category = BloodPressureCategory.getCategory(latest.systolic, latest.diastolic)
        val summary = "Último registro (${latest.systolic}/${latest.diastolic} mmHg):\n" +
                     "Categoría: " + category.description
        binding.tvStatusSummary.text = summary
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sign_out -> {
                signOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun signOut() {
        auth.signOut()
        GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
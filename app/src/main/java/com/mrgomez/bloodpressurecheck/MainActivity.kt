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
import java.util.Date
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

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
        adapter = BloodPressureAdapter()
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
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Error: userId es null")
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_LONG).show()
            return
        }
        Log.d(TAG, "Intentando guardar registro para usuario: $userId")
        Log.d(TAG, "Datos a guardar - Sistólica: $systolic, Diastólica: $diastolic, Pulso: $pulse, Fecha: $timestamp")
        val record = hashMapOf(
            "systolic" to systolic,
            "diastolic" to diastolic,
            "pulse" to (pulse ?: 0),
            "notes" to notes,
            "timestamp" to com.google.firebase.Timestamp(timestamp)
        )
        db.collection("registro_medico_usuarios")
            .document(userId)
            .collection("registros")
            .add(record)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Documento guardado con ID: ${documentReference.id}")
                Toast.makeText(this, "Registro guardado exitosamente", Toast.LENGTH_SHORT).show()
                loadRecords()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar documento", e)
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
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
                records = snapshot.documents.mapNotNull { doc ->
                    try {
                        val id = doc.id
                        val systolic = doc.getLong("systolic")?.toInt() ?: 0
                        val diastolic = doc.getLong("diastolic")?.toInt() ?: 0
                        val pulse = doc.getLong("pulse")?.toInt() ?: 0
                        val notes = doc.getString("notes") ?: ""
                        val timestamp = doc.getTimestamp("timestamp")?.toDate()
                        if (timestamp == null) {
                            Log.e(TAG, "Documento $id omitido: campo 'timestamp' nulo o inválido")
                            return@mapNotNull null
                        }
                        BloodPressureRecord(
                            id = id,
                            userId = userId,
                            systolic = systolic,
                            diastolic = diastolic,
                            pulse = pulse,
                            notes = notes,
                            timestamp = timestamp
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al convertir documento ${doc.id}: ${e.message}", e)
                        null
                    }
                }
                Log.d(TAG, "Registros cargados: ${records.size}")
                adapter.submitList(records)
                updateLineChart()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
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
        // Cerrar sesión de Firebase
        auth.signOut()
        
        // Cerrar sesión de Google
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        
        googleSignInClient.signOut().addOnCompleteListener {
            Log.d(TAG, "Sesión de Google cerrada")
            // Redirigir a la pantalla de login
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }
}
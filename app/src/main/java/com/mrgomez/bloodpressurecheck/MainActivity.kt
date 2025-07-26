package com.mrgomez.bloodpressurecheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.PopupMenu
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
import java.util.Calendar
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
        Log.d(TAG, "Email del usuario: ${currentUser.email}")
        
        setupRecyclerView()
        setupClickListeners()
        loadRecords()
        
        // Mostrar saludo personalizado
        showPersonalizedGreeting(currentUser)
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

        // Configurar el botón de menú
        binding.btnMenu.setOnClickListener {
            showPopupMenu()
        }
    }

    private fun showPopupMenu() {
        val popup = PopupMenu(this, binding.btnMenu)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            handleMenuItemClick(item)
            true
        }
        
        popup.show()
    }

    private fun handleMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                // Ir al perfil del usuario
                startActivity(Intent(this, UserProfileActivity::class.java))
                true
            }
            R.id.action_settings -> {
                // Mostrar configuración (por ahora solo un mensaje)
                Toast.makeText(this, "Configuración próximamente", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_help -> {
                // Mostrar ayuda (por ahora solo un mensaje)
                Toast.makeText(this, "Ayuda próximamente", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sign_out -> {
                showSignOutConfirmation()
                true
            }
            else -> false
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

    private fun showPersonalizedGreeting(user: com.google.firebase.auth.FirebaseUser) {
        // Obtener el saludo según la hora del día
        val greeting = getGreetingByTime()
        
        // Obtener el nombre del usuario
        val userName = getUserDisplayName(user)
        
        // Actualizar los TextViews
        binding.tvGreeting.text = greeting
        binding.tvUserName.text = userName
        
        Log.d(TAG, "Saludo personalizado: $greeting $userName")
    }

    private fun getGreetingByTime(): String {
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when {
            hourOfDay < 12 -> getString(R.string.greeting_morning)
            hourOfDay < 18 -> getString(R.string.greeting_afternoon)
            else -> getString(R.string.greeting_evening)
        }
    }

    private fun getUserDisplayName(user: com.google.firebase.auth.FirebaseUser): String {
        // Intentar obtener el nombre del perfil de Firestore primero
        val userId = user.uid
        
        // Buscar en la colección 'users' (nueva estructura)
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name")
                    if (!name.isNullOrBlank()) {
                        binding.tvUserName.text = name
                        return@addOnSuccessListener
                    }
                }
                
                // Si no se encuentra en 'users', buscar en 'registro_medico_usuarios' (legacy)
                db.collection("registro_medico_usuarios").document(userId).get()
                    .addOnSuccessListener { legacyDocument ->
                        if (legacyDocument.exists()) {
                            val name = legacyDocument.getString("name")
                            if (!name.isNullOrBlank()) {
                                binding.tvUserName.text = name
                                return@addOnSuccessListener
                            }
                        }
                        
                        // Si no se encuentra en ninguna colección, usar displayName de Firebase Auth
                        val displayName = user.displayName
                        if (!displayName.isNullOrBlank()) {
                            binding.tvUserName.text = displayName
                        } else {
                            // Si no hay displayName, usar email o "Usuario"
                            val email = user.email
                            if (!email.isNullOrBlank()) {
                                // Extraer solo la parte antes del @ del email
                                val emailName = email.substringBefore("@")
                                binding.tvUserName.text = emailName.replaceFirstChar { it.uppercase() }
                            } else {
                                binding.tvUserName.text = "Usuario"
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error al obtener nombre del usuario legacy", e)
                        // Fallback a displayName o email
                        val displayName = user.displayName
                        if (!displayName.isNullOrBlank()) {
                            binding.tvUserName.text = displayName
                        } else {
                            val email = user.email
                            if (!email.isNullOrBlank()) {
                                val emailName = email.substringBefore("@")
                                binding.tvUserName.text = emailName.replaceFirstChar { it.uppercase() }
                            } else {
                                binding.tvUserName.text = "Usuario"
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener nombre del usuario", e)
                // Fallback a displayName o email
                val displayName = user.displayName
                if (!displayName.isNullOrBlank()) {
                    binding.tvUserName.text = displayName
                } else {
                    val email = user.email
                    if (!email.isNullOrBlank()) {
                        val emailName = email.substringBefore("@")
                        binding.tvUserName.text = emailName.replaceFirstChar { it.uppercase() }
                    } else {
                        binding.tvUserName.text = "Usuario"
                    }
                }
            }
        
        // Retornar un valor temporal mientras se carga
        return "Usuario"
    }

    private fun showSignOutConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.sign_out_confirmation_title))
            .setMessage(getString(R.string.sign_out_confirmation_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                signOut()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun signOut() {
        auth.signOut()
        GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
        
        // Mostrar mensaje de confirmación
        Toast.makeText(this, getString(R.string.sign_out_success), Toast.LENGTH_SHORT).show()
        
        // Ir a la pantalla de login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
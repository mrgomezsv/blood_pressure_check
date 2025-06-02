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
import com.mrgomez.bloodpressurecheck.databinding.ActivityMainBinding
import com.mrgomez.bloodpressurecheck.model.BloodPressureRecord
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: BloodPressureAdapter

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

    private fun setupClickListeners() {
        binding.btnSave.setOnClickListener {
            saveRecord()
        }

        binding.fabAdd.setOnClickListener {
            clearInputs()
        }
    }

    private fun saveRecord() {
        val systolic = binding.etSystolic.text.toString().toIntOrNull()
        val diastolic = binding.etDiastolic.text.toString().toIntOrNull()
        val pulse = binding.etPulse.text.toString().toIntOrNull()
        val notes = binding.etNotes.text.toString()

        if (systolic == null || diastolic == null) {
            Toast.makeText(this, "Por favor ingrese valores válidos", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Error: userId es null")
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_LONG).show()
            return
        }

        Log.d(TAG, "Intentando guardar registro para usuario: $userId")
        Log.d(TAG, "Datos a guardar - Sistólica: $systolic, Diastólica: $diastolic, Pulso: $pulse")

        val record = hashMapOf(
            "userId" to userId,
            "systolic" to systolic,
            "diastolic" to diastolic,
            "pulse" to (pulse ?: 0),
            "notes" to notes,
            "timestamp" to Timestamp.now()
        )

        db.collection("registro_medico_usuarios")
            .add(record)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Documento guardado con ID: ${documentReference.id}")
                Toast.makeText(this, "Registro guardado exitosamente", Toast.LENGTH_SHORT).show()
                clearInputs()
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
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
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

                val records = snapshot.documents.mapNotNull { doc ->
                    try {
                        BloodPressureRecord(
                            id = doc.id,
                            userId = doc.getString("userId") ?: "",
                            systolic = doc.getLong("systolic")?.toInt() ?: 0,
                            diastolic = doc.getLong("diastolic")?.toInt() ?: 0,
                            pulse = doc.getLong("pulse")?.toInt() ?: 0,
                            notes = doc.getString("notes") ?: "",
                            timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al convertir documento ${doc.id}", e)
                        null
                    }
                }

                Log.d(TAG, "Registros cargados: ${records.size}")
                adapter.submitList(records)
            }
    }

    private fun clearInputs() {
        binding.etSystolic.text?.clear()
        binding.etDiastolic.text?.clear()
        binding.etPulse.text?.clear()
        binding.etNotes.text?.clear()
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
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
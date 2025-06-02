package com.mrgomez.bloodpressurecheck

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mrgomez.bloodpressurecheck.databinding.ActivityUserProfileBinding
import com.mrgomez.bloodpressurecheck.model.UserProfile

class UserProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }
    }

    private fun saveUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        val gender = when (binding.rgGender.checkedRadioButtonId) {
            R.id.rbMale -> "HOMBRE"
            R.id.rbFemale -> "MUJER"
            else -> {
                Toast.makeText(this, "Por favor selecciona tu género", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val age = binding.etAge.text.toString().toIntOrNull()
        if (age == null || age <= 0 || age > 120) {
            Toast.makeText(this, "Por favor ingresa una edad válida", Toast.LENGTH_SHORT).show()
            return
        }

        val height = binding.etHeight.text.toString().toFloatOrNull()
        if (height == null || height <= 0 || height > 300) {
            Toast.makeText(this, "Por favor ingresa una estatura válida", Toast.LENGTH_SHORT).show()
            return
        }

        val weight = binding.etWeight.text.toString().toFloatOrNull()
        if (weight == null || weight <= 0 || weight > 500) {
            Toast.makeText(this, "Por favor ingresa un peso válido", Toast.LENGTH_SHORT).show()
            return
        }

        val weightUnit = when (binding.rgWeightUnit.checkedRadioButtonId) {
            R.id.rbKg -> "KG"
            R.id.rbLb -> "LB"
            else -> "KG"
        }

        // Convertir peso a kilogramos si está en libras
        val weightInKg = if (weightUnit == "LB") weight * 0.453592f else weight

        val userProfile = UserProfile(
            userId = userId,
            gender = gender,
            age = age,
            height = height,
            weight = weightInKg,
            weightUnit = weightUnit
        )

        db.collection("registro_medico_usuarios")
            .document(userId)
            .set(userProfile)
            .addOnSuccessListener {
                Toast.makeText(this, "Perfil guardado exitosamente", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar el perfil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
} 
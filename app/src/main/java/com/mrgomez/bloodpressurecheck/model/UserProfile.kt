package com.mrgomez.bloodpressurecheck.model

import com.google.firebase.Timestamp

data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val email: String? = null,
    val phoneNumber: String? = null,
    val googleId: String? = null,
    val authMethods: List<String> = listOf(), // ["email", "phone", "google"]
    val primaryEmail: String? = null, // Email principal para comunicación
    val createdAt: Timestamp = Timestamp.now(),
    val lastLogin: Timestamp = Timestamp.now(),
    val isProfileComplete: Boolean = false,
    val profileImageUrl: String? = null,
    val photoURL: String? = null,
    val dateOfBirth: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val height: Int? = null, // en cm
    val weight: Float? = null, // en kg
    val weightUnit: String? = null, // "KG" o "LB"
    val pin: String? = null, // PIN para acceso rápido
    val password: String? = null, // Contraseña encriptada (solo para email)
    val emergencyContact: EmergencyContact? = null,
    val medicalHistory: List<String> = listOf(),
    val medications: List<String> = listOf(),
    val allergies: List<String> = listOf()
)

data class EmergencyContact(
    val name: String = "",
    val relationship: String = "",
    val phoneNumber: String = "",
    val email: String? = null
)

enum class BloodPressureCategory(val systolicRange: ClosedRange<Int>, val diastolicRange: ClosedRange<Int>, val description: String) {
    NORMAL(0..119, 0..79, "Normal"),
    ELEVATED(120..129, 0..79, "Elevada"),
    HYPERTENSION_STAGE_1(130..139, 80..89, "Hipertensión grado 1"),
    HYPERTENSION_STAGE_2(140..Int.MAX_VALUE, 90..Int.MAX_VALUE, "Hipertensión grado 2"),
    HYPERTENSIVE_CRISIS(180..Int.MAX_VALUE, 120..Int.MAX_VALUE, "Crisis hipertensiva");

    companion object {
        fun getCategory(systolic: Int, diastolic: Int): BloodPressureCategory {
            return values().find { category ->
                systolic in category.systolicRange && diastolic in category.diastolicRange
            } ?: NORMAL
        }
    }
} 
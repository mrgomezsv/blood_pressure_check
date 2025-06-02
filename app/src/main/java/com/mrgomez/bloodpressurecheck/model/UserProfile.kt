package com.mrgomez.bloodpressurecheck.model

data class UserProfile(
    val userId: String = "",
    val gender: String = "", // "HOMBRE" o "MUJER"
    val age: Int = 0,
    val height: Float = 0f, // en centímetros
    val weight: Float = 0f, // en kilogramos
    val weightUnit: String = "KG", // "KG" o "LB"
    val createdAt: Long = System.currentTimeMillis()
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
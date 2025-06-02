package com.mrgomez.bloodpressurecheck.model

import java.util.Date

data class BloodPressureRecord(
    val id: String = "",
    val userId: String = "",
    val systolic: Int = 0,
    val diastolic: Int = 0,
    val pulse: Int = 0,
    val notes: String = "",
    val timestamp: Date = Date()
) 
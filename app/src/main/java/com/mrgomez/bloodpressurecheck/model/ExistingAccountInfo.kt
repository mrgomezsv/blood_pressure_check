package com.mrgomez.bloodpressurecheck.model

data class ExistingAccountInfo(
    val userId: String,
    val name: String,
    val email: String?,
    val phoneNumber: String?,
    val authMethods: List<String>,
    val hasProfile: Boolean
) {
    fun getDisplayName(): String {
        return name.ifEmpty { "Usuario" }
    }
    
    fun getAuthMethodsDisplay(): String {
        return when {
            authMethods.contains("email") && authMethods.contains("google") && authMethods.contains("phone") -> 
                "Email, Google y Teléfono"
            authMethods.contains("email") && authMethods.contains("google") -> 
                "Email y Google"
            authMethods.contains("email") && authMethods.contains("phone") -> 
                "Email y Teléfono"
            authMethods.contains("google") && authMethods.contains("phone") -> 
                "Google y Teléfono"
            authMethods.contains("email") -> "Email"
            authMethods.contains("google") -> "Google"
            authMethods.contains("phone") -> "Teléfono"
            else -> "Cuenta"
        }
    }
    
    fun getProfileStatus(): String {
        return if (hasProfile) "Perfil completo" else "Perfil incompleto"
    }
} 
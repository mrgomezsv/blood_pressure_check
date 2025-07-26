package com.mrgomez.bloodpressurecheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mrgomez.bloodpressurecheck.databinding.ActivityRegisterBinding
import com.mrgomez.bloodpressurecheck.service.AuthService
import com.mrgomez.bloodpressurecheck.model.UserProfile
import com.mrgomez.bloodpressurecheck.model.ExistingAccountInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var authService: AuthService

    companion object {
        private const val TAG = "RegisterActivity"
        private const val MIN_PASSWORD_LENGTH = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        authService = AuthService()

        setupClickListeners()
        setupAnimations()
    }

    private fun setupClickListeners() {
        binding.apply {
            btnRegister.setOnClickListener {
                val name = etName.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString()
                val confirmPassword = etConfirmPassword.text.toString()

                if (validateInputs(name, email, password, confirmPassword)) {
                    createUserWithEmailAndPassword(name, email, password)
                }
            }

            btnBackToLogin.setOnClickListener {
                finish()
            }
        }
    }

    private fun setupAnimations() {
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        binding.cardRegister.startAnimation(fadeIn)
    }

    private fun validateInputs(name: String, email: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        // Validar nombre
        if (name.isEmpty()) {
            binding.tilName.error = "El nombre es requerido"
            isValid = false
        } else if (name.length < 2) {
            binding.tilName.error = "El nombre debe tener al menos 2 caracteres"
            isValid = false
        } else {
            binding.tilName.error = null
        }

        // Validar email
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.email_required)
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.invalid_email_format)
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        // Validar contraseña
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.password_required)
            isValid = false
        } else if (password.length < MIN_PASSWORD_LENGTH) {
            binding.tilPassword.error = getString(R.string.password_min_length)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        // Validar confirmación de contraseña
        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = "Confirma tu contraseña"
            isValid = false
        } else if (password != confirmPassword) {
            binding.tilConfirmPassword.error = getString(R.string.passwords_dont_match)
            isValid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return isValid
    }

    private fun createUserWithEmailAndPassword(name: String, email: String, password: String) {
        showLoading(true)

        // Primero verificar si el email ya existe
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val exists = withContext(Dispatchers.IO) {
                    authService.userExists(email)
                }
                
                if (exists) {
                    // Email ya existe, mostrar diálogo de vinculación
                    val existingAccount = withContext(Dispatchers.IO) {
                        authService.findExistingAccountInfo(email)
                    }
                    
                    if (existingAccount != null) {
                        showLinkAccountDialog(existingAccount, name, email, password)
                    } else {
                        showSnackbar("Error al verificar cuenta existente")
                    }
                    showLoading(false)
                } else {
                    // Email no existe, crear nueva cuenta
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this@RegisterActivity) { task ->
                            if (task.isSuccessful) {
                                Log.d(TAG, "createUserWithEmailAndPassword:success")
                                handleSuccessfulRegistration(name, email, password)
                            } else {
                                Log.w(TAG, "createUserWithEmailAndPassword:failure", task.exception)
                                handleRegistrationError(task.exception)
                            }
                            showLoading(false)
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking existing account", e)
                showSnackbar("Error al verificar cuenta: ${e.message}")
                showLoading(false)
            }
        }
    }

    private fun handleRegistrationError(exception: Exception?) {
        val errorMessage = exception?.message ?: getString(R.string.error_unknown)
        when {
            errorMessage.contains("weak password") -> {
                showSnackbar("La contraseña es muy débil. Usa al menos 6 caracteres")
            }
            errorMessage.contains("badly formatted") -> {
                showSnackbar("El formato del email es inválido")
            }
            errorMessage.contains("already in use") -> {
                showSnackbar("Ya existe una cuenta con este email")
            }
            errorMessage.contains("network") -> {
                showSnackbar(getString(R.string.error_network))
            }
            else -> {
                showSnackbar(getString(R.string.error_unknown))
            }
        }
    }

    private fun handleSuccessfulRegistration(name: String, email: String, password: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    authService.handleAuthentication("email", email, name, password)
                }
                
                result.fold(
                    onSuccess = { userProfile ->
                        showSnackbar("Cuenta creada exitosamente")
                        navigateToNextScreen(userProfile)
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error handling registration", exception)
                        showSnackbar("Error al crear la cuenta: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in handleSuccessfulRegistration", e)
                showSnackbar("Error inesperado: ${e.message}")
            }
        }
    }

    private fun navigateToNextScreen(userProfile: UserProfile) {
        val intent = if (userProfile.isProfileComplete) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, UserProfileActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLinkAccountDialog(
        existingAccount: ExistingAccountInfo,
        newName: String,
        newEmail: String,
        newPassword: String
    ) {
        val message = """
            Ya existe una cuenta con el email: $newEmail
            
            Cuenta existente:
            • Nombre: ${existingAccount.getDisplayName()}
            • Métodos de acceso: ${existingAccount.getAuthMethodsDisplay()}
            • Estado: ${existingAccount.getProfileStatus()}
            
            ¿Quieres vincular esta nueva cuenta con la existente? 
            Esto te permitirá acceder con ambos métodos de autenticación.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Cuenta Existente Encontrada")
            .setMessage(message)
            .setPositiveButton("Sí, Vincular") { _, _ ->
                linkNewAccountToExisting(existingAccount, newName, newEmail, newPassword)
            }
            .setNegativeButton("No, Crear Nueva Cuenta") { _, _ ->
                showSnackbar("Por favor usa un email diferente")
            }
            .setCancelable(false)
            .show()
    }

    private fun linkNewAccountToExisting(
        existingAccount: ExistingAccountInfo,
        newName: String,
        newEmail: String,
        newPassword: String
    ) {
        showLoading(true)
        
        // Primero autenticar con la cuenta existente (si es posible)
        // Luego vincular la nueva cuenta
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Intentar autenticar con la cuenta existente
                val result = withContext(Dispatchers.IO) {
                    // Aquí podríamos implementar un flujo de autenticación
                    // Por ahora, simplemente vinculamos la nueva cuenta
                    authService.linkNewAccountToExisting(
                        existingAccount.userId,
                        "email",
                        newEmail,
                        newName,
                        newPassword
                    )
                }
                
                result.fold(
                    onSuccess = { userProfile ->
                        showSnackbar("Cuenta vinculada exitosamente")
                        navigateToNextScreen(userProfile)
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error linking account", exception)
                        showSnackbar("Error al vincular cuenta: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in linkNewAccountToExisting", e)
                showSnackbar("Error inesperado: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !show
        binding.btnBackToLogin.isEnabled = !show
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
} 
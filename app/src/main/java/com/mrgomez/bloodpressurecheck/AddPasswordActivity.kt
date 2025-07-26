package com.mrgomez.bloodpressurecheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.mrgomez.bloodpressurecheck.databinding.ActivityAddPasswordBinding

class AddPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddPasswordBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var userEmail: String = ""
    private var pendingPassword: String = ""

    companion object {
        private const val TAG = "AddPasswordActivity"
        const val EXTRA_EMAIL = "extra_email"
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleGoogleSignInResult(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        userEmail = intent.getStringExtra(EXTRA_EMAIL) ?: ""

        if (userEmail.isEmpty()) {
            Toast.makeText(this, "Error: Email no proporcionado", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Configurar Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.tvEmail.text = "Email: $userEmail"
        binding.tvMessage.text = "Tu cuenta existe pero no tiene contraseña. Por favor, inicia sesión con Google y luego crea una contraseña para poder iniciar sesión con email y contraseña."

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnAddPassword.setOnClickListener {
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (validateInputs(password, confirmPassword)) {
                pendingPassword = password
                // Primero autenticar con Google
                signInWithGoogle()
            }
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun validateInputs(password: String, confirmPassword: String): Boolean {
        if (password.isEmpty()) {
            binding.tilPassword.error = "La contraseña es requerida"
            return false
        }

        if (password.length < 6) {
            binding.tilPassword.error = "La contraseña debe tener al menos 6 caracteres"
            return false
        }

        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = "Confirma tu contraseña"
            return false
        }

        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Las contraseñas no coinciden"
            return false
        }

        // Limpiar errores
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null

        return true
    }

    private fun signInWithGoogle() {
        showLoading(true)
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun handleGoogleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            
            // Verificar que el email coincida
            if (account.email != userEmail) {
                showLoading(false)
                Toast.makeText(this, "Por favor, inicia sesión con la cuenta: $userEmail", Toast.LENGTH_LONG).show()
                return
            }
            
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            showLoading(false)
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
            Toast.makeText(this, "Error en el inicio de sesión con Google: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    // Ahora agregar la contraseña
                    addPasswordToAccount(pendingPassword)
                } else {
                    showLoading(false)
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Error en la autenticación: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun addPasswordToAccount(password: String) {
        auth.currentUser?.let { user ->
            user.updatePassword(password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Contraseña agregada exitosamente")
                        Toast.makeText(this, "Contraseña agregada exitosamente", Toast.LENGTH_LONG).show()
                        
                        // Ir a MainActivity
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        Log.e(TAG, "Error al agregar contraseña", task.exception)
                        showLoading(false)
                        
                        val errorMessage = when {
                            task.exception?.message?.contains("requires-recent-login") == true -> {
                                "Por seguridad, necesitas iniciar sesión nuevamente con Google antes de agregar una contraseña"
                            }
                            else -> "Error al agregar contraseña: ${task.exception?.message}"
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        } ?: run {
            showLoading(false)
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_LONG).show()
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnAddPassword.isEnabled = !show
        binding.btnCancel.isEnabled = !show
    }
} 
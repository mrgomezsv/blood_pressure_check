package com.mrgomez.bloodpressurecheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.mrgomez.bloodpressurecheck.databinding.ActivityLoginBinding
import com.mrgomez.bloodpressurecheck.service.AuthService
import com.mrgomez.bloodpressurecheck.model.UserProfile
import com.mrgomez.bloodpressurecheck.model.ExistingAccountInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var db: FirebaseFirestore
    private lateinit var authService: AuthService
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    companion object {
        private const val TAG = "LoginActivity"
        private const val MIN_PASSWORD_LENGTH = 6
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleSignInResult(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        authService = AuthService()

        setupGoogleSignIn()
        setupClickListeners()
        setupAnimations()

        // Verificar si el usuario ya está autenticado
        if (auth.currentUser != null) {
            Log.d(TAG, "Usuario ya autenticado: ${auth.currentUser?.email}")
            Log.d(TAG, "UID del usuario: ${auth.currentUser?.uid}")
            
            // Mostrar mensaje de que se está verificando la sesión
            showSnackbar("Verificando sesión...")
            
            // Verificar el perfil del usuario
            checkUserProfile()
        } else {
            Log.d(TAG, "No hay usuario autenticado")
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupClickListeners() {
        binding.apply {
            btnEmailSignIn.setOnClickListener {
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString()

                if (validateInputs(email, password)) {
                    signInWithEmailAndPassword(email, password)
                }
            }

            btnGoogleSignIn.setOnClickListener {
                signInWithGoogle()
            }

            btnPhoneSignIn.setOnClickListener {
                showPhoneAuthDialog()
            }

            tvForgotPassword.setOnClickListener {
                showForgotPasswordDialog()
            }

            tvSignUp.setOnClickListener {
                val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun setupAnimations() {
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        binding.cardLogin.startAnimation(fadeIn)
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

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

        return isValid
    }

    private fun signInWithEmailAndPassword(email: String, password: String) {
        showLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmailAndPassword:success")
                    handleSuccessfulAuth("email", email)
                } else {
                    Log.w(TAG, "signInWithEmailAndPassword:failure", task.exception)
                    handleSignInError(task.exception, email)
                }
                showLoading(false)
            }
    }

    private fun handleSignInError(exception: Exception?, email: String) {
        val errorMessage = exception?.message ?: getString(R.string.error_unknown)
        when {
            errorMessage.contains("password is invalid") -> {
                showSnackbar(getString(R.string.error_wrong_password))
            }
            errorMessage.contains("no user record") -> {
                checkIfUserExistsWithoutPassword(email)
            }
            errorMessage.contains("network") -> {
                showSnackbar(getString(R.string.error_network))
            }
            else -> {
                showSnackbar(getString(R.string.error_unknown))
            }
        }
    }

    private fun checkIfUserExistsWithoutPassword(email: String) {
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val signInMethods = task.result.signInMethods
                    when {
                        signInMethods?.isEmpty() == true -> {
                            showSnackbar(getString(R.string.error_user_not_found))
                        }
                        signInMethods?.contains("google.com") == true -> {
                            showSnackbar("Este email está registrado con Google. Usa el botón de Google para iniciar sesión.")
                        }
                        signInMethods?.contains("phone") == true -> {
                            showSnackbar("Este email está registrado con teléfono. Usa el botón de teléfono para iniciar sesión.")
                        }
                        else -> {
                            showSnackbar(getString(R.string.error_wrong_password))
                        }
                    }
                } else {
                    showSnackbar(getString(R.string.error_unknown))
                }
            }
    }

    private fun signInWithGoogle() {
        showLoading(true)
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
            showLoading(false)
            showSnackbar(getString(R.string.error_google_sign_in))
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    handleSuccessfulAuth("google", user?.email, user?.displayName, photoURL = user?.photoUrl?.toString())
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    showSnackbar(getString(R.string.error_google_sign_in))
                }
                showLoading(false)
            }
    }

    private fun showPhoneAuthDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_phone_auth, null)
        val phoneInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPhoneNumber)
        val codeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVerificationCode)
        val sendCodeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSendCode)
        val verifyButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVerifyCode)
        val resendButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnResendCode)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.phone_verification_title))
            .setView(dialogView)
            .setCancelable(false)
            .create()

        sendCodeButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                sendVerificationCode(phoneNumber, dialogView)
                sendCodeButton.isEnabled = false
                resendButton.visibility = View.VISIBLE
            }
        }

        verifyButton.setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.isNotEmpty()) {
                verifyPhoneCode(code)
                dialog.dismiss()
            }
        }

        resendButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString().trim()
            if (phoneNumber.isNotEmpty()) {
                resendVerificationCode(phoneNumber)
            }
        }

        dialog.show()
    }

    private fun sendVerificationCode(phoneNumber: String, dialogView: View) {
        // Primero verificar si el teléfono ya existe
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val exists = withContext(Dispatchers.IO) {
                    authService.userExists(phoneNumber)
                }
                
                if (exists) {
                    // Teléfono ya existe, mostrar diálogo de vinculación
                    val existingAccount = withContext(Dispatchers.IO) {
                        authService.findExistingAccountInfo(phoneNumber)
                    }
                    
                    if (existingAccount != null) {
                        showLinkPhoneAccountDialog(existingAccount, phoneNumber)
                    } else {
                        showSnackbar("Error al verificar cuenta existente")
                    }
                } else {
                    // Teléfono no existe, proceder con verificación normal
                    proceedWithPhoneVerification(phoneNumber)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking existing phone account", e)
                showSnackbar("Error al verificar cuenta: ${e.message}")
            }
        }
    }

    private fun proceedWithPhoneVerification(phoneNumber: String) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "onVerificationCompleted:$credential")
                signInWithPhoneCredential(credential)
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                Log.w(TAG, "onVerificationFailed", e)
                showSnackbar("Error al enviar código: ${e.message}")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "onCodeSent:$verificationId")
                this@LoginActivity.verificationId = verificationId
                resendToken = token
                showSnackbar(getString(R.string.code_sent))
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun resendVerificationCode(phoneNumber: String) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithPhoneCredential(credential)
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                showSnackbar("Error al reenviar código: ${e.message}")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                this@LoginActivity.verificationId = verificationId
                resendToken = token
                showSnackbar("Código reenviado")
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .setForceResendingToken(resendToken!!)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyPhoneCode(code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        signInWithPhoneCredential(credential)
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        showLoading(true)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    handleSuccessfulAuth("phone", user?.phoneNumber, user?.displayName)
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    showSnackbar(getString(R.string.error_phone_sign_in))
                }
                showLoading(false)
            }
    }

    private fun showForgotPasswordDialog() {
        val email = binding.etEmail.text.toString().trim()
        val emailInput = if (email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            email
        } else {
            ""
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val emailEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail)
        emailEditText.setText(emailInput)

        AlertDialog.Builder(this)
            .setTitle("Recuperar Contraseña")
            .setView(dialogView)
            .setPositiveButton("Enviar") { _, _ ->
                val emailToReset = emailEditText.text.toString().trim()
                if (emailToReset.isNotEmpty()) {
                    resetPassword(emailToReset)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showSnackbar("Email de recuperación enviado a $email")
                } else {
                    showSnackbar("Error al enviar email de recuperación")
                }
            }
    }

    private fun handleSuccessfulAuth(authMethod: String, identifier: String?, name: String? = null, photoURL: String? = null) {
        Log.d(TAG, "handleSuccessfulAuth iniciado - Método: $authMethod, Identificador: $identifier")
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "Iniciando handleAuthentication en AuthService")
                val result = withContext(Dispatchers.IO) {
                    authService.handleAuthentication(authMethod, identifier, name, photoURL = photoURL)
                }
                
                result.fold(
                    onSuccess = { userProfile ->
                        showSnackbar(getString(R.string.success_login))
                        navigateToNextScreen(userProfile)
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error handling authentication", exception)
                        showSnackbar("Error en la autenticación: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in handleSuccessfulAuth", e)
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
        startActivity(intent)
        finish()
    }

    private fun checkUserProfile() {
        val currentUser = auth.currentUser ?: return
        
        Log.d(TAG, "=== INICIO VERIFICACIÓN DE PERFIL ===")
        Log.d(TAG, "Usuario UID: ${currentUser.uid}")
        Log.d(TAG, "Usuario Email: ${currentUser.email}")
        
        // SOLUCIÓN SIMPLE: Si el usuario está autenticado, ir directamente a MainActivity
        Log.d(TAG, "Usuario autenticado, yendo directamente a MainActivity")
        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
        finish()
        
        /* CÓDIGO ORIGINAL (comentado para debugging):
        // Primero buscar en la colección 'users' (nueva estructura)
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userProfile = document.toObject(UserProfile::class.java)
                    if (userProfile != null) {
                        Log.d(TAG, "Perfil encontrado en 'users': ${userProfile.name}")
                        Log.d(TAG, "Perfil completo: ${userProfile.isProfileComplete}")
                        
                        // Actualizar último login
                        updateLastLogin(currentUser.uid)
                        
                        // Navegar a la pantalla correspondiente
                        navigateToNextScreen(userProfile)
                    } else {
                        Log.e(TAG, "Error al convertir documento a UserProfile")
                        checkLegacyProfile(currentUser.uid)
                    }
                } else {
                    Log.d(TAG, "Perfil no encontrado en 'users', verificando colección legacy")
                    checkLegacyProfile(currentUser.uid)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al verificar perfil en 'users'", e)
                checkLegacyProfile(currentUser.uid)
            }
        */
    }
    
    private fun checkLegacyProfile(userId: String) {
        // Buscar en la colección legacy 'registro_medico_usuarios'
        db.collection("registro_medico_usuarios").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Log.d(TAG, "Perfil encontrado en 'registro_medico_usuarios'")
                    Log.d(TAG, "Documento completo: ${document.data}")
                    
                    // Verificar si tiene datos básicos del perfil
                    val hasName = document.contains("name")
                    val hasAge = document.contains("age")
                    val hasGender = document.contains("gender")
                    val hasHeight = document.contains("height")
                    val hasWeight = document.contains("weight")
                    
                    Log.d(TAG, "Verificación de campos:")
                    Log.d(TAG, "- name: $hasName (${document.getString("name")})")
                    Log.d(TAG, "- age: $hasAge (${document.getLong("age")})")
                    Log.d(TAG, "- gender: $hasGender (${document.getString("gender")})")
                    Log.d(TAG, "- height: $hasHeight (${document.getLong("height")})")
                    Log.d(TAG, "- weight: $hasWeight (${document.getDouble("weight")})")
                    
                    val hasBasicProfile = hasName || hasAge || hasGender || hasHeight || hasWeight
                    
                    Log.d(TAG, "¿Tiene perfil básico? $hasBasicProfile")
                    
                    // TEMPORAL: Si existe cualquier documento del usuario, ir a MainActivity
                    Log.d(TAG, "Perfil legacy encontrado, ir a MainActivity (modo permisivo)")
                    updateLegacyLastLogin(userId)
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                    
                    /* CÓDIGO ORIGINAL (comentado temporalmente):
                    if (hasBasicProfile) {
                        Log.d(TAG, "Perfil legacy tiene datos básicos, ir a MainActivity")
                        // Actualizar último login en la colección legacy
                        updateLegacyLastLogin(userId)
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        Log.d(TAG, "Perfil legacy sin datos básicos, ir a UserProfileActivity")
                        showSnackbar("Completa tu perfil para continuar")
                        startActivity(Intent(this@LoginActivity, UserProfileActivity::class.java))
                        finish()
                    }
                    */
                } else {
                    Log.d(TAG, "No se encontró perfil en ninguna colección")
                    showSnackbar("Completa tu perfil para continuar")
                    startActivity(Intent(this@LoginActivity, UserProfileActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al verificar perfil legacy", e)
                showSnackbar("Error al verificar perfil: ${e.message}")
                // En caso de error, ir a MainActivity
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnEmailSignIn.isEnabled = !show
        binding.btnGoogleSignIn.isEnabled = !show
        binding.btnPhoneSignIn.isEnabled = !show
    }

    private fun showLinkPhoneAccountDialog(existingAccount: ExistingAccountInfo, phoneNumber: String) {
        val message = """
            Ya existe una cuenta con el teléfono: $phoneNumber
            
            Cuenta existente:
            • Nombre: ${existingAccount.getDisplayName()}
            • Métodos de acceso: ${existingAccount.getAuthMethodsDisplay()}
            • Estado: ${existingAccount.getProfileStatus()}
            
            ¿Quieres vincular este teléfono con la cuenta existente? 
            Esto te permitirá acceder con ambos métodos de autenticación.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Cuenta Existente Encontrada")
            .setMessage(message)
            .setPositiveButton("Sí, Vincular") { _, _ ->
                linkPhoneToExistingAccount(existingAccount, phoneNumber)
            }
            .setNegativeButton("No, Usar Otro Teléfono") { _, _ ->
                showSnackbar("Por favor usa un teléfono diferente")
            }
            .setCancelable(false)
            .show()
    }

    private fun linkPhoneToExistingAccount(existingAccount: ExistingAccountInfo, phoneNumber: String) {
        showLoading(true)
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    authService.linkNewAccountToExisting(
                        existingAccount.userId,
                        "phone",
                        phoneNumber,
                        null
                    )
                }
                
                result.fold(
                    onSuccess = { userProfile ->
                        showSnackbar("Teléfono vinculado exitosamente")
                        navigateToNextScreen(userProfile)
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error linking phone account", exception)
                        showSnackbar("Error al vincular teléfono: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in linkPhoneToExistingAccount", e)
                showSnackbar("Error inesperado: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateLastLogin(userId: String) {
        val updates = hashMapOf<String, Any>(
            "lastLogin" to com.google.firebase.Timestamp.now()
        )
        
        db.collection("users").document(userId)
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Último login actualizado en 'users'")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar último login en 'users'", e)
            }
    }
    
    private fun updateLegacyLastLogin(userId: String) {
        val updates = hashMapOf<String, Any>(
            "lastLogin" to com.google.firebase.Timestamp.now()
        )
        
        db.collection("registro_medico_usuarios").document(userId)
            .update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Último login actualizado en 'registro_medico_usuarios'")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar último login en 'registro_medico_usuarios'", e)
            }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
} 
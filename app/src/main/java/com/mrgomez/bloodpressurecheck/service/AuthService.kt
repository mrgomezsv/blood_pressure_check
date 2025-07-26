package com.mrgomez.bloodpressurecheck.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.mrgomez.bloodpressurecheck.model.UserProfile
import com.mrgomez.bloodpressurecheck.model.ExistingAccountInfo
import kotlinx.coroutines.tasks.await

class AuthService {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    
    companion object {
        private const val TAG = "AuthService"
        private const val USERS_COLLECTION = "users"
    }

    /**
     * Vincula una cuenta existente con un nuevo método de autenticación
     */
    suspend fun linkAccount(
        userId: String,
        authMethod: String,
        identifier: String? = null
    ): Result<UserProfile> {
        return try {
            val userRef = db.collection(USERS_COLLECTION).document(userId)
            val userDoc = userRef.get().await()
            
            if (userDoc.exists()) {
                val currentProfile = userDoc.toObject(UserProfile::class.java)
                val updatedProfile = currentProfile?.copy(
                    authMethods = (currentProfile.authMethods + authMethod).distinct(),
                    lastLogin = Timestamp.now()
                )
                
                // Actualizar el campo específico según el método de autenticación
                val finalProfile = when (authMethod) {
                    "email" -> updatedProfile?.copy(email = identifier)
                    "phone" -> updatedProfile?.copy(phoneNumber = identifier)
                    "google" -> updatedProfile?.copy(googleId = identifier)
                    else -> updatedProfile
                }
                
                userRef.set(finalProfile!!).await()
                Log.d(TAG, "Account linked successfully: $authMethod")
                Result.success(finalProfile)
            } else {
                Result.failure(Exception("User profile not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error linking account", e)
            Result.failure(e)
        }
    }

    /**
     * Busca un usuario por cualquier identificador (email, teléfono, Google ID)
     */
    suspend fun findUserByIdentifier(identifier: String): UserProfile? {
        return try {
            // Buscar en la colección 'users' (nueva estructura)
            val query = db.collection(USERS_COLLECTION)
                .whereEqualTo("email", identifier)
                .get()
                .await()
            
            if (!query.isEmpty) {
                return query.documents[0].toObject(UserProfile::class.java)
            }
            
            // Buscar por teléfono en nueva estructura
            val phoneQuery = db.collection(USERS_COLLECTION)
                .whereEqualTo("phoneNumber", identifier)
                .get()
                .await()
            
            if (!phoneQuery.isEmpty) {
                return phoneQuery.documents[0].toObject(UserProfile::class.java)
            }
            
            // Buscar por Google ID en nueva estructura
            val googleQuery = db.collection(USERS_COLLECTION)
                .whereEqualTo("googleId", identifier)
                .get()
                .await()
            
            if (!googleQuery.isEmpty) {
                return googleQuery.documents[0].toObject(UserProfile::class.java)
            }
            
            // Si no se encuentra en la nueva estructura, buscar en la legacy
            val legacyQuery = db.collection("registro_medico_usuarios")
                .whereEqualTo("email", identifier)
                .get()
                .await()
            
            if (!legacyQuery.isEmpty) {
                val legacyDoc = legacyQuery.documents[0]
                // Convertir documento legacy a UserProfile
                return UserProfile(
                    userId = legacyDoc.id,
                    name = legacyDoc.getString("name") ?: "",
                    email = legacyDoc.getString("email"),
                    createdAt = legacyDoc.getTimestamp("createdAt") ?: Timestamp.now(),
                    lastLogin = legacyDoc.getTimestamp("lastLogin") ?: Timestamp.now(),
                    isProfileComplete = true // Asumir que si existe en legacy, está completo
                )
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding user by identifier", e)
            null
        }
    }

    /**
     * Crea un nuevo perfil de usuario
     */
    suspend fun createUserProfile(
        userId: String,
        name: String,
        authMethod: String,
        identifier: String? = null,
        password: String? = null,
        pin: String? = null,
        photoURL: String? = null
    ): Result<UserProfile> {
        return try {
            val userProfile = UserProfile(
                userId = userId,
                name = name,
                authMethods = listOf(authMethod),
                createdAt = Timestamp.now(),
                lastLogin = Timestamp.now(),
                photoURL = photoURL
            ).let { profile ->
                when (authMethod) {
                    "email" -> profile.copy(
                        email = identifier, 
                        primaryEmail = identifier,
                        password = password // Guardar contraseña encriptada
                    )
                    "phone" -> profile.copy(
                        phoneNumber = identifier,
                        pin = pin // Guardar PIN para teléfono
                    )
                    "google" -> profile.copy(
                        googleId = identifier, 
                        email = identifier, 
                        primaryEmail = identifier,
                        photoURL = photoURL
                    )
                    else -> profile
                }
            }
            
            db.collection(USERS_COLLECTION).document(userId).set(userProfile).await()
            Log.d(TAG, "User profile created successfully")
            Result.success(userProfile)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user profile", e)
            Result.failure(e)
        }
    }

    /**
     * Actualiza el último login del usuario
     */
    suspend fun updateLastLogin(userId: String): Result<Unit> {
        return try {
            db.collection(USERS_COLLECTION).document(userId)
                .update("lastLogin", Timestamp.now())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last login", e)
            Result.failure(e)
        }
    }

    /**
     * Verifica si un usuario existe por cualquier método de autenticación
     */
    suspend fun userExists(identifier: String): Boolean {
        return findUserByIdentifier(identifier) != null
    }

    /**
     * Busca información de cuenta existente para mostrar al usuario
     */
    suspend fun findExistingAccountInfo(identifier: String): ExistingAccountInfo? {
        return try {
            val userProfile = findUserByIdentifier(identifier)
            userProfile?.let {
                ExistingAccountInfo(
                    userId = it.userId,
                    name = it.name,
                    email = it.email,
                    phoneNumber = it.phoneNumber,
                    authMethods = it.authMethods,
                    hasProfile = it.isProfileComplete
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding existing account info", e)
            null
        }
    }

    /**
     * Vincula una nueva cuenta con una existente
     */
    suspend fun linkNewAccountToExisting(
        existingUserId: String,
        newAuthMethod: String,
        newIdentifier: String?,
        newName: String? = null,
        newPassword: String? = null,
        newPin: String? = null,
        newPhotoURL: String? = null
    ): Result<UserProfile> {
        return try {
            val existingProfile = getCurrentUserProfile() ?: findUserByIdentifier(existingUserId)
            
            if (existingProfile != null) {
                val updatedProfile = existingProfile.copy(
                    authMethods = (existingProfile.authMethods + newAuthMethod).distinct(),
                    lastLogin = Timestamp.now()
                ).let { profile ->
                    when (newAuthMethod) {
                        "email" -> profile.copy(
                            email = newIdentifier,
                            primaryEmail = newIdentifier ?: profile.primaryEmail,
                            password = newPassword // Guardar contraseña encriptada
                        )
                        "phone" -> profile.copy(
                            phoneNumber = newIdentifier,
                            pin = newPin // Guardar PIN para teléfono
                        )
                        "google" -> profile.copy(
                            googleId = newIdentifier,
                            email = newIdentifier ?: profile.email,
                            primaryEmail = newIdentifier ?: profile.primaryEmail,
                            photoURL = newPhotoURL ?: profile.photoURL
                        )
                        else -> profile
                    }
                }
                
                db.collection(USERS_COLLECTION).document(existingUserId).set(updatedProfile).await()
                Log.d(TAG, "Account linked successfully: $newAuthMethod to $existingUserId")
                Result.success(updatedProfile)
            } else {
                Result.failure(Exception("Existing account not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error linking new account to existing", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene el perfil completo del usuario actual
     */
    suspend fun getCurrentUserProfile(): UserProfile? {
        val currentUser = auth.currentUser ?: return null
        Log.d(TAG, "getCurrentUserProfile - Usuario actual: ${currentUser.uid}")
        
        return try {
            Log.d(TAG, "Intentando obtener documento de Firestore: ${currentUser.uid}")
            val doc = db.collection(USERS_COLLECTION).document(currentUser.uid).get().await()
            Log.d(TAG, "Documento existe: ${doc.exists()}")
            
            if (doc.exists()) {
                val userProfile = doc.toObject(UserProfile::class.java)
                Log.d(TAG, "Perfil obtenido: ${userProfile?.name}")
                userProfile
            } else {
                Log.d(TAG, "Documento no existe en Firestore")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user profile", e)
            Log.e(TAG, "Error details: ${e.message}")
            null
        }
    }

    /**
     * Actualiza el perfil del usuario
     */
    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            db.collection(USERS_COLLECTION).document(userId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user profile", e)
            Result.failure(e)
        }
    }

    /**
     * Maneja el proceso de autenticación y vinculación de cuentas
     */
    suspend fun handleAuthentication(
        authMethod: String,
        identifier: String?,
        name: String? = null,
        password: String? = null,
        pin: String? = null,
        photoURL: String? = null
    ): Result<UserProfile> {
        Log.d(TAG, "AuthService.handleAuthentication iniciado - Método: $authMethod, Identificador: $identifier")
        return try {
            val currentUser = auth.currentUser
            Log.d(TAG, "Usuario actual: ${currentUser?.uid}, Email: ${currentUser?.email}")
            
            if (currentUser != null) {
                // Usuario ya autenticado, verificar si necesita vinculación
                val existingProfile = getCurrentUserProfile()
                
                if (existingProfile != null) {
                    // Verificar si el método de autenticación ya está vinculado
                    if (!existingProfile.authMethods.contains(authMethod)) {
                        // Vincular nuevo método de autenticación
                        linkAccount(currentUser.uid, authMethod, identifier)
                    } else {
                        // Método ya vinculado, solo actualizar último login
                        updateLastLogin(currentUser.uid)
                        Result.success(existingProfile)
                    }
                } else {
                    // Crear perfil para usuario autenticado
                    createUserProfile(
                        currentUser.uid, 
                        name ?: currentUser.displayName ?: "Usuario", 
                        authMethod, 
                        identifier,
                        password,
                        pin,
                        photoURL
                    )
                }
            } else {
                // Usuario no autenticado, buscar por identificador
                val existingUser = identifier?.let { findUserByIdentifier(it) }
                
                if (existingUser != null) {
                    // Usuario existe, verificar si el método está vinculado
                    if (!existingUser.authMethods.contains(authMethod)) {
                        // Vincular nuevo método
                        linkAccount(existingUser.userId, authMethod, identifier)
                    } else {
                        // Método ya vinculado
                        updateLastLogin(existingUser.userId)
                        Result.success(existingUser)
                    }
                } else {
                    // Usuario no existe, crear nuevo perfil
                    val newUserId = auth.currentUser?.uid ?: generateUserId()
                    createUserProfile(
                        newUserId, 
                        name ?: "Usuario", 
                        authMethod, 
                        identifier,
                        password,
                        pin,
                        photoURL
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling authentication", e)
            Result.failure(e)
        }
    }

    private fun generateUserId(): String {
        return "user_${System.currentTimeMillis()}_${(0..999).random()}"
    }
} 
package com.example.features.auth.data.repository

import android.util.Log
import com.example.features.auth.domain.repository.FirebaseAuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : FirebaseAuthRepository {

    override val currentUserEmail: Flow<String?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            try {
                trySend(auth.currentUser?.email)
            } catch (e: Exception) {
                trySend(null)
            }
        }
        try {
            firebaseAuth.addAuthStateListener(authStateListener)
        } catch (e: Exception) {
            trySend(null)
        }
        awaitClose {
            try {
                firebaseAuth.removeAuthStateListener(authStateListener)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        val sanitizedEmail = email.trim().lowercase(java.util.Locale.getDefault())
        if (sanitizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Email address cannot be empty."))
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(sanitizedEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty."))
        }

        return try {
            firebaseAuth.signInWithEmailAndPassword(sanitizedEmail, password).await()
            Result.success(Unit)
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            Log.w("FirebaseAuth", "User not found: ${e.message}")
            Result.failure(Exception("No account found with this email. Please switch to Create Account."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.w("FirebaseAuth", "Invalid credentials entered: ${e.message}")
            Result.failure(Exception("Incorrect email or password. If you signed up with Google, please use 'Continue with Google'. If you forgot your password, tap 'Forgot password?'."))
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            Log.w("FirebaseAuth", "Network exception during signIn: ${e.message}")
            Result.failure(Exception("Network error. Please check your internet connection and try again."))
        } catch (e: FirebaseAuthException) {
            Log.w("FirebaseAuth", "FirebaseAuthException [Code: ${e.errorCode}]: ${e.message}")
            val msg = when (e.errorCode) {
                "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Incorrect email or password. If you signed up with Google, use 'Continue with Google'."
                "ERROR_USER_NOT_FOUND" -> "No account found with this email. Please switch to Create Account."
                "ERROR_USER_DISABLED" -> "This account has been disabled. Please contact support."
                "ERROR_TOO_MANY_REQUESTS" -> "Too many failed attempts. Please try again in a few minutes."
                else -> e.message ?: "Authentication failed. Please try again."
            }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Log.w("FirebaseAuth", "signInWithEmail unexpected error: ${e.message}")
            Result.failure(Exception(e.message ?: "Authentication failed. Please try again."))
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<Unit> {
        val sanitizedEmail = email.trim().lowercase(java.util.Locale.getDefault())
        if (sanitizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Email address cannot be empty."))
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(sanitizedEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters long."))
        }

        return try {
            firebaseAuth.createUserWithEmailAndPassword(sanitizedEmail, password).await()
            Result.success(Unit)
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            Log.w("FirebaseAuth", "User collision: ${e.message}")
            Result.failure(Exception("An account with this email already exists. Please switch to Sign In or use Google Sign-In."))
        } catch (e: com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
            Log.w("FirebaseAuth", "Weak password: ${e.message}")
            Result.failure(Exception("Password is too weak. Please use at least 6 characters."))
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            Log.w("FirebaseAuth", "Network exception during signUp: ${e.message}")
            Result.failure(Exception("Network error. Please check your internet connection and try again."))
        } catch (e: FirebaseAuthException) {
            Log.w("FirebaseAuth", "FirebaseAuthException [Code: ${e.errorCode}]: ${e.message}")
            val msg = when (e.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Please switch to Sign In."
                "ERROR_WEAK_PASSWORD" -> "Password is too weak. Please use at least 6 characters."
                "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                "ERROR_TOO_MANY_REQUESTS" -> "Too many failed attempts. Please try again in a few minutes."
                else -> e.message ?: "Account registration failed. Please try again."
            }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Log.w("FirebaseAuth", "signUpWithEmail failed: ${e.message}")
            Result.failure(Exception(e.message ?: "Account registration failed. Please try again."))
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val sanitizedEmail = email.trim().lowercase(java.util.Locale.getDefault())
        if (sanitizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Email address cannot be empty."))
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(sanitizedEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        return try {
            firebaseAuth.sendPasswordResetEmail(sanitizedEmail).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseAuth", "sendPasswordResetEmail exception: ${e.message}")
            Result.failure(Exception(e.message ?: "Failed to send password reset email."))
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        return try {
            Log.d("GoogleSignIn", "Obtaining GoogleAuthCredential with ID token length: ${idToken.length}")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
            Log.d("GoogleSignIn", "Firebase Auth signInWithCredential SUCCESS! User UID: ${user?.uid}, Email: ${user?.email}")
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e("GoogleSignIn", "FirebaseAuthInvalidCredentialsException: ${e.message}", e)
            Result.failure(e)
        } catch (e: FirebaseAuthException) {
            Log.e("GoogleSignIn", "FirebaseAuthException [Code: ${e.errorCode}]: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("GoogleSignIn", "Unexpected FirebaseAuth error [${e.javaClass.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        try {
            firebaseAuth.signOut()
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "SignOut exception: ${e.message}")
        }
    }

    override fun isUserLoggedIn(): Boolean {
        return try {
            firebaseAuth.currentUser != null
        } catch (e: Exception) {
            false
        }
    }

    override fun getCurrentUserEmail(): String? {
        return try {
            firebaseAuth.currentUser?.email
        } catch (e: Exception) {
            null
        }
    }
}

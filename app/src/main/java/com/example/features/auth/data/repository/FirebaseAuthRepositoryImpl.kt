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
        val sanitizedEmail = email.trim()
        val sanitizedPassword = password.trim()
        if (sanitizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Email address cannot be empty."))
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(sanitizedEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address (e.g. user@example.com)."))
        }
        if (sanitizedPassword.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty."))
        }
        return try {
            firebaseAuth.signInWithEmailAndPassword(sanitizedEmail, sanitizedPassword).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e("FirebaseAuth", "Invalid credentials: ${e.message}")
            Result.failure(Exception("Invalid email or password. Please verify and try again."))
        } catch (e: FirebaseAuthException) {
            Log.e("FirebaseAuth", "FirebaseAuthException [Code: ${e.errorCode}]: ${e.message}")
            val msg = when (e.errorCode) {
                "ERROR_INVALID_EMAIL" -> "The email address is badly formatted."
                "ERROR_WRONG_PASSWORD" -> "Incorrect password. Please try again."
                "ERROR_USER_NOT_FOUND" -> "No account found with this email. Please sign up first."
                "ERROR_USER_DISABLED" -> "This user account has been disabled."
                "ERROR_TOO_MANY_REQUESTS" -> "Too many failed attempts. Please try again later."
                else -> e.message ?: "Authentication failed."
            }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "signInWithEmail failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<Unit> {
        val sanitizedEmail = email.trim()
        val sanitizedPassword = password.trim()
        if (sanitizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("Email address cannot be empty."))
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(sanitizedEmail).matches()) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address (e.g. user@example.com)."))
        }
        if (sanitizedPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters long."))
        }
        return try {
            firebaseAuth.createUserWithEmailAndPassword(sanitizedEmail, sanitizedPassword).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthException) {
            Log.e("FirebaseAuth", "FirebaseAuthException [Code: ${e.errorCode}]: ${e.message}")
            val msg = when (e.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Please sign in instead."
                "ERROR_WEAK_PASSWORD" -> "Password is too weak. Please use at least 6 characters."
                "ERROR_INVALID_EMAIL" -> "The email address is badly formatted."
                else -> e.message ?: "Account registration failed."
            }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "signUpWithEmail failed: ${e.message}")
            Result.failure(e)
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

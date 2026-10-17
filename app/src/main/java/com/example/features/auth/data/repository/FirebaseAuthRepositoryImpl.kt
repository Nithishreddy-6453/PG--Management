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
        } catch (e: Exception) {
            Log.w("FirebaseAuth", "signInWithEmail exception [${e.javaClass.simpleName}]: ${e.message}")
            Result.failure(Exception(mapFirebaseSignInException(e)))
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
        } catch (e: Exception) {
            Log.w("FirebaseAuth", "signUpWithEmail exception [${e.javaClass.simpleName}]: ${e.message}")
            Result.failure(Exception(mapFirebaseSignUpException(e)))
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

    companion object {
        fun mapFirebaseSignInException(e: Exception): String {
            val fullMessage = ((e.message ?: "") + " " + (e.localizedMessage ?: "") + " " + (e.cause?.message ?: ""))
            if (fullMessage.contains("supplied auth credential", ignoreCase = true) ||
                fullMessage.contains("credential is incorrect", ignoreCase = true) ||
                fullMessage.contains("malformed or has expired", ignoreCase = true) ||
                fullMessage.contains("RecaptchaAction", ignoreCase = true) ||
                fullMessage.contains("Recaptcha", ignoreCase = true) ||
                fullMessage.contains("signInWithPassword", ignoreCase = true) ||
                fullMessage.contains("auth credential", ignoreCase = true) ||
                fullMessage.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                fullMessage.contains("INVALID_CREDENTIAL", ignoreCase = true) ||
                fullMessage.contains("wrong-password", ignoreCase = true)
            ) {
                return "Couldn't sign in with email and password. If you originally registered with Google, select 'Continue with Google'. Otherwise, check your email and password."
            }

            return when (e) {
                is com.google.firebase.auth.FirebaseAuthInvalidUserException -> {
                    "No account found with this email. Please switch to Create Account."
                }
                is FirebaseAuthInvalidCredentialsException -> {
                    "Couldn't sign in with email and password. If you originally registered with Google, select 'Continue with Google'. Otherwise, check your email and password."
                }
                is com.google.firebase.FirebaseNetworkException -> {
                    "Network error. Please check your internet connection and try again."
                }
                is FirebaseAuthException -> {
                    when (e.errorCode) {
                        "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Couldn't sign in with email and password. If you originally registered with Google, select 'Continue with Google'. Otherwise, check your email and password."
                        "ERROR_USER_NOT_FOUND" -> "No account found with this email. Please switch to Create Account."
                        "ERROR_USER_DISABLED" -> "This account has been disabled. Please contact support."
                        "ERROR_TOO_MANY_REQUESTS" -> "Too many failed attempts. Please try again in a few minutes."
                        else -> e.message ?: "Authentication failed. Please try again."
                    }
                }
                else -> e.message ?: "Authentication failed. Please try again."
            }
        }

        fun mapFirebaseSignUpException(e: Exception): String {
            return when (e) {
                is com.google.firebase.auth.FirebaseAuthUserCollisionException -> {
                    "An account with this email already exists. Please switch to Sign In or use Google Sign-In."
                }
                is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> {
                    "Password is too weak. Please use at least 6 characters."
                }
                is com.google.firebase.FirebaseNetworkException -> {
                    "Network error. Please check your internet connection and try again."
                }
                is FirebaseAuthException -> {
                    when (e.errorCode) {
                        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Please switch to Sign In."
                        "ERROR_WEAK_PASSWORD" -> "Password is too weak. Please use at least 6 characters."
                        "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                        "ERROR_TOO_MANY_REQUESTS" -> "Too many failed attempts. Please try again in a few minutes."
                        else -> e.message ?: "Account registration failed. Please try again."
                    }
                }
                else -> e.message ?: "Account registration failed. Please try again."
            }
        }
    }
}

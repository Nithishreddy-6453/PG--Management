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
        return try {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<Unit> {
        return try {
            firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
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

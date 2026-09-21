package com.example.features.auth.domain.repository

import kotlinx.coroutines.flow.Flow

interface FirebaseAuthRepository {
    val currentUserEmail: Flow<String?>
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>
    suspend fun signUpWithEmail(email: String, password: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<Unit>
    suspend fun signOut()
    fun isUserLoggedIn(): Boolean
    fun getCurrentUserEmail(): String?
}

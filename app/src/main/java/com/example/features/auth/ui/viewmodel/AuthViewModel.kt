package com.example.features.auth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.sync.SignInResult
import com.example.data.sync.SyncCoordinator
import com.example.features.auth.domain.repository.FirebaseAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val signInResult: SignInResult = SignInResult.AlreadyBootstrapped) : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: FirebaseAuthRepository,
    private val syncCoordinator: SyncCoordinator? = null
) : ViewModel() {
    constructor(authRepository: FirebaseAuthRepository) : this(authRepository, null)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun signInWithEmail(email: String, password: String) {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        if (trimmedEmail.isBlank()) {
            _authState.value = AuthState.Error("Please enter your email address.")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _authState.value = AuthState.Error("Please enter a valid email address (e.g. name@example.com).")
            return
        }
        if (trimmedPassword.isBlank()) {
            _authState.value = AuthState.Error("Please enter your password.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithEmail(trimmedEmail, trimmedPassword)
            if (result.isSuccess) {
                val uid = getCurrentUser()?.uid ?: "default_owner"
                val signInResult = syncCoordinator?.handleUserSignIn(uid) ?: SignInResult.NewAccount
                _authState.value = AuthState.Success(signInResult)
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        if (trimmedEmail.isBlank()) {
            _authState.value = AuthState.Error("Please enter your email address.")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _authState.value = AuthState.Error("Please enter a valid email address (e.g. name@example.com).")
            return
        }
        if (trimmedPassword.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters long.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signUpWithEmail(trimmedEmail, trimmedPassword)
            if (result.isSuccess) {
                val uid = getCurrentUser()?.uid ?: "default_owner"
                val signInResult = syncCoordinator?.handleUserSignIn(uid) ?: SignInResult.NewAccount
                _authState.value = AuthState.Success(signInResult)
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Sign up failed")
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            android.util.Log.d("GoogleSignIn", "AuthViewModel initiating signInWithGoogle...")
            val result = authRepository.signInWithGoogle(idToken)
            if (result.isSuccess) {
                android.util.Log.d("GoogleSignIn", "AuthViewModel signInWithGoogle SUCCESS!")
                val uid = getCurrentUser()?.uid ?: "default_owner"
                val signInResult = syncCoordinator?.handleUserSignIn(uid) ?: SignInResult.NewAccount
                _authState.value = AuthState.Success(signInResult)
            } else {
                val ex = result.exceptionOrNull()
                android.util.Log.e("GoogleSignIn", "AuthViewModel signInWithGoogle FAILED: ${ex?.message}", ex)
                _authState.value = AuthState.Error(ex?.message ?: "Google Sign In failed")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun signOut() {
        viewModelScope.launch {
            syncCoordinator?.handleUserSignOut()
            authRepository.signOut()
        }
    }
    
    fun getCurrentUser() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
}


package com.example.features.googleform.data.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.features.googleform.domain.model.GoogleAccountInfo
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.googleAuthDataStore: DataStore<Preferences> by preferencesDataStore(name = "google_forms_auth_prefs")

@Singleton
class GoogleFormsAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val FORMS_BODY_SCOPE = "https://www.googleapis.com/auth/forms.body"
        const val FORMS_RESPONSES_READONLY_SCOPE = "https://www.googleapis.com/auth/forms.responses.readonly"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        val KEY_IS_CONNECTED = booleanPreferencesKey("is_google_connected")
        val KEY_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val KEY_ACCOUNT_NAME = stringPreferencesKey("google_account_name")
        val KEY_LAST_CONNECTED = longPreferencesKey("google_last_connected_at")
        val KEY_CACHED_TOKEN = stringPreferencesKey("google_cached_auth_token")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _accountInfoFlow = MutableStateFlow(GoogleAccountInfo())
    val accountInfoFlow: StateFlow<GoogleAccountInfo> = _accountInfoFlow.asStateFlow()

    private val gso: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(
            Scope(FORMS_BODY_SCOPE),
            Scope(FORMS_RESPONSES_READONLY_SCOPE),
            Scope(DRIVE_FILE_SCOPE)
        )
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    init {
        scope.launch {
            refreshAccountState()
        }
    }

    fun hasFormsBodyScope(): Boolean {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        return lastAccount != null && GoogleSignIn.hasPermissions(lastAccount, Scope(FORMS_BODY_SCOPE))
    }

    fun hasResponsesScope(): Boolean {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        return lastAccount != null && GoogleSignIn.hasPermissions(lastAccount, Scope(FORMS_RESPONSES_READONLY_SCOPE))
    }

    fun hasDriveScope(): Boolean {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        return lastAccount != null && GoogleSignIn.hasPermissions(lastAccount, Scope(DRIVE_FILE_SCOPE))
    }

    fun hasAllRequiredScopes(): Boolean {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        return lastAccount != null && GoogleSignIn.hasPermissions(
            lastAccount,
            Scope(FORMS_BODY_SCOPE),
            Scope(FORMS_RESPONSES_READONLY_SCOPE)
        )
    }

    fun getSignInIntent(): Intent {
        return signInClient.signInIntent
    }

    fun getConsentIntent(): Intent {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        val email = lastAccount?.email ?: _accountInfoFlow.value.email
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(FORMS_BODY_SCOPE),
                Scope(FORMS_RESPONSES_READONLY_SCOPE),
                Scope(DRIVE_FILE_SCOPE)
            )
        if (email.isNotBlank()) {
            builder.setAccountName(email)
        }
        return GoogleSignIn.getClient(context, builder.build()).signInIntent
    }

    fun getDriveConsentIntent(): Intent {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        val email = lastAccount?.email ?: _accountInfoFlow.value.email
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DRIVE_FILE_SCOPE)
            )
        if (email.isNotBlank()) {
            builder.setAccountName(email)
        }
        return GoogleSignIn.getClient(context, builder.build()).signInIntent
    }

    suspend fun refreshAccountState(): GoogleAccountInfo = withContext(Dispatchers.IO) {
        try {
            val prefs = context.googleAuthDataStore.data.first()
            val isConnected = prefs[KEY_IS_CONNECTED] ?: false
            val email = prefs[KEY_ACCOUNT_EMAIL] ?: ""
            val name = prefs[KEY_ACCOUNT_NAME] ?: ""
            val lastConnected = prefs[KEY_LAST_CONNECTED] ?: 0L

            val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
            val hasBody = lastAccount != null && GoogleSignIn.hasPermissions(lastAccount, Scope(FORMS_BODY_SCOPE))
            val hasResponses = lastAccount != null && GoogleSignIn.hasPermissions(lastAccount, Scope(FORMS_RESPONSES_READONLY_SCOPE))
            val hasDrive = lastAccount != null && GoogleSignIn.hasPermissions(lastAccount, Scope(DRIVE_FILE_SCOPE))

            if ((isConnected && email.isNotBlank()) || (lastAccount != null && (hasBody || hasResponses || hasDrive))) {
                val resolvedEmail = if (email.isNotBlank()) email else lastAccount?.email ?: ""
                val resolvedName = if (name.isNotBlank()) name else lastAccount?.displayName ?: ""
                val info = GoogleAccountInfo(
                    isConnected = true,
                    email = resolvedEmail,
                    displayName = resolvedName,
                    hasFormsBodyScope = hasBody,
                    hasResponsesReadonlyScope = hasResponses,
                    hasDriveFileScope = hasDrive,
                    lastConnectedAt = if (lastConnected > 0) lastConnected else System.currentTimeMillis(),
                    authError = null
                )
                _accountInfoFlow.value = info
                return@withContext info
            }

            val info = GoogleAccountInfo(isConnected = false)
            _accountInfoFlow.value = info
            info
        } catch (e: Exception) {
            val info = GoogleAccountInfo(isConnected = false, authError = e.message)
            _accountInfoFlow.value = info
            info
        }
    }

    suspend fun handleSignInResult(data: Intent?): PgResult<GoogleAccountInfo> = withContext(Dispatchers.IO) {
        try {
            if (data == null) {
                // User cancelled or no intent returned - keep existing account intact
                val current = refreshAccountState()
                return@withContext PgResult.Success(current)
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            if (account != null) {
                val email = account.email ?: "connected_account@gmail.com"
                val name = account.displayName ?: email.substringBefore("@")
                val now = System.currentTimeMillis()

                val hasBody = GoogleSignIn.hasPermissions(account, Scope(FORMS_BODY_SCOPE))
                val hasResponses = GoogleSignIn.hasPermissions(account, Scope(FORMS_RESPONSES_READONLY_SCOPE))
                val hasDrive = GoogleSignIn.hasPermissions(account, Scope(DRIVE_FILE_SCOPE))

                context.googleAuthDataStore.edit { prefs ->
                    prefs[KEY_IS_CONNECTED] = true
                    prefs[KEY_ACCOUNT_EMAIL] = email
                    prefs[KEY_ACCOUNT_NAME] = name
                    prefs[KEY_LAST_CONNECTED] = now
                }

                val info = GoogleAccountInfo(
                    isConnected = true,
                    email = email,
                    displayName = name,
                    hasFormsBodyScope = hasBody,
                    hasResponsesReadonlyScope = hasResponses,
                    hasDriveFileScope = hasDrive,
                    lastConnectedAt = now,
                    authError = null
                )
                _accountInfoFlow.value = info
                PgResult.Success(info)
            } else {
                val current = refreshAccountState()
                PgResult.Success(current)
            }
        } catch (e: ApiException) {
            if (e.statusCode == 12501) {
                // Google Sign-In / consent was cancelled by the user - leave existing connection intact
                val current = refreshAccountState()
                return@withContext PgResult.Success(current)
            }
            val errorMsg = when (e.statusCode) {
                12500 -> "Sign in failed: Configuration mismatch or missing SHA-1"
                12502 -> "Google Sign-In currently in progress"
                7 -> "Network error occurred during Google Sign-In"
                else -> "Google Sign-In error (Status code: ${e.statusCode})"
            }
            PgResult.Failure(PgError.SecurityError(errorMsg))
        } catch (e: Exception) {
            PgResult.Failure(PgError.SecurityError(e.localizedMessage ?: "Unknown Google authentication error"))
        }
    }

    /**
     * Obtains an OAuth Bearer token for Google Forms API.
     * @param requireResponsesScope whether forms.responses.readonly scope is required (true for response retrieval)
     */
    suspend fun getAuthorizationHeader(requireResponsesScope: Boolean = true): PgResult<String> = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            val androidAccount = account?.account ?: run {
                val email = _accountInfoFlow.value.email
                if (email.isNotBlank()) Account(email, "com.google") else null
            }

            // Check if required scope is already granted on the account
            if (account != null && requireResponsesScope) {
                val hasResponses = GoogleSignIn.hasPermissions(account, Scope(FORMS_RESPONSES_READONLY_SCOPE))
                if (!hasResponses) {
                    return@withContext PgResult.Failure(
                        PgError.ConsentRequiredError(
                            message = "Additional Google permission required to read form responses.",
                            consentIntent = getConsentIntent()
                        )
                    )
                }
            }

            if (androidAccount != null) {
                val tokenScope = if (requireResponsesScope) {
                    "oauth2:$FORMS_BODY_SCOPE $FORMS_RESPONSES_READONLY_SCOPE"
                } else {
                    "oauth2:$FORMS_BODY_SCOPE"
                }

                try {
                    val token = GoogleAuthUtil.getToken(context, androidAccount, tokenScope)
                    if (!token.isNullOrBlank()) {
                        return@withContext PgResult.Success("Bearer $token")
                    }
                } catch (e: UserRecoverableAuthException) {
                    return@withContext PgResult.Failure(
                        PgError.ConsentRequiredError(
                            message = "Additional Google permission required to read form responses.",
                            consentIntent = e.intent ?: getConsentIntent()
                        )
                    )
                } catch (e: GoogleAuthException) {
                    if (e.message?.contains("NeedRemoteConsent", ignoreCase = true) == true) {
                        return@withContext PgResult.Failure(
                            PgError.ConsentRequiredError(
                                message = "Additional Google permission required to read form responses.",
                                consentIntent = getConsentIntent()
                            )
                        )
                    }
                    return@withContext PgResult.Failure(PgError.SecurityError("Failed to authorize with Google: ${e.localizedMessage}"))
                } catch (e: Exception) {
                    if (e.message?.contains("NeedRemoteConsent", ignoreCase = true) == true) {
                        return@withContext PgResult.Failure(
                            PgError.ConsentRequiredError(
                                message = "Additional Google permission required to read form responses.",
                                consentIntent = getConsentIntent()
                            )
                        )
                    }
                    return@withContext PgResult.Failure(PgError.SecurityError("Failed to authorize with Google: ${e.localizedMessage}"))
                }
            }

            // Fallback for development if token cached
            val cached = context.googleAuthDataStore.data.first()[KEY_CACHED_TOKEN]
            if (!cached.isNullOrBlank()) {
                return@withContext PgResult.Success("Bearer $cached")
            }

            PgResult.Failure(
                PgError.ConsentRequiredError(
                    message = "Google account not connected. Please connect Google account.",
                    consentIntent = getConsentIntent()
                )
            )
        } catch (e: Exception) {
            PgResult.Failure(PgError.SecurityError("Failed to authorize with Google: ${e.localizedMessage}"))
        }
    }

    /**
     * Obtains an OAuth Bearer token for Google Drive API.
     */
    suspend fun getDriveAuthorizationHeader(): PgResult<String> = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            val androidAccount = account?.account ?: run {
                val email = _accountInfoFlow.value.email
                if (email.isNotBlank()) Account(email, "com.google") else null
            }

            // Check if drive scope is already granted on the account
            if (account != null) {
                val hasDrive = GoogleSignIn.hasPermissions(account, Scope(DRIVE_FILE_SCOPE))
                if (!hasDrive) {
                    return@withContext PgResult.Failure(
                        PgError.ConsentRequiredError(
                            message = "Additional Google Drive permission required to upload and view tenant photos.",
                            consentIntent = getDriveConsentIntent()
                        )
                    )
                }
            }

            if (androidAccount != null) {
                val tokenScope = "oauth2:$DRIVE_FILE_SCOPE"
                try {
                    val token = GoogleAuthUtil.getToken(context, androidAccount, tokenScope)
                    if (!token.isNullOrBlank()) {
                        return@withContext PgResult.Success("Bearer $token")
                    }
                } catch (e: UserRecoverableAuthException) {
                    return@withContext PgResult.Failure(
                        PgError.ConsentRequiredError(
                            message = "Additional Google Drive permission required to upload and view tenant photos.",
                            consentIntent = e.intent ?: getDriveConsentIntent()
                        )
                    )
                } catch (e: GoogleAuthException) {
                    if (e.message?.contains("NeedRemoteConsent", ignoreCase = true) == true) {
                        return@withContext PgResult.Failure(
                            PgError.ConsentRequiredError(
                                message = "Additional Google Drive permission required to upload and view tenant photos.",
                                consentIntent = getDriveConsentIntent()
                            )
                        )
                    }
                    return@withContext PgResult.Failure(PgError.SecurityError("Failed to authorize Google Drive: ${e.localizedMessage}"))
                } catch (e: Exception) {
                    if (e.message?.contains("NeedRemoteConsent", ignoreCase = true) == true) {
                        return@withContext PgResult.Failure(
                            PgError.ConsentRequiredError(
                                message = "Additional Google Drive permission required to upload and view tenant photos.",
                                consentIntent = getDriveConsentIntent()
                            )
                        )
                    }
                    return@withContext PgResult.Failure(PgError.SecurityError("Failed to authorize Google Drive: ${e.localizedMessage}"))
                }
            }

            val cached = context.googleAuthDataStore.data.first()[KEY_CACHED_TOKEN]
            if (!cached.isNullOrBlank()) {
                return@withContext PgResult.Success("Bearer $cached")
            }

            PgResult.Failure(
                PgError.ConsentRequiredError(
                    message = "Google account not connected. Please connect Google account.",
                    consentIntent = getDriveConsentIntent()
                )
            )
        } catch (e: Exception) {
            PgResult.Failure(PgError.SecurityError("Failed to authorize Google Drive: ${e.localizedMessage}"))
        }
    }

    suspend fun disconnect(): PgResult<Unit> = withContext(Dispatchers.IO) {
        try {
            signInClient.signOut()
            context.googleAuthDataStore.edit { prefs ->
                prefs.clear()
            }
            _accountInfoFlow.value = GoogleAccountInfo(isConnected = false)
            PgResult.Success(Unit)
        } catch (e: Exception) {
            _accountInfoFlow.value = GoogleAccountInfo(isConnected = false)
            PgResult.Success(Unit)
        }
    }
}

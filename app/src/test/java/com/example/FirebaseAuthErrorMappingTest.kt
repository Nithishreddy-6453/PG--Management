package com.example

import com.example.features.auth.data.repository.FirebaseAuthRepositoryImpl
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseAuthErrorMappingTest {

    @Test
    fun testGenericInvalidCredentials_producesNeutralMessageAndGuidesGoogleUsers() {
        val ex = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "The supplied credentials are malformed or incorrect.")
        val mapped = FirebaseAuthRepositoryImpl.mapFirebaseSignInException(ex)

        // 1. Must never state that the account does not exist
        assertFalse(
            "Invalid credentials must not claim that the account does not exist",
            mapped.contains("No account found", ignoreCase = true)
        )

        // 2. Must guide Google users to use 'Continue with Google'
        assertTrue(
            "Message must guide users who registered with Google to use Google Sign-In",
            mapped.contains("Continue with Google", ignoreCase = true)
        )

        // 3. Must prompt user to check email and password
        assertTrue(
            "Message must prompt to check email and password",
            mapped.contains("check your email and password", ignoreCase = true)
        )
    }

    @Test
    fun testWrongPasswordErrorCode_producesNeutralMessageAndGuidesGoogleUsers() {
        val ex = FirebaseAuthException("ERROR_WRONG_PASSWORD", "The password is invalid or the user does not have a password.")
        val mapped = FirebaseAuthRepositoryImpl.mapFirebaseSignInException(ex)

        assertFalse(mapped.contains("No account found", ignoreCase = true))
        assertTrue(mapped.contains("Continue with Google", ignoreCase = true))
    }

    @Test
    fun testExplicitUserNotFound_handledCorrectly() {
        val ex = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "There is no user record corresponding to this identifier.")
        val mapped = FirebaseAuthRepositoryImpl.mapFirebaseSignInException(ex)

        assertTrue(
            "Explicit user-not-found exception should inform user that no account was found",
            mapped.contains("No account found with this email", ignoreCase = true)
        )
    }

    @Test
    fun testUserCollisionOnSignUp_advisesSignInOrGoogle() {
        val ex = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "The email address is already in use by another account.")
        val mapped = FirebaseAuthRepositoryImpl.mapFirebaseSignUpException(ex)

        assertTrue(
            "Collision on sign-up should advise sign-in or Google Sign-In",
            mapped.contains("already exists", ignoreCase = true) && mapped.contains("Sign In", ignoreCase = true)
        )
    }

    @Test
    fun testNetworkException_notReportedAsInvalidCredentials() {
        val ex = FirebaseNetworkException("A network error (such as timeout, interrupted connection or unreachable host) has occurred.")
        val signInMapped = FirebaseAuthRepositoryImpl.mapFirebaseSignInException(ex)
        val signUpMapped = FirebaseAuthRepositoryImpl.mapFirebaseSignUpException(ex)

        assertFalse(signInMapped.contains("email or password", ignoreCase = true))
        assertFalse(signInMapped.contains("Google", ignoreCase = true))
        assertTrue(signInMapped.contains("Network error", ignoreCase = true))

        assertFalse(signUpMapped.contains("email or password", ignoreCase = true))
        assertTrue(signUpMapped.contains("Network error", ignoreCase = true))
    }

    @Test
    fun testWeakPasswordOnSignUp_reportsWeakPassword() {
        val ex = FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "Password should be at least 6 characters", "weak")
        val mapped = FirebaseAuthRepositoryImpl.mapFirebaseSignUpException(ex)

        assertTrue(
            "Weak password should inform about password requirements",
            mapped.contains("Password is too weak", ignoreCase = true)
        )
    }

    @Test
    fun testInvalidEmail_reportsValidEmailPrompt() {
        val ex = FirebaseAuthException("ERROR_INVALID_EMAIL", "The email address is badly formatted.")
        val signInMapped = FirebaseAuthRepositoryImpl.mapFirebaseSignInException(ex)
        val signUpMapped = FirebaseAuthRepositoryImpl.mapFirebaseSignUpException(ex)

        assertTrue(signInMapped.contains("valid email address", ignoreCase = true))
        assertTrue(signUpMapped.contains("valid email address", ignoreCase = true))
    }

    @Test
    fun testUnexpectedException_producesSafeGenericMessage() {
        val ex = RuntimeException("Internal unexpected backend exception details")
        val mapped = FirebaseAuthRepositoryImpl.mapFirebaseSignInException(ex)

        assertFalse(
            "Should not expose raw stack trace or internal details",
            mapped.contains("RuntimeException")
        )
    }

    @Test
    fun testRecaptchaActionException_producesUserFriendlyMessage() {
        val ex = RuntimeException("Initial task failed for action RecaptchaAction(action=signInWithPassword)with exception - The supplied auth credential is incorrect, malformed or has expired.")
        val mapped = FirebaseAuthRepositoryImpl.mapFirebaseSignInException(ex)

        assertFalse(
            "Should not contain raw Recaptcha technical error",
            mapped.contains("RecaptchaAction", ignoreCase = true)
        )
        assertTrue(
            "Should direct user to check email/password or use Google Sign-In",
            mapped.contains("check your email and password", ignoreCase = true)
        )
    }
}

package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Maps FirebaseFirestoreException codes into domain PgError types.
 */
internal fun mapFirestoreException(e: FirebaseFirestoreException): PgError {
    val exceptionClass = e.javaClass.name
    val codeName = e.code.name
    val fullMsg = "[$exceptionClass / $codeName]: ${e.message}"
    return when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            PgError.SecurityError("Firestore Access Denied $fullMsg")
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            PgError.NetworkError("Firestore Connection/Timeout $fullMsg")
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            PgError.NetworkError("Firestore Quota Exceeded $fullMsg")
        FirebaseFirestoreException.Code.NOT_FOUND ->
            PgError.DatabaseError("Firestore Document Not Found $fullMsg")
        else ->
            PgError.DatabaseError("Firestore Error $fullMsg")
    }
}

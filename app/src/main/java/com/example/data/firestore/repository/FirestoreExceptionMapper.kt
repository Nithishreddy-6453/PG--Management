package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Maps FirebaseFirestoreException codes into domain PgError types.
 */
internal fun mapFirestoreException(e: FirebaseFirestoreException): PgError {
    return when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            PgError.SecurityError("Firestore Access Denied: ${e.message}")
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            PgError.NetworkError("Firestore Connection/Timeout Issue: ${e.message}")
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            PgError.NetworkError("Firestore Quota Exceeded: ${e.message}")
        FirebaseFirestoreException.Code.NOT_FOUND ->
            PgError.DatabaseError("Firestore Document Not Found: ${e.message}")
        else ->
            PgError.DatabaseError("Firestore Error (${e.code}): ${e.message}")
    }
}

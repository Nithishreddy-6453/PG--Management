package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.firestore.model.SettingsDto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSettingsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("settings")

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: "default_owner"

    suspend fun saveSettings(settingsDto: SettingsDto): PgResult<Unit> {
        return try {
            val docId = if (settingsDto.id.isNotBlank()) settingsDto.id else "settings_${currentOwnerId}"
            val dtoToSave = settingsDto.copy(
                id = docId,
                ownerId = if (settingsDto.ownerId.isNotBlank()) settingsDto.ownerId else currentOwnerId,
                updatedAt = System.currentTimeMillis()
            )
            collection.document(docId).set(dtoToSave).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to save settings to Firestore", e))
        }
    }

    suspend fun getSettings(ownerId: String = currentOwnerId): PgResult<SettingsDto?> {
        return try {
            val docId = "settings_$ownerId"
            val snapshot = collection.document(docId).get().await()
            if (snapshot.exists()) {
                val dto = snapshot.toObject(SettingsDto::class.java)
                PgResult.Success(dto)
            } else {
                PgResult.Success(null)
            }
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to get settings from Firestore", e))
        }
    }
}

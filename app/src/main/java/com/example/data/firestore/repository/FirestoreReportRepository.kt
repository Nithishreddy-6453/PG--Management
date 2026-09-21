package com.example.data.firestore.repository

import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.data.firestore.model.ReportDto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreReportRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("reports")

    private val currentOwnerId: String
        get() = auth.currentUser?.uid ?: "default_owner"

    suspend fun saveReport(reportDto: ReportDto): PgResult<Unit> {
        return try {
            val docId = if (reportDto.id.isNotBlank()) reportDto.id else "report_${reportDto.reportPeriod}_${currentOwnerId}"
            val dtoToSave = reportDto.copy(
                id = docId,
                ownerId = if (reportDto.ownerId.isNotBlank()) reportDto.ownerId else currentOwnerId,
                updatedAt = System.currentTimeMillis()
            )
            collection.document(docId).set(dtoToSave).await()
            PgResult.Success(Unit)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to save report to Firestore", e))
        }
    }

    suspend fun getReports(ownerId: String = currentOwnerId): PgResult<List<ReportDto>> {
        return try {
            val snapshot = collection
                .whereEqualTo("ownerId", ownerId)
                .whereEqualTo("deleted", false)
                .get()
                .await()
            val list = snapshot.toObjects(ReportDto::class.java)
            PgResult.Success(list)
        } catch (e: FirebaseFirestoreException) {
            PgResult.Failure(mapFirestoreException(e))
        } catch (e: Exception) {
            PgResult.Failure(PgError.UnknownError("Failed to fetch reports from Firestore", e))
        }
    }
}

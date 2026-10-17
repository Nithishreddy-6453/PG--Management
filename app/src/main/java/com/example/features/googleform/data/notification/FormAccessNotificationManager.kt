package com.example.features.googleform.data.notification

import android.content.Context
import android.util.Log
import com.example.core.common.PgError
import com.example.core.common.PgResult
import com.example.features.googleform.domain.model.FormAccessRequest
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormAccessNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "FormAccessNotification"
    }

    suspend fun sendAccessRequestNotification(
        request: FormAccessRequest,
        propertyName: String
    ): PgResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val recipientEmail = request.ownerGoogleEmail.ifBlank {
                return@withContext PgResult.Failure(
                    PgError.ValidationError("Verified owner Google email is missing.")
                )
            }

            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(request.createdAt))

            val subject = "Google Forms Access Request — $propertyName"
            val body = """
                Hello,

                A Google Forms access request has been submitted for your PG property '$propertyName'.

                PG / Property: $propertyName
                Requested by: ${request.requesterName} (${request.requesterGoogleEmail})
                Form: ${request.formTitle.ifBlank { request.formId }}
                Requested Permission: ${request.requestedRole}
                Request Date: $formattedDate

                Note: ${request.requestNote.ifBlank { "None provided" }}

                Status: Awaiting Owner Approval

                Instructions:
                Please open PG Manager on Mobile A or open Google Forms to review and grant editing permission to ${request.requesterGoogleEmail}.

                Best regards,
                PG Manager
            """.trimIndent()

            Log.i(TAG, "Queuing email notification to Firestore outbox for owner: $recipientEmail")

            // Write notification record to server-side notification outbox
            val outboxPayload = mapOf(
                "requestId" to request.requestId,
                "propertyId" to request.propertyId,
                "propertyName" to propertyName,
                "recipientEmail" to recipientEmail,
                "requesterEmail" to request.requesterGoogleEmail,
                "requesterName" to request.requesterName,
                "formTitle" to request.formTitle,
                "subject" to subject,
                "body" to body,
                "status" to "PENDING",
                "createdAt" to request.createdAt,
                "queuedAt" to System.currentTimeMillis()
            )

            try {
                firestore.collection("notification_outbox")
                    .document(request.requestId)
                    .set(outboxPayload)
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write outbox record to Firestore: ${e.message}")
            }

            PgResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Notification dispatch error: ${e.message}")
            PgResult.Failure(PgError.NetworkError("Failed to prepare email notification: ${e.localizedMessage}"))
        }
    }
}

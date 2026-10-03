package com.example.core.integrity

/**
 * Categorized anomaly types for PG Room / Bed / Tenant / BedAssignment integrity.
 */
enum class IntegrityIssueType {
    A_ACTIVE_TENANT_NO_ASSIGNMENT,
    B_ACTIVE_TENANT_NO_MATCHING_BED_ASSIGNMENT,
    C_ASSIGNMENT_MISSING_TENANT,
    D_ASSIGNMENT_MISSING_BED,
    E_ASSIGNMENT_WRONG_ROOM,
    F_PROPERTY_ID_MISMATCH,
    G_BED_OCCUPIED_WITHOUT_ASSIGNMENT,
    H_BED_AVAILABLE_WITH_ACTIVE_ASSIGNMENT,
    I_MORE_ASSIGNMENTS_THAN_USABLE_BEDS,
    J_MORE_TENANTS_THAN_CAPACITY,
    K_DUPLICATE_ACTIVE_BED_ASSIGNMENT,
    L_TENANT_MULTIPLE_ACTIVE_BEDS,
    M_OVERLAPPING_TENANT_ASSIGNMENTS,
    N_OVERLAPPING_BED_ASSIGNMENTS,
    O_BLOCKED_BED_OCCUPIED,
    P_DEACTIVATED_ROOM_ACTIVE_ASSIGNMENTS,
    Q_VACATED_TENANT_ACTIVE_ASSIGNMENT,
    R_ASSIGNMENT_PAST_LEAVING_DATE,
    S_TENANT_RECORD_ASSIGNMENT_MISMATCH,
    MISSING_PHYSICAL_BEDS_IN_ROOM
}

enum class ResolutionAction {
    AUTOMATICALLY_REPAIRED,
    FLAGGED_FOR_MANUAL_REVIEW,
    REPAIR_SKIPPED_AMBIGUOUS,
    UNCHANGED_VALID
}

data class IntegrityIssue(
    val type: IntegrityIssueType,
    val propertyId: String,
    val roomNumber: String,
    val tenantId: Int? = null,
    val tenantName: String? = null,
    val bedId: String? = null,
    val assignmentId: String? = null,
    val description: String,
    val actionTaken: ResolutionAction = ResolutionAction.FLAGGED_FOR_MANUAL_REVIEW,
    val details: String = ""
)

data class IntegrityScanReport(
    val scannedAt: Long = System.currentTimeMillis(),
    val propertyId: String?,
    val totalRoomsScanned: Int,
    val totalBedsScanned: Int,
    val totalTenantsScanned: Int,
    val totalAssignmentsScanned: Int,
    val issuesFound: List<IntegrityIssue>,
    val isClean: Boolean = issuesFound.isEmpty()
) {
    val totalIssuesCount: Int get() = issuesFound.size
    val issuesByType: Map<IntegrityIssueType, Int> get() = issuesFound.groupBy { it.type }.mapValues { it.value.size }
}

data class RepairActionLog(
    val propertyId: String,
    val roomNumber: String,
    val tenantId: Int? = null,
    val tenantName: String? = null,
    val bedId: String? = null,
    val previousState: String,
    val repairedState: String,
    val reason: String,
    val action: ResolutionAction
)

data class IntegrityRepairReport(
    val repairedAt: Long = System.currentTimeMillis(),
    val propertyId: String?,
    val totalIssuesIdentified: Int,
    val automaticallyRepairedCount: Int,
    val reviewItemsCount: Int,
    val repairLogs: List<RepairActionLog>,
    val remainingReviewItems: List<IntegrityIssue>
)

data class BedAssignmentValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

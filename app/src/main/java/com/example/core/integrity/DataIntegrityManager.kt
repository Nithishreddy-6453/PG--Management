package com.example.core.integrity

import com.example.core.common.PgLogger
import com.example.data.database.*
import com.example.data.sync.SyncCoordinator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataIntegrityManager @Inject constructor(
    private val roomDao: RoomDao,
    private val bedDao: BedDao,
    private val bedAssignmentDao: BedAssignmentDao,
    private val tenantDao: TenantDao,
    private val propertyDao: PropertyDao,
    private val logger: PgLogger,
    private val syncCoordinator: SyncCoordinator? = null
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Performs a comprehensive read-only consistency scan across Rooms, Beds, BedAssignments, and Tenants.
     */
    suspend fun performScan(targetPropertyId: String? = null, todayDateOverride: String? = null): IntegrityScanReport {
        val todayStr = todayDateOverride ?: dateFormat.format(Date())
        logger.i(TAG, "Starting Data Integrity Scan for property: ${targetPropertyId ?: "ALL"}")

        val properties = if (!targetPropertyId.isNullOrBlank()) {
            propertyDao.getProperty(targetPropertyId)?.let { listOf(it) } ?: propertyDao.getAllProperties()
        } else {
            propertyDao.getAllProperties()
        }

        val allRooms = if (!targetPropertyId.isNullOrBlank()) {
            roomDao.getAllRooms(targetPropertyId)
        } else {
            roomDao.getAllRooms()
        }

        val allBeds = if (!targetPropertyId.isNullOrBlank()) {
            bedDao.getAllBedsForProperty(targetPropertyId)
        } else {
            properties.flatMap { bedDao.getAllBedsForProperty(it.propertyId) }
        }

        val allTenants = if (!targetPropertyId.isNullOrBlank()) {
            tenantDao.getAllTenants(targetPropertyId)
        } else {
            tenantDao.getAllTenants()
        }

        val allAssignments = if (!targetPropertyId.isNullOrBlank()) {
            bedAssignmentDao.getAllAssignments(targetPropertyId)
        } else {
            properties.flatMap { bedAssignmentDao.getAllAssignments(it.propertyId) }
        }

        val issues = mutableListOf<IntegrityIssue>()

        // 1. Scan Rooms vs Beds
        for (room in allRooms) {
            val roomBeds = allBeds.filter { it.propertyId == room.propertyId && it.roomNumber == room.roomNumber && !it.deleted }
            if (roomBeds.size < room.capacity) {
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.MISSING_PHYSICAL_BEDS_IN_ROOM,
                        propertyId = room.propertyId,
                        roomNumber = room.roomNumber,
                        description = "Room ${room.roomNumber} has capacity ${room.capacity} but only ${roomBeds.size} bed entities configured."
                    )
                )
            }

            val activeRoomTenants = allTenants.filter {
                it.propertyId == room.propertyId &&
                        it.roomNumber == room.roomNumber &&
                        !it.deleted &&
                        (it.leavingDate.isBlank() || it.leavingDate > todayStr)
            }

            // Case J: More active tenants than room capacity
            val usableBedsCount = roomBeds.count { it.status != "BLOCKED" }.coerceAtLeast(room.capacity)
            if (activeRoomTenants.size > usableBedsCount) {
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.J_MORE_TENANTS_THAN_CAPACITY,
                        propertyId = room.propertyId,
                        roomNumber = room.roomNumber,
                        description = "Room ${room.roomNumber} OVER CAPACITY: ${activeRoomTenants.size} active tenants for capacity $usableBedsCount.",
                        details = "Tenants: ${activeRoomTenants.joinToString { "${it.name} (id:${it.id})" }}"
                    )
                )
            }
        }

        // 2. Scan Tenants vs Assignments
        val tenantMap = allTenants.associateBy { it.id }
        for (tenant in allTenants) {
            val isTenantActive = !tenant.deleted &&
                    tenant.roomNumber.isNotBlank() &&
                    (tenant.leavingDate.isBlank() || tenant.leavingDate > todayStr)

            val tenantAssignments = allAssignments.filter { it.tenantId == tenant.id && !it.deleted }
            val activeAssignments = tenantAssignments.filter { it.endDate == null || it.endDate > todayStr }

            if (isTenantActive) {
                // Case A: Active tenant has room but no active bed assignment
                if (activeAssignments.isEmpty()) {
                    issues.add(
                        IntegrityIssue(
                            type = IntegrityIssueType.A_ACTIVE_TENANT_NO_ASSIGNMENT,
                            propertyId = tenant.propertyId,
                            roomNumber = tenant.roomNumber,
                            tenantId = tenant.id,
                            tenantName = tenant.name,
                            bedId = tenant.bedId,
                            description = "Active tenant '${tenant.name}' assigned to Room ${tenant.roomNumber} has no active BedAssignmentEntity."
                        )
                    )
                } else if (activeAssignments.size == 1) {
                    val assignment = activeAssignments.first()
                    // Case S: Current room/bed on TenantEntity disagrees with active assignment
                    if (assignment.roomNumber != tenant.roomNumber || assignment.bedId != tenant.bedId) {
                        issues.add(
                            IntegrityIssue(
                                type = IntegrityIssueType.S_TENANT_RECORD_ASSIGNMENT_MISMATCH,
                                propertyId = tenant.propertyId,
                                roomNumber = tenant.roomNumber,
                                tenantId = tenant.id,
                                tenantName = tenant.name,
                                bedId = tenant.bedId,
                                assignmentId = assignment.assignmentId,
                                description = "Tenant record (${tenant.roomNumber}/${tenant.bedId}) does not match active assignment (${assignment.roomNumber}/${assignment.bedId})."
                            )
                        )
                    }
                    // Case F: Property ID mismatch
                    if (assignment.propertyId.isNotBlank() && tenant.propertyId.isNotBlank() && assignment.propertyId != tenant.propertyId) {
                        issues.add(
                            IntegrityIssue(
                                type = IntegrityIssueType.F_PROPERTY_ID_MISMATCH,
                                propertyId = tenant.propertyId,
                                roomNumber = tenant.roomNumber,
                                tenantId = tenant.id,
                                tenantName = tenant.name,
                                assignmentId = assignment.assignmentId,
                                description = "Tenant propertyId '${tenant.propertyId}' differs from assignment propertyId '${assignment.propertyId}'."
                            )
                        )
                    }
                } else {
                    // Case L: Same tenant assigned to multiple active beds
                    issues.add(
                        IntegrityIssue(
                            type = IntegrityIssueType.L_TENANT_MULTIPLE_ACTIVE_BEDS,
                            propertyId = tenant.propertyId,
                            roomNumber = tenant.roomNumber,
                            tenantId = tenant.id,
                            tenantName = tenant.name,
                            description = "Tenant '${tenant.name}' has ${activeAssignments.size} active bed assignments simultaneously.",
                            details = activeAssignments.joinToString { "${it.roomNumber}/${it.bedId}" }
                        )
                    )
                }
            } else {
                // Tenant is vacated/deleted/empty room
                // Case Q: Tenant marked vacated while an active bed assignment remains
                for (activeAssign in activeAssignments) {
                    issues.add(
                        IntegrityIssue(
                            type = IntegrityIssueType.Q_VACATED_TENANT_ACTIVE_ASSIGNMENT,
                            propertyId = tenant.propertyId,
                            roomNumber = activeAssign.roomNumber,
                            tenantId = tenant.id,
                            tenantName = tenant.name,
                            bedId = activeAssign.bedId,
                            assignmentId = activeAssign.assignmentId,
                            description = "Vacated tenant '${tenant.name}' still has active bed assignment in Room ${activeAssign.roomNumber} ${activeAssign.bedId}."
                        )
                    )
                }
            }

            // Case R: Active assignment after leaving date
            if (tenant.leavingDate.isNotBlank() && tenant.leavingDate <= todayStr) {
                for (assign in activeAssignments) {
                    if (assign.endDate == null || assign.endDate > tenant.leavingDate) {
                        issues.add(
                            IntegrityIssue(
                                type = IntegrityIssueType.R_ASSIGNMENT_PAST_LEAVING_DATE,
                                propertyId = tenant.propertyId,
                                roomNumber = assign.roomNumber,
                                tenantId = tenant.id,
                                tenantName = tenant.name,
                                assignmentId = assign.assignmentId,
                                description = "Bed assignment active past tenant's leaving date (${tenant.leavingDate})."
                            )
                        )
                    }
                }
            }
        }

        // 3. Scan BedAssignments vs Beds & Tenants
        val activeAssignmentsGroupedByBed = allAssignments
            .filter { !it.deleted && (it.endDate == null || it.endDate > todayStr) }
            .groupBy { "${it.propertyId}#${it.roomNumber}#${it.bedId}" }

        for ((bedKey, assignments) in activeAssignmentsGroupedByBed) {
            // Case K: Duplicate active assignments on same bed
            if (assignments.size > 1) {
                val sample = assignments.first()
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.K_DUPLICATE_ACTIVE_BED_ASSIGNMENT,
                        propertyId = sample.propertyId,
                        roomNumber = sample.roomNumber,
                        bedId = sample.bedId,
                        description = "Bed '${sample.bedId}' in Room ${sample.roomNumber} has ${assignments.size} conflicting active assignments.",
                        details = assignments.joinToString { "tenantId:${it.tenantId}" }
                    )
                )
            }
        }

        for (assign in allAssignments.filter { !it.deleted }) {
            // Case C: Assignment pointing to missing tenant
            val tenant = tenantMap[assign.tenantId]
            if (tenant == null) {
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.C_ASSIGNMENT_MISSING_TENANT,
                        propertyId = assign.propertyId,
                        roomNumber = assign.roomNumber,
                        tenantId = assign.tenantId,
                        bedId = assign.bedId,
                        assignmentId = assign.assignmentId,
                        description = "Bed assignment points to non-existent tenantId ${assign.tenantId}."
                    )
                )
            }

            // Case D: Assignment pointing to missing bed
            val matchingBed = allBeds.firstOrNull {
                it.propertyId == assign.propertyId && it.roomNumber == assign.roomNumber && it.bedId == assign.bedId && !it.deleted
            }
            if (matchingBed == null) {
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.D_ASSIGNMENT_MISSING_BED,
                        propertyId = assign.propertyId,
                        roomNumber = assign.roomNumber,
                        tenantId = assign.tenantId,
                        bedId = assign.bedId,
                        assignmentId = assign.assignmentId,
                        description = "Bed assignment points to missing bed '${assign.bedId}' in Room ${assign.roomNumber}."
                    )
                )
            } else if (matchingBed.status == "BLOCKED" && (assign.endDate == null || assign.endDate > todayStr)) {
                // Case O: Blocked bed occupied
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.O_BLOCKED_BED_OCCUPIED,
                        propertyId = assign.propertyId,
                        roomNumber = assign.roomNumber,
                        tenantId = assign.tenantId,
                        bedId = assign.bedId,
                        assignmentId = assign.assignmentId,
                        description = "Blocked bed '${assign.bedId}' in Room ${assign.roomNumber} has active assignment."
                    )
                )
            }
        }

        // 4. Scan Beds vs Active Assignments
        for (bed in allBeds.filter { !it.deleted }) {
            val key = "${bed.propertyId}#${bed.roomNumber}#${bed.bedId}"
            val activeAssigns = activeAssignmentsGroupedByBed[key].orEmpty()

            if (bed.status == "OCCUPIED" && activeAssigns.isEmpty()) {
                // Case G: Bed marked OCCUPIED but no active assignment
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.G_BED_OCCUPIED_WITHOUT_ASSIGNMENT,
                        propertyId = bed.propertyId,
                        roomNumber = bed.roomNumber,
                        bedId = bed.bedId,
                        description = "Bed '${bed.bedId}' in Room ${bed.roomNumber} is marked OCCUPIED but has no active BedAssignment."
                    )
                )
            } else if (bed.status == "AVAILABLE" && activeAssigns.isNotEmpty()) {
                // Case H: Bed marked AVAILABLE but has active assignment
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.H_BED_AVAILABLE_WITH_ACTIVE_ASSIGNMENT,
                        propertyId = bed.propertyId,
                        roomNumber = bed.roomNumber,
                        bedId = bed.bedId,
                        description = "Bed '${bed.bedId}' in Room ${bed.roomNumber} is marked AVAILABLE but has ${activeAssigns.size} active assignment(s)."
                    )
                )
            }
        }

        logger.i(TAG, "Integrity Scan Complete: ${issues.size} issues detected.")
        return IntegrityScanReport(
            propertyId = targetPropertyId,
            totalRoomsScanned = allRooms.size,
            totalBedsScanned = allBeds.size,
            totalTenantsScanned = allTenants.size,
            totalAssignmentsScanned = allAssignments.size,
            issuesFound = issues
        )
    }

    /**
     * Executes safe, deterministic, conservative repairs to synchronize
     * TENANT ↕ BED ASSIGNMENT ↕ BED ↕ ROOM.
     * Never invents arbitrary data or destroys ambiguous multi-tenant associations.
     */
    suspend fun performSafeRepair(targetPropertyId: String? = null, todayDateOverride: String? = null): IntegrityRepairReport {
        val todayStr = todayDateOverride ?: dateFormat.format(Date())
        logger.i(TAG, "Starting Safe Data Integrity Repair for property: ${targetPropertyId ?: "ALL"}")

        val scanReport = performScan(targetPropertyId, todayStr)
        val repairLogs = mutableListOf<RepairActionLog>()
        val remainingReviewItems = mutableListOf<IntegrityIssue>()

        val properties = if (!targetPropertyId.isNullOrBlank()) {
            propertyDao.getProperty(targetPropertyId)?.let { listOf(it) } ?: propertyDao.getAllProperties()
        } else {
            propertyDao.getAllProperties()
        }

        val defaultPropId = properties.firstOrNull()?.propertyId ?: "property_default"

        // PHASE 1: Ensure all rooms have valid physical BedEntity records up to their capacity
        val allRooms = if (!targetPropertyId.isNullOrBlank()) {
            roomDao.getAllRooms(targetPropertyId)
        } else {
            roomDao.getAllRooms()
        }

        for (room in allRooms) {
            val propId = if (room.propertyId.isNotBlank() && room.propertyId != "property_default") room.propertyId else defaultPropId
            val existingBeds = bedDao.getBedsForRoom(propId, room.roomNumber).filter { !it.deleted }
            val existingBedIds = existingBeds.map { it.bedId }.toSet()

            // 1.1 Ensure standard "Bed 1", "Bed 2" up to capacity exist
            for (i in 1..maxOf(1, room.capacity)) {
                val standardBedId = "Bed $i"
                val alternateBedId = "Bed " + ('A' + (i - 1))
                if (!existingBedIds.contains(standardBedId) && !existingBedIds.contains(alternateBedId)) {
                    val newBed = BedEntity(
                        roomNumber = room.roomNumber,
                        bedId = standardBedId,
                        status = "AVAILABLE",
                        propertyId = propId,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                    bedDao.insertBed(newBed)
                    repairLogs.add(
                        RepairActionLog(
                            propertyId = propId,
                            roomNumber = room.roomNumber,
                            bedId = standardBedId,
                            previousState = "Missing BedEntity",
                            repairedState = "Created BedEntity(status=AVAILABLE)",
                            reason = "Initialized missing physical bed for room capacity (${room.capacity})",
                            action = ResolutionAction.AUTOMATICALLY_REPAIRED
                        )
                    )
                }
            }
        }

        // Refresh all beds and assignments after bed initialization
        val allBeds = if (!targetPropertyId.isNullOrBlank()) {
            bedDao.getAllBedsForProperty(targetPropertyId)
        } else {
            properties.flatMap { bedDao.getAllBedsForProperty(it.propertyId) }
        }

        val allTenants = if (!targetPropertyId.isNullOrBlank()) {
            tenantDao.getAllTenants(targetPropertyId)
        } else {
            tenantDao.getAllTenants()
        }

        val allAssignments = if (!targetPropertyId.isNullOrBlank()) {
            bedAssignmentDao.getAllAssignments(targetPropertyId)
        } else {
            properties.flatMap { bedAssignmentDao.getAllAssignments(it.propertyId) }
        }

        // PHASE 2: Close active assignments for vacated/deleted tenants (Case Q, R)
        for (tenant in allTenants) {
            val isVacated = tenant.deleted || tenant.roomNumber.isBlank() || (tenant.leavingDate.isNotBlank() && tenant.leavingDate <= todayStr)
            if (isVacated) {
                val activeAssignments = allAssignments.filter {
                    it.tenantId == tenant.id && !it.deleted && (it.endDate == null || it.endDate > todayStr)
                }
                for (assignment in activeAssignments) {
                    val effectiveEnd = if (tenant.leavingDate.isNotBlank()) tenant.leavingDate else todayStr
                    val updatedAssign = assignment.copy(
                        endDate = effectiveEnd,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                    bedAssignmentDao.updateAssignment(updatedAssign)
                    syncCoordinator?.enqueueOperation("BED_ASSIGNMENT", assignment.assignmentId, "UPDATE")

                    repairLogs.add(
                        RepairActionLog(
                            propertyId = assignment.propertyId,
                            roomNumber = assignment.roomNumber,
                            tenantId = tenant.id,
                            tenantName = tenant.name,
                            bedId = assignment.bedId,
                            previousState = "Active BedAssignment (endDate=${assignment.endDate})",
                            repairedState = "Closed BedAssignment (endDate=$effectiveEnd)",
                            reason = "Tenant '${tenant.name}' is vacated; closed lingering active assignment.",
                            action = ResolutionAction.AUTOMATICALLY_REPAIRED
                        )
                    )
                }
            }
        }

        // PHASE 3: Reconcile Active Tenants with Beds and BedAssignments (Case A, B, S)
        val activeTenants = allTenants.filter {
            !it.deleted && it.roomNumber.isNotBlank() && (it.leavingDate.isBlank() || it.leavingDate > todayStr)
        }

        // Re-query assignments after Phase 2
        val currentAssignments = if (!targetPropertyId.isNullOrBlank()) {
            bedAssignmentDao.getAllAssignments(targetPropertyId)
        } else {
            properties.flatMap { bedAssignmentDao.getAllAssignments(it.propertyId) }
        }

        for (tenant in activeTenants) {
            val propId = if (tenant.propertyId.isNotBlank() && tenant.propertyId != "property_default") tenant.propertyId else defaultPropId
            val tenantActiveAssignments = currentAssignments.filter {
                it.tenantId == tenant.id && !it.deleted && (it.endDate == null || it.endDate > todayStr)
            }

            if (tenantActiveAssignments.isEmpty()) {
                // Tenant has roomNumber and bedId, but no BedAssignmentEntity
                val roomBeds = bedDao.getBedsForRoom(propId, tenant.roomNumber).filter { !it.deleted }
                
                // Determine candidate bed
                val rawBedId = tenant.bedId.trim()
                val targetBed = if (rawBedId.isNotBlank()) {
                    // Try exact match or normalized match
                    roomBeds.firstOrNull { it.bedId.equals(rawBedId, ignoreCase = true) }
                        ?: roomBeds.firstOrNull { it.bedId.endsWith(rawBedId, ignoreCase = true) }
                        ?: if (rawBedId.startsWith("Bed", ignoreCase = true)) {
                            // Create bed if it doesn't exist
                            val newBed = BedEntity(
                                roomNumber = tenant.roomNumber,
                                bedId = rawBedId,
                                status = "AVAILABLE",
                                propertyId = propId,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = "PENDING_UPLOAD"
                            )
                            bedDao.insertBed(newBed)
                            newBed
                        } else null
                } else {
                    // If bedId is blank, pick first available unassigned bed
                    roomBeds.firstOrNull { it.status != "BLOCKED" }
                }

                if (targetBed != null && targetBed.status != "BLOCKED") {
                    // Check if target bed already has an active assignment for ANOTHER tenant
                    val isBedAlreadyAssigned = currentAssignments.any {
                        it.propertyId == propId &&
                                it.roomNumber == tenant.roomNumber &&
                                it.bedId == targetBed.bedId &&
                                it.tenantId != tenant.id &&
                                !it.deleted &&
                                (it.endDate == null || it.endDate > todayStr)
                    }

                    if (!isBedAlreadyAssigned) {
                        val newAssignment = BedAssignmentEntity(
                            assignmentId = UUID.randomUUID().toString(),
                            tenantId = tenant.id,
                            roomNumber = tenant.roomNumber,
                            bedId = targetBed.bedId,
                            startDate = tenant.moveInDate.ifBlank { todayStr },
                            endDate = if (tenant.leavingDate.isNotBlank()) tenant.leavingDate else null,
                            agreedRent = tenant.monthlyRent,
                            propertyId = propId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = "PENDING_UPLOAD"
                        )
                        bedAssignmentDao.insertAssignment(newAssignment)
                        syncCoordinator?.enqueueOperation("BED_ASSIGNMENT", newAssignment.assignmentId, "CREATE")

                        // Update bed to OCCUPIED
                        val updatedBed = targetBed.copy(
                            status = "OCCUPIED",
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = "PENDING_UPLOAD"
                        )
                        bedDao.updateBed(updatedBed)
                        syncCoordinator?.enqueueOperation("BED", "${propId}_${targetBed.roomNumber}_${targetBed.bedId}", "UPDATE")

                        // Update tenant if bedId was updated
                        if (tenant.bedId != targetBed.bedId || tenant.propertyId != propId) {
                            val updatedTenant = tenant.copy(
                                bedId = targetBed.bedId,
                                propertyId = propId,
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = "PENDING_UPLOAD"
                            )
                            tenantDao.updateTenant(updatedTenant)
                            syncCoordinator?.enqueueOperation("TENANT", tenant.id.toString(), "UPDATE")
                        }

                        repairLogs.add(
                            RepairActionLog(
                                propertyId = propId,
                                roomNumber = tenant.roomNumber,
                                tenantId = tenant.id,
                                tenantName = tenant.name,
                                bedId = targetBed.bedId,
                                previousState = "Active Tenant with missing BedAssignment",
                                repairedState = "Created BedAssignment & Bed marked OCCUPIED",
                                reason = "Reconstructed missing active bed assignment for active tenant '${tenant.name}'",
                                action = ResolutionAction.AUTOMATICALLY_REPAIRED
                            )
                        )
                    } else {
                        // Bed collision: Cannot safely guess
                        remainingReviewItems.add(
                            IntegrityIssue(
                                type = IntegrityIssueType.K_DUPLICATE_ACTIVE_BED_ASSIGNMENT,
                                propertyId = propId,
                                roomNumber = tenant.roomNumber,
                                tenantId = tenant.id,
                                tenantName = tenant.name,
                                bedId = targetBed.bedId,
                                description = "Conflict: Bed '${targetBed.bedId}' is already assigned to another active tenant. Flagged for manual review.",
                                actionTaken = ResolutionAction.FLAGGED_FOR_MANUAL_REVIEW
                            )
                        )
                    }
                } else {
                    remainingReviewItems.add(
                        IntegrityIssue(
                            type = IntegrityIssueType.A_ACTIVE_TENANT_NO_ASSIGNMENT,
                            propertyId = propId,
                            roomNumber = tenant.roomNumber,
                            tenantId = tenant.id,
                            tenantName = tenant.name,
                            description = "Could not unambiguously allocate a bed for tenant '${tenant.name}' in Room ${tenant.roomNumber}. Flagged for manual review.",
                            actionTaken = ResolutionAction.FLAGGED_FOR_MANUAL_REVIEW
                        )
                    )
                }
            } else if (tenantActiveAssignments.size == 1) {
                val assignment = tenantActiveAssignments.first()
                if (tenant.roomNumber != assignment.roomNumber || tenant.bedId != assignment.bedId || tenant.propertyId != assignment.propertyId) {
                    val updatedTenant = tenant.copy(
                        roomNumber = assignment.roomNumber,
                        bedId = assignment.bedId,
                        propertyId = assignment.propertyId,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                    tenantDao.updateTenant(updatedTenant)
                    syncCoordinator?.enqueueOperation("TENANT", tenant.id.toString(), "UPDATE")

                    repairLogs.add(
                        RepairActionLog(
                            propertyId = assignment.propertyId,
                            roomNumber = assignment.roomNumber,
                            tenantId = tenant.id,
                            tenantName = tenant.name,
                            bedId = assignment.bedId,
                            previousState = "Tenant record (${tenant.roomNumber}/${tenant.bedId}) out of sync",
                            repairedState = "Tenant record synced with assignment (${assignment.roomNumber}/${assignment.bedId})",
                            reason = "Synchronized tenant fields with authoritative active bed assignment.",
                            action = ResolutionAction.AUTOMATICALLY_REPAIRED
                        )
                    )
                }
            } else {
                remainingReviewItems.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.L_TENANT_MULTIPLE_ACTIVE_BEDS,
                        propertyId = propId,
                        roomNumber = tenant.roomNumber,
                        tenantId = tenant.id,
                        tenantName = tenant.name,
                        description = "Tenant '${tenant.name}' has multiple active bed assignments. Flagged for manual review.",
                        actionTaken = ResolutionAction.FLAGGED_FOR_MANUAL_REVIEW
                    )
                )
            }
        }

        // PHASE 4: Reconcile Bed States (OCCUPIED vs AVAILABLE vs BLOCKED) (Case G, H)
        val refreshedAssignments = if (!targetPropertyId.isNullOrBlank()) {
            bedAssignmentDao.getAllAssignments(targetPropertyId)
        } else {
            properties.flatMap { bedAssignmentDao.getAllAssignments(it.propertyId) }
        }

        val activeAssignsByBedKey = refreshedAssignments
            .filter { !it.deleted && (it.endDate == null || it.endDate > todayStr) }
            .groupBy { "${it.propertyId}#${it.roomNumber}#${it.bedId}" }

        val refreshedBeds = if (!targetPropertyId.isNullOrBlank()) {
            bedDao.getAllBedsForProperty(targetPropertyId)
        } else {
            properties.flatMap { bedDao.getAllBedsForProperty(it.propertyId) }
        }

        for (bed in refreshedBeds.filter { !it.deleted }) {
            val key = "${bed.propertyId}#${bed.roomNumber}#${bed.bedId}"
            val activeAssigns = activeAssignsByBedKey[key].orEmpty()

            if (bed.status == "AVAILABLE" && activeAssigns.isNotEmpty()) {
                val updatedBed = bed.copy(
                    status = "OCCUPIED",
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = "PENDING_UPLOAD"
                )
                bedDao.updateBed(updatedBed)
                syncCoordinator?.enqueueOperation("BED", "${bed.propertyId}_${bed.roomNumber}_${bed.bedId}", "UPDATE")

                repairLogs.add(
                    RepairActionLog(
                        propertyId = bed.propertyId,
                        roomNumber = bed.roomNumber,
                        bedId = bed.bedId,
                        previousState = "Status = AVAILABLE with ${activeAssigns.size} active assignment(s)",
                        repairedState = "Status = OCCUPIED",
                        reason = "Corrected bed availability state to match active assignment.",
                        action = ResolutionAction.AUTOMATICALLY_REPAIRED
                    )
                )
            } else if (bed.status == "OCCUPIED" && activeAssigns.isEmpty()) {
                val updatedBed = bed.copy(
                    status = "AVAILABLE",
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = "PENDING_UPLOAD"
                )
                bedDao.updateBed(updatedBed)
                syncCoordinator?.enqueueOperation("BED", "${bed.propertyId}_${bed.roomNumber}_${bed.bedId}", "UPDATE")

                repairLogs.add(
                    RepairActionLog(
                        propertyId = bed.propertyId,
                        roomNumber = bed.roomNumber,
                        bedId = bed.bedId,
                        previousState = "Status = OCCUPIED with 0 active assignments",
                        repairedState = "Status = AVAILABLE",
                        reason = "Bed has no active tenant or assignment; reset status to AVAILABLE.",
                        action = ResolutionAction.AUTOMATICALLY_REPAIRED
                    )
                )
            }
        }

        logger.i(TAG, "Safe Repair Completed: ${repairLogs.size} records repaired, ${remainingReviewItems.size} items for manual review.")
        return IntegrityRepairReport(
            propertyId = targetPropertyId,
            totalIssuesIdentified = scanReport.totalIssuesCount,
            automaticallyRepairedCount = repairLogs.size,
            reviewItemsCount = remainingReviewItems.size,
            repairLogs = repairLogs,
            remainingReviewItems = remainingReviewItems
        )
    }

    companion object {
        private const val TAG = "DataIntegrityManager"
    }
}

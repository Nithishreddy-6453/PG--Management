package com.example.core.integrity

import com.example.core.common.PgLogger
import com.example.core.util.PgDateUtil
import com.example.data.database.*
import com.example.data.sync.SyncCoordinator
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

    /**
     * Performs a comprehensive read-only consistency scan across Rooms, Beds, BedAssignments, and Tenants.
     */
    suspend fun performScan(targetPropertyId: String? = null, todayDateOverride: String? = null): IntegrityScanReport {
        val todayStr = todayDateOverride ?: PgDateUtil.todayIso()
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

        // 1. Scan Rooms vs Beds & Canonical Capacity
        for (room in allRooms) {
            val roomBeds = allBeds.filter { it.propertyId == room.propertyId && it.roomNumber == room.roomNumber && !it.deleted }
            
            // Check if bed records differ from room capacity
            if (roomBeds.size != room.capacity) {
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.MISSING_PHYSICAL_BEDS_IN_ROOM,
                        propertyId = room.propertyId,
                        roomNumber = room.roomNumber,
                        description = "Room ${room.roomNumber} has canonical capacity ${room.capacity} but ${roomBeds.size} bed entities exist."
                    )
                )
            }

            val activeRoomTenants = allTenants.filter {
                it.propertyId == room.propertyId &&
                        it.roomNumber == room.roomNumber &&
                        !it.deleted &&
                        (it.leavingDate.isBlank() || PgDateUtil.isDateFuture(it.leavingDate, todayStr))
            }

            val usableBedsCount = (room.capacity - roomBeds.count { it.status == "BLOCKED" }).coerceAtLeast(0)
            if (activeRoomTenants.size > usableBedsCount) {
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.J_MORE_TENANTS_THAN_CAPACITY,
                        propertyId = room.propertyId,
                        roomNumber = room.roomNumber,
                        description = "Room ${room.roomNumber} OVER CAPACITY: ${activeRoomTenants.size} active tenants for canonical usable capacity $usableBedsCount.",
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
                    (tenant.leavingDate.isBlank() || PgDateUtil.isDateFuture(tenant.leavingDate, todayStr))

            val tenantAssignments = allAssignments.filter { it.tenantId == tenant.id && !it.deleted }
            val activeAssignments = tenantAssignments.filter {
                (it.startDate.isBlank() || !PgDateUtil.isDateFuture(it.startDate, todayStr)) &&
                        (it.endDate.isNullOrBlank() || PgDateUtil.isDateFuture(it.endDate, todayStr))
            }

            if (isTenantActive) {
                if (activeAssignments.isEmpty()) {
                    issues.add(
                        IntegrityIssue(
                            type = IntegrityIssueType.A_ACTIVE_TENANT_NO_ASSIGNMENT,
                            propertyId = tenant.propertyId,
                            roomNumber = tenant.roomNumber,
                            tenantId = tenant.id,
                            tenantName = tenant.name,
                            bedId = tenant.bedId,
                            description = "Active tenant '${tenant.name}' in Room ${tenant.roomNumber} has no active BedAssignmentEntity."
                        )
                    )
                } else if (activeAssignments.size == 1) {
                    val assignment = activeAssignments.first()
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
                                description = "Tenant record (${tenant.roomNumber}/${tenant.bedId}) does not match assignment (${assignment.roomNumber}/${assignment.bedId})."
                            )
                        )
                    }
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
                // Tenant is vacated or deleted
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

            // Lingering past leaving date
            if (tenant.leavingDate.isNotBlank() && PgDateUtil.isDatePastOrToday(tenant.leavingDate, todayStr)) {
                for (assign in activeAssignments) {
                    if (assign.endDate == null || PgDateUtil.isDateAfter(assign.endDate, tenant.leavingDate)) {
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
            .filter {
                !it.deleted &&
                        (it.startDate.isBlank() || !PgDateUtil.isDateFuture(it.startDate, todayStr)) &&
                        (it.endDate == null || PgDateUtil.isDateFuture(it.endDate, todayStr))
            }
            .groupBy { "${it.propertyId}#${it.roomNumber}#${it.bedId.lowercase()}" }

        for ((_, assignments) in activeAssignmentsGroupedByBed) {
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

            val matchingBed = allBeds.firstOrNull {
                it.propertyId == assign.propertyId && it.roomNumber == assign.roomNumber && it.bedId.equals(assign.bedId, ignoreCase = true) && !it.deleted
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
            } else if (matchingBed.status == "BLOCKED" && (assign.endDate == null || PgDateUtil.isDateFuture(assign.endDate, todayStr))) {
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
            val key = "${bed.propertyId}#${bed.roomNumber}#${bed.bedId.lowercase()}"
            val activeAssigns = activeAssignmentsGroupedByBed[key].orEmpty()

            if (bed.status == "OCCUPIED" && activeAssigns.isEmpty()) {
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
                issues.add(
                    IntegrityIssue(
                        type = IntegrityIssueType.H_BED_AVAILABLE_WITH_ACTIVE_ASSIGNMENT,
                        propertyId = bed.propertyId,
                        roomNumber = bed.roomNumber,
                        bedId = bed.bedId,
                        description = "Bed '${bed.bedId}' in Room ${bed.roomNumber} is marked AVAILABLE but has active assignment."
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
     * ROOM -> USABLE BEDS -> ACTIVE BED ASSIGNMENTS -> TENANTS.
     * Prevents bed count exceeding room capacity, closes expired assignments,
     * resolves duplicate beds, and maintains strict property isolation.
     */
    suspend fun performSafeRepair(targetPropertyId: String? = null, todayDateOverride: String? = null): IntegrityRepairReport {
        val todayStr = todayDateOverride ?: PgDateUtil.todayIso()
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

        val allRooms = if (!targetPropertyId.isNullOrBlank()) {
            roomDao.getAllRooms(targetPropertyId)
        } else {
            roomDao.getAllRooms()
        }

        val allTenantsInitial = if (!targetPropertyId.isNullOrBlank()) {
            tenantDao.getAllTenants(targetPropertyId)
        } else {
            tenantDao.getAllTenants()
        }

        val allAssignmentsInitial = if (!targetPropertyId.isNullOrBlank()) {
            bedAssignmentDao.getAllAssignments(targetPropertyId)
        } else {
            properties.flatMap { bedAssignmentDao.getAllAssignments(it.propertyId) }
        }

        // ====================================================================
        // PHASE 1: CLOSE PAST/EXPIRED TENANT VACANCIES (Case Q, R)
        // ====================================================================
        for (tenant in allTenantsInitial) {
            val isLeavingDatePast = tenant.leavingDate.isNotBlank() && PgDateUtil.isDatePastOrToday(tenant.leavingDate, todayStr)
            val isVacated = tenant.deleted || tenant.roomNumber.isBlank() || isLeavingDatePast

            if (isVacated) {
                val effectiveEnd = if (tenant.leavingDate.isNotBlank()) {
                    PgDateUtil.normalizeDate(tenant.leavingDate) ?: todayStr
                } else todayStr

                // Close all active assignments for this vacated tenant
                val tenantActiveAssignments = allAssignmentsInitial.filter {
                    it.tenantId == tenant.id && !it.deleted &&
                            (it.endDate.isNullOrBlank() || PgDateUtil.isDateFuture(it.endDate, todayStr) || PgDateUtil.isDateAfter(it.endDate, effectiveEnd))
                }

                for (assignment in tenantActiveAssignments) {
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
                            reason = "Tenant '${tenant.name}' leaving date ($effectiveEnd) has passed; finalized vacation.",
                            action = ResolutionAction.AUTOMATICALLY_REPAIRED
                        )
                    )
                }

                // If tenant's roomNumber/bedId is still active on TenantEntity, clear them
                if (tenant.roomNumber.isNotBlank() || tenant.bedId.isNotBlank()) {
                    val previousRoom = tenant.roomNumber
                    val previousBed = tenant.bedId
                    val vacatedNotes = if (tenant.notes.contains("Vacated")) {
                        tenant.notes
                    } else if (previousRoom.isNotBlank() && previousBed.isNotBlank()) {
                        "${tenant.notes}\n[Vacated from Room $previousRoom $previousBed on $effectiveEnd]".trim()
                    } else {
                        "${tenant.notes}\n[Vacated on $effectiveEnd]".trim()
                    }

                    val updatedTenant = tenant.copy(
                        roomNumber = "",
                        bedId = "",
                        leavingDate = effectiveEnd,
                        notes = vacatedNotes,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                    tenantDao.updateTenant(updatedTenant)
                    syncCoordinator?.enqueueOperation("TENANT", tenant.id.toString(), "UPDATE")

                    repairLogs.add(
                        RepairActionLog(
                            propertyId = tenant.propertyId,
                            roomNumber = previousRoom,
                            tenantId = tenant.id,
                            tenantName = tenant.name,
                            bedId = previousBed,
                            previousState = "Tenant record held room=$previousRoom bed=$previousBed past leaving date",
                            repairedState = "Cleared room and bed references on vacated tenant",
                            reason = "Finalized vacated tenant state for '${tenant.name}' past leaving date $effectiveEnd",
                            action = ResolutionAction.AUTOMATICALLY_REPAIRED
                        )
                    )
                }
            }
        }

        // ====================================================================
        // PHASE 2: CANONICALIZE PHYSICAL BEDS PER ROOM (Strict BedEntity cleanup)
        // ====================================================================
        // Refresh tenants after Phase 1 vacate cleanup
        val allTenantsPostVacate = if (!targetPropertyId.isNullOrBlank()) {
            tenantDao.getAllTenants(targetPropertyId)
        } else {
            tenantDao.getAllTenants()
        }

        for (room in allRooms) {
            val propId = if (room.propertyId.isNotBlank() && room.propertyId != "property_default") room.propertyId else defaultPropId
            val existingBeds = bedDao.getBedsForRoom(propId, room.roomNumber).filter { !it.deleted }
            val activeTenantsInRoom = allTenantsPostVacate.filter {
                it.propertyId == propId &&
                        it.roomNumber == room.roomNumber &&
                        !it.deleted &&
                        (it.leavingDate.isBlank() || PgDateUtil.isDateFuture(it.leavingDate, todayStr))
            }

            val referencedBedIds = activeTenantsInRoom.map { it.bedId.trim() }.filter { it.isNotBlank() }.toSet()

            // Detect naming convention used in room: letter-based (Bed A) or number-based (Bed 1)
            val usesLetters = referencedBedIds.any { it.matches(Regex("(?i)Bed\\s*[A-Z]")) } ||
                    existingBeds.any { it.bedId.matches(Regex("(?i)Bed\\s*[A-Z]")) }

            // 2.1 Prune redundant orphaned beds if existing beds exceed canonical room.capacity
            if (existingBeds.size > room.capacity) {
                // Determine which beds to keep:
                // First keep beds referenced by active tenants
                val bedsToKeep = mutableListOf<BedEntity>()
                val excessBeds = mutableListOf<BedEntity>()

                for (bed in existingBeds) {
                    if (referencedBedIds.any { it.equals(bed.bedId, ignoreCase = true) } || bed.status == "BLOCKED") {
                        if (bedsToKeep.size < room.capacity) {
                            bedsToKeep.add(bed)
                        } else {
                            excessBeds.add(bed)
                        }
                    } else {
                        excessBeds.add(bed)
                    }
                }

                // If still need more beds to reach room.capacity, pick from unassigned excess
                for (bed in excessBeds.toList()) {
                    if (bedsToKeep.size < room.capacity && bed.status != "BLOCKED") {
                        bedsToKeep.add(bed)
                        excessBeds.remove(bed)
                    }
                }

                // Delete all leftover excess beds
                for (excess in excessBeds) {
                    bedDao.deleteBed(propId, room.roomNumber, excess.bedId)
                    syncCoordinator?.enqueueOperation("BED", "${propId}_${room.roomNumber}_${excess.bedId}", "DELETE")

                    repairLogs.add(
                        RepairActionLog(
                            propertyId = propId,
                            roomNumber = room.roomNumber,
                            bedId = excess.bedId,
                            previousState = "Redundant BedEntity (${excess.bedId}) beyond capacity ${room.capacity}",
                            repairedState = "Deleted redundant BedEntity",
                            reason = "Enforced canonical room capacity (${room.capacity}); removed orphaned bed duplicate",
                            action = ResolutionAction.AUTOMATICALLY_REPAIRED
                        )
                    )
                }
            }

            // 2.2 Re-query non-deleted beds for room
            val updatedBeds = bedDao.getBedsForRoom(propId, room.roomNumber).filter { !it.deleted }
            val updatedBedIds = updatedBeds.map { it.bedId.lowercase() }.toSet()

            // 2.3 If room has fewer beds than room.capacity, create missing beds up to room.capacity
            var currentCount = updatedBeds.size
            var index = 1
            while (currentCount < room.capacity) {
                val candidateBedId = if (usesLetters) "Bed " + ('A' + (index - 1)) else "Bed $index"
                if (!updatedBedIds.contains(candidateBedId.lowercase())) {
                    val newBed = BedEntity(
                        roomNumber = room.roomNumber,
                        bedId = candidateBedId,
                        status = "AVAILABLE",
                        propertyId = propId,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                    bedDao.insertBed(newBed)
                    syncCoordinator?.enqueueOperation("BED", "${propId}_${room.roomNumber}_$candidateBedId", "CREATE")
                    currentCount++

                    repairLogs.add(
                        RepairActionLog(
                            propertyId = propId,
                            roomNumber = room.roomNumber,
                            bedId = candidateBedId,
                            previousState = "Missing physical BedEntity",
                            repairedState = "Created BedEntity(status=AVAILABLE)",
                            reason = "Initialized missing bed to match canonical room capacity (${room.capacity})",
                            action = ResolutionAction.AUTOMATICALLY_REPAIRED
                        )
                    )
                }
                index++
            }
        }

        // ====================================================================
        // PHASE 3: RECONCILE BED ASSIGNMENTS, DUPLICATES & TENANTS (Case A, B, K, S)
        // ====================================================================
        val allBedsRefreshed = if (!targetPropertyId.isNullOrBlank()) {
            bedDao.getAllBedsForProperty(targetPropertyId)
        } else {
            properties.flatMap { bedDao.getAllBedsForProperty(it.propertyId) }
        }

        val allTenantsRefreshed = if (!targetPropertyId.isNullOrBlank()) {
            tenantDao.getAllTenants(targetPropertyId)
        } else {
            tenantDao.getAllTenants()
        }

        val allAssignmentsRefreshed = if (!targetPropertyId.isNullOrBlank()) {
            bedAssignmentDao.getAllAssignments(targetPropertyId)
        } else {
            properties.flatMap { bedAssignmentDao.getAllAssignments(it.propertyId) }
        }

        for (room in allRooms) {
            val propId = if (room.propertyId.isNotBlank() && room.propertyId != "property_default") room.propertyId else defaultPropId
            val roomBeds = allBedsRefreshed.filter { it.propertyId == propId && it.roomNumber == room.roomNumber && !it.deleted }
            val roomBedMap = roomBeds.associateBy { it.bedId.lowercase() }

            val activeTenantsInRoom = allTenantsRefreshed.filter {
                it.propertyId == propId &&
                        it.roomNumber == room.roomNumber &&
                        !it.deleted &&
                        (it.leavingDate.isBlank() || PgDateUtil.isDateFuture(it.leavingDate, todayStr))
            }

            val roomAssignments = allAssignmentsRefreshed.filter {
                it.propertyId == propId &&
                        it.roomNumber == room.roomNumber &&
                        !it.deleted &&
                        (it.startDate.isBlank() || !PgDateUtil.isDateFuture(it.startDate, todayStr)) &&
                        (it.endDate.isNullOrBlank() || PgDateUtil.isDateFuture(it.endDate, todayStr))
            }

            // Track occupied beds in this room during reconciliation
            val assignedTenantToBed = mutableMapOf<String, Int>() // bedId.lowercase -> tenantId

            // 3.1 First respect existing valid assignments that link an active tenant in this room
            for (assign in roomAssignments) {
                val bedKey = assign.bedId.lowercase()
                val targetBed = roomBedMap[bedKey]
                if (targetBed != null && targetBed.status != "BLOCKED") {
                    val matchingTenant = activeTenantsInRoom.firstOrNull { it.id == assign.tenantId }
                    if (matchingTenant != null) {
                        if (!assignedTenantToBed.containsKey(bedKey)) {
                            assignedTenantToBed[bedKey] = matchingTenant.id
                            // Ensure tenant record has matching bedId
                            if (!matchingTenant.bedId.equals(targetBed.bedId, ignoreCase = true)) {
                                val updatedTenant = matchingTenant.copy(
                                    bedId = targetBed.bedId,
                                    updatedAt = System.currentTimeMillis(),
                                    syncStatus = "PENDING_UPLOAD"
                                )
                                tenantDao.updateTenant(updatedTenant)
                                syncCoordinator?.enqueueOperation("TENANT", matchingTenant.id.toString(), "UPDATE")
                            }
                        } else {
                            // DUPLICATE assignment on same bed!
                            // Close duplicate assignment to resolve bed collision
                            val duplicateAssign = assign.copy(
                                endDate = todayStr,
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = "PENDING_UPLOAD"
                            )
                            bedAssignmentDao.updateAssignment(duplicateAssign)
                            syncCoordinator?.enqueueOperation("BED_ASSIGNMENT", duplicateAssign.assignmentId, "UPDATE")

                            remainingReviewItems.add(
                                IntegrityIssue(
                                    type = IntegrityIssueType.K_DUPLICATE_ACTIVE_BED_ASSIGNMENT,
                                    propertyId = propId,
                                    roomNumber = room.roomNumber,
                                    tenantId = matchingTenant.id,
                                    tenantName = matchingTenant.name,
                                    bedId = targetBed.bedId,
                                    assignmentId = assign.assignmentId,
                                    description = "Bed '${targetBed.bedId}' in Room ${room.roomNumber} had duplicate assignment for tenant '${matchingTenant.name}'. Closed duplicate assignment and flagged for review.",
                                    actionTaken = ResolutionAction.FLAGGED_FOR_MANUAL_REVIEW
                                )
                            )
                        }
                    }
                }
            }

            // 3.2 Now process active tenants who do not yet have an active assignment recorded
            for (tenant in activeTenantsInRoom) {
                if (assignedTenantToBed.values.contains(tenant.id)) {
                    continue // Already assigned in 3.1
                }

                val requestedBedKey = tenant.bedId.trim().lowercase()
                val candidateBed = if (requestedBedKey.isNotBlank()) roomBedMap[requestedBedKey] else null

                if (candidateBed != null && candidateBed.status != "BLOCKED" && !assignedTenantToBed.containsKey(candidateBed.bedId.lowercase())) {
                    // Bed requested by tenant is free: Reconstruct missing BedAssignment!
                    val bedKey = candidateBed.bedId.lowercase()
                    assignedTenantToBed[bedKey] = tenant.id

                    val newAssignment = BedAssignmentEntity(
                        assignmentId = UUID.randomUUID().toString(),
                        tenantId = tenant.id,
                        roomNumber = room.roomNumber,
                        bedId = candidateBed.bedId,
                        startDate = tenant.moveInDate.ifBlank { todayStr },
                        endDate = if (tenant.leavingDate.isNotBlank()) PgDateUtil.normalizeDate(tenant.leavingDate) else null,
                        agreedRent = tenant.monthlyRent,
                        propertyId = propId,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = "PENDING_UPLOAD"
                    )
                    bedAssignmentDao.insertAssignment(newAssignment)
                    syncCoordinator?.enqueueOperation("BED_ASSIGNMENT", newAssignment.assignmentId, "CREATE")

                    repairLogs.add(
                        RepairActionLog(
                            propertyId = propId,
                            roomNumber = room.roomNumber,
                            tenantId = tenant.id,
                            tenantName = tenant.name,
                            bedId = candidateBed.bedId,
                            previousState = "Active tenant without active BedAssignment",
                            repairedState = "Created BedAssignment for bed ${candidateBed.bedId}",
                            reason = "Reconstructed missing bed assignment for active tenant '${tenant.name}'",
                            action = ResolutionAction.AUTOMATICALLY_REPAIRED
                        )
                    )
                } else {
                    // Bed is unavailable or conflicting!
                    // Check if room has an unassigned usable bed available
                    val freeBeds = roomBeds.filter { it.status != "BLOCKED" && !assignedTenantToBed.containsKey(it.bedId.lowercase()) }
                    
                    if (freeBeds.size == 1 && activeTenantsInRoom.count { !assignedTenantToBed.values.contains(it.id) } == 1) {
                        // Unambiguous deterministic allocation: exactly 1 tenant unassigned and exactly 1 bed free!
                        val freeBed = freeBeds.first()
                        val bedKey = freeBed.bedId.lowercase()
                        assignedTenantToBed[bedKey] = tenant.id

                        val newAssignment = BedAssignmentEntity(
                            assignmentId = UUID.randomUUID().toString(),
                            tenantId = tenant.id,
                            roomNumber = room.roomNumber,
                            bedId = freeBed.bedId,
                            startDate = tenant.moveInDate.ifBlank { todayStr },
                            endDate = if (tenant.leavingDate.isNotBlank()) PgDateUtil.normalizeDate(tenant.leavingDate) else null,
                            agreedRent = tenant.monthlyRent,
                            propertyId = propId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = "PENDING_UPLOAD"
                        )
                        bedAssignmentDao.insertAssignment(newAssignment)
                        syncCoordinator?.enqueueOperation("BED_ASSIGNMENT", newAssignment.assignmentId, "CREATE")

                        val updatedTenant = tenant.copy(
                            bedId = freeBed.bedId,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = "PENDING_UPLOAD"
                        )
                        tenantDao.updateTenant(updatedTenant)
                        syncCoordinator?.enqueueOperation("TENANT", tenant.id.toString(), "UPDATE")

                        repairLogs.add(
                            RepairActionLog(
                                propertyId = propId,
                                roomNumber = room.roomNumber,
                                tenantId = tenant.id,
                                tenantName = tenant.name,
                                bedId = freeBed.bedId,
                                previousState = "Conflicting/unallocated bed reference '${tenant.bedId}'",
                                repairedState = "Allocated remaining vacant bed ${freeBed.bedId}",
                                reason = "Resolved unambiguous single free bed in Room ${room.roomNumber} for tenant '${tenant.name}'",
                                action = ResolutionAction.AUTOMATICALLY_REPAIRED
                            )
                        )
                    } else {
                        // Ambiguous collision or room over capacity: preserve financial history and flag for review
                        remainingReviewItems.add(
                            IntegrityIssue(
                                type = if (activeTenantsInRoom.size > room.capacity) IntegrityIssueType.J_MORE_TENANTS_THAN_CAPACITY else IntegrityIssueType.K_DUPLICATE_ACTIVE_BED_ASSIGNMENT,
                                propertyId = propId,
                                roomNumber = room.roomNumber,
                                tenantId = tenant.id,
                                tenantName = tenant.name,
                                bedId = tenant.bedId,
                                description = "Tenant '${tenant.name}' has conflicting bed '${tenant.bedId}' in Room ${room.roomNumber}. Preserved tenant data and flagged for manual room allocation.",
                                actionTaken = ResolutionAction.FLAGGED_FOR_MANUAL_REVIEW
                            )
                        )
                    }
                }
            }

            // 3.3 Update Bed status (OCCUPIED vs AVAILABLE) for this room
            for (bed in roomBeds) {
                if (bed.status == "BLOCKED") continue
                val bedKey = bed.bedId.lowercase()
                val isOccupied = assignedTenantToBed.containsKey(bedKey)

                if (isOccupied && bed.status != "OCCUPIED") {
                    val updatedBed = bed.copy(status = "OCCUPIED", updatedAt = System.currentTimeMillis(), syncStatus = "PENDING_UPLOAD")
                    bedDao.updateBed(updatedBed)
                    syncCoordinator?.enqueueOperation("BED", "${propId}_${room.roomNumber}_${bed.bedId}", "UPDATE")
                } else if (!isOccupied && bed.status != "AVAILABLE") {
                    val updatedBed = bed.copy(status = "AVAILABLE", updatedAt = System.currentTimeMillis(), syncStatus = "PENDING_UPLOAD")
                    bedDao.updateBed(updatedBed)
                    syncCoordinator?.enqueueOperation("BED", "${propId}_${room.roomNumber}_${bed.bedId}", "UPDATE")
                }
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

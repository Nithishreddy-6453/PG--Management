package com.example.data.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ==========================================
// 1. DATABASE ENTITIES
// ==========================================

@Entity(
    tableName = "properties",
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["ownerId", "propertyId"]),
        Index(value = ["ownerId", "isActive"])
    ]
)
data class PropertyEntity(
    @PrimaryKey val propertyId: String = java.util.UUID.randomUUID().toString(),
    val ownerId: String = "",
    val propertyName: String,
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val contactNumber: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(
    tableName = "rooms",
    primaryKeys = ["propertyId", "roomNumber"],
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["ownerId", "propertyId"]),
        Index(value = ["propertyId", "roomNumber"])
    ]
)
data class RoomEntity(
    val roomNumber: String, // e.g., "101", "204"
    val floor: String = "Ground", // e.g., "Ground", "1st", "2nd"
    val capacity: Int = 1, // e.g., 1, 2, 3
    val ratePerBed: Double = 0.0,
    val roomType: String = "AC",
    val notes: String = "",
    val isActive: Boolean = true,
    val ownerId: String = "",
    val propertyId: String = "property_default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(
    tableName = "beds",
    primaryKeys = ["propertyId", "roomNumber", "bedId"],
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["ownerId", "propertyId"]),
        Index(value = ["propertyId", "roomNumber"]),
        Index(value = ["propertyId", "roomNumber", "bedId"])
    ]
)
data class BedEntity(
    val roomNumber: String,
    val bedId: String, // e.g., "Bed 1", "Bed 2"
    val status: String = "AVAILABLE", // AVAILABLE, OCCUPIED, BLOCKED
    val notes: String = "",
    val ownerId: String = "",
    val propertyId: String = "property_default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(
    tableName = "bed_assignments",
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["ownerId", "propertyId"]),
        Index(value = ["propertyId", "roomNumber"]),
        Index(value = ["propertyId", "tenantId"]),
        Index(value = ["propertyId", "roomNumber", "bedId"])
    ]
)
data class BedAssignmentEntity(
    @PrimaryKey val assignmentId: String = java.util.UUID.randomUUID().toString(),
    val tenantId: Int,
    val roomNumber: String,
    val bedId: String,
    val startDate: String, // yyyy-MM-dd
    val endDate: String? = null, // null if active
    val agreedRent: Double,
    val ownerId: String = "",
    val propertyId: String = "property_default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(
    tableName = "tenants",
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["ownerId", "propertyId"]),
        Index(value = ["propertyId", "roomNumber"]),
        Index(value = ["cloudId"])
    ]
)
data class TenantEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cloudId: String = "",
    val name: String,
    val phone: String,
    val email: String,
    val emergencyContact: String,
    val roomNumber: String,
    val bedId: String, // e.g., "Bed A", "Bed B", "Bed C"
    val monthlyRent: Double,
    val securityDeposit: Double,
    val moveInDate: String,
    val isKycUploaded: Boolean,
    val kycDocType: String, // e.g., "Aadhaar Card", "PAN Card", "None"
    val alternateContact: String = "",
    val dob: String = "",
    val gender: String = "",
    val address: String = "",
    val occupation: String = "",
    val companyOrCollege: String = "",
    val advancePaid: Double = 0.0,
    val leavingDate: String = "",
    val notes: String = "",
    val ownerId: String = "",
    val propertyId: String = "property_default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["ownerId", "propertyId"]),
        Index(value = ["propertyId", "tenantId"]),
        Index(value = ["propertyId", "dueDate"]),
        Index(value = ["cloudId"])
    ]
)
data class RentPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cloudId: String = "",
    val tenantId: Int,
    val tenantName: String,
    val roomNumber: String,
    val billingMonth: String, // e.g., "July 2026"
    val amount: Double, // Expected amount
    val amountPaid: Double = 0.0,
    val dueDate: String, // e.g., "2026-07-16"
    val paymentDate: String?, // e.g., "2026-07-18" if paid
    val paymentMode: String?, // e.g., "UPI", "Cash", "Bank Transfer"
    val transactionReference: String? = null,
    val remarks: String? = null,
    val status: String, // "Paid", "Pending", "Overdue", "Partial", "Advance", "Cancelled"
    val ownerId: String = "",
    val propertyId: String = "property_default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["ownerId", "propertyId"]),
        Index(value = ["propertyId", "date"]),
        Index(value = ["cloudId"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cloudId: String = "",
    val amount: Double,
    val category: String, // e.g., "Plumbing", "Food", "Electricity", "Staff Salary", "Other"
    val date: String, // e.g., "2026-07-18"
    val notes: String,
    val title: String = "",
    val paymentMethod: String = "Cash",
    val vendor: String? = null,
    val ownerId: String = "",
    val propertyId: String = "property_default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(tableName = "owner_profile")
data class OwnerProfileEntity(
    @PrimaryKey val id: Int = 1,
    val pgName: String,
    val ownerName: String,
    val phone: String,
    val upiId: String,
    val pinCode: String,
    val ownerId: String = "",
    val propertyId: String = "property_default",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["propertyId"])
    ]
)
data class SyncOperationEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val entityType: String, // "ROOM", "TENANT", "PAYMENT", "EXPENSE", "PROPERTY", "SETTINGS"
    val entityId: String,   // Local ID / String key e.g. "101", "12", "payment_5"
    val operationType: String, // "CREATE", "UPDATE", "DELETE"
    val payloadJson: String = "",
    val ownerId: String = "",
    val propertyId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastAttemptAt: Long = 0L,
    val error: String? = null,
    val status: String = "PENDING_UPLOAD" // "PENDING_UPLOAD", "SYNCING", "SYNCED", "FAILED", "CONFLICT"
)

@Entity(tableName = "conflict_records")
data class ConflictRecordEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val entityId: String,
    val entityType: String, // "ROOM", "TENANT", "PAYMENT", "EXPENSE", "PROPERTY"
    val propertyId: String = "",
    val deviceA: String = "", // Local device identifier
    val deviceB: String = "", // Remote device identifier
    val localVersion: Int = 1,
    val remoteVersion: Int = 1,
    val localUpdatedAt: Long = 0L,
    val remoteUpdatedAt: Long = 0L,
    val localDataJson: String = "",
    val remoteDataJson: String = "",
    val status: String = "CONFLICT", // "CONFLICT", "RESOLVED_LOCAL", "RESOLVED_REMOTE"
    val resolvedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tenant_registration_forms",
    indices = [
        Index(value = ["propertyId"], unique = true),
        Index(value = ["ownerId"]),
        Index(value = ["formId"])
    ]
)
data class TenantRegistrationFormEntity(
    @PrimaryKey val propertyId: String,
    val cloudId: String = java.util.UUID.randomUUID().toString(),
    val ownerId: String = "",
    val formId: String = "",
    val formTitle: String = "",
    val responderUri: String = "",
    val editUri: String = "",
    val googleAccountEmail: String = "",
    val formVersion: Int = 1,
    val published: Boolean = false,
    val active: Boolean = true,
    val questionMapping: String = "{}",
    val lastCheckedAt: Long = 0L,
    val lastSuccessfulCheckAt: Long = 0L,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(
    tableName = "pending_tenant_registrations",
    indices = [
        Index(value = ["formId", "responseId"], unique = true),
        Index(value = ["propertyId"]),
        Index(value = ["ownerId"]),
        Index(value = ["status"]),
        Index(value = ["phone"]),
        Index(value = ["email"]),
        Index(value = ["cloudId"])
    ]
)
data class PendingTenantRegistrationEntity(
    @PrimaryKey val cloudId: String = java.util.UUID.randomUUID().toString(),
    val ownerId: String = "",
    val propertyId: String = "",
    val formId: String = "",
    val responseId: String = "",
    val submittedAt: Long = System.currentTimeMillis(),
    val formVersion: Int = 1,
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
    val emergencyRelation: String = "",
    val permanentAddress: String = "",
    val currentAddress: String = "",
    val occupation: String = "",
    val organization: String = "",
    val expectedJoiningDate: String = "",
    val notes: String = "",
    val status: String = "PENDING", // PENDING, NEEDS_REVIEW, DUPLICATE, ACCEPTING, ACCEPTED, REJECTED
    val createdTenantCloudId: String = "",
    val reviewedAt: Long = 0L,
    val reviewedBy: String = "",
    val rejectionReason: String = "",
    val duplicateMatchedTenantName: String = "",
    val duplicateMatchedTenantRoom: String = "",
    val duplicateMatchedTenantStatus: String = "",
    val claimedByDeviceId: String = "",
    val claimedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(
    tableName = "tenant_media",
    indices = [
        Index(value = ["tenantCloudId"]),
        Index(value = ["propertyId"]),
        Index(value = ["ownerId"]),
        Index(value = ["mediaType"]),
        Index(value = ["cloudId"], unique = true)
    ]
)
data class TenantMediaEntity(
    @PrimaryKey val cloudId: String = java.util.UUID.randomUUID().toString(),
    val ownerId: String = "",
    val propertyId: String = "",
    val tenantCloudId: String = "",
    val driveFileId: String = "",
    val driveTenantFolderId: String = "",
    val drivePhotosFolderId: String = "",
    val fileName: String = "",
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 0L,
    val mediaType: String = "PROFILE_PHOTO", // PROFILE_PHOTO, ID_PROOF
    val status: String = "ACTIVE", // ACTIVE, DELETED
    val localFilePath: String = "",
    val driveFolderId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val lastSyncedAt: Long = 0L,
    val lastModifiedByDeviceId: String = ""
)

@Entity(
    tableName = "drive_folder_mappings",
    indices = [
        Index(value = ["folderPathKey"], unique = true)
    ]
)
data class DriveFolderMappingEntity(
    @PrimaryKey val folderPathKey: String, // e.g. "ROOT", "PROP_prop123", "PROP_prop123_TENANTS", "TENANT_tenant123"
    val driveFolderId: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface PropertyDao {
    @Query("SELECT * FROM properties WHERE deleted = 0 AND isActive = 1 ORDER BY propertyName ASC")
    fun getActivePropertiesFlow(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE (ownerId = :ownerId OR ownerId = '') AND deleted = 0 AND isActive = 1 ORDER BY propertyName ASC")
    fun getActivePropertiesForOwnerFlow(ownerId: String): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE deleted = 0 ORDER BY propertyName ASC")
    fun getAllPropertiesFlow(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE (ownerId = :ownerId OR ownerId = '') AND deleted = 0 ORDER BY propertyName ASC")
    fun getAllPropertiesForOwnerFlow(ownerId: String): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE (ownerId = :ownerId OR ownerId = '') AND deleted = 0")
    suspend fun getProperties(ownerId: String): List<PropertyEntity>

    @Query("SELECT * FROM properties WHERE deleted = 0")
    suspend fun getAllProperties(): List<PropertyEntity>

    @Query("SELECT * FROM properties ORDER BY propertyName ASC")
    suspend fun getAllPropertiesIncludingDeleted(): List<PropertyEntity>

    @Query("SELECT * FROM properties WHERE propertyId = :propertyId AND deleted = 0 LIMIT 1")
    fun getPropertyFlow(propertyId: String): Flow<PropertyEntity?>

    @Query("SELECT * FROM properties WHERE propertyId = :propertyId LIMIT 1")
    suspend fun getProperty(propertyId: String): PropertyEntity?

    @Query("SELECT * FROM properties WHERE propertyId = :propertyId LIMIT 1")
    suspend fun getPropertyIncludingDeleted(propertyId: String): PropertyEntity?

    @Query("SELECT * FROM properties WHERE propertyId = :propertyId AND deleted = 0 LIMIT 1")
    suspend fun getActiveProperty(propertyId: String): PropertyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProperty(property: PropertyEntity)

    @Update
    suspend fun updateProperty(property: PropertyEntity)

    @Query("UPDATE properties SET isActive = 0, updatedAt = :timestamp, syncStatus = 'PENDING_UPLOAD' WHERE propertyId = :propertyId")
    suspend fun archiveProperty(propertyId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE properties SET deleted = 1, updatedAt = :timestamp, syncStatus = 'PENDING_UPLOAD' WHERE propertyId = :propertyId")
    suspend fun softDeleteProperty(propertyId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM properties WHERE propertyId = :propertyId")
    suspend fun hardDeleteProperty(propertyId: String)

    @Query("DELETE FROM properties")
    suspend fun clearAll()
}

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND deleted = 0 ORDER BY floor ASC, roomNumber ASC")
    fun getRoomsForPropertyFlow(propertyId: String): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND deleted = 0 ORDER BY floor ASC, roomNumber ASC")
    suspend fun getAllRooms(propertyId: String): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE deleted = 0 ORDER BY floor ASC, roomNumber ASC")
    fun getAllRoomsFlow(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE deleted = 0 ORDER BY floor ASC, roomNumber ASC")
    suspend fun getAllRooms(): List<RoomEntity>

    @Query("SELECT * FROM rooms ORDER BY floor ASC, roomNumber ASC")
    suspend fun getAllRoomsIncludingDeleted(): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND deleted = 0 AND roomNumber = :roomNumber LIMIT 1")
    fun getRoomFlow(propertyId: String, roomNumber: String): Flow<RoomEntity?>

    @Query("SELECT * FROM rooms WHERE deleted = 0 AND roomNumber = :roomNumber LIMIT 1")
    fun getRoomFlow(roomNumber: String): Flow<RoomEntity?>

    @Query("SELECT * FROM rooms WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND deleted = 0 AND roomNumber = :roomNumber LIMIT 1")
    suspend fun getRoom(propertyId: String, roomNumber: String): RoomEntity?

    @Query("SELECT * FROM rooms WHERE deleted = 0 AND roomNumber = :roomNumber LIMIT 1")
    suspend fun getRoom(roomNumber: String): RoomEntity?

    @Query("SELECT * FROM rooms WHERE roomNumber = :roomNumber LIMIT 1")
    suspend fun getRoomIncludingDeleted(roomNumber: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomEntity)

    @Update
    suspend fun updateRoom(room: RoomEntity)

    @Query("DELETE FROM rooms WHERE roomNumber = :roomNumber")
    suspend fun hardDeleteRoom(roomNumber: String)

    @Query("DELETE FROM rooms WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND roomNumber = :roomNumber")
    suspend fun hardDeleteRoom(propertyId: String, roomNumber: String)

    @Query("UPDATE rooms SET deleted = 1, syncStatus = 'PENDING_UPLOAD', updatedAt = :timestamp WHERE roomNumber = :roomNumber")
    suspend fun softDeleteRoom(roomNumber: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE rooms SET deleted = 1, syncStatus = 'PENDING_UPLOAD', updatedAt = :timestamp WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND roomNumber = :roomNumber")
    suspend fun softDeleteRoom(propertyId: String, roomNumber: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM rooms")
    suspend fun clearAll()
}

@Dao
interface BedDao {
    @Query("SELECT * FROM beds WHERE propertyId = :propertyId AND roomNumber = :roomNumber AND deleted = 0")
    fun getBedsForRoomFlow(propertyId: String, roomNumber: String): Flow<List<BedEntity>>

    @Query("SELECT * FROM beds WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL))) AND deleted = 0")
    fun getAllBedsForPropertyFlow(propertyId: String): Flow<List<BedEntity>>

    @Query("SELECT * FROM beds WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL))) AND deleted = 0")
    suspend fun getAllBedsForProperty(propertyId: String): List<BedEntity>

    @Query("SELECT * FROM beds WHERE propertyId = :propertyId AND roomNumber = :roomNumber AND deleted = 0")
    suspend fun getBedsForRoom(propertyId: String, roomNumber: String): List<BedEntity>

    @Query("SELECT * FROM beds WHERE propertyId = :propertyId AND roomNumber = :roomNumber AND bedId = :bedId AND deleted = 0 LIMIT 1")
    suspend fun getBed(propertyId: String, roomNumber: String, bedId: String): BedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBed(bed: BedEntity)

    @Update
    suspend fun updateBed(bed: BedEntity)

    @Query("UPDATE beds SET deleted = 1 WHERE propertyId = :propertyId AND roomNumber = :roomNumber AND bedId = :bedId")
    suspend fun deleteBed(propertyId: String, roomNumber: String, bedId: String)

    @Query("DELETE FROM beds")
    suspend fun clearAll()
}

@Dao
interface BedAssignmentDao {
    @Query("SELECT * FROM bed_assignments WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL))) AND deleted = 0")
    fun getAllAssignmentsFlow(propertyId: String): Flow<List<BedAssignmentEntity>>

    @Query("SELECT * FROM bed_assignments WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL))) AND deleted = 0")
    suspend fun getAllAssignments(propertyId: String): List<BedAssignmentEntity>

    @Query("SELECT * FROM bed_assignments WHERE propertyId = :propertyId AND roomNumber = :roomNumber AND deleted = 0")
    fun getAssignmentsForRoomFlow(propertyId: String, roomNumber: String): Flow<List<BedAssignmentEntity>>

    @Query("SELECT * FROM bed_assignments WHERE propertyId = :propertyId AND roomNumber = :roomNumber AND deleted = 0")
    suspend fun getAssignmentsForRoom(propertyId: String, roomNumber: String): List<BedAssignmentEntity>

    @Query("SELECT * FROM bed_assignments WHERE propertyId = :propertyId AND tenantId = :tenantId AND deleted = 0")
    suspend fun getAssignmentsForTenant(propertyId: String, tenantId: Int): List<BedAssignmentEntity>

    @Query("SELECT * FROM bed_assignments WHERE propertyId = :propertyId AND roomNumber = :roomNumber AND bedId = :bedId AND endDate IS NULL AND deleted = 0 LIMIT 1")
    suspend fun getActiveAssignmentForBed(propertyId: String, roomNumber: String, bedId: String): BedAssignmentEntity?

    @Query("SELECT * FROM bed_assignments WHERE propertyId = :propertyId AND tenantId = :tenantId AND endDate IS NULL AND deleted = 0 LIMIT 1")
    suspend fun getActiveAssignmentForTenant(propertyId: String, tenantId: Int): BedAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: BedAssignmentEntity)

    @Update
    suspend fun updateAssignment(assignment: BedAssignmentEntity)

    @Query("DELETE FROM bed_assignments")
    suspend fun clearAll()
}

@Dao
interface TenantDao {
    @Query("SELECT * FROM tenants WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND deleted = 0 ORDER BY name ASC")
    fun getAllTenantsForPropertyFlow(propertyId: String): Flow<List<TenantEntity>>

    @Query("SELECT * FROM tenants WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND deleted = 0 ORDER BY name ASC")
    suspend fun getAllTenants(propertyId: String): List<TenantEntity>

    @Query("SELECT * FROM tenants WHERE deleted = 0 ORDER BY name ASC")
    fun getAllTenantsFlow(): Flow<List<TenantEntity>>

    @Query("SELECT * FROM tenants WHERE deleted = 0 ORDER BY name ASC")
    suspend fun getAllTenants(): List<TenantEntity>

    @Query("SELECT * FROM tenants ORDER BY name ASC")
    suspend fun getAllTenantsIncludingDeleted(): List<TenantEntity>

    @Query("SELECT * FROM tenants WHERE deleted = 0 AND id = :id")
    suspend fun getTenantById(id: Int): TenantEntity?

    @Query("SELECT * FROM tenants WHERE id = :id")
    suspend fun getTenantByIdIncludingDeleted(id: Int): TenantEntity?

    @Query("SELECT * FROM tenants WHERE deleted = 0 AND cloudId = :cloudId LIMIT 1")
    suspend fun getTenantByCloudId(cloudId: String): TenantEntity?

    @Query("SELECT * FROM tenants WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getTenantByCloudIdIncludingDeleted(cloudId: String): TenantEntity?

    @Query("SELECT * FROM tenants WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND deleted = 0 AND roomNumber = :roomNumber")
    fun getTenantsInRoomForPropertyFlow(propertyId: String, roomNumber: String): Flow<List<TenantEntity>>

    @Query("SELECT * FROM tenants WHERE deleted = 0 AND roomNumber = :roomNumber")
    fun getTenantsInRoomFlow(roomNumber: String): Flow<List<TenantEntity>>

    @Query("SELECT * FROM tenants WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND deleted = 0 AND roomNumber = :roomNumber")
    suspend fun getTenantsInRoom(propertyId: String, roomNumber: String): List<TenantEntity>

    @Query("SELECT * FROM tenants WHERE deleted = 0 AND roomNumber = :roomNumber")
    suspend fun getTenantsInRoom(roomNumber: String): List<TenantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTenant(tenant: TenantEntity): Long

    @Update
    suspend fun updateTenant(tenant: TenantEntity)

    @Query("DELETE FROM tenants WHERE id = :id")
    suspend fun hardDeleteTenant(id: Int)

    @Query("UPDATE tenants SET deleted = 1, syncStatus = 'PENDING_UPLOAD', updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteTenant(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM tenants")
    suspend fun clearAll()
}

@Dao
interface RentPaymentDao {
    @Query("SELECT * FROM payments WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL))) AND deleted = 0 ORDER BY dueDate DESC")
    fun getAllPaymentsForPropertyFlow(propertyId: String): Flow<List<RentPaymentEntity>>

    @Query("SELECT * FROM payments WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL))) AND deleted = 0 ORDER BY dueDate DESC")
    suspend fun getAllPaymentsForPropertySync(propertyId: String): List<RentPaymentEntity>

    @Query("SELECT * FROM payments WHERE deleted = 0 ORDER BY dueDate DESC")
    fun getAllPaymentsFlow(): Flow<List<RentPaymentEntity>>

    @Query("SELECT * FROM payments WHERE deleted = 0 ORDER BY dueDate DESC")
    suspend fun getAllPaymentsSync(): List<RentPaymentEntity>

    @Query("SELECT * FROM payments ORDER BY dueDate DESC")
    suspend fun getAllPaymentsIncludingDeleted(): List<RentPaymentEntity>

    @Query("SELECT * FROM payments WHERE deleted = 0 AND tenantId = :tenantId ORDER BY billingMonth DESC")
    fun getPaymentsForTenantFlow(tenantId: Int): Flow<List<RentPaymentEntity>>

    @Query("SELECT * FROM payments WHERE deleted = 0 AND tenantId = :tenantId ORDER BY billingMonth DESC")
    suspend fun getPaymentsForTenantSync(tenantId: Int): List<RentPaymentEntity>

    @Query("SELECT * FROM payments WHERE deleted = 0 AND tenantId = :tenantId AND (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND LOWER(TRIM(billingMonth)) = LOWER(TRIM(:billingMonth)) LIMIT 1")
    suspend fun getPaymentForTenantPropertyAndMonth(tenantId: Int, propertyId: String, billingMonth: String): RentPaymentEntity?

    @Query("SELECT * FROM payments WHERE deleted = 0 AND tenantId = :tenantId AND LOWER(TRIM(billingMonth)) = LOWER(TRIM(:billingMonth)) LIMIT 1")
    suspend fun getPaymentForTenantAndMonth(tenantId: Int, billingMonth: String): RentPaymentEntity?

    @Query("SELECT * FROM payments WHERE deleted = 0 AND (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND LOWER(TRIM(tenantName)) = LOWER(TRIM(:tenantName)) AND LOWER(TRIM(billingMonth)) = LOWER(TRIM(:billingMonth)) LIMIT 1")
    suspend fun getPaymentForTenantNamePropertyAndMonth(tenantName: String, propertyId: String, billingMonth: String): RentPaymentEntity?

    @Query("SELECT * FROM payments WHERE deleted = 0 AND (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND LOWER(TRIM(billingMonth)) = LOWER(TRIM(:billingMonth)) ORDER BY tenantName ASC")
    suspend fun getPaymentsForPropertyAndMonth(propertyId: String, billingMonth: String): List<RentPaymentEntity>

    @Query("SELECT * FROM payments WHERE deleted = 0 AND (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL)) OR (:propertyId = '' AND (propertyId = 'property_default' OR propertyId IS NULL))) AND LOWER(TRIM(billingMonth)) = LOWER(TRIM(:billingMonth)) ORDER BY tenantName ASC")
    fun getPaymentsForPropertyAndMonthFlow(propertyId: String, billingMonth: String): Flow<List<RentPaymentEntity>>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentByIdIncludingDeleted(id: Int): RentPaymentEntity?

    @Query("SELECT * FROM payments WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getPaymentByCloudIdIncludingDeleted(cloudId: String): RentPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: RentPaymentEntity): Long

    @Update
    suspend fun updatePayment(payment: RentPaymentEntity)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun hardDeletePayment(id: Int)

    @Query("UPDATE payments SET deleted = 1, syncStatus = 'PENDING_UPLOAD', updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeletePayment(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE payments SET deleted = 1, syncStatus = 'PENDING_UPLOAD', updatedAt = :timestamp WHERE tenantId = :tenantId")
    suspend fun softDeletePaymentsForTenant(tenantId: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM payments WHERE tenantId = :tenantId")
    suspend fun deletePaymentsForTenant(tenantId: Int)

    @Query("DELETE FROM payments")
    suspend fun clearAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL))) AND deleted = 0 ORDER BY date DESC")
    fun getAllExpensesForPropertyFlow(propertyId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE (propertyId = :propertyId OR (:propertyId = 'property_default' AND (propertyId = '' OR propertyId IS NULL))) AND deleted = 0 ORDER BY date DESC")
    suspend fun getAllExpensesForPropertySync(propertyId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE deleted = 0 ORDER BY date DESC")
    fun getAllExpensesFlow(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE deleted = 0 ORDER BY date DESC")
    suspend fun getAllExpensesSync(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAllExpensesIncludingDeleted(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE deleted = 0 AND id = :id")
    suspend fun getExpenseById(id: Int): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseByIdIncludingDeleted(id: Int): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getExpenseByCloudIdIncludingDeleted(cloudId: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun hardDeleteExpense(id: Int)

    @Query("UPDATE expenses SET deleted = 1, syncStatus = 'PENDING_UPLOAD', updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteExpense(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}

@Dao
interface OwnerProfileDao {
    @Query("SELECT * FROM owner_profile WHERE id = 1 AND deleted = 0 LIMIT 1")
    fun getProfileFlow(): Flow<OwnerProfileEntity?>

    @Query("SELECT * FROM owner_profile WHERE id = 1 AND deleted = 0 LIMIT 1")
    suspend fun getProfile(): OwnerProfileEntity?

    @Query("SELECT * FROM owner_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileIncludingDeleted(): OwnerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: OwnerProfileEntity)

    @Query("DELETE FROM owner_profile")
    suspend fun clearAll()
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING_UPLOAD' OR status = 'FAILED' ORDER BY createdAt ASC")
    suspend fun getPendingOperations(): List<SyncOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(operation: SyncOperationEntity)

    @Update
    suspend fun update(operation: SyncOperationEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteOperation(id: String)

    @Query("DELETE FROM sync_queue WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteByEntity(entityType: String, entityId: String)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING_UPLOAD' OR status = 'FAILED'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'CONFLICT'")
    fun getConflictCountFlow(): Flow<Int>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC")
    fun getAllOperationsFlow(): Flow<List<SyncOperationEntity>>

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()
}

@Dao
interface ConflictRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConflict(record: ConflictRecordEntity)

    @Update
    suspend fun updateConflict(record: ConflictRecordEntity)

    @Query("SELECT * FROM conflict_records WHERE status = 'CONFLICT' ORDER BY createdAt DESC")
    fun getActiveConflictsFlow(): Flow<List<ConflictRecordEntity>>

    @Query("SELECT * FROM conflict_records WHERE status = 'CONFLICT'")
    suspend fun getActiveConflicts(): List<ConflictRecordEntity>

    @Query("SELECT COUNT(*) FROM conflict_records WHERE status = 'CONFLICT'")
    fun getConflictCountFlow(): Flow<Int>

    @Query("SELECT * FROM conflict_records WHERE entityType = :entityType AND entityId = :entityId AND status = 'CONFLICT' LIMIT 1")
    suspend fun getConflictForEntity(entityType: String, entityId: String): ConflictRecordEntity?

    @Query("SELECT * FROM conflict_records WHERE entityType = :entityType AND entityId = :entityId AND status = 'RESOLVED' ORDER BY resolvedAt DESC LIMIT 1")
    suspend fun getResolvedConflictForEntity(entityType: String, entityId: String): ConflictRecordEntity?

    @Query("DELETE FROM conflict_records WHERE id = :id")
    suspend fun deleteConflict(id: String)

    @Query("DELETE FROM conflict_records WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteConflictsForEntity(entityType: String, entityId: String)

    @Query("DELETE FROM conflict_records")
    suspend fun clearAll()
}

@Dao
interface TenantRegistrationFormDao {
    @Query("SELECT * FROM tenant_registration_forms WHERE propertyId = :propertyId AND deleted = 0 LIMIT 1")
    fun getFormForPropertyFlow(propertyId: String): Flow<TenantRegistrationFormEntity?>

    @Query("SELECT * FROM tenant_registration_forms WHERE propertyId = :propertyId AND deleted = 0 LIMIT 1")
    suspend fun getFormForProperty(propertyId: String): TenantRegistrationFormEntity?

    @Query("SELECT * FROM tenant_registration_forms WHERE formId = :formId AND deleted = 0 LIMIT 1")
    suspend fun getFormByFormId(formId: String): TenantRegistrationFormEntity?

    @Query("SELECT * FROM tenant_registration_forms WHERE deleted = 0")
    fun getAllFormsFlow(): Flow<List<TenantRegistrationFormEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateForm(form: TenantRegistrationFormEntity)

    @Query("UPDATE tenant_registration_forms SET active = :active, lastCheckedAt = :lastCheckedAt, lastError = :lastError, updatedAt = :updatedAt WHERE propertyId = :propertyId")
    suspend fun updateFormStatus(propertyId: String, active: Boolean, lastCheckedAt: Long, lastError: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tenant_registration_forms SET deleted = 1, active = 0, updatedAt = :updatedAt WHERE propertyId = :propertyId")
    suspend fun softDeleteForm(propertyId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tenant_registration_forms WHERE propertyId = :propertyId")
    suspend fun deleteForm(propertyId: String)

    @Query("DELETE FROM tenant_registration_forms")
    suspend fun clearAll()
}

@Dao
interface PendingTenantRegistrationDao {
    @Query("SELECT * FROM pending_tenant_registrations WHERE propertyId = :propertyId AND deleted = 0 ORDER BY submittedAt DESC")
    fun getRegistrationsForPropertyFlow(propertyId: String): Flow<List<PendingTenantRegistrationEntity>>

    @Query("SELECT * FROM pending_tenant_registrations WHERE propertyId = :propertyId AND status = :status AND deleted = 0 ORDER BY submittedAt DESC")
    fun getRegistrationsByStatusFlow(propertyId: String, status: String): Flow<List<PendingTenantRegistrationEntity>>

    @Query("SELECT * FROM pending_tenant_registrations WHERE cloudId = :cloudId LIMIT 1")
    fun getRegistrationByCloudIdFlow(cloudId: String): Flow<PendingTenantRegistrationEntity?>

    @Query("SELECT * FROM pending_tenant_registrations WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getRegistrationByCloudId(cloudId: String): PendingTenantRegistrationEntity?

    @Query("SELECT * FROM pending_tenant_registrations WHERE formId = :formId AND responseId = :responseId LIMIT 1")
    suspend fun getRegistrationByResponseId(formId: String, responseId: String): PendingTenantRegistrationEntity?

    @Query("SELECT * FROM pending_tenant_registrations WHERE propertyId = :propertyId AND deleted = 0")
    suspend fun getAllForProperty(propertyId: String): List<PendingTenantRegistrationEntity>

    @Query("SELECT COUNT(*) FROM pending_tenant_registrations WHERE propertyId = :propertyId AND status IN ('PENDING', 'NEEDS_REVIEW', 'DUPLICATE') AND deleted = 0")
    fun getPendingCountFlow(propertyId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRegistration(entity: PendingTenantRegistrationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: PendingTenantRegistrationEntity)

    @Update
    suspend fun update(entity: PendingTenantRegistrationEntity)

    @Query("UPDATE pending_tenant_registrations SET status = :status, reviewedAt = :reviewedAt, reviewedBy = :reviewedBy, rejectionReason = :rejectionReason, createdTenantCloudId = :tenantCloudId, updatedAt = :updatedAt WHERE cloudId = :cloudId")
    suspend fun updateReviewStatus(
        cloudId: String,
        status: String,
        reviewedAt: Long,
        reviewedBy: String,
        rejectionReason: String,
        tenantCloudId: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE pending_tenant_registrations SET status = 'ACCEPTING', claimedByDeviceId = :deviceId, claimedAt = :claimedAt, updatedAt = :claimedAt WHERE cloudId = :cloudId AND status != 'ACCEPTED'")
    suspend fun claimForAcceptance(cloudId: String, deviceId: String, claimedAt: Long): Int

    @Query("UPDATE pending_tenant_registrations SET status = :status, claimedByDeviceId = '', claimedAt = 0, updatedAt = :updatedAt WHERE cloudId = :cloudId")
    suspend fun releaseClaim(cloudId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pending_tenant_registrations SET deleted = 1, updatedAt = :updatedAt WHERE cloudId = :cloudId")
    suspend fun softDelete(cloudId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM pending_tenant_registrations")
    suspend fun clearAll()
}

@Dao
interface TenantMediaDao {
    @Query("SELECT * FROM tenant_media WHERE tenantCloudId = :tenantCloudId AND mediaType = 'PROFILE_PHOTO' AND deleted = 0 AND status = 'ACTIVE' ORDER BY updatedAt DESC LIMIT 1")
    fun getProfilePhotoFlow(tenantCloudId: String): Flow<TenantMediaEntity?>

    @Query("SELECT * FROM tenant_media WHERE tenantCloudId = :tenantCloudId AND mediaType = 'PROFILE_PHOTO' AND deleted = 0 AND status = 'ACTIVE' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getProfilePhoto(tenantCloudId: String): TenantMediaEntity?

    @Query("SELECT * FROM tenant_media WHERE tenantCloudId = :tenantCloudId AND deleted = 0 ORDER BY createdAt DESC")
    fun getAllMediaForTenantFlow(tenantCloudId: String): Flow<List<TenantMediaEntity>>

    @Query("SELECT * FROM tenant_media WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getMediaByCloudId(cloudId: String): TenantMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: TenantMediaEntity)

    @Update
    suspend fun update(entity: TenantMediaEntity)

    @Query("UPDATE tenant_media SET deleted = 1, status = 'DELETED', updatedAt = :updatedAt WHERE tenantCloudId = :tenantCloudId AND mediaType = :mediaType")
    suspend fun softDeleteTenantMediaByType(tenantCloudId: String, mediaType: String = "PROFILE_PHOTO", updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tenant_media SET deleted = 1, status = 'DELETED', updatedAt = :updatedAt WHERE cloudId = :cloudId")
    suspend fun softDelete(cloudId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tenant_media SET localFilePath = :localFilePath, updatedAt = :updatedAt WHERE cloudId = :cloudId")
    suspend fun updateLocalFilePath(cloudId: String, localFilePath: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tenant_media WHERE tenantCloudId = :tenantCloudId AND mediaType = 'PROFILE_PHOTO' AND cloudId != :canonicalCloudId")
    suspend fun deleteNonCanonicalProfilePhotos(tenantCloudId: String, canonicalCloudId: String)

    @Query("DELETE FROM tenant_media")
    suspend fun clearAll()
}

@Dao
interface DriveFolderDao {
    @Query("SELECT * FROM drive_folder_mappings WHERE folderPathKey = :key LIMIT 1")
    suspend fun getFolderMapping(key: String): DriveFolderMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderMapping(entity: DriveFolderMappingEntity)

    @Query("DELETE FROM drive_folder_mappings")
    suspend fun clearAll()
}

// ==========================================
// 3. DATABASE CONTAINER & MIGRATIONS
// ==========================================

private fun addColumnIfNotExists(
    db: SupportSQLiteDatabase,
    tableName: String,
    columnName: String,
    columnDefinition: String
) {
    try {
        val cursor = db.query("PRAGMA table_info($tableName)")
        var exists = false
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIndex != -1 && cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                exists = true
                break
            }
        }
        cursor.close()
        if (!exists) {
            db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDefinition")
        }
    } catch (_: Exception) {}
}

private fun performFullSchemaUpgradeToV8(db: SupportSQLiteDatabase) {
    // 1. Create properties table if not exists
    db.execSQL("""
        CREATE TABLE IF NOT EXISTS properties (
            propertyId TEXT NOT NULL PRIMARY KEY,
            ownerId TEXT NOT NULL,
            propertyName TEXT NOT NULL,
            address TEXT NOT NULL,
            city TEXT NOT NULL,
            state TEXT NOT NULL,
            postalCode TEXT NOT NULL,
            contactNumber TEXT NOT NULL,
            description TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            isActive INTEGER NOT NULL,
            version INTEGER NOT NULL,
            deleted INTEGER NOT NULL,
            syncStatus TEXT NOT NULL,
            lastSyncedAt INTEGER NOT NULL,
            lastModifiedByDeviceId TEXT NOT NULL
        )
    """.trimIndent())

    db.execSQL("CREATE INDEX IF NOT EXISTS index_properties_ownerId ON properties(ownerId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_properties_ownerId_propertyId ON properties(ownerId, propertyId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_properties_ownerId_isActive ON properties(ownerId, isActive)")

    // 2. Insert Default Property
    db.execSQL("""
        INSERT OR IGNORE INTO properties (
            propertyId, ownerId, propertyName, address, city, state, postalCode, contactNumber,
            description, createdAt, updatedAt, isActive, version, deleted, syncStatus, lastSyncedAt, lastModifiedByDeviceId
        ) VALUES (
            'property_default', '', 'Emerald Stays', 'Main Road, Near Tech Park', 'Bangalore', 'Karnataka', '560001',
            '+91 98765 43210', 'Primary PG Facility with modern amenities', 1773792000000, 1773792000000, 1, 1, 0, 'LOCAL_ONLY', 0, ''
        )
    """.trimIndent())

    // 3. Recreate rooms table with composite primary key (propertyId, roomNumber)
    // First ensure old rooms table has columns added safely so SELECT never throws column not found
    addColumnIfNotExists(db, "rooms", "propertyId", "TEXT NOT NULL DEFAULT 'property_default'")
    addColumnIfNotExists(db, "rooms", "lastModifiedByDeviceId", "TEXT NOT NULL DEFAULT ''")

    db.execSQL("DROP TABLE IF EXISTS rooms_temp")
    db.execSQL("""
        CREATE TABLE rooms_temp (
            roomNumber TEXT NOT NULL,
            floor TEXT NOT NULL,
            capacity INTEGER NOT NULL,
            ratePerBed REAL NOT NULL,
            roomType TEXT NOT NULL,
            notes TEXT NOT NULL,
            ownerId TEXT NOT NULL,
            propertyId TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            version INTEGER NOT NULL,
            deleted INTEGER NOT NULL,
            syncStatus TEXT NOT NULL,
            lastSyncedAt INTEGER NOT NULL,
            lastModifiedByDeviceId TEXT NOT NULL,
            PRIMARY KEY(propertyId, roomNumber)
        )
    """.trimIndent())

    try {
        db.execSQL("""
            INSERT OR REPLACE INTO rooms_temp (
                roomNumber, floor, capacity, ratePerBed, roomType, notes,
                ownerId, propertyId, createdAt, updatedAt, version, deleted,
                syncStatus, lastSyncedAt, lastModifiedByDeviceId
            )
            SELECT 
                roomNumber,
                COALESCE(floor, 'Ground'),
                COALESCE(capacity, 1),
                COALESCE(ratePerBed, 0.0),
                COALESCE(roomType, 'AC'),
                COALESCE(notes, ''),
                COALESCE(ownerId, ''),
                COALESCE(propertyId, 'property_default'),
                COALESCE(createdAt, CAST(strftime('%s','now') AS INTEGER) * 1000),
                COALESCE(updatedAt, CAST(strftime('%s','now') AS INTEGER) * 1000),
                COALESCE(version, 1),
                COALESCE(deleted, 0),
                COALESCE(syncStatus, 'LOCAL_ONLY'),
                COALESCE(lastSyncedAt, 0),
                COALESCE(lastModifiedByDeviceId, '')
            FROM rooms
        """.trimIndent())
    } catch (_: Exception) {}

    db.execSQL("DROP TABLE IF EXISTS rooms")
    db.execSQL("ALTER TABLE rooms_temp RENAME TO rooms")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_rooms_ownerId ON rooms(ownerId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_rooms_ownerId_propertyId ON rooms(ownerId, propertyId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_rooms_propertyId_roomNumber ON rooms(propertyId, roomNumber)")

    // 4. Tenants
    addColumnIfNotExists(db, "tenants", "propertyId", "TEXT NOT NULL DEFAULT 'property_default'")
    addColumnIfNotExists(db, "tenants", "lastModifiedByDeviceId", "TEXT NOT NULL DEFAULT ''")
    addColumnIfNotExists(db, "tenants", "cloudId", "TEXT NOT NULL DEFAULT ''")
    db.execSQL("UPDATE tenants SET propertyId = 'property_default' WHERE propertyId IS NULL OR propertyId = ''")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tenants_ownerId ON tenants(ownerId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tenants_ownerId_propertyId ON tenants(ownerId, propertyId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tenants_propertyId_roomNumber ON tenants(propertyId, roomNumber)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tenants_cloudId ON tenants(cloudId)")

    // 5. Payments
    addColumnIfNotExists(db, "payments", "propertyId", "TEXT NOT NULL DEFAULT 'property_default'")
    addColumnIfNotExists(db, "payments", "lastModifiedByDeviceId", "TEXT NOT NULL DEFAULT ''")
    addColumnIfNotExists(db, "payments", "cloudId", "TEXT NOT NULL DEFAULT ''")
    db.execSQL("UPDATE payments SET propertyId = 'property_default' WHERE propertyId IS NULL OR propertyId = ''")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_ownerId ON payments(ownerId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_ownerId_propertyId ON payments(ownerId, propertyId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_propertyId_tenantId ON payments(propertyId, tenantId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_propertyId_dueDate ON payments(propertyId, dueDate)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_cloudId ON payments(cloudId)")

    // 6. Expenses
    addColumnIfNotExists(db, "expenses", "propertyId", "TEXT NOT NULL DEFAULT 'property_default'")
    addColumnIfNotExists(db, "expenses", "lastModifiedByDeviceId", "TEXT NOT NULL DEFAULT ''")
    addColumnIfNotExists(db, "expenses", "cloudId", "TEXT NOT NULL DEFAULT ''")
    db.execSQL("UPDATE expenses SET propertyId = 'property_default' WHERE propertyId IS NULL OR propertyId = ''")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_ownerId ON expenses(ownerId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_ownerId_propertyId ON expenses(ownerId, propertyId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_propertyId_date ON expenses(propertyId, date)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_cloudId ON expenses(cloudId)")

    // 7. Owner Profile
    addColumnIfNotExists(db, "owner_profile", "propertyId", "TEXT NOT NULL DEFAULT 'property_default'")
    addColumnIfNotExists(db, "owner_profile", "lastModifiedByDeviceId", "TEXT NOT NULL DEFAULT ''")
    db.execSQL("UPDATE owner_profile SET propertyId = 'property_default' WHERE propertyId IS NULL OR propertyId = ''")

    // 8. Sync Queue
    addColumnIfNotExists(db, "sync_queue", "ownerId", "TEXT NOT NULL DEFAULT ''")
    addColumnIfNotExists(db, "sync_queue", "propertyId", "TEXT NOT NULL DEFAULT ''")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_status ON sync_queue(status)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_propertyId ON sync_queue(propertyId)")

    // 9. Conflict records
    addColumnIfNotExists(db, "conflict_records", "propertyId", "TEXT NOT NULL DEFAULT ''")
}

val MIGRATION_1_8 = object : Migration(1, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_2_8 = object : Migration(2, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_3_8 = object : Migration(3, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_4_8 = object : Migration(4, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_5_8 = object : Migration(5, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_6_8 = object : Migration(6, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_7_8 = object : Migration(7, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_6_7 = object : Migration(6, 7) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addColumnIfNotExists(db, "tenants", "cloudId", "TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tenants_cloudId ON tenants(cloudId)")

        addColumnIfNotExists(db, "payments", "cloudId", "TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_cloudId ON payments(cloudId)")

        addColumnIfNotExists(db, "expenses", "cloudId", "TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_cloudId ON expenses(cloudId)")
    }
}
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addColumnIfNotExists(db, "tenants", "leavingDate", "TEXT NOT NULL DEFAULT ''")
    }
}
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `tenant_registration_forms` (
                `propertyId` TEXT NOT NULL,
                `cloudId` TEXT NOT NULL,
                `ownerId` TEXT NOT NULL,
                `formId` TEXT NOT NULL,
                `formTitle` TEXT NOT NULL,
                `responderUri` TEXT NOT NULL,
                `editUri` TEXT NOT NULL,
                `googleAccountEmail` TEXT NOT NULL,
                `formVersion` INTEGER NOT NULL,
                `published` INTEGER NOT NULL,
                `active` INTEGER NOT NULL,
                `questionMapping` TEXT NOT NULL,
                `lastCheckedAt` INTEGER NOT NULL,
                `lastSuccessfulCheckAt` INTEGER NOT NULL,
                `lastError` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `lastSyncedAt` INTEGER NOT NULL,
                `lastModifiedByDeviceId` TEXT NOT NULL,
                PRIMARY KEY(`propertyId`)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tenant_registration_forms_propertyId` ON `tenant_registration_forms` (`propertyId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tenant_registration_forms_ownerId` ON `tenant_registration_forms` (`ownerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tenant_registration_forms_formId` ON `tenant_registration_forms` (`formId`)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pending_tenant_registrations` (
                `cloudId` TEXT NOT NULL,
                `ownerId` TEXT NOT NULL,
                `propertyId` TEXT NOT NULL,
                `formId` TEXT NOT NULL,
                `responseId` TEXT NOT NULL,
                `submittedAt` INTEGER NOT NULL,
                `formVersion` INTEGER NOT NULL,
                `fullName` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `emergencyName` TEXT NOT NULL,
                `emergencyPhone` TEXT NOT NULL,
                `emergencyRelation` TEXT NOT NULL,
                `permanentAddress` TEXT NOT NULL,
                `currentAddress` TEXT NOT NULL,
                `occupation` TEXT NOT NULL,
                `organization` TEXT NOT NULL,
                `expectedJoiningDate` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `createdTenantCloudId` TEXT NOT NULL,
                `reviewedAt` INTEGER NOT NULL,
                `reviewedBy` TEXT NOT NULL,
                `rejectionReason` TEXT NOT NULL,
                `duplicateMatchedTenantName` TEXT NOT NULL,
                `duplicateMatchedTenantRoom` TEXT NOT NULL,
                `duplicateMatchedTenantStatus` TEXT NOT NULL,
                `claimedByDeviceId` TEXT NOT NULL,
                `claimedAt` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `lastSyncedAt` INTEGER NOT NULL,
                `lastModifiedByDeviceId` TEXT NOT NULL,
                PRIMARY KEY(`cloudId`)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_tenant_registrations_formId_responseId` ON `pending_tenant_registrations` (`formId`, `responseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_tenant_registrations_propertyId` ON `pending_tenant_registrations` (`propertyId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_tenant_registrations_ownerId` ON `pending_tenant_registrations` (`ownerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_tenant_registrations_status` ON `pending_tenant_registrations` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_tenant_registrations_phone` ON `pending_tenant_registrations` (`phone`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_tenant_registrations_email` ON `pending_tenant_registrations` (`email`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_tenant_registrations_cloudId` ON `pending_tenant_registrations` (`cloudId`)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `tenant_media` (
                `cloudId` TEXT NOT NULL,
                `ownerId` TEXT NOT NULL,
                `propertyId` TEXT NOT NULL,
                `tenantCloudId` TEXT NOT NULL,
                `driveFileId` TEXT NOT NULL,
                `fileName` TEXT NOT NULL,
                `mimeType` TEXT NOT NULL,
                `sizeBytes` INTEGER NOT NULL,
                `mediaType` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `localFilePath` TEXT NOT NULL,
                `driveFolderId` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `lastSyncedAt` INTEGER NOT NULL,
                `lastModifiedByDeviceId` TEXT NOT NULL,
                PRIMARY KEY(`cloudId`)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tenant_media_cloudId` ON `tenant_media` (`cloudId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tenant_media_tenantCloudId` ON `tenant_media` (`tenantCloudId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tenant_media_propertyId` ON `tenant_media` (`propertyId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tenant_media_ownerId` ON `tenant_media` (`ownerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tenant_media_mediaType` ON `tenant_media` (`mediaType`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `drive_folder_mappings` (
                `folderPathKey` TEXT NOT NULL,
                `driveFolderId` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`folderPathKey`)
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_folder_mappings_folderPathKey` ON `drive_folder_mappings` (`folderPathKey`)")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addColumnIfNotExists(db, "tenant_media", "driveTenantFolderId", "TEXT NOT NULL DEFAULT ''")
        addColumnIfNotExists(db, "tenant_media", "drivePhotosFolderId", "TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addColumnIfNotExists(db, "rooms", "isActive", "INTEGER NOT NULL DEFAULT 1")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS beds (
                roomNumber TEXT NOT NULL,
                bedId TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'AVAILABLE',
                notes TEXT NOT NULL DEFAULT '',
                ownerId TEXT NOT NULL DEFAULT '',
                propertyId TEXT NOT NULL DEFAULT 'property_default',
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                version INTEGER NOT NULL DEFAULT 1,
                deleted INTEGER NOT NULL DEFAULT 0,
                syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                lastModifiedByDeviceId TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(propertyId, roomNumber, bedId)
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bed_assignments (
                assignmentId TEXT NOT NULL PRIMARY KEY,
                tenantId INTEGER NOT NULL,
                roomNumber TEXT NOT NULL,
                bedId TEXT NOT NULL,
                startDate TEXT NOT NULL,
                endDate TEXT,
                agreedRent REAL NOT NULL,
                ownerId TEXT NOT NULL DEFAULT '',
                propertyId TEXT NOT NULL DEFAULT 'property_default',
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0,
                version INTEGER NOT NULL DEFAULT 1,
                deleted INTEGER NOT NULL DEFAULT 0,
                syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                lastModifiedByDeviceId TEXT NOT NULL DEFAULT ''
            )
        """)
        val cursor = db.query("SELECT propertyId, roomNumber, capacity FROM rooms WHERE deleted = 0")
        while (cursor.moveToNext()) {
            val propertyId = cursor.getString(0) ?: "property_default"
            val roomNumber = cursor.getString(1)
            val capacity = cursor.getInt(2)
            for (i in 1..capacity) {
                val bedId = "Bed $i"
                db.execSQL("""
                    INSERT OR IGNORE INTO beds (roomNumber, bedId, status, propertyId)
                    VALUES ('$roomNumber', '$bedId', 'AVAILABLE', '$propertyId')
                """)
            }
        }
        cursor.close()

        val tenantCursor = db.query("SELECT id, propertyId, roomNumber, bedId, monthlyRent, moveInDate FROM tenants WHERE deleted = 0 AND roomNumber IS NOT NULL AND roomNumber != ''")
        while (tenantCursor.moveToNext()) {
            val tenantId = tenantCursor.getInt(0)
            val propertyId = tenantCursor.getString(1) ?: "property_default"
            val roomNumber = tenantCursor.getString(2)
            val bedId = tenantCursor.getString(3).ifBlank { "Bed 1" }
            val agreedRent = tenantCursor.getDouble(4)
            val moveInDate = tenantCursor.getString(5).ifBlank { "2026-01-01" }
            val assignmentId = java.util.UUID.randomUUID().toString()
            db.execSQL("""
                INSERT OR IGNORE INTO bed_assignments (assignmentId, tenantId, roomNumber, bedId, startDate, endDate, agreedRent, propertyId)
                VALUES ('$assignmentId', $tenantId, '$roomNumber', '$bedId', '$moveInDate', NULL, $agreedRent, '$propertyId')
            """)
            db.execSQL("""
                UPDATE beds SET status = 'OCCUPIED' WHERE propertyId = '$propertyId' AND roomNumber = '$roomNumber' AND bedId = '$bedId'
            """)
        }
        tenantCursor.close()
    }
}

@Database(
    entities = [
        PropertyEntity::class,
        RoomEntity::class,
        TenantEntity::class,
        RentPaymentEntity::class,
        ExpenseEntity::class,
        OwnerProfileEntity::class,
        SyncOperationEntity::class,
        ConflictRecordEntity::class,
        TenantRegistrationFormEntity::class,
        PendingTenantRegistrationEntity::class,
        TenantMediaEntity::class,
        DriveFolderMappingEntity::class,
        BedEntity::class,
        BedAssignmentEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun propertyDao(): PropertyDao
    abstract fun roomDao(): RoomDao
    abstract fun tenantDao(): TenantDao
    abstract fun rentPaymentDao(): RentPaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun ownerProfileDao(): OwnerProfileDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun conflictRecordDao(): ConflictRecordDao
    abstract fun tenantRegistrationFormDao(): TenantRegistrationFormDao
    abstract fun pendingTenantRegistrationDao(): PendingTenantRegistrationDao
    abstract fun tenantMediaDao(): TenantMediaDao
    abstract fun driveFolderDao(): DriveFolderDao
    abstract fun bedDao(): BedDao
    abstract fun bedAssignmentDao(): BedAssignmentDao

    suspend fun clearAllUserData() {
        propertyDao().clearAll()
        roomDao().clearAll()
        tenantDao().clearAll()
        rentPaymentDao().clearAll()
        expenseDao().clearAll()
        ownerProfileDao().clearAll()
        syncQueueDao().clearAll()
        conflictRecordDao().clearAll()
        tenantRegistrationFormDao().clearAll()
        pendingTenantRegistrationDao().clearAll()
        tenantMediaDao().clearAll()
        driveFolderDao().clearAll()
        bedDao().clearAll()
        bedAssignmentDao().clearAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pg_manager_database"
                )
                .addMigrations(
                    MIGRATION_1_8,
                    MIGRATION_2_8,
                    MIGRATION_3_8,
                    MIGRATION_4_8,
                    MIGRATION_5_8,
                    MIGRATION_6_8,
                    MIGRATION_7_8,
                    MIGRATION_6_7,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(DatabaseCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    prepopulateDatabase(database)
                }
            }
        }

        private suspend fun prepopulateDatabase(db: AppDatabase) {
            // 1. Insert Default Property
            db.propertyDao().insertProperty(
                PropertyEntity(
                    propertyId = "property_default",
                    ownerId = "",
                    propertyName = "Emerald Stays",
                    address = "Main Road, Near Tech Park",
                    city = "Bangalore",
                    state = "Karnataka",
                    postalCode = "560001",
                    contactNumber = "+91 98765 43210",
                    description = "Primary PG Facility with modern amenities",
                    isActive = true
                )
            )

            // 2. Insert Default Owner Profile
            db.ownerProfileDao().insertProfile(
                OwnerProfileEntity(
                    id = 1,
                    pgName = "Emerald Stays",
                    ownerName = "Nithish Prasad",
                    phone = "+91 98765 43210",
                    upiId = "nithish@okaxis",
                    pinCode = "1234",
                    propertyId = "property_default"
                )
            )

            // 3. Insert Base Rooms
            val rooms = listOf(
                RoomEntity("101", "1st Floor", 2, 12000.0, propertyId = "property_default"),
                RoomEntity("102", "1st Floor", 1, 12500.0, propertyId = "property_default"),
                RoomEntity("201", "2nd Floor", 3, 10000.0, propertyId = "property_default"),
                RoomEntity("204", "2nd Floor", 2, 14000.0, propertyId = "property_default")
            )
            for (room in rooms) {
                db.roomDao().insertRoom(room)
            }

            // 4. Insert Base Tenants
            val arjunId = db.tenantDao().insertTenant(
                TenantEntity(
                    id = 0,
                    name = "Arjun Kapoor",
                    phone = "9876543210",
                    email = "arjun@gmail.com",
                    emergencyContact = "9876543211",
                    roomNumber = "204",
                    bedId = "Bed A",
                    monthlyRent = 14000.0,
                    securityDeposit = 15000.0,
                    moveInDate = "2026-06-01",
                    isKycUploaded = false,
                    kycDocType = "None",
                    propertyId = "property_default"
                )
            ).toInt()

            val priyaId = db.tenantDao().insertTenant(
                TenantEntity(
                    id = 0,
                    name = "Priya Singh",
                    phone = "9812345678",
                    email = "priya@gmail.com",
                    emergencyContact = "9812345679",
                    roomNumber = "102",
                    bedId = "Bed A",
                    monthlyRent = 12500.0,
                    securityDeposit = 12500.0,
                    moveInDate = "2026-05-15",
                    isKycUploaded = true,
                    kycDocType = "Aadhaar Card",
                    propertyId = "property_default"
                )
            ).toInt()

            // 5. Insert Rent Payments for July 2026
            db.rentPaymentDao().insertPayment(
                RentPaymentEntity(
                    id = 0,
                    tenantId = arjunId,
                    tenantName = "Arjun Kapoor",
                    roomNumber = "204",
                    billingMonth = "July 2026",
                    amount = 14000.0,
                    dueDate = "2026-07-16",
                    paymentDate = null,
                    paymentMode = null,
                    status = "Overdue",
                    propertyId = "property_default"
                )
            )

            db.rentPaymentDao().insertPayment(
                RentPaymentEntity(
                    id = 0,
                    tenantId = priyaId,
                    tenantName = "Priya Singh",
                    roomNumber = "102",
                    billingMonth = "July 2026",
                    amount = 12500.0,
                    dueDate = "2026-07-18",
                    paymentDate = null,
                    paymentMode = null,
                    status = "Pending",
                    propertyId = "property_default"
                )
            )

            // 6. Insert Base Expenses
            db.expenseDao().insertExpense(
                ExpenseEntity(
                    id = 0,
                    amount = 1200.0,
                    category = "Plumbing",
                    date = "2026-07-14",
                    notes = "Fixed leakage in Room 201 bathroom",
                    propertyId = "property_default"
                )
            )

            db.expenseDao().insertExpense(
                ExpenseEntity(
                    id = 0,
                    amount = 13000.0,
                    category = "Food",
                    date = "2026-07-15",
                    notes = "Catering provisions for first half of July",
                    propertyId = "property_default"
                )
            )
        }
    }
}

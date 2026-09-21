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
        Index(value = ["propertyId", "roomNumber"])
    ]
)
data class TenantEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
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
        Index(value = ["propertyId", "dueDate"])
    ]
)
data class RentPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
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
        Index(value = ["propertyId", "date"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
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

// ==========================================
// 2. DATA ACCESS OBJECTS (DAOs)
// ==========================================

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

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentByIdIncludingDeleted(id: Int): RentPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: RentPaymentEntity)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

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

// ==========================================
// 3. DATABASE CONTAINER & MIGRATIONS
// ==========================================

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
    // First ensure old rooms table has columns added so SELECT never throws column not found
    try {
        db.execSQL("ALTER TABLE rooms ADD COLUMN propertyId TEXT NOT NULL DEFAULT 'property_default'")
    } catch (_: Exception) {}
    try {
        db.execSQL("ALTER TABLE rooms ADD COLUMN lastModifiedByDeviceId TEXT NOT NULL DEFAULT ''")
    } catch (_: Exception) {}

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
    try {
        db.execSQL("ALTER TABLE tenants ADD COLUMN propertyId TEXT NOT NULL DEFAULT 'property_default'")
    } catch (_: Exception) {}
    try {
        db.execSQL("ALTER TABLE tenants ADD COLUMN lastModifiedByDeviceId TEXT NOT NULL DEFAULT ''")
    } catch (_: Exception) {}
    db.execSQL("UPDATE tenants SET propertyId = 'property_default' WHERE propertyId IS NULL OR propertyId = ''")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tenants_ownerId ON tenants(ownerId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tenants_ownerId_propertyId ON tenants(ownerId, propertyId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_tenants_propertyId_roomNumber ON tenants(propertyId, roomNumber)")

    // 5. Payments
    try {
        db.execSQL("ALTER TABLE payments ADD COLUMN propertyId TEXT NOT NULL DEFAULT 'property_default'")
    } catch (_: Exception) {}
    try {
        db.execSQL("ALTER TABLE payments ADD COLUMN lastModifiedByDeviceId TEXT NOT NULL DEFAULT ''")
    } catch (_: Exception) {}
    db.execSQL("UPDATE payments SET propertyId = 'property_default' WHERE propertyId IS NULL OR propertyId = ''")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_ownerId ON payments(ownerId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_ownerId_propertyId ON payments(ownerId, propertyId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_propertyId_tenantId ON payments(propertyId, tenantId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_propertyId_dueDate ON payments(propertyId, dueDate)")

    // 6. Expenses
    try {
        db.execSQL("ALTER TABLE expenses ADD COLUMN propertyId TEXT NOT NULL DEFAULT 'property_default'")
    } catch (_: Exception) {}
    try {
        db.execSQL("ALTER TABLE expenses ADD COLUMN lastModifiedByDeviceId TEXT NOT NULL DEFAULT ''")
    } catch (_: Exception) {}
    db.execSQL("UPDATE expenses SET propertyId = 'property_default' WHERE propertyId IS NULL OR propertyId = ''")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_ownerId ON expenses(ownerId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_ownerId_propertyId ON expenses(ownerId, propertyId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_propertyId_date ON expenses(propertyId, date)")

    // 7. Owner Profile
    try {
        db.execSQL("ALTER TABLE owner_profile ADD COLUMN propertyId TEXT NOT NULL DEFAULT 'property_default'")
    } catch (_: Exception) {}
    try {
        db.execSQL("ALTER TABLE owner_profile ADD COLUMN lastModifiedByDeviceId TEXT NOT NULL DEFAULT ''")
    } catch (_: Exception) {}
    db.execSQL("UPDATE owner_profile SET propertyId = 'property_default' WHERE propertyId IS NULL OR propertyId = ''")

    // 8. Sync Queue
    try {
        db.execSQL("ALTER TABLE sync_queue ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
    } catch (_: Exception) {}
    try {
        db.execSQL("ALTER TABLE sync_queue ADD COLUMN propertyId TEXT NOT NULL DEFAULT ''")
    } catch (_: Exception) {}
    db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_status ON sync_queue(status)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_propertyId ON sync_queue(propertyId)")

    // 9. Conflict records
    try {
        db.execSQL("ALTER TABLE conflict_records ADD COLUMN propertyId TEXT NOT NULL DEFAULT ''")
    } catch (_: Exception) {}
}

val MIGRATION_1_8 = object : Migration(1, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_2_8 = object : Migration(2, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_3_8 = object : Migration(3, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_4_8 = object : Migration(4, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_5_8 = object : Migration(5, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_6_8 = object : Migration(6, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_7_8 = object : Migration(7, 8) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }
val MIGRATION_6_7 = object : Migration(6, 7) { override fun migrate(db: SupportSQLiteDatabase) { performFullSchemaUpgradeToV8(db) } }

@Database(
    entities = [
        PropertyEntity::class,
        RoomEntity::class,
        TenantEntity::class,
        RentPaymentEntity::class,
        ExpenseEntity::class,
        OwnerProfileEntity::class,
        SyncOperationEntity::class,
        ConflictRecordEntity::class
    ],
    version = 8,
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

    suspend fun clearAllUserData() {
        propertyDao().clearAll()
        roomDao().clearAll()
        tenantDao().clearAll()
        rentPaymentDao().clearAll()
        expenseDao().clearAll()
        ownerProfileDao().clearAll()
        syncQueueDao().clearAll()
        conflictRecordDao().clearAll()
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
                    MIGRATION_6_7
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

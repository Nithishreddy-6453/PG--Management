package com.example.data.firestore.mapper

import com.example.data.database.ExpenseEntity
import com.example.data.database.OwnerProfileEntity
import com.example.data.database.PropertyEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.data.firestore.model.ExpenseDto
import com.example.data.firestore.model.PaymentDto
import com.example.data.firestore.model.PropertyDto
import com.example.data.firestore.model.RoomDto
import com.example.data.firestore.model.SettingsDto
import com.example.data.firestore.model.TenantDto
import com.example.features.settings.domain.model.BusinessSettings

/**
 * Enterprise Mapper pattern converting Room Entities and Domain Models
 * to/from Firestore DTOs.
 */

// ==========================================
// PROPERTY MAPPERS
// ==========================================
fun PropertyEntity.toDto(ownerId: String, deviceId: String = ""): PropertyDto {
    return PropertyDto(
        id = propertyId,
        ownerId = ownerId,
        pgName = propertyName,
        propertyName = propertyName,
        address = address,
        city = city,
        state = state,
        postalCode = postalCode,
        contactNumber = contactNumber,
        description = description,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        deleted = deleted,
        syncStatus = "SYNCED",
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = deviceId
    )
}

fun PropertyDto.toEntity(): PropertyEntity {
    return PropertyEntity(
        propertyId = id.ifBlank { "prop_${java.util.UUID.randomUUID()}" },
        ownerId = ownerId,
        propertyName = if (propertyName.isNotBlank()) propertyName else if (pgName.isNotBlank()) pgName else "Emerald Stays",
        address = address,
        city = city,
        state = state,
        postalCode = postalCode,
        contactNumber = if (contactNumber.isNotBlank()) contactNumber else phone,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isActive = isActive,
        version = version,
        deleted = deleted,
        syncStatus = "LOCAL_ONLY",
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
}

// ==========================================
// ROOM MAPPERS
// ==========================================
fun RoomEntity.toDto(ownerId: String, deviceId: String = ""): RoomDto {
    return RoomDto(
        id = "${propertyId}_$roomNumber",
        ownerId = ownerId,
        roomNumber = roomNumber,
        floor = floor,
        capacity = capacity,
        ratePerBed = ratePerBed,
        roomType = roomType,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        deleted = deleted,
        syncStatus = "SYNCED",
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = deviceId
    )
}

fun RoomDto.toEntity(): RoomEntity {
    return RoomEntity(
        roomNumber = roomNumber,
        floor = floor,
        capacity = capacity,
        ratePerBed = ratePerBed,
        roomType = roomType,
        notes = notes,
        ownerId = ownerId,
        propertyId = if (id.contains("_")) id.substringBefore("_") else "property_default",
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        deleted = deleted,
        syncStatus = "LOCAL_ONLY",
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
}

// ==========================================
// TENANT MAPPERS
// ==========================================
fun TenantEntity.toDto(ownerId: String, deviceId: String = ""): TenantDto {
    val docId = if (id > 0) "tenant_$id" else "tenant_${System.currentTimeMillis()}"
    return TenantDto(
        id = docId,
        ownerId = ownerId,
        localId = id,
        name = name,
        phone = phone,
        email = email,
        emergencyContact = emergencyContact,
        roomNumber = roomNumber,
        bedId = bedId,
        monthlyRent = monthlyRent,
        securityDeposit = securityDeposit,
        moveInDate = moveInDate,
        isKycUploaded = isKycUploaded,
        kycDocType = kycDocType,
        alternateContact = alternateContact,
        dob = dob,
        gender = gender,
        address = address,
        occupation = occupation,
        companyOrCollege = companyOrCollege,
        advancePaid = advancePaid,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        deleted = deleted,
        syncStatus = "SYNCED",
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = deviceId
    )
}

fun TenantDto.toEntity(): TenantEntity {
    return TenantEntity(
        id = localId,
        name = name,
        phone = phone,
        email = email,
        emergencyContact = emergencyContact,
        roomNumber = roomNumber,
        bedId = bedId,
        monthlyRent = monthlyRent,
        securityDeposit = securityDeposit,
        moveInDate = moveInDate,
        isKycUploaded = isKycUploaded,
        kycDocType = kycDocType,
        alternateContact = alternateContact,
        dob = dob,
        gender = gender,
        address = address,
        occupation = occupation,
        companyOrCollege = companyOrCollege,
        advancePaid = advancePaid,
        notes = notes,
        ownerId = ownerId,
        propertyId = "property_default",
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        deleted = deleted,
        syncStatus = "LOCAL_ONLY",
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
}

// ==========================================
// RENT PAYMENT MAPPERS
// ==========================================
fun RentPaymentEntity.toDto(ownerId: String, deviceId: String = ""): PaymentDto {
    val docId = if (id > 0) "payment_$id" else "payment_${System.currentTimeMillis()}"
    return PaymentDto(
        id = docId,
        ownerId = ownerId,
        localId = id,
        tenantId = tenantId,
        tenantName = tenantName,
        roomNumber = roomNumber,
        billingMonth = billingMonth,
        amount = amount,
        amountPaid = amountPaid,
        dueDate = dueDate,
        paymentDate = paymentDate,
        paymentMode = paymentMode,
        transactionReference = transactionReference,
        remarks = remarks,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        deleted = deleted,
        syncStatus = "SYNCED",
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = deviceId
    )
}

fun PaymentDto.toEntity(): RentPaymentEntity {
    return RentPaymentEntity(
        id = localId,
        tenantId = tenantId,
        tenantName = tenantName,
        roomNumber = roomNumber,
        billingMonth = billingMonth,
        amount = amount,
        amountPaid = amountPaid,
        dueDate = dueDate,
        paymentDate = paymentDate,
        paymentMode = paymentMode,
        transactionReference = transactionReference,
        remarks = remarks,
        status = status,
        ownerId = ownerId,
        propertyId = "property_default",
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        deleted = deleted,
        syncStatus = "LOCAL_ONLY",
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
}

// ==========================================
// EXPENSE MAPPERS
// ==========================================
fun ExpenseEntity.toDto(ownerId: String, deviceId: String = ""): ExpenseDto {
    val docId = if (id > 0) "expense_$id" else "expense_${System.currentTimeMillis()}"
    return ExpenseDto(
        id = docId,
        ownerId = ownerId,
        localId = id,
        amount = amount,
        category = category,
        date = date,
        notes = notes,
        title = title,
        paymentMethod = paymentMethod,
        vendor = vendor,
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        deleted = deleted,
        syncStatus = "SYNCED",
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = deviceId
    )
}

fun ExpenseDto.toEntity(): ExpenseEntity {
    return ExpenseEntity(
        id = localId,
        amount = amount,
        category = category,
        date = date,
        notes = notes,
        title = title,
        paymentMethod = paymentMethod,
        vendor = vendor,
        ownerId = ownerId,
        propertyId = "property_default",
        createdAt = createdAt,
        updatedAt = updatedAt,
        version = version,
        deleted = deleted,
        syncStatus = "LOCAL_ONLY",
        lastSyncedAt = lastSyncedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
}

// ==========================================
// OWNER PROFILE / PROPERTY MAPPERS
// ==========================================
fun OwnerProfileEntity.toPropertyDto(ownerId: String): PropertyDto {
    return PropertyDto(
        id = "property_main",
        ownerId = ownerId,
        pgName = pgName,
        ownerName = ownerName,
        phone = phone,
        upiId = upiId,
        address = "",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}

fun PropertyDto.toOwnerProfileEntity(): OwnerProfileEntity {
    return OwnerProfileEntity(
        id = 1,
        pgName = pgName,
        ownerName = ownerName,
        phone = phone,
        upiId = upiId,
        pinCode = ""
    )
}

// ==========================================
// SETTINGS MAPPERS
// ==========================================
fun BusinessSettings.toSettingsDto(ownerId: String): SettingsDto {
    return SettingsDto(
        id = "settings_main",
        ownerId = ownerId,
        pgName = pgName,
        ownerName = ownerName,
        contactNumber = contactNumber,
        emailAddress = emailAddress,
        address = address,
        defaultMonthlyRent = defaultMonthlyRent,
        defaultSecurityDeposit = defaultSecurityDeposit,
        defaultRentDueDay = defaultRentDueDay,
        currency = currency,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}

fun SettingsDto.toBusinessSettings(): BusinessSettings {
    return BusinessSettings(
        pgName = pgName,
        ownerName = ownerName,
        contactNumber = contactNumber,
        emailAddress = emailAddress,
        address = address,
        defaultMonthlyRent = defaultMonthlyRent,
        defaultSecurityDeposit = defaultSecurityDeposit,
        defaultRentDueDay = defaultRentDueDay,
        currency = currency
    )
}

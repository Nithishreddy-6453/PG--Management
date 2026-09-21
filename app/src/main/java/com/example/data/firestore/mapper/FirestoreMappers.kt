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
    val propId = if (propertyId.isNotBlank()) propertyId else "property_default"
    return RoomDto(
        id = "${propId}_$roomNumber",
        ownerId = ownerId,
        propertyId = propId,
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
    val propId = if (propertyId.isNotBlank() && propertyId != "property_default") {
        propertyId
    } else if (id.contains("_")) {
        id.substringBefore("_")
    } else {
        "property_default"
    }
    return RoomEntity(
        roomNumber = roomNumber,
        floor = floor,
        capacity = capacity,
        ratePerBed = ratePerBed,
        roomType = roomType,
        notes = notes,
        ownerId = ownerId,
        propertyId = propId,
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
    val docId = when {
        cloudId.isNotBlank() -> cloudId
        id > 0 -> "tenant_$id"
        else -> "tenant_${System.currentTimeMillis()}"
    }
    val propId = if (propertyId.isNotBlank()) propertyId else "property_default"
    return TenantDto(
        id = docId,
        cloudId = docId,
        ownerId = ownerId,
        propertyId = propId,
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
    val cId = when {
        cloudId.isNotBlank() -> cloudId
        id.isNotBlank() -> id
        localId > 0 -> "tenant_$localId"
        else -> ""
    }
    val propId = if (propertyId.isNotBlank()) propertyId else "property_default"
    return TenantEntity(
        id = localId,
        cloudId = cId,
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
        propertyId = propId,
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
    val docId = when {
        cloudId.isNotBlank() -> cloudId
        id > 0 -> "payment_$id"
        else -> "payment_${System.currentTimeMillis()}"
    }
    val propId = if (propertyId.isNotBlank()) propertyId else "property_default"
    return PaymentDto(
        id = docId,
        cloudId = docId,
        ownerId = ownerId,
        propertyId = propId,
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
    val cId = when {
        cloudId.isNotBlank() -> cloudId
        id.isNotBlank() -> id
        localId > 0 -> "payment_$localId"
        else -> ""
    }
    val propId = if (propertyId.isNotBlank()) propertyId else "property_default"
    return RentPaymentEntity(
        id = localId,
        cloudId = cId,
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
        propertyId = propId,
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
    val docId = when {
        cloudId.isNotBlank() -> cloudId
        id > 0 -> "expense_$id"
        else -> "expense_${System.currentTimeMillis()}"
    }
    val propId = if (propertyId.isNotBlank()) propertyId else "property_default"
    return ExpenseDto(
        id = docId,
        cloudId = docId,
        ownerId = ownerId,
        propertyId = propId,
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
    val cId = when {
        cloudId.isNotBlank() -> cloudId
        id.isNotBlank() -> id
        localId > 0 -> "expense_$localId"
        else -> ""
    }
    val propId = if (propertyId.isNotBlank()) propertyId else "property_default"
    return ExpenseEntity(
        id = localId,
        cloudId = cId,
        amount = amount,
        category = category,
        date = date,
        notes = notes,
        title = title,
        paymentMethod = paymentMethod,
        vendor = vendor,
        ownerId = ownerId,
        propertyId = propId,
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

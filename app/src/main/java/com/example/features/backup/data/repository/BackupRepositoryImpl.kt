package com.example.features.backup.data.repository

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.database.ExpenseEntity
import com.example.data.database.OwnerProfileEntity
import com.example.data.database.RentPaymentEntity
import com.example.data.database.RoomEntity
import com.example.data.database.TenantEntity
import com.example.features.backup.domain.model.*
import com.example.features.backup.domain.repository.BackupRepository
import com.example.features.settings.domain.model.AppSettings
import com.example.features.settings.domain.model.AppTheme
import com.example.features.settings.domain.model.BusinessSettings
import com.example.features.settings.domain.model.NotificationSettings
import com.example.features.settings.domain.model.SecuritySettings
import com.example.features.settings.domain.repository.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository
) : BackupRepository {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(BackupData::class.java).indent("  ")

    override suspend fun createBackup(outputStream: OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val appSettings = settingsRepository.getAppSettings().first()
            val businessSettings = settingsRepository.getBusinessSettings().first()
            val securitySettings = settingsRepository.getSecuritySettings().first()
            val notificationSettings = settingsRepository.getNotificationSettings().first()

            val profileEntity = db.ownerProfileDao().getProfile()
            val profileDto = profileEntity?.let {
                OwnerProfileDto(
                    pgName = it.pgName,
                    ownerName = it.ownerName,
                    phone = it.phone,
                    upiId = it.upiId,
                    pinCode = it.pinCode
                )
            }

            val rooms = db.roomDao().getAllRooms().map {
                RoomDto(
                    roomNumber = it.roomNumber,
                    floor = it.floor,
                    capacity = it.capacity,
                    ratePerBed = it.ratePerBed,
                    roomType = it.roomType,
                    notes = it.notes
                )
            }

            val tenants = db.tenantDao().getAllTenants().map {
                TenantDto(
                    id = it.id,
                    name = it.name,
                    phone = it.phone,
                    email = it.email,
                    emergencyContact = it.emergencyContact,
                    roomNumber = it.roomNumber,
                    bedId = it.bedId,
                    monthlyRent = it.monthlyRent,
                    securityDeposit = it.securityDeposit,
                    moveInDate = it.moveInDate,
                    isKycUploaded = it.isKycUploaded,
                    kycDocType = it.kycDocType,
                    alternateContact = it.alternateContact,
                    dob = it.dob,
                    gender = it.gender,
                    address = it.address,
                    occupation = it.occupation,
                    companyOrCollege = it.companyOrCollege,
                    advancePaid = it.advancePaid,
                    notes = it.notes,
                )
            }

            val payments = db.rentPaymentDao().getAllPaymentsSync().map {
                RentPaymentDto(
                    id = it.id,
                    tenantId = it.tenantId,
                    tenantName = it.tenantName,
                    roomNumber = it.roomNumber,
                    billingMonth = it.billingMonth,
                    amount = it.amount,
                    amountPaid = it.amountPaid,
                    dueDate = it.dueDate,
                    paymentDate = it.paymentDate,
                    paymentMode = it.paymentMode,
                    transactionReference = it.transactionReference,
                    remarks = it.remarks,
                    status = it.status
                )
            }

            val expenses = db.expenseDao().getAllExpensesFlow().first().map {
                ExpenseDto(
                    id = it.id,
                    amount = it.amount,
                    category = it.category,
                    date = it.date,
                    notes = it.notes,
                    title = it.title,
                    paymentMethod = it.paymentMethod,
                    vendor = it.vendor
                )
            }

            val backupData = BackupData(
                backupVersion = 1,
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()),
                applicationVersion = "1.0.0",
                databaseVersion = 4,
                businessSettings = BusinessSettingsDto(
                    pgName = businessSettings.pgName,
                    ownerName = businessSettings.ownerName,
                    contactNumber = businessSettings.contactNumber,
                    emailAddress = businessSettings.emailAddress,
                    address = businessSettings.address,
                    defaultMonthlyRent = businessSettings.defaultMonthlyRent,
                    defaultSecurityDeposit = businessSettings.defaultSecurityDeposit,
                    defaultRentDueDay = businessSettings.defaultRentDueDay,
                    currency = businessSettings.currency
                ),
                appSettings = AppSettingsDto(
                    theme = appSettings.theme.name,
                    dynamicColor = appSettings.dynamicColor,
                    dateFormat = appSettings.dateFormat,
                    numberFormat = appSettings.numberFormat,
                    language = appSettings.language
                ),
                securitySettings = SecuritySettingsDto(
                    biometricEnabled = securitySettings.biometricEnabled,
                    autoLockTimeout = securitySettings.autoLockTimeout,
                    requirePinOnResume = securitySettings.requirePinOnResume
                ),
                notificationSettings = NotificationSettingsDto(
                    rentDueReminder = notificationSettings.rentDueReminder,
                    overdueRentReminder = notificationSettings.overdueRentReminder,
                    monthlyInvoiceReminder = notificationSettings.monthlyInvoiceReminder,
                    backupReminder = notificationSettings.backupReminder,
                    appUpdates = notificationSettings.appUpdates
                ),
                profile = profileDto,
                rooms = rooms,
                tenants = tenants,
                payments = payments,
                expenses = expenses
            )

            val json = adapter.toJson(backupData)
            outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreBackup(inputStream: InputStream): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val backupData = adapter.fromJson(json) ?: throw Exception("Failed to parse backup data")

            if (backupData.backupVersion != 1) {
                throw Exception("Unsupported backup version: ${backupData.backupVersion}")
            }

            db.withTransaction {
                db.clearAllTables()
                
                backupData.profile?.let {
                    db.ownerProfileDao().insertProfile(
                        OwnerProfileEntity(
                            id = 1,
                            pgName = it.pgName,
                            ownerName = it.ownerName,
                            phone = it.phone,
                            upiId = it.upiId,
                            pinCode = it.pinCode
                        )
                    )
                }

                backupData.rooms.forEach { dto ->
                    db.roomDao().insertRoom(
                        RoomEntity(
                            roomNumber = dto.roomNumber,
                            floor = dto.floor,
                            capacity = dto.capacity,
                            ratePerBed = dto.ratePerBed,
                            roomType = dto.roomType,
                            notes = dto.notes
                        )
                    )
                }

                backupData.tenants.forEach { dto ->
                    db.tenantDao().insertTenant(
                        TenantEntity(
                            id = dto.id,
                            name = dto.name,
                            phone = dto.phone,
                            email = dto.email,
                            emergencyContact = dto.emergencyContact,
                            roomNumber = dto.roomNumber,
                            bedId = dto.bedId,
                            monthlyRent = dto.monthlyRent,
                            securityDeposit = dto.securityDeposit,
                            moveInDate = dto.moveInDate,
                            isKycUploaded = dto.isKycUploaded,
                            kycDocType = dto.kycDocType,
                            alternateContact = dto.alternateContact,
                            dob = dto.dob,
                            gender = dto.gender,
                            address = dto.address,
                            occupation = dto.occupation,
                            companyOrCollege = dto.companyOrCollege,
                            advancePaid = dto.advancePaid,
                            notes = dto.notes
                        )
                    )
                }

                backupData.payments.forEach { dto ->
                    db.rentPaymentDao().insertPayment(
                        RentPaymentEntity(
                            id = dto.id,
                            tenantId = dto.tenantId,
                            tenantName = dto.tenantName,
                            roomNumber = dto.roomNumber,
                            billingMonth = dto.billingMonth,
                            amount = dto.amount,
                            amountPaid = dto.amountPaid,
                            dueDate = dto.dueDate,
                            paymentDate = dto.paymentDate,
                            paymentMode = dto.paymentMode,
                            transactionReference = dto.transactionReference,
                            remarks = dto.remarks,
                            status = dto.status
                        )
                    )
                }

                backupData.expenses.forEach { dto ->
                    db.expenseDao().insertExpense(
                        ExpenseEntity(
                            id = dto.id,
                            amount = dto.amount,
                            category = dto.category,
                            date = dto.date,
                            notes = dto.notes,
                            title = dto.title,
                            paymentMethod = dto.paymentMethod,
                            vendor = dto.vendor
                        )
                    )
                }
            }

            backupData.appSettings?.let { dto ->
                settingsRepository.updateAppSettings(
                    AppSettings(
                        theme = AppTheme.valueOf(dto.theme),
                        dynamicColor = dto.dynamicColor,
                        dateFormat = dto.dateFormat,
                        numberFormat = dto.numberFormat,
                        language = dto.language
                    )
                )
            }

            backupData.businessSettings?.let { dto ->
                settingsRepository.updateBusinessSettings(
                    BusinessSettings(
                        pgName = dto.pgName,
                        ownerName = dto.ownerName,
                        contactNumber = dto.contactNumber,
                        emailAddress = dto.emailAddress,
                        address = dto.address,
                        defaultMonthlyRent = dto.defaultMonthlyRent,
                        defaultSecurityDeposit = dto.defaultSecurityDeposit,
                        defaultRentDueDay = dto.defaultRentDueDay,
                        currency = dto.currency
                    )
                )
            }

            backupData.securitySettings?.let { dto ->
                settingsRepository.updateSecuritySettings(
                    SecuritySettings(
                        biometricEnabled = dto.biometricEnabled,
                        autoLockTimeout = dto.autoLockTimeout,
                        requirePinOnResume = dto.requirePinOnResume
                    )
                )
            }

            backupData.notificationSettings?.let { dto ->
                settingsRepository.updateNotificationSettings(
                    NotificationSettings(
                        rentDueReminder = dto.rentDueReminder,
                        overdueRentReminder = dto.overdueRentReminder,
                        monthlyInvoiceReminder = dto.monthlyInvoiceReminder,
                        backupReminder = dto.backupReminder,
                        appUpdates = dto.appUpdates
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

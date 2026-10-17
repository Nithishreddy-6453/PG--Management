package com.example.features.rent.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.PgResult
import com.example.data.database.RentPaymentEntity
import com.example.data.database.TenantEntity
import com.example.features.properties.data.CurrentPropertyManager
import com.example.features.rent.domain.repository.RentRepository
import com.example.features.rent.domain.usecase.RecordPaymentUseCase
import com.example.features.rent.domain.usecase.UpdatePaymentUseCase
import com.example.features.rent.domain.util.CurrentBillingMonthManager
import com.example.features.rent.domain.util.RentBillingEngine
import com.example.features.tenants.domain.repository.TenantMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class PaymentUiState(
    val isLoading: Boolean = false,
    val paymentId: Int? = null,
    val cloudId: String = "",
    val ownerId: String = "",
    val propertyId: String = "",
    val tenantId: Int = 0,
    val tenantName: String = "",
    val roomNumber: String = "",
    val tenantList: List<TenantEntity> = emptyList(),
    val billingMonth: String = "",
    val availableBillingMonths: List<String> = emptyList(),
    val expectedAmount: Double = 0.0,
    val amountPaid: String = "",
    val alreadyPaidAmount: Double = 0.0,
    val dueDate: String = "",
    val paymentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val paymentMode: String = "UPI",
    val transactionReference: String = "",
    val remarks: String = "",
    val pastPayments: List<RentPaymentEntity> = emptyList(),
    val attachmentUri: Uri? = null,
    val attachmentLocalPath: String = "",
    val propertyName: String = "",
    val tenantPhone: String = "",
    val bedId: String = "",
    val attachmentFileName: String = "",
    val isUploadingAttachment: Boolean = false,
    val attachmentError: String? = null,
    val isEditMode: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val recordPaymentUseCase: RecordPaymentUseCase,
    private val updatePaymentUseCase: UpdatePaymentUseCase,
    private val rentRepository: RentRepository,
    private val rentPaymentDao: com.example.data.database.RentPaymentDao,
    private val tenantDao: com.example.data.database.TenantDao,
    private val propertyDao: com.example.data.database.PropertyDao,
    private val tenantMediaRepository: TenantMediaRepository,
    private val currentPropertyManager: CurrentPropertyManager,
    private val currentBillingMonthManager: CurrentBillingMonthManager,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val paymentId: Int? = savedStateHandle.get<Any>("paymentId")?.let { raw ->
        when (raw) {
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    private val argTenantId: Int = savedStateHandle.get<Any>("tenantId")?.let { raw ->
        when (raw) {
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull() ?: 0
            else -> raw.toString().toIntOrNull() ?: 0
        }
    } ?: 0

    private val argBillingMonth: String? = savedStateHandle.get<Any>("billingMonth")?.let { raw ->
        val str = when (raw) {
            is String -> raw
            else -> raw.toString()
        }
        if (str.isBlank()) null else try { Uri.decode(str) } catch (_: Exception) { str }
    }

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _actionEvent = MutableSharedFlow<Boolean>()
    val actionEvent = _actionEvent.asSharedFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val currentPropertyId = currentPropertyManager.getCurrentPropertyId()
            val currentProperty = if (currentPropertyId.isNotBlank()) {
                propertyDao.getProperty(currentPropertyId) ?: propertyDao.getActiveProperty(currentPropertyId)
            } else null
            val defaultPropertyName = currentProperty?.propertyName?.trim().orEmpty()
            val tenants = tenantDao.getAllTenants(currentPropertyId)
            val months = generateMonthList()
            val currentMonth = currentBillingMonthManager.getCurrentBillingMonth()

            if (paymentId != null) {
                // Edit existing payment
                val payment = rentRepository.getLedger().firstOrNull()?.find { it.id == paymentId }
                    ?: rentPaymentDao.getPaymentByIdIncludingDeleted(paymentId)
                if (payment != null) {
                    val initialAmountPaid = if (payment.amountPaid > 0) payment.amountPaid.toString() else ""
                    val pastList = rentRepository.getTenantLedger(payment.tenantId).firstOrNull() ?: emptyList()
                    val tenant = tenants.find { it.id == payment.tenantId } ?: tenantDao.getTenantById(payment.tenantId)
                    val effectivePropertyId = payment.propertyId.ifBlank { tenant?.propertyId.orEmpty() }.ifBlank { currentPropertyId }
                    val paymentProperty = if (effectivePropertyId.isNotBlank()) {
                        propertyDao.getProperty(effectivePropertyId) ?: propertyDao.getActiveProperty(effectivePropertyId)
                    } else currentProperty
                    val propertyName = paymentProperty?.propertyName?.trim().orEmpty().ifBlank { defaultPropertyName }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isEditMode = true,
                        paymentId = payment.id,
                        cloudId = payment.cloudId,
                        ownerId = payment.ownerId,
                        propertyId = effectivePropertyId,
                        propertyName = propertyName,
                        tenantId = payment.tenantId,
                        tenantName = payment.tenantName,
                        roomNumber = payment.roomNumber,
                        bedId = tenant?.bedId ?: "",
                        tenantPhone = tenant?.phone ?: "",
                        tenantList = tenants,
                        billingMonth = payment.billingMonth,
                        availableBillingMonths = months,
                        expectedAmount = payment.amount,
                        amountPaid = initialAmountPaid,
                        alreadyPaidAmount = payment.amountPaid,
                        dueDate = payment.dueDate,
                        paymentDate = payment.paymentDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                        paymentMode = payment.paymentMode ?: "UPI",
                        transactionReference = payment.transactionReference ?: "",
                        remarks = payment.remarks ?: "",
                        pastPayments = pastList
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Payment not found")
                }
            } else {
                // New / Collect Payment mode
                val initialTenant = if (argTenantId > 0) {
                    tenants.find { it.id == argTenantId } ?: tenantDao.getTenantById(argTenantId)
                } else {
                    tenants.firstOrNull()
                }

                if (argTenantId > 0 && initialTenant == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        tenantList = tenants,
                        error = "Specified tenant (ID: $argTenantId) could not be found."
                    )
                    return@launch
                }
                val selectedMonth = if (!argBillingMonth.isNullOrBlank()) argBillingMonth else currentMonth

                val tenantId = initialTenant?.id ?: 0
                val tenantName = initialTenant?.name ?: ""
                val roomNumber = initialTenant?.roomNumber ?: ""
                val bedId = initialTenant?.bedId ?: ""
                val tenantPhone = initialTenant?.phone ?: ""

                val tenantPropertyId = initialTenant?.propertyId?.ifBlank { currentPropertyId } ?: currentPropertyId
                val tenantProperty = if (tenantPropertyId.isNotBlank()) {
                    propertyDao.getProperty(tenantPropertyId) ?: propertyDao.getActiveProperty(tenantPropertyId)
                } else currentProperty
                val propertyName = tenantProperty?.propertyName?.trim().orEmpty().ifBlank { defaultPropertyName }

                val parsedSelected = RentBillingEngine.parseBillingMonth(selectedMonth)
                val canonicalMonth = parsedSelected.canonicalName
                val defaultDueDate = String.format(Locale.US, "%04d-%02d-05", parsedSelected.year, parsedSelected.month1Based)

                val existingForMonth = if (tenantId > 0) {
                    rentPaymentDao.getPaymentForTenantPropertyAndMonth(tenantId, tenantPropertyId, canonicalMonth)
                        ?: rentPaymentDao.getPaymentForTenantAndMonth(tenantId, canonicalMonth)
                        ?: rentPaymentDao.getPaymentsForTenantSync(tenantId).find {
                            RentBillingEngine.parseBillingMonth(it.billingMonth).canonicalName == canonicalMonth && !it.deleted
                        }
                } else null

                val expected = existingForMonth?.amount ?: initialTenant?.monthlyRent?.takeIf { it > 0 } ?: 5000.0
                val alreadyPaid = existingForMonth?.amountPaid ?: 0.0
                val remaining = (expected - alreadyPaid).coerceAtLeast(0.0)
                val pastList = if (tenantId > 0) rentRepository.getTenantLedger(tenantId).firstOrNull() ?: emptyList() else emptyList()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    paymentId = existingForMonth?.id,
                    cloudId = existingForMonth?.cloudId ?: "",
                    propertyId = tenantPropertyId,
                    propertyName = propertyName,
                    tenantId = tenantId,
                    tenantName = tenantName,
                    roomNumber = roomNumber,
                    bedId = bedId,
                    tenantPhone = tenantPhone,
                    tenantList = tenants,
                    billingMonth = canonicalMonth,
                    availableBillingMonths = months,
                    expectedAmount = expected,
                    amountPaid = formatAmountString(remaining),
                    alreadyPaidAmount = alreadyPaid,
                    dueDate = existingForMonth?.dueDate ?: defaultDueDate,
                    pastPayments = pastList
                )
            }
        }
    }

    fun onTenantSelected(tenantId: Int) {
        val tenant = _uiState.value.tenantList.find { it.id == tenantId } ?: return
        viewModelScope.launch {
            val propertyId = tenant.propertyId.ifBlank { _uiState.value.propertyId.ifBlank { currentPropertyManager.getCurrentPropertyId() } }
            val prop = if (propertyId.isNotBlank()) {
                propertyDao.getProperty(propertyId) ?: propertyDao.getActiveProperty(propertyId)
            } else null
            val propName = prop?.propertyName?.trim().orEmpty()
            val month = _uiState.value.billingMonth
            val parsedMonth = RentBillingEngine.parseBillingMonth(month)
            val canonicalMonth = parsedMonth.canonicalName
            val defaultDueDate = String.format(Locale.US, "%04d-%02d-05", parsedMonth.year, parsedMonth.month1Based)

            val existingForMonth = rentPaymentDao.getPaymentForTenantPropertyAndMonth(tenantId, propertyId, canonicalMonth)
                ?: rentPaymentDao.getPaymentForTenantAndMonth(tenantId, canonicalMonth)
                ?: rentPaymentDao.getPaymentsForTenantSync(tenantId).find {
                    RentBillingEngine.parseBillingMonth(it.billingMonth).canonicalName == canonicalMonth && !it.deleted
                }

            val expected = existingForMonth?.amount ?: tenant.monthlyRent.takeIf { it > 0 } ?: 5000.0
            val alreadyPaid = existingForMonth?.amountPaid ?: 0.0
            val remaining = (expected - alreadyPaid).coerceAtLeast(0.0)
            val pastList = rentRepository.getTenantLedger(tenantId).firstOrNull() ?: emptyList()

            _uiState.value = _uiState.value.copy(
                propertyId = propertyId,
                propertyName = propName,
                tenantId = tenant.id,
                tenantName = tenant.name,
                roomNumber = tenant.roomNumber,
                bedId = tenant.bedId,
                tenantPhone = tenant.phone,
                paymentId = existingForMonth?.id,
                cloudId = existingForMonth?.cloudId ?: "",
                billingMonth = canonicalMonth,
                expectedAmount = expected,
                amountPaid = formatAmountString(remaining),
                alreadyPaidAmount = alreadyPaid,
                dueDate = existingForMonth?.dueDate ?: defaultDueDate,
                pastPayments = pastList,
                attachmentUri = null,
                attachmentLocalPath = "",
                attachmentFileName = "",
                error = null
            )
        }
    }

    fun onBillingMonthChanged(month: String) {
        viewModelScope.launch {
            val tenantId = _uiState.value.tenantId
            val propertyId = _uiState.value.propertyId.ifBlank { currentPropertyManager.getCurrentPropertyId() }
            val parsedMonth = RentBillingEngine.parseBillingMonth(month)
            val canonicalMonth = parsedMonth.canonicalName
            val defaultDueDate = String.format(Locale.US, "%04d-%02d-05", parsedMonth.year, parsedMonth.month1Based)

            val existingForMonth = if (tenantId > 0) {
                rentPaymentDao.getPaymentForTenantPropertyAndMonth(tenantId, propertyId, canonicalMonth)
                    ?: rentPaymentDao.getPaymentForTenantAndMonth(tenantId, canonicalMonth)
                    ?: rentPaymentDao.getPaymentsForTenantSync(tenantId).find {
                        RentBillingEngine.parseBillingMonth(it.billingMonth).canonicalName == canonicalMonth && !it.deleted
                    }
            } else null

            val tenant = _uiState.value.tenantList.find { it.id == tenantId }
            val expected = existingForMonth?.amount ?: tenant?.monthlyRent?.takeIf { it > 0 } ?: _uiState.value.expectedAmount.takeIf { it > 0 } ?: 5000.0
            val alreadyPaid = existingForMonth?.amountPaid ?: 0.0
            val remaining = (expected - alreadyPaid).coerceAtLeast(0.0)

            _uiState.value = _uiState.value.copy(
                billingMonth = canonicalMonth,
                paymentId = existingForMonth?.id,
                cloudId = existingForMonth?.cloudId ?: "",
                expectedAmount = expected,
                amountPaid = formatAmountString(remaining),
                alreadyPaidAmount = alreadyPaid,
                dueDate = existingForMonth?.dueDate ?: defaultDueDate,
                error = null
            )
        }
    }

    fun onAmountPaidChanged(amount: String) {
        _uiState.value = _uiState.value.copy(amountPaid = amount, error = null)
    }

    fun onPaymentDateChanged(date: String) {
        _uiState.value = _uiState.value.copy(paymentDate = date, error = null)
    }

    fun onPaymentModeChanged(mode: String) {
        _uiState.value = _uiState.value.copy(paymentMode = mode, error = null)
    }

    fun onTransactionReferenceChanged(ref: String) {
        _uiState.value = _uiState.value.copy(transactionReference = ref, error = null)
    }

    fun onRemarksChanged(remarks: String) {
        _uiState.value = _uiState.value.copy(remarks = remarks, error = null)
    }

    fun onAttachmentSelected(uri: Uri, fileName: String = "payment_screenshot.jpg") {
        _uiState.value = _uiState.value.copy(
            attachmentUri = uri,
            attachmentFileName = fileName,
            attachmentLocalPath = uri.toString(),
            attachmentError = null
        )
    }

    fun removeAttachment() {
        _uiState.value = _uiState.value.copy(
            attachmentUri = null,
            attachmentLocalPath = "",
            attachmentFileName = "",
            attachmentError = null
        )
    }

    fun savePayment() {
        val state = _uiState.value
        if (state.isLoading) return // Prevent double-tap submission

        val currentOutstanding = (state.expectedAmount - state.alreadyPaidAmount).coerceAtLeast(0.0)

        if (state.tenantId <= 0) {
            _uiState.value = state.copy(error = "Please select a tenant")
            return
        }

        if (currentOutstanding <= 0.009 && !state.isEditMode) {
            _uiState.value = state.copy(error = "Tenant has already paid in full for ${state.billingMonth}.")
            return
        }

        val cleanedInput = state.amountPaid.replace(",", "").replace("₹", "").trim()
        val amountPaidDouble = cleanedInput.toDoubleOrNull()

        if (amountPaidDouble == null || amountPaidDouble <= 0.0) {
            _uiState.value = state.copy(error = "Please enter a valid payment amount greater than ₹0.")
            return
        }

        if (!state.isEditMode && amountPaidDouble > currentOutstanding + 0.009) {
            _uiState.value = state.copy(
                error = "Entered amount (₹${formatAmountString(amountPaidDouble)}) exceeds current outstanding balance (₹${formatAmountString(currentOutstanding)})."
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = state.copy(isLoading = true, error = null)
                val rawTotal = if (state.isEditMode) {
                    amountPaidDouble
                } else {
                    state.alreadyPaidAmount + amountPaidDouble
                }
                val totalPaid = (rawTotal * 100.0).let { java.lang.Math.round(it) / 100.0 }
                val parsedMonth = RentBillingEngine.parseBillingMonth(state.billingMonth)
                val defaultDueDate = String.format(Locale.US, "%04d-%02d-05", parsedMonth.year, parsedMonth.month1Based)
                val safeDueDate = state.dueDate.ifBlank { defaultDueDate }
                val safePaymentDate = state.paymentDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

                val calculatedStatus = RentBillingEngine.calculatePaymentStatus(
                    expectedAmount = state.expectedAmount,
                    amountPaid = totalPaid,
                    dueDateStr = safeDueDate
                )
                val tenant = state.tenantList.find { it.id == state.tenantId }
                val tenantName = tenant?.name ?: state.tenantName
                val roomNumber = tenant?.roomNumber ?: state.roomNumber

                val updatedPayment = RentPaymentEntity(
                    id = state.paymentId ?: 0,
                    cloudId = state.cloudId,
                    ownerId = state.ownerId,
                    propertyId = state.propertyId.ifBlank { currentPropertyManager.getCurrentPropertyId() },
                    tenantId = state.tenantId,
                    tenantName = tenantName,
                    roomNumber = roomNumber,
                    billingMonth = parsedMonth.canonicalName,
                    amount = if (state.expectedAmount > 0) state.expectedAmount else totalPaid,
                    amountPaid = totalPaid,
                    dueDate = safeDueDate,
                    paymentDate = safePaymentDate,
                    paymentMode = state.paymentMode.ifBlank { "UPI" },
                    transactionReference = state.transactionReference.takeIf { it.isNotBlank() },
                    remarks = state.remarks.takeIf { it.isNotBlank() },
                    status = calculatedStatus
                )

                if (state.paymentId != null && state.paymentId > 0) {
                    updatePaymentUseCase(updatedPayment)
                } else {
                    recordPaymentUseCase(updatedPayment)
                }

                // Handle optional attachment upload
                if (state.attachmentUri != null && tenant != null) {
                    val propertyId = state.propertyId.ifBlank { currentPropertyManager.getCurrentPropertyId() }
                    val tenantCloudId = tenant.cloudId.ifBlank { tenant.id.toString() }
                    try {
                        val result = tenantMediaRepository.uploadDocument(
                            propertyId = propertyId,
                            tenantCloudId = tenantCloudId,
                            documentUri = state.attachmentUri!!,
                            fileName = state.attachmentFileName.ifBlank { "Payment_Screenshot_${state.billingMonth.replace(' ', '_')}.jpg" },
                            mimeType = "image/jpeg"
                        )
                        if (result is PgResult.Failure) {
                            tenantMediaRepository.queueDocumentForUpload(
                                propertyId = propertyId,
                                tenantCloudId = tenantCloudId,
                                documentUri = state.attachmentUri!!,
                                fileName = state.attachmentFileName,
                                mimeType = "image/jpeg"
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            attachmentError = "Payment saved, but attachment upload failed: ${e.message}"
                        )
                        return@launch
                    }
                }

                _actionEvent.emit(true)
            } catch (e: Exception) {
                _uiState.value = state.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    private fun formatAmountString(amount: Double): String {
        return if (amount <= 0.0) "0"
        else if (amount % 1.0 == 0.0) amount.toLong().toString()
        else String.format(Locale.US, "%.2f", amount)
    }

    private fun generateMonthList(): List<String> {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -3)
        for (i in 0..6) {
            list.add(RentBillingEngine.formatCanonicalBillingMonth(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        return list
    }
}

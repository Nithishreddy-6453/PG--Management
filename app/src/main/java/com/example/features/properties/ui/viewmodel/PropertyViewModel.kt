package com.example.features.properties.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.PgResult
import com.example.data.database.PropertyEntity
import com.example.features.properties.domain.usecase.ArchivePropertyUseCase
import com.example.features.properties.domain.usecase.CreatePropertyUseCase
import com.example.features.properties.domain.usecase.GetActivePropertiesUseCase
import com.example.features.properties.domain.usecase.GetCurrentPropertyUseCase
import com.example.features.properties.domain.usecase.SwitchPropertyUseCase
import com.example.features.properties.domain.usecase.UpdatePropertyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PropertyUiEffect {
    data class ShowSnackbar(val message: String) : PropertyUiEffect
    data class PropertyCreated(val property: PropertyEntity) : PropertyUiEffect
    data class PropertySwitched(val propertyName: String) : PropertyUiEffect
}

@HiltViewModel
class PropertyViewModel @Inject constructor(
    getActivePropertiesUseCase: GetActivePropertiesUseCase,
    getCurrentPropertyUseCase: GetCurrentPropertyUseCase,
    private val switchPropertyUseCase: SwitchPropertyUseCase,
    private val createPropertyUseCase: CreatePropertyUseCase,
    private val updatePropertyUseCase: UpdatePropertyUseCase,
    private val archivePropertyUseCase: ArchivePropertyUseCase
) : ViewModel() {

    val activeProperties: StateFlow<List<PropertyEntity>> = getActivePropertiesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentProperty: StateFlow<PropertyEntity?> = getCurrentPropertyUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiEffect = MutableSharedFlow<PropertyUiEffect>()
    val uiEffect: SharedFlow<PropertyUiEffect> = _uiEffect.asSharedFlow()

    fun switchProperty(property: PropertyEntity) {
        viewModelScope.launch {
            switchPropertyUseCase(property.propertyId)
            _uiEffect.emit(PropertyUiEffect.PropertySwitched(property.propertyName))
        }
    }

    fun switchPropertyById(propertyId: String) {
        viewModelScope.launch {
            switchPropertyUseCase(propertyId)
        }
    }

    fun createProperty(
        name: String,
        address: String = "",
        city: String = "",
        state: String = "",
        postalCode: String = "",
        contactNumber: String = "",
        description: String = "",
        onSuccess: () -> Unit = {}
    ) {
        if (name.isBlank()) {
            viewModelScope.launch {
                _uiEffect.emit(PropertyUiEffect.ShowSnackbar("PG Name is required"))
            }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            when (val result = createPropertyUseCase(name, address, city, state, postalCode, contactNumber, description)) {
                is PgResult.Success -> {
                    _isLoading.value = false
                    _uiEffect.emit(PropertyUiEffect.ShowSnackbar("Added PG: ${result.data.propertyName}"))
                    _uiEffect.emit(PropertyUiEffect.PropertyCreated(result.data))
                    onSuccess()
                }
                is PgResult.Failure -> {
                    _isLoading.value = false
                    _uiEffect.emit(PropertyUiEffect.ShowSnackbar(result.error.message))
                }
            }
        }
    }

    fun updateProperty(property: PropertyEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = updatePropertyUseCase(property)) {
                is PgResult.Success -> {
                    _isLoading.value = false
                    _uiEffect.emit(PropertyUiEffect.ShowSnackbar("Property updated successfully"))
                    onSuccess()
                }
                is PgResult.Failure -> {
                    _isLoading.value = false
                    _uiEffect.emit(PropertyUiEffect.ShowSnackbar(result.error.message))
                }
            }
        }
    }

    fun archiveProperty(propertyId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = archivePropertyUseCase(propertyId)) {
                is PgResult.Success -> {
                    _isLoading.value = false
                    _uiEffect.emit(PropertyUiEffect.ShowSnackbar("Property archived"))
                    onSuccess()
                }
                is PgResult.Failure -> {
                    _isLoading.value = false
                    _uiEffect.emit(PropertyUiEffect.ShowSnackbar(result.error.message))
                }
            }
        }
    }
}

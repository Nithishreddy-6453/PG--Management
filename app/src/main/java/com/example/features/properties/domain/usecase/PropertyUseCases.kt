package com.example.features.properties.domain.usecase

import com.example.core.common.PgResult
import com.example.data.database.PropertyEntity
import com.example.features.properties.domain.repository.PropertyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActivePropertiesUseCase @Inject constructor(
    private val repository: PropertyRepository
) {
    operator fun invoke(): Flow<List<PropertyEntity>> = repository.getActivePropertiesFlow()
}

class GetCurrentPropertyUseCase @Inject constructor(
    private val repository: PropertyRepository
) {
    operator fun invoke(): Flow<PropertyEntity?> = repository.getCurrentPropertyFlow()
    
    suspend fun get(): PropertyEntity? = repository.getCurrentProperty()
}

class SwitchPropertyUseCase @Inject constructor(
    private val repository: PropertyRepository
) {
    suspend operator fun invoke(propertyId: String) {
        repository.setSelectedPropertyId(propertyId)
    }
}

class CreatePropertyUseCase @Inject constructor(
    private val repository: PropertyRepository
) {
    suspend operator fun invoke(
        name: String,
        address: String = "",
        city: String = "",
        state: String = "",
        postalCode: String = "",
        contactNumber: String = "",
        description: String = ""
    ): PgResult<PropertyEntity> {
        return repository.createProperty(name, address, city, state, postalCode, contactNumber, description)
    }
}

class UpdatePropertyUseCase @Inject constructor(
    private val repository: PropertyRepository
) {
    suspend operator fun invoke(property: PropertyEntity): PgResult<Unit> {
        return repository.updateProperty(property)
    }
}

class ArchivePropertyUseCase @Inject constructor(
    private val repository: PropertyRepository
) {
    suspend operator fun invoke(propertyId: String): PgResult<Unit> {
        return repository.archiveProperty(propertyId)
    }
}

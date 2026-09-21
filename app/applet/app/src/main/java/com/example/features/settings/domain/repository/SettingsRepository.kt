package com.example.features.settings.domain.repository

import com.example.features.settings.domain.model.*
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getBusinessSettings(): Flow<BusinessSettings>
    suspend fun updateBusinessSettings(settings: BusinessSettings)

    fun getAppSettings(): Flow<AppSettings>
    suspend fun updateAppSettings(settings: AppSettings)

    fun getSecuritySettings(): Flow<SecuritySettings>
    suspend fun updateSecuritySettings(settings: SecuritySettings)

    fun getNotificationSettings(): Flow<NotificationSettings>
    suspend fun updateNotificationSettings(settings: NotificationSettings)
}

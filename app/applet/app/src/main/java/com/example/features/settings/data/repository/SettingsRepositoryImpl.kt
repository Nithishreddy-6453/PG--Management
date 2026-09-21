package com.example.features.settings.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.features.settings.domain.model.*
import com.example.features.settings.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val dataStore = context.dataStore

    // Keys
    private object PreferencesKeys {
        val PG_NAME = stringPreferencesKey("pg_name")
        val OWNER_NAME = stringPreferencesKey("owner_name")
        val CONTACT_NUMBER = stringPreferencesKey("contact_number")
        val EMAIL_ADDRESS = stringPreferencesKey("email_address")
        val ADDRESS = stringPreferencesKey("address")
        val DEFAULT_MONTHLY_RENT = doublePreferencesKey("default_monthly_rent")
        val DEFAULT_SECURITY_DEPOSIT = doublePreferencesKey("default_security_deposit")
        val DEFAULT_RENT_DUE_DAY = intPreferencesKey("default_rent_due_day")
        val CURRENCY = stringPreferencesKey("currency")

        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val NUMBER_FORMAT = stringPreferencesKey("number_format")
        val LANGUAGE = stringPreferencesKey("language")

        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_TIMEOUT = longPreferencesKey("auto_lock_timeout")
        val REQUIRE_PIN_ON_RESUME = booleanPreferencesKey("require_pin_on_resume")

        val RENT_DUE_REMINDER = booleanPreferencesKey("rent_due_reminder")
        val OVERDUE_RENT_REMINDER = booleanPreferencesKey("overdue_rent_reminder")
        val MONTHLY_INVOICE_REMINDER = booleanPreferencesKey("monthly_invoice_reminder")
        val BACKUP_REMINDER = booleanPreferencesKey("backup_reminder")
        val APP_UPDATES = booleanPreferencesKey("app_updates")
    }

    override fun getBusinessSettings(): Flow<BusinessSettings> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                BusinessSettings(
                    pgName = preferences[PreferencesKeys.PG_NAME] ?: "",
                    ownerName = preferences[PreferencesKeys.OWNER_NAME] ?: "",
                    contactNumber = preferences[PreferencesKeys.CONTACT_NUMBER] ?: "",
                    emailAddress = preferences[PreferencesKeys.EMAIL_ADDRESS] ?: "",
                    address = preferences[PreferencesKeys.ADDRESS] ?: "",
                    defaultMonthlyRent = preferences[PreferencesKeys.DEFAULT_MONTHLY_RENT] ?: 0.0,
                    defaultSecurityDeposit = preferences[PreferencesKeys.DEFAULT_SECURITY_DEPOSIT] ?: 0.0,
                    defaultRentDueDay = preferences[PreferencesKeys.DEFAULT_RENT_DUE_DAY] ?: 1,
                    currency = preferences[PreferencesKeys.CURRENCY] ?: "₹"
                )
            }
    }

    override suspend fun updateBusinessSettings(settings: BusinessSettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PG_NAME] = settings.pgName
            preferences[PreferencesKeys.OWNER_NAME] = settings.ownerName
            preferences[PreferencesKeys.CONTACT_NUMBER] = settings.contactNumber
            preferences[PreferencesKeys.EMAIL_ADDRESS] = settings.emailAddress
            preferences[PreferencesKeys.ADDRESS] = settings.address
            preferences[PreferencesKeys.DEFAULT_MONTHLY_RENT] = settings.defaultMonthlyRent
            preferences[PreferencesKeys.DEFAULT_SECURITY_DEPOSIT] = settings.defaultSecurityDeposit
            preferences[PreferencesKeys.DEFAULT_RENT_DUE_DAY] = settings.defaultRentDueDay
            preferences[PreferencesKeys.CURRENCY] = settings.currency
        }
    }

    override fun getAppSettings(): Flow<AppSettings> {
        return dataStore.data.map { preferences ->
            AppSettings(
                theme = AppTheme.valueOf(preferences[PreferencesKeys.THEME] ?: AppTheme.SYSTEM.name),
                dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true,
                dateFormat = preferences[PreferencesKeys.DATE_FORMAT] ?: "dd/MM/yyyy",
                numberFormat = preferences[PreferencesKeys.NUMBER_FORMAT] ?: "Indian",
                language = preferences[PreferencesKeys.LANGUAGE] ?: "en"
            )
        }
    }

    override suspend fun updateAppSettings(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = settings.theme.name
            preferences[PreferencesKeys.DYNAMIC_COLOR] = settings.dynamicColor
            preferences[PreferencesKeys.DATE_FORMAT] = settings.dateFormat
            preferences[PreferencesKeys.NUMBER_FORMAT] = settings.numberFormat
            preferences[PreferencesKeys.LANGUAGE] = settings.language
        }
    }

    override fun getSecuritySettings(): Flow<SecuritySettings> {
        return dataStore.data.map { preferences ->
            SecuritySettings(
                biometricEnabled = preferences[PreferencesKeys.BIOMETRIC_ENABLED] ?: false,
                autoLockTimeout = preferences[PreferencesKeys.AUTO_LOCK_TIMEOUT] ?: 0L,
                requirePinOnResume = preferences[PreferencesKeys.REQUIRE_PIN_ON_RESUME] ?: true
            )
        }
    }

    override suspend fun updateSecuritySettings(settings: SecuritySettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BIOMETRIC_ENABLED] = settings.biometricEnabled
            preferences[PreferencesKeys.AUTO_LOCK_TIMEOUT] = settings.autoLockTimeout
            preferences[PreferencesKeys.REQUIRE_PIN_ON_RESUME] = settings.requirePinOnResume
        }
    }

    override fun getNotificationSettings(): Flow<NotificationSettings> {
        return dataStore.data.map { preferences ->
            NotificationSettings(
                rentDueReminder = preferences[PreferencesKeys.RENT_DUE_REMINDER] ?: true,
                overdueRentReminder = preferences[PreferencesKeys.OVERDUE_RENT_REMINDER] ?: true,
                monthlyInvoiceReminder = preferences[PreferencesKeys.MONTHLY_INVOICE_REMINDER] ?: true,
                backupReminder = preferences[PreferencesKeys.BACKUP_REMINDER] ?: false,
                appUpdates = preferences[PreferencesKeys.APP_UPDATES] ?: true
            )
        }
    }

    override suspend fun updateNotificationSettings(settings: NotificationSettings) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RENT_DUE_REMINDER] = settings.rentDueReminder
            preferences[PreferencesKeys.OVERDUE_RENT_REMINDER] = settings.overdueRentReminder
            preferences[PreferencesKeys.MONTHLY_INVOICE_REMINDER] = settings.monthlyInvoiceReminder
            preferences[PreferencesKeys.BACKUP_REMINDER] = settings.backupReminder
            preferences[PreferencesKeys.APP_UPDATES] = settings.appUpdates
        }
    }
}

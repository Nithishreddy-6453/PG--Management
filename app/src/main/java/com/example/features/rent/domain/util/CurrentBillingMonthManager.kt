package com.example.features.rent.domain.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

private val Context.billingMonthDataStore: DataStore<Preferences> by preferencesDataStore(name = "billing_month_settings_prefs")

/**
 * Single source of truth for the currently selected/active billing month context across the entire application.
 * Persists across screens, viewmodels, and app sessions.
 */
@Singleton
class CurrentBillingMonthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_CURRENT_BILLING_MONTH = stringPreferencesKey("current_billing_month_key")
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val defaultMonth = RentBillingEngine.formatCanonicalBillingMonth(Date())

    @Volatile
    private var inMemoryBillingMonth: String = defaultMonth

    private val _currentMonthState = MutableStateFlow(defaultMonth)
    val currentBillingMonthState: StateFlow<String> = _currentMonthState.asStateFlow()

    val currentBillingMonthFlow: Flow<String> = merge(
        _currentMonthState,
        context.billingMonthDataStore.data.map { prefs ->
            val stored = prefs[KEY_CURRENT_BILLING_MONTH]
            val resolved = if (!stored.isNullOrBlank()) stored else inMemoryBillingMonth
            inMemoryBillingMonth = resolved
            if (_currentMonthState.value != resolved) {
                _currentMonthState.value = resolved
            }
            resolved
        }
    ).distinctUntilChanged()

    fun getCurrentBillingMonth(): String {
        return inMemoryBillingMonth
    }

    fun setCurrentBillingMonth(month: String) {
        if (month.isNotBlank()) {
            val canonical = RentBillingEngine.parseBillingMonth(month).canonicalName
            inMemoryBillingMonth = canonical
            _currentMonthState.value = canonical
            scope.launch {
                try {
                    context.billingMonthDataStore.edit { prefs ->
                        prefs[KEY_CURRENT_BILLING_MONTH] = canonical
                    }
                } catch (_: Exception) {}
            }
        }
    }
}

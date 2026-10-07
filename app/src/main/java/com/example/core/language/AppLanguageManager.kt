package com.example.core.language

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(name = "language_preferences")

val LocalAppLanguage = compositionLocalOf { "EN" }

/**
 * Global single source of truth for the application language (English vs Telugu).
 * Automatically persists to DataStore and provides synchronous in-memory StateFlow.
 */
object AppLanguageManager {
    val KEY_LANGUAGE = stringPreferencesKey("app_selected_language")
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dataStore: DataStore<Preferences>? = null

    private val _languageFlow = MutableStateFlow("EN")
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    val currentLanguage: String
        get() = _languageFlow.value

    fun init(context: Context) {
        if (dataStore == null) {
            val ds = context.applicationContext.languageDataStore
            dataStore = ds
            scope.launch {
                try {
                    val prefs = ds.data.first()
                    val savedLang = prefs[KEY_LANGUAGE] ?: "EN"
                    _languageFlow.value = savedLang
                } catch (_: Exception) {
                    _languageFlow.value = "EN"
                }
            }
        }
    }

    fun setLanguage(language: String) {
        val target = if (language == "తెలుగు" || language.equals("te", ignoreCase = true)) "తెలుగు" else "EN"
        _languageFlow.value = target
        val ds = dataStore
        if (ds != null) {
            scope.launch {
                try {
                    ds.edit { prefs ->
                        prefs[KEY_LANGUAGE] = target
                    }
                } catch (_: Exception) {
                    // Ignore persistence failure
                }
            }
        }
    }

    fun isTelugu(): Boolean = _languageFlow.value == "తెలుగు"

    // Centralized Dictionary for instant translation lookups
    private val translations = mapOf(
        // Navigation & Bottom Bar
        "Home" to mapOf("EN" to "Home", "తెలుగు" to "హోమ్"),
        "Rooms" to mapOf("EN" to "Rooms", "తెలుగు" to "గదులు"),
        "Tenants" to mapOf("EN" to "Tenants", "తెలుగు" to "అద్దెదారులు"),
        "Payments" to mapOf("EN" to "Payments", "తెలుగు" to "చెల్లింపులు"),
        "More" to mapOf("EN" to "More", "తెలుగు" to "మరిన్ని"),
        "Settings" to mapOf("EN" to "Settings", "తెలుగు" to "సెట్టింగ్‌లు"),
        "Expenses" to mapOf("EN" to "Expenses", "తెలుగు" to "ఖర్చులు"),
        "Reports" to mapOf("EN" to "Reports", "తెలుగు" to "నివేదికలు"),
        "Profile" to mapOf("EN" to "Profile", "తెలుగు" to "ప్రొఫైల్"),
        "Properties" to mapOf("EN" to "Properties", "తెలుగు" to "ప్రాపర్టీలు"),

        // Room actions & titles
        "Add Room" to mapOf("EN" to "Add Room", "తెలుగు" to "రూమ్ జోడించు"),
        "Edit Room" to mapOf("EN" to "Edit Room", "తెలుగు" to "రూమ్ సవరించు"),
        "Room Details" to mapOf("EN" to "Room Details", "తెలుగు" to "గది వివరాలు"),
        "All Rooms" to mapOf("EN" to "All Rooms", "తెలుగు" to "అన్ని గదులు"),
        "Search by Room No, Floor or Type..." to mapOf("EN" to "Search by Room No, Floor or Type...", "తెలుగు" to "రూమ్ నంబర్, ఫ్లోర్ లేదా టైప్ ద్వారా శోధించండి..."),

        // Tenant actions & titles
        "Add Tenant" to mapOf("EN" to "Add Tenant", "తెలుగు" to "అద్దెదారుని జోడించు"),
        "Edit Tenant" to mapOf("EN" to "Edit Tenant", "తెలుగు" to "అద్దెదారుని సవరించు"),
        "Tenant Details" to mapOf("EN" to "Tenant Details", "తెలుగు" to "అద్దెదారు వివరాలు"),
        "Search by name or phone..." to mapOf("EN" to "Search by name or phone...", "తెలుగు" to "పేరు లేదా ఫోన్ ద్వారా శోధించండి..."),
        "Search tenant, phone or room..." to mapOf("EN" to "Search tenant, phone or room...", "తెలుగు" to "అద్దెదారు, ఫోన్ లేదా గది ద్వారా శోధించండి..."),
        "Tenant Registration" to mapOf("EN" to "Tenant Registration", "తెలుగు" to "అద్దెదారు నమోదు"),
        "Share Form" to mapOf("EN" to "Share Form", "తెలుగు" to "ఫారం షేర్ చేయండి"),
        "Share with new tenants." to mapOf("EN" to "Share with new tenants.", "తెలుగు" to "కొత్త అద్దెదారులతో పంచుకోండి."),
        "new submissions" to mapOf("EN" to "new submissions", "తెలుగు" to "కొత్త సమర్పణలు"),
        "Leaving Soon" to mapOf("EN" to "Leaving Soon", "తెలుగు" to "త్వరలో ఖాళీ"),
        "Leaving soon" to mapOf("EN" to "Leaving soon", "తెలుగు" to "త్వరలో ఖాళీ"),
        "Moves out today" to mapOf("EN" to "Moves out today", "తెలుగు" to "ఈరోజే ఖాళీ"),
        "Move-out overdue" to mapOf("EN" to "Move-out overdue", "తెలుగు" to "ఖాళీ గడువు ముగిసింది"),
        "New" to mapOf("EN" to "New", "తెలుగు" to "కొత్త"),
        "Sort" to mapOf("EN" to "Sort", "తెలుగు" to "క్రమబద్ధీకరించు"),
        "Filter" to mapOf("EN" to "Filter", "తెలుగు" to "ఫిల్టర్"),
        "Clear all" to mapOf("EN" to "Clear all", "తెలుగు" to "అన్నీ క్లియర్ చేయండి"),
        "Apply" to mapOf("EN" to "Apply", "తెలుగు" to "వర్తించు"),
        "No tenants yet" to mapOf("EN" to "No tenants yet", "తెలుగు" to "ఇంకా అద్దెదారులు లేరు"),
        "No tenants found" to mapOf("EN" to "No tenants found", "తెలుగు" to "అద్దెదారులు కనుగొనబడలేదు"),
        "No tenants match these filters" to mapOf("EN" to "No tenants match these filters", "తెలుగు" to "ఫిల్టర్‌లకు సరిపోయే అద్దెదారులు లేరు"),
        "Clear Search" to mapOf("EN" to "Clear Search", "తెలుగు" to "శోధనను క్లియర్ చేయండి"),
        "Clear Filters" to mapOf("EN" to "Clear Filters", "తెలుగు" to "ఫిల్టర్లను క్లియర్ చేయండి"),

        // Financial & Rent
        "Collect Rent" to mapOf("EN" to "Collect Rent", "తెలుగు" to "అద్దె వసూలు"),
        "Rent Ledger" to mapOf("EN" to "Rent Ledger", "తెలుగు" to "అద్దె లెడ్జర్"),
        "Track all rent payments" to mapOf("EN" to "Track all rent payments", "తెలుగు" to "అన్ని అద్దె చెల్లింపులను ట్రాక్ చేయండి"),
        "Search by tenant name or room..." to mapOf("EN" to "Search by tenant name or room...", "తెలుగు" to "అద్దెదారు పేరు లేదా గది ద్వారా శోధించండి..."),
        "Add Expense" to mapOf("EN" to "Add Expense", "తెలుగు" to "ఖర్చు జోడించు"),
        "Expense Details" to mapOf("EN" to "Expense Details", "తెలుగు" to "ఖర్చు వివరాలు"),
        "Monthly Rent" to mapOf("EN" to "Monthly Rent", "తెలుగు" to "నెలవారీ అద్దె"),
        "Security Deposit" to mapOf("EN" to "Security Deposit", "తెలుగు" to "సెక్యూరిటీ డిపాజిట్"),
        "Pending Rent" to mapOf("EN" to "Pending Rent", "తెలుగు" to "పెండింగ్ అద్దె"),
        "Monthly Revenue" to mapOf("EN" to "Monthly Revenue", "తెలుగు" to "నెలవారీ ఆదాయం"),
        "Monthly Expenses" to mapOf("EN" to "Monthly Expenses", "తెలుగు" to "నెలవారీ ఖర్చులు"),
        "Collected" to mapOf("EN" to "Collected", "తెలుగు" to "వసూలు చేయబడింది"),
        "Total Dues" to mapOf("EN" to "Total Dues", "తెలుగు" to "మొత్తం బకాయిలు"),
        "Payment Records" to mapOf("EN" to "Payment Records", "తెలుగు" to "చెల్లింపు రికార్డులు"),
        "Latest First" to mapOf("EN" to "Latest First", "తెలుగు" to "తాజా మొదటిది"),
        "Due" to mapOf("EN" to "Due", "తెలుగు" to "గడువు"),
        "Add Payment" to mapOf("EN" to "Add Payment", "తెలుగు" to "చెల్లింపు జోడించు"),

        // Statuses & Filters
        "Active" to mapOf("EN" to "Active", "తెలుగు" to "యాక్టివ్"),
        "Vacated" to mapOf("EN" to "Vacated", "తెలుగు" to "ఖాళీ చేయబడింది"),
        "All" to mapOf("EN" to "All", "తెలుగు" to "అన్నీ"),
        "Available" to mapOf("EN" to "Available", "తెలుగు" to "అందుబాటులో ఉంది"),
        "Partial" to mapOf("EN" to "Partial", "తెలుగు" to "పాక్షికం"),
        "Full" to mapOf("EN" to "Full", "తెలుగు" to "పూర్తి"),
        "Empty" to mapOf("EN" to "Empty", "తెలుగు" to "ఖాళీ"),
        "Occupied" to mapOf("EN" to "Occupied", "తెలుగు" to "ఆక్రమించబడింది"),
        "Pending" to mapOf("EN" to "Pending", "తెలుగు" to "పెండింగ్‌లో ఉంది"),
        "Paid" to mapOf("EN" to "Paid", "తెలుగు" to "చెల్లించబడింది"),
        "Unpaid" to mapOf("EN" to "Unpaid", "తెలుగు" to "చెల్లించలేదు"),
        "Partially Paid" to mapOf("EN" to "Partially Paid", "తెలుగు" to "పాక్షికంగా చెల్లించబడింది"),
        "Outstanding" to mapOf("EN" to "Outstanding", "తెలుగు" to "బకాయి"),
        "Total Expenses" to mapOf("EN" to "Total Expenses", "తెలుగు" to "మొత్తం ఖర్చులు"),
        "Record Payment" to mapOf("EN" to "Record Payment", "తెలుగు" to "చెల్లింపు నమోదు చేయండి"),
        "Payment History" to mapOf("EN" to "Payment History", "తెలుగు" to "చెల్లింపు చరిత్ర"),
        "Expected Amount" to mapOf("EN" to "Expected Amount", "తెలుగు" to "ఆశించిన మొత్తం"),
        "Paid Amount" to mapOf("EN" to "Paid Amount", "తెలుగు" to "చెల్లించిన మొత్తం"),
        "Remaining Amount" to mapOf("EN" to "Remaining Amount", "తెలుగు" to "మిగిలిన మొత్తం"),
        "Remaining" to mapOf("EN" to "Remaining", "తెలుగు" to "మిగిలినది"),
        "One-time" to mapOf("EN" to "One-time", "తెలుగు" to "ఒక సారి"),
        "Recurring" to mapOf("EN" to "Recurring", "తెలుగు" to "పునరావృతం"),
        "Recurring Expenses" to mapOf("EN" to "Recurring Expenses", "తెలుగు" to "పునరావృత ఖర్చులు"),
        "Frequency" to mapOf("EN" to "Frequency", "తెలుగు" to "ఫ్రీక్వెన్సీ"),
        "Monthly" to mapOf("EN" to "Monthly", "తెలుగు" to "నెలవారీ"),
        "Yearly" to mapOf("EN" to "Yearly", "తెలుగు" to "వార్షిక"),
        "Expense Date" to mapOf("EN" to "Expense Date", "తెలుగు" to "ఖర్చు తేదీ"),
        "Payment Date" to mapOf("EN" to "Payment Date", "తెలుగు" to "చెల్లింపు తేదీ"),
        "Vendor" to mapOf("EN" to "Vendor", "తెలుగు" to "విక్రేత"),
        "Payee" to mapOf("EN" to "Payee", "తెలుగు" to "గ్రహీత"),
        "Category" to mapOf("EN" to "Category", "తెలుగు" to "వర్గం"),
        "Notes" to mapOf("EN" to "Notes", "తెలుగు" to "గమనికలు"),
        "Payment Method" to mapOf("EN" to "Payment Method", "తెలుగు" to "చెల్లింపు పద్ధతి"),
        "Reference" to mapOf("EN" to "Reference", "తెలుగు" to "రిఫరెన్స్"),
        "Electricity" to mapOf("EN" to "Electricity", "తెలుగు" to "విద్యుత్"),
        "Water" to mapOf("EN" to "Water", "తెలుగు" to "నీరు"),
        "Internet" to mapOf("EN" to "Internet", "తెలుగు" to "ఇంటర్నెట్"),
        "Maintenance" to mapOf("EN" to "Maintenance", "తెలుగు" to "నిర్వహణ"),
        "Cleaning" to mapOf("EN" to "Cleaning", "తెలుగు" to "శుభ్రత"),
        "Repairs" to mapOf("EN" to "Repairs", "తెలుగు" to "మరమ్మతులు"),
        "Staff" to mapOf("EN" to "Staff", "తెలుగు" to "సిబ్బంది"),
        "Staff Salary" to mapOf("EN" to "Staff Salary", "తెలుగు" to "సిబ్బంది జీతం"),
        "Supplies" to mapOf("EN" to "Supplies", "తెలుగు" to "సరఫరాలు"),
        "Food" to mapOf("EN" to "Food", "తెలుగు" to "ఆహారం"),
        "Furniture" to mapOf("EN" to "Furniture", "తెలుగు" to "ఫర్నిచర్"),
        "Security" to mapOf("EN" to "Security", "తెలుగు" to "భద్రత"),
        "Property Tax" to mapOf("EN" to "Property Tax", "తెలుగు" to "ఆస్తి పన్ను"),
        "Rent/Lease" to mapOf("EN" to "Rent/Lease", "తెలుగు" to "అద్దె/లీజు"),
        "Appliances" to mapOf("EN" to "Appliances", "తెలుగు" to "ఉపకరణాలు"),
        "Utilities" to mapOf("EN" to "Utilities", "తెలుగు" to "సదుపాయాలు"),
        "Other" to mapOf("EN" to "Other", "తెలుగు" to "ఇతర"),
        "Overdue" to mapOf("EN" to "Overdue", "తెలుగు" to "గడువు ముగిసింది"),

        // Reporting & Dashboard Translations
        "Tenants / Total PG Capacity" to mapOf("EN" to "Tenants / Total PG Capacity", "తెలుగు" to "అద్దెదారులు / మొత్తం PG సామర్థ్యం"),
        "Tenants / Total Capacity" to mapOf("EN" to "Tenants / Total Capacity", "తెలుగు" to "అద్దెదారులు / సామర్థ్యం"),
        "Rent Due" to mapOf("EN" to "Rent Due", "తెలుగు" to "చెల్లించాల్సిన అద్దె"),
        "Rent Received for This Month" to mapOf("EN" to "Rent Received for This Month", "తెలుగు" to "ఈ నెలకు వసూలైన అద్దె"),
        "Rent Received" to mapOf("EN" to "Rent Received", "తెలుగు" to "వసూలైన అద్దె"),
        "Rent Outstanding" to mapOf("EN" to "Rent Outstanding", "తెలుగు" to "బకాయి అద్దె"),
        "Balance After Expenses" to mapOf("EN" to "Balance After Expenses", "తెలుగు" to "ఖర్చుల తర్వాత నికర మొత్తం"),
        "Rent Still to Collect" to mapOf("EN" to "Rent Still to Collect", "తెలుగు" to "ఇంకా వసూలు చేయాల్సిన అద్దె"),
        "Previous Dues Received" to mapOf("EN" to "Previous Dues Received", "తెలుగు" to "గత బకాయిల వసూళ్లు"),
        "previous dues collected" to mapOf("EN" to "previous dues collected", "తెలుగు" to "మునుపటి బకాయిలు వసూలయ్యాయి"),
        "Advance / Credit" to mapOf("EN" to "Advance / Credit", "తెలుగు" to "అడ్వాన్స్ / క్రెడిట్"),
        "Money Left After Expenses" to mapOf("EN" to "Money Left After Expenses", "తెలుగు" to "ఖర్చుల తర్వాత మిగిలిన మొత్తం"),
        "Occupancy" to mapOf("EN" to "Occupancy", "తెలుగు" to "ఆక్రమణ"),
        "Vacancy" to mapOf("EN" to "Vacancy", "తెలుగు" to "ఖాళీ"),
        "Vacancies" to mapOf("EN" to "Vacancies", "తెలుగు" to "ఖాళీలు"),
        "What Needs Attention" to mapOf("EN" to "What Needs Attention", "తెలుగు" to "దృష్టి సారించాల్సినవి"),
        "Needs Attention" to mapOf("EN" to "Needs Attention", "తెలుగు" to "దృష్టి సారించాల్సినవి"),
        "Upcoming Move-outs" to mapOf("EN" to "Upcoming Move-outs", "తెలుగు" to "రాబోయే నిష్క్రమణలు"),
        "Moving out today" to mapOf("EN" to "Moving out today", "తెలుగు" to "ఈరోజే ఖాళీ చేస్తున్నారు"),
        "No upcoming move-outs scheduled" to mapOf("EN" to "No upcoming move-outs scheduled", "తెలుగు" to "షెడ్యూల్ చేసిన నిష్క్రమణలు లేవు"),
        "Financial Snapshot" to mapOf("EN" to "Financial Snapshot", "తెలుగు" to "ఆర్థిక సారాంశం"),
        "Owner Overview" to mapOf("EN" to "Owner Overview", "తెలుగు" to "యజమాని అవలోకనం"),
        "Monthly History" to mapOf("EN" to "Monthly History", "తెలుగు" to "నెలవారీ చరిత్ర"),
        "Rent Collection" to mapOf("EN" to "Rent Collection", "తెలుగు" to "అద్దె వసూలు"),
        "Tenant Statements" to mapOf("EN" to "Tenant Statements", "తెలుగు" to "అద్దెదారు స్టేట్‌మెంట్లు"),
        "Room Status" to mapOf("EN" to "Room Status", "తెలుగు" to "గది స్థితి"),
        "Custom Date Range" to mapOf("EN" to "Custom Date Range", "తెలుగు" to "కస్టమ్ తేదీ పరిధి"),
        "Export PDF" to mapOf("EN" to "Export PDF", "తెలుగు" to "PDF ఎగుమతి"),
        "Export Excel" to mapOf("EN" to "Export Excel", "తెలుగు" to "Excel ఎగుమతి"),
        "Data Freshness" to mapOf("EN" to "Data Freshness", "తెలుగు" to "డేటా తాజాదనం"),
        "Collection Rate" to mapOf("EN" to "Collection Rate", "తెలుగు" to "వసూలు రేటు"),
        "Total Capacity" to mapOf("EN" to "Total Capacity", "తెలుగు" to "మొత్తం సామర్థ్యం"),
        "Prorated" to mapOf("EN" to "Prorated", "తెలుగు" to "ప్రొరేటెడ్"),
        "Full Month" to mapOf("EN" to "Full Month", "తెలుగు" to "పూర్తి నెల"),

        // Common Buttons & Actions
        "Save" to mapOf("EN" to "Save", "తెలుగు" to "సేవ్ చేయండి"),
        "Save Changes" to mapOf("EN" to "Save Changes", "తెలుగు" to "మార్పులను సేవ్ చేయండి"),
        "Register Room" to mapOf("EN" to "Register Room", "తెలుగు" to "గదిని నమోదు చేయండి"),
        "Cancel" to mapOf("EN" to "Cancel", "తెలుగు" to "రద్దు చేయండి"),
        "Delete" to mapOf("EN" to "Delete", "తెలుగు" to "తొలగించు"),
        "Edit" to mapOf("EN" to "Edit", "తెలుగు" to "సవరించు"),
        "Back" to mapOf("EN" to "Back", "తెలుగు" to "వెనక్కి"),
        "Confirm" to mapOf("EN" to "Confirm", "తెలుగు" to "నిర్ధారించండి"),
        "Close" to mapOf("EN" to "Close", "తెలుగు" to "మూసివేయి"),
        "View All" to mapOf("EN" to "View All", "తెలుగు" to "అన్నీ చూడండి"),
        "See all" to mapOf("EN" to "See all", "తెలుగు" to "అన్నీ చూడండి"),
        "Quick Actions" to mapOf("EN" to "Quick Actions", "తెలుగు" to "త్వరిత చర్యలు"),
        "Overview" to mapOf("EN" to "Overview", "తెలుగు" to "అవలోకనం"),
        "This Month" to mapOf("EN" to "This Month", "తెలుగు" to "ఈ నెల"),
        "Rooms Occupied" to mapOf("EN" to "Rooms Occupied", "తెలుగు" to "గదులు ఆక్రమించబడ్డాయి"),
        "Recent Activity" to mapOf("EN" to "Recent Activity", "తెలుగు" to "ఇటీవలి కార్యకలాపాలు"),

        // Messages & Errors
        "Room not found" to mapOf("EN" to "Room not found", "తెలుగు" to "గది కనుగొనబడలేదు"),
        "Tenant not found" to mapOf("EN" to "Tenant not found", "తెలుగు" to "అద్దెదారు కనుగొనబడలేదు"),
        "Payment successful" to mapOf("EN" to "Payment successful", "తెలుగు" to "చెల్లింపు విజయవంతమైంది"),
        "Failed to save" to mapOf("EN" to "Failed to save", "తెలుగు" to "సేవ్ చేయడం విఫలమైంది"),
        "Are you sure you want to delete this?" to mapOf("EN" to "Are you sure you want to delete this?", "తెలుగు" to "మీరు దీన్ని ఖచ్చితంగా తొలగించాలనుకుంటున్నారా?"),

        // Settings Screen
        "Manage your app preferences" to mapOf("EN" to "Manage your app preferences", "తెలుగు" to "మీ యాప్ ప్రాధాన్యతలను నిర్వహించండి"),
        "PG Details" to mapOf("EN" to "PG Details", "తెలుగు" to "పిజి వివరాలు"),
        "Business Settings" to mapOf("EN" to "Business Settings", "తెలుగు" to "వ్యాపార సెట్టింగ్‌లు"),
        "Setup your PG details" to mapOf("EN" to "Setup your PG details", "తెలుగు" to "మీ పిజి వివరాలను సెటప్ చేయండి"),
        "Preferences" to mapOf("EN" to "Preferences", "తెలుగు" to "ప్రాధాన్యతలు"),
        "App Settings" to mapOf("EN" to "App Settings", "తెలుగు" to "యాప్ సెట్టింగ్‌లు"),
        "Theme, Language, Currency" to mapOf("EN" to "Theme, Language, Currency", "తెలుగు" to "థీమ్, భాష, కరెన్సీ"),
        "Manage alerts and reminders" to mapOf("EN" to "Manage alerts and reminders", "తెలుగు" to "హెచ్చరికలు మరియు రిమైండర్‌లను నిర్వహించండి"),
        "Security & Data" to mapOf("EN" to "Security & Data", "తెలుగు" to "భద్రత & డేటా"),
        "Security" to mapOf("EN" to "Security", "తెలుగు" to "భద్రత"),
        "App lock and biometrics" to mapOf("EN" to "App lock and biometrics", "తెలుగు" to "యాప్ లాక్ మరియు బయోమెట్రిక్స్"),
        "Backup & Restore" to mapOf("EN" to "Backup & Restore", "తెలుగు" to "బ్యాకప్ & పునరుద్ధరణ"),
        "Secure your data" to mapOf("EN" to "Secure your data", "తెలుగు" to "మీ డేటాను సురక్షితం చేయండి"),
        "Multi-Device Cloud Sync" to mapOf("EN" to "Multi-Device Cloud Sync", "తెలుగు" to "మల్టీ-డివైస్ క్లౌడ్ సింక్"),
        "Real-time sync, device identity & conflicts" to mapOf("EN" to "Real-time sync, device identity & conflicts", "తెలుగు" to "రియల్ టైమ్ సింక్, డివైస్ గుర్తింపు & వైరుధ్యాలు"),
        "Tools & Data" to mapOf("EN" to "Tools & Data", "తెలుగు" to "టూల్స్ & డేటా"),
        "Export to Excel" to mapOf("EN" to "Export to Excel", "తెలుగు" to "ఎక్సెల్‌కు ఎగుమతి చేయండి"),
        "Summary, tenants, payments, expenses & rooms" to mapOf("EN" to "Summary, tenants, payments, expenses & rooms", "తెలుగు" to "సారాంశం, అద్దెదారులు, చెల్లింపులు, ఖర్చులు & గదులు"),
        "Import from Excel" to mapOf("EN" to "Import from Excel", "తెలుగు" to "ఎక్సెల్ నుండి దిగుమతి చేయండి"),
        "Import existing tenants from spreadsheet" to mapOf("EN" to "Import existing tenants from spreadsheet", "తెలుగు" to "స్ప్రెడ్‌షీట్ నుండి ఇప్పటికే ఉన్న అద్దెదారులను దిగుమతి చేయండి"),
        "About" to mapOf("EN" to "About", "తెలుగు" to "గూర్చి"),
        "App version and information" to mapOf("EN" to "App version and information", "తెలుగు" to "యాప్ వెర్షన్ మరియు సమాచారం"),

        // Activity Log Translations
        "Tenant Checked In" to mapOf("EN" to "Tenant Checked In", "తెలుగు" to "అద్దెదారు చెక్ ఇన్ అయ్యారు"),
        "moved into" to mapOf("EN" to "moved into", "తెలుగు" to "గదిలోకి మారారు"),
        "Room" to mapOf("EN" to "Room", "తెలుగు" to "గది"),
        "Bed" to mapOf("EN" to "Bed", "తెలుగు" to "బెడ్"),
        "Rent Paid" to mapOf("EN" to "Rent Paid", "తెలుగు" to "అద్దె చెల్లించబడింది"),
        "Rent Partially Paid" to mapOf("EN" to "Rent Partially Paid", "తెలుగు" to "అద్దె కొంత చెల్లించబడింది"),
        "collected from" to mapOf("EN" to "collected from", "తెలుగు" to "నుండి వసూలు చేయబడింది"),
        "Expense Recorded" to mapOf("EN" to "Expense Recorded", "తెలుగు" to "ఖర్చు నమోదు చేయబడింది"),
        "for" to mapOf("EN" to "for", "తెలుగు" to "కోసం"),
        "Room Vacated" to mapOf("EN" to "Room Vacated", "తెలుగు" to "గది ఖాళీ చేయబడింది"),
        "vacated" to mapOf("EN" to "vacated", "తెలుగు" to "ఖాళీ చేశారు"),

        // Tenant Leaving Date & Upcoming Vacancy Translations
        "Leaving Date" to mapOf("EN" to "Leaving Date", "తెలుగు" to "నిష్క్రమణ తేదీ"),
        "Leaving Date (Optional)" to mapOf("EN" to "Leaving Date (Optional)", "తెలుగు" to "నిష్క్రమణ తేదీ (ఐచ్ఛికం)"),
        "Leaving Soon" to mapOf("EN" to "Leaving Soon", "తెలుగు" to "త్వరలో ఖాళీ"),
        "Notice Period" to mapOf("EN" to "Notice Period", "తెలుగు" to "నోటీస్ పీరియడ్"),
        "Upcoming Vacancies" to mapOf("EN" to "Upcoming Vacancies", "తెలుగు" to "రాబోయే ఖాళీలు"),
        "Scheduled to Vacate" to mapOf("EN" to "Scheduled to Vacate", "తెలుగు" to "ఖాళీ చేయడానికి షెడ్యూల్ చేయబడింది"),
        "Leaving on" to mapOf("EN" to "Leaving on", "తెలుగు" to "నిష్క్రమించే తేదీ"),
        "Vacating on" to mapOf("EN" to "Vacating on", "తెలుగు" to "ఖాళీ చేసే తేదీ"),
        "Vacating Soon" to mapOf("EN" to "Vacating Soon", "తెలుగు" to "త్వరలో ఖాళీ అవుతుంది"),
        "Set Leaving Date" to mapOf("EN" to "Set Leaving Date", "తెలుగు" to "నిష్క్రమణ తేదీని సెట్ చేయండి"),
        "Change Date" to mapOf("EN" to "Change Date", "తెలుగు" to "తేదీ మార్చండి"),
        "Clear Date" to mapOf("EN" to "Clear Date", "తెలుగు" to "తేదీని తొలగించండి"),
        "Save Date" to mapOf("EN" to "Save Date", "తెలుగు" to "తేదీని సేవ్ చేయండి"),
        "Clear Leaving Date" to mapOf("EN" to "Clear Leaving Date", "తెలుగు" to "నిష్క్రమణ తేదీని తొలగించండి"),
        "No upcoming vacancies scheduled" to mapOf("EN" to "No upcoming vacancies scheduled", "తెలుగు" to "రాబోయే ఖాళీలు ఏవీ షెడ్యూల్ చేయబడలేదు"),

        // Google Form Tenant Registration Translations
        "Tenant Registration Form" to mapOf("EN" to "Tenant Registration Form", "తెలుగు" to "అద్దెదారు నమోదు ఫారం"),
        "Google Forms" to mapOf("EN" to "Google Forms", "తెలుగు" to "గూగుల్ ఫారమ్‌లు"),
        "Connect Google" to mapOf("EN" to "Connect Google", "తెలుగు" to "గూగుల్ కనెక్ట్ చేయండి"),
        "Connected" to mapOf("EN" to "Connected", "తెలుగు" to "కనెక్ట్ చేయబడింది"),
        "Not Connected" to mapOf("EN" to "Not Connected", "తెలుగు" to "కనెక్ట్ చేయబడలేదు"),
        "Create Registration Form" to mapOf("EN" to "Create Registration Form", "తెలుగు" to "నమోదు ఫారం సృష్టించండి"),
        "Creating Registration Form..." to mapOf("EN" to "Creating Registration Form...", "తెలుగు" to "నమోదు ఫారం సృష్టిస్తోంది..."),
        "Registration Form" to mapOf("EN" to "Registration Form", "తెలుగు" to "నమోదు ఫారం"),
        "Active" to mapOf("EN" to "Active", "తెలుగు" to "యాక్టివ్"),
        "Unavailable" to mapOf("EN" to "Unavailable", "తెలుగు" to "అందుబాటులో లేదు"),
        "Share Form" to mapOf("EN" to "Share Form", "తెలుగు" to "ఫారం షేర్ చేయండి"),
        "Copy Link" to mapOf("EN" to "Copy Link", "తెలుగు" to "లింక్ కాపీ చేయండి"),
        "Open Form" to mapOf("EN" to "Open Form", "తెలుగు" to "ఫారం తెరవండి"),
        "Link copied to clipboard" to mapOf("EN" to "Link copied to clipboard", "తెలుగు" to "లింక్ క్లిప్‌బోర్డ్‌కి కాపీ చేయబడింది"),
        "Last checked" to mapOf("EN" to "Last checked", "తెలుగు" to "చివరిగా తనిఖీ చేయబడింది"),
        "Check Status" to mapOf("EN" to "Check Status", "తెలుగు" to "స్థితిని తనిఖీ చేయండి"),
        "Registration form unavailable." to mapOf("EN" to "Registration form unavailable.", "తెలుగు" to "నమోదు ఫారం అందుబాటులో లేదు."),
        "Create Replacement Form" to mapOf("EN" to "Create Replacement Form", "తెలుగు" to "ప్రత్యామ్నాయ ఫారం సృష్టించండి"),
        "Disconnect Google" to mapOf("EN" to "Disconnect Google", "తెలుగు" to "గూగుల్ డిస్‌కనెక్ట్ చేయండి"),
        "Reconnect Google" to mapOf("EN" to "Reconnect Google", "తెలుగు" to "గూగుల్ మళ్లీ కనెక్ట్ చేయండి"),
        "Questions Included in Registration Form" to mapOf("EN" to "Questions Included in Registration Form", "తెలుగు" to "నమోదు ఫారంలో చేర్చబడిన ప్రశ్నలు"),
        "Required Questions" to mapOf("EN" to "Required Questions", "తెలుగు" to "తప్పనిసరి ప్రశ్నలు"),
        "Optional Questions" to mapOf("EN" to "Optional Questions", "తెలుగు" to "ఐచ్ఛిక ప్రశ్నలు"),
        "Online Registration Form" to mapOf("EN" to "Online Registration Form", "తెలుగు" to "ఆన్‌లైన్ నమోదు ఫారం")
    )

    fun translate(text: String, lang: String = _languageFlow.value): String {
        val dict = translations[text]
        if (dict != null) {
            return dict[lang] ?: dict["EN"] ?: text
        }
        return text
    }
}

@Composable
fun rememberTranslation(key: String): String {
    val currentLang by AppLanguageManager.languageFlow.collectAsState()
    return remember(key, currentLang) {
        AppLanguageManager.translate(key, currentLang)
    }
}

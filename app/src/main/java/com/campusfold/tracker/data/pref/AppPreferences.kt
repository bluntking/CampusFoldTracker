package com.campusfold.tracker.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.campusfold.tracker.util.KeystoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "campus_fold_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val LAST_SMS_SYNC = longPreferencesKey("last_sms_sync_timestamp")
        private val OVERALL_MONTHLY_BUDGET = doublePreferencesKey("overall_monthly_budget")
        private val SAVED_AI_MODEL_PATH = stringPreferencesKey("saved_ai_model_path")
        private val GMAIL_SYNC_EMAIL = stringPreferencesKey("gmail_sync_email")
        private val GMAIL_SYNC_APP_PASSWORD = stringPreferencesKey("gmail_sync_app_password")
        private val GMAIL_LAST_SYNC_TIMESTAMP = longPreferencesKey("gmail_last_sync_timestamp")
        private val GMAIL_UID_VALIDITY = longPreferencesKey("gmail_uid_validity")
        private val GMAIL_LAST_UID = longPreferencesKey("gmail_last_uid")
        private val AUTO_ACCEPT_AI_RESULTS = booleanPreferencesKey("auto_accept_ai_results")
        private val AI_FALLBACK_PARSING = booleanPreferencesKey("ai_fallback_parsing_enabled")
        private val AI_FALLBACK_ACCOUNTS = booleanPreferencesKey("ai_fallback_accounts_enabled")
        private val AI_FALLBACK_VENDORS = booleanPreferencesKey("ai_fallback_vendors_enabled")
        private val AI_FALLBACK_CATEGORIES = booleanPreferencesKey("ai_fallback_categories_enabled")
        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val HIDE_BALANCES = booleanPreferencesKey("hide_balances")
        private val THEME_MODE = stringPreferencesKey("theme_mode") // "SYSTEM", "LIGHT", "DARK"
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
    }

    // ─── Onboarding & Appearance Preferences ──────────────────────────────────

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_COMPLETED_ONBOARDING] ?: false
    }

    suspend fun isCompletedOnboardingSync(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[HAS_COMPLETED_ONBOARDING] ?: false
    }

    suspend fun setCompletedOnboarding(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    val userName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME] ?: ""
    }

    suspend fun getUserNameSync(): String {
        val prefs = context.dataStore.data.first()
        return prefs[USER_NAME] ?: ""
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name.trim()
        }
    }

    val hideBalances: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HIDE_BALANCES] ?: false
    }

    suspend fun setHideBalances(hide: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HIDE_BALANCES] = hide
        }
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "SYSTEM"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR] ?: true
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = enabled
        }
    }

    val amoledMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AMOLED_MODE] ?: false
    }

    suspend fun setAmoledMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AMOLED_MODE] = enabled
        }
    }

    val autoAcceptAiResults: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_ACCEPT_AI_RESULTS] ?: false
    }

    suspend fun isAutoAcceptAiResultsSync(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[AUTO_ACCEPT_AI_RESULTS] ?: false
    }

    suspend fun setAutoAcceptAiResults(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_ACCEPT_AI_RESULTS] = enabled
        }
    }

    val aiFallbackParsingEnabled: Flow<Boolean> = context.dataStore.data.map { it[AI_FALLBACK_PARSING] ?: true }
    val aiFallbackAccountsEnabled: Flow<Boolean> = context.dataStore.data.map { it[AI_FALLBACK_ACCOUNTS] ?: true }
    val aiFallbackVendorsEnabled: Flow<Boolean> = context.dataStore.data.map { it[AI_FALLBACK_VENDORS] ?: true }
    val aiFallbackCategoriesEnabled: Flow<Boolean> = context.dataStore.data.map { it[AI_FALLBACK_CATEGORIES] ?: true }

    suspend fun isAiTaskEnabledSync(task: String): Boolean {
        val prefs = context.dataStore.data.first()
        return when (task) {
            "PARSE_UNSTRUCTURED" -> prefs[AI_FALLBACK_PARSING] ?: true
            "ACCOUNT_IDENTIFICATION", "ACCOUNT_IS_SAME" -> prefs[AI_FALLBACK_ACCOUNTS] ?: true
            "VENDOR_IS_SAME" -> prefs[AI_FALLBACK_VENDORS] ?: true
            "CATEGORIZE_VENDOR" -> prefs[AI_FALLBACK_CATEGORIES] ?: true
            else -> true
        }
    }

    suspend fun setAiTaskEnabled(task: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            when (task) {
                "PARSE_UNSTRUCTURED" -> prefs[AI_FALLBACK_PARSING] = enabled
                "ACCOUNT_IDENTIFICATION", "ACCOUNT_IS_SAME" -> prefs[AI_FALLBACK_ACCOUNTS] = enabled
                "VENDOR_IS_SAME" -> prefs[AI_FALLBACK_VENDORS] = enabled
                "CATEGORIZE_VENDOR" -> prefs[AI_FALLBACK_CATEGORIES] = enabled
            }
        }
    }

    val gmailSyncEmail: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GMAIL_SYNC_EMAIL]
    }

    /**
     * Decrypts the Gmail app password with Android Keystore on flow emissions.
     */
    val gmailSyncAppPassword: Flow<String?> = context.dataStore.data.map { preferences ->
        val raw = preferences[GMAIL_SYNC_APP_PASSWORD]
        raw?.let { KeystoreHelper.decrypt(it) ?: it }
    }.flowOn(Dispatchers.IO)

    val gmailLastSyncTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[GMAIL_LAST_SYNC_TIMESTAMP] ?: 0L
    }

    suspend fun getGmailCredentialsSync(): Pair<String?, String?> {
        val prefs = context.dataStore.data.first()
        val rawPassword = prefs[GMAIL_SYNC_APP_PASSWORD]
        val decryptedPassword = rawPassword?.let { KeystoreHelper.decrypt(it) ?: it }
        return Pair(prefs[GMAIL_SYNC_EMAIL], decryptedPassword)
    }

    suspend fun saveGmailCredentials(email: String, appPassword: String) {
        val sanitizedPassword = appPassword.replace(" ", "").trim()
        val encryptedPassword = KeystoreHelper.encrypt(sanitizedPassword)
        context.dataStore.edit { preferences ->
            preferences[GMAIL_SYNC_EMAIL] = email.trim()
            preferences[GMAIL_SYNC_APP_PASSWORD] = encryptedPassword
        }
    }

    suspend fun clearGmailCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(GMAIL_SYNC_EMAIL)
            preferences.remove(GMAIL_SYNC_APP_PASSWORD)
        }
    }

    suspend fun setGmailLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[GMAIL_LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    val savedAiModelPath: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SAVED_AI_MODEL_PATH]
    }

    suspend fun setSavedAiModelPath(path: String?) {
        context.dataStore.edit { preferences ->
            if (path != null) {
                preferences[SAVED_AI_MODEL_PATH] = path
            } else {
                preferences.remove(SAVED_AI_MODEL_PATH)
            }
        }
    }

    suspend fun getSavedAiModelPathSync(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[SAVED_AI_MODEL_PATH]
    }

    suspend fun getGmailUidSyncState(): Pair<Long, Long> {
        val prefs = context.dataStore.data.first()
        val uidValidity = prefs[GMAIL_UID_VALIDITY] ?: 0L
        val lastUid = prefs[GMAIL_LAST_UID] ?: 0L
        return Pair(uidValidity, lastUid)
    }

    suspend fun setGmailUidSyncState(uidValidity: Long, lastUid: Long) {
        context.dataStore.edit { preferences ->
            preferences[GMAIL_UID_VALIDITY] = uidValidity
            preferences[GMAIL_LAST_UID] = lastUid
        }
    }

    suspend fun resetGmailUidSyncState() {
        context.dataStore.edit { preferences ->
            preferences[GMAIL_LAST_UID] = 0L
        }
    }

    val lastSmsSyncTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_SMS_SYNC] ?: 0L
    }

    val overallMonthlyBudget: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[OVERALL_MONTHLY_BUDGET] ?: 20000.0
    }

    suspend fun setLastSmsSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SMS_SYNC] = timestamp
        }
    }

    suspend fun setOverallMonthlyBudget(budget: Double) {
        context.dataStore.edit { preferences ->
            preferences[OVERALL_MONTHLY_BUDGET] = budget
        }
    }

    suspend fun getOverallMonthlyBudgetSync(): Double {
        val prefs = context.dataStore.data.first()
        return prefs[OVERALL_MONTHLY_BUDGET] ?: 20000.0
    }

    // ─── Budget Alert Threshold Tracking (50, 70, 90, 100) ───────────────────

    suspend fun getCategoryAlertThresholdSync(yearMonth: String, categoryId: Long): Int {
        val key = intPreferencesKey("alert_${yearMonth}_cat_${categoryId}")
        val prefs = context.dataStore.data.first()
        return prefs[key] ?: 0
    }

    suspend fun setCategoryAlertThreshold(yearMonth: String, categoryId: Long, threshold: Int) {
        val key = intPreferencesKey("alert_${yearMonth}_cat_${categoryId}")
        context.dataStore.edit { preferences ->
            preferences[key] = threshold
        }
    }

    suspend fun getOverallAlertThresholdSync(yearMonth: String): Int {
        val key = intPreferencesKey("alert_${yearMonth}_overall")
        val prefs = context.dataStore.data.first()
        return prefs[key] ?: 0
    }

    suspend fun setOverallAlertThreshold(yearMonth: String, threshold: Int) {
        val key = intPreferencesKey("alert_${yearMonth}_overall")
        context.dataStore.edit { preferences ->
            preferences[key] = threshold
        }
    }
}

package com.campusfold.tracker.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "campus_fold_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val PNB_CREDIT_LIMIT = doublePreferencesKey("pnb_credit_limit")
        private val LAST_SMS_SYNC = longPreferencesKey("last_sms_sync_timestamp")
        private val OVERALL_MONTHLY_BUDGET = doublePreferencesKey("overall_monthly_budget")
    }

    val latestPnbCreditLimit: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[PNB_CREDIT_LIMIT] ?: 50000.0
    }

    val lastSmsSyncTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_SMS_SYNC] ?: 0L
    }

    val overallMonthlyBudget: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[OVERALL_MONTHLY_BUDGET] ?: 20000.0
    }

    suspend fun setPnbCreditLimit(limit: Double) {
        context.dataStore.edit { preferences ->
            preferences[PNB_CREDIT_LIMIT] = limit
        }
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

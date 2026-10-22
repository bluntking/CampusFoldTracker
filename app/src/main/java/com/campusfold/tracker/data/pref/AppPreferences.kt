package com.campusfold.tracker.data.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "campus_fold_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val PNB_CREDIT_LIMIT = doublePreferencesKey("pnb_credit_limit")
        private val LAST_SMS_SYNC = longPreferencesKey("last_sms_sync_timestamp")
    }

    val latestPnbCreditLimit: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[PNB_CREDIT_LIMIT] ?: 50000.0
    }

    val lastSmsSyncTimestamp: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_SMS_SYNC] ?: 0L
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
}

package com.example.ytshortsblocker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val DAILY_LIMIT_MINUTES = intPreferencesKey("daily_limit_minutes")
        val ENABLED = booleanPreferencesKey("enabled")
        val USAGE_DATE = stringPreferencesKey("usage_date")
        val USAGE_SECONDS = intPreferencesKey("usage_seconds")
    }

    companion object {
        const val DEFAULT_LIMIT_MINUTES = 30
        const val DEFAULT_ENABLED = true
        const val MAX_LIMIT_MINUTES = 24 * 60 // sanity cap: one day
    }

    private fun today(): String = LocalDate.now().toString()

    val dailyLimitMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.DAILY_LIMIT_MINUTES] ?: DEFAULT_LIMIT_MINUTES
    }

    val enabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ENABLED] ?: DEFAULT_ENABLED
    }

    val usageSecondsToday: Flow<Int> = context.dataStore.data.map { prefs ->
        if (prefs[Keys.USAGE_DATE] == today()) prefs[Keys.USAGE_SECONDS] ?: 0
        else 0
    }

    suspend fun setDailyLimitMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.DAILY_LIMIT_MINUTES] = minutes.coerceIn(1, MAX_LIMIT_MINUTES) }
    }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.ENABLED] = value }
    }

    suspend fun rolloverIfNewDay() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.USAGE_DATE] != today()) {
                prefs[Keys.USAGE_DATE] = today()
                prefs[Keys.USAGE_SECONDS] = 0
            }
        }
    }

    suspend fun addUsageSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            val startFrom = if (prefs[Keys.USAGE_DATE] == today()) prefs[Keys.USAGE_SECONDS] ?: 0
                            else 0
            prefs[Keys.USAGE_DATE] = today()
            prefs[Keys.USAGE_SECONDS] = startFrom + seconds
        }
    }
}

package com.example.ytshortsblocker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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

        /** History is stored as one key per day, e.g. "day_2026-08-11" -> seconds. */
        private const val DAY_PREFIX = "day_"
        private const val HISTORY_DAYS = 60L
    }

    private fun today(): String = LocalDate.now().toString()

    private fun dayKey(date: String) = intPreferencesKey("$DAY_PREFIX$date")

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

    /**
     * Every day we have a record for, newest first. Derived by scanning the "day_" keys rather
     * than kept as a separate list, so it can never drift out of sync with what was written.
     */
    val history: Flow<List<DayUsage>> = context.dataStore.data.map { prefs ->
        prefs.asMap()
            .mapNotNull { (key, value) ->
                if (!key.name.startsWith(DAY_PREFIX)) return@mapNotNull null
                val seconds = value as? Int ?: return@mapNotNull null
                DayUsage(date = key.name.removePrefix(DAY_PREFIX), seconds = seconds)
            }
            .sortedByDescending { it.date }
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
            pruneOldHistory(prefs)
        }
    }

    suspend fun addUsageSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            val startFrom = if (prefs[Keys.USAGE_DATE] == today()) prefs[Keys.USAGE_SECONDS] ?: 0
                            else 0
            val total = startFrom + seconds
            prefs[Keys.USAGE_DATE] = today()
            prefs[Keys.USAGE_SECONDS] = total
            // Same transaction, so the history can never disagree with the live counter.
            prefs[dayKey(today())] = total
        }
    }

    /**
     * Drops day records older than [HISTORY_DAYS]. ISO dates sort lexicographically in the same
     * order as chronologically, so a plain string comparison is enough.
     */
    private fun pruneOldHistory(prefs: MutablePreferences) {
        val cutoff = LocalDate.now().minusDays(HISTORY_DAYS).toString()
        prefs.asMap().keys
            .filter { it.name.startsWith(DAY_PREFIX) && it.name.removePrefix(DAY_PREFIX) < cutoff }
            .forEach { prefs.remove(it) }
    }
}

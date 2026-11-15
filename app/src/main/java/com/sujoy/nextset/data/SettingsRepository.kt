package com.sujoy.nextset.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("settings")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AlertMode(val sound: Boolean, val vibrate: Boolean) {
    SOUND_AND_VIBRATION(true, true),
    SOUND(true, false),
    VIBRATION(false, true),
    SILENT(false, false),
    DEVICE(true, true),
}

data class UserSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultRest: Int = 90,
    val reminders: Boolean = false,
    val manualReminderMinute: Int? = null,
    val alertMode: AlertMode = AlertMode.SOUND_AND_VIBRATION,
)

class SettingsRepository(context: Context) {
    private val store = context.settingsStore
    private val themeKey = stringPreferencesKey("theme")
    private val restKey = intPreferencesKey("rest_seconds")
    private val remindersKey = booleanPreferencesKey("reminders_enabled")
    private val minuteKey = intPreferencesKey("reminder_minute")
    private val alertModeKey = stringPreferencesKey("alert_mode")
    val settings =
        store.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map {
                UserSettings(
                    ThemeMode.entries.find { mode -> mode.name == it[themeKey] }
                        ?: ThemeMode.SYSTEM,
                    it[restKey] ?: 90,
                    it[remindersKey] ?: false,
                    it[minuteKey],
                    AlertMode.entries.find { mode -> mode.name == it[alertModeKey] }
                        ?: AlertMode.SOUND_AND_VIBRATION,
                )
            }

    suspend fun alertMode(mode: AlertMode) {
        store.edit { it[alertModeKey] = mode.name }
    }

    suspend fun theme(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.name }
    }

    suspend fun rest(seconds: Int) {
        require(seconds in 0..Int.MAX_VALUE)
        store.edit { it[restKey] = seconds }
    }

    suspend fun reminders(enabled: Boolean) {
        store.edit { it[remindersKey] = enabled }
    }

    suspend fun reminderMinute(minute: Int?) {
        require(minute == null || minute in 0..1439)
        store.edit { if (minute == null) it.remove(minuteKey) else it[minuteKey] = minute }
    }
}

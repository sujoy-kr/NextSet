package com.sujoy.nextset.data

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sujoy.nextset.domain.*
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

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

/** How the user confirms a step: tap anywhere, or hold for one second. */
enum class AdvanceMode {
    HOLD,
    TAP,
}

data class UserSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultRest: Int = 90,
    val reminders: Boolean = false,
    val manualReminderMinute: Int? = null,
    val alertMode: AlertMode = AlertMode.SOUND_AND_VIBRATION,
    val setAdvance: AdvanceMode = AdvanceMode.HOLD,
    val restAdvance: AdvanceMode = AdvanceMode.HOLD,
    val soundUri: String? = null,
    val routine: RoutinePlan = RoutinePlan(),
)

class SettingsRepository(context: Context) {
    private val store = context.settingsStore
    private val themeKey = stringPreferencesKey("theme")
    private val restKey = intPreferencesKey("rest_seconds")
    private val remindersKey = booleanPreferencesKey("reminders_enabled")
    private val minuteKey = intPreferencesKey("reminder_minute")
    private val alertModeKey = stringPreferencesKey("alert_mode")
    private val setAdvanceKey = stringPreferencesKey("set_advance")
    private val restAdvanceKey = stringPreferencesKey("rest_advance")
    private val soundKey = stringPreferencesKey("sound_uri")
    private val routineModeKey = stringPreferencesKey("routine_mode")
    private val routineEntriesKey = stringPreferencesKey("routine_entries")
    private val routineStartKey = stringPreferencesKey("routine_start")
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
                    setAdvance =
                        AdvanceMode.entries.find { m -> m.name == it[setAdvanceKey] }
                            ?: AdvanceMode.HOLD,
                    restAdvance =
                        AdvanceMode.entries.find { m -> m.name == it[restAdvanceKey] }
                            ?: AdvanceMode.HOLD,
                    soundUri = it[soundKey],
                    routine =
                        RoutinePlan(
                            mode =
                                PlanMode.entries.find { mode -> mode.name == it[routineModeKey] }
                                    ?: PlanMode.WEEKDAYS,
                            entries =
                                JSONArray(it[routineEntriesKey] ?: "[]").let { values ->
                                    (0 until values.length()).map { i ->
                                        if (values.isNull(i)) null else values.getString(i)
                                    }
                                },
                            startDate =
                                it[routineStartKey]?.let(LocalDate::parse)
                                    ?: LocalDate.ofEpochDay(0),
                        ),
                )
            }

    suspend fun setAdvance(mode: AdvanceMode) {
        store.edit { it[setAdvanceKey] = mode.name }
    }

    suspend fun restAdvance(mode: AdvanceMode) {
        store.edit { it[restAdvanceKey] = mode.name }
    }

    suspend fun sound(uri: String?) {
        require(uri == null || uri.toUri().scheme in listOf("content", "android.resource"))
        store.edit { if (uri == null) it.remove(soundKey) else it[soundKey] = uri }
    }

    suspend fun routine(plan: RoutinePlan) {
        require(plan.entries.all { it == null || it.isNotBlank() })
        store.edit {
            it[routineModeKey] = plan.mode.name
            it[routineEntriesKey] =
                JSONArray()
                    .apply { plan.entries.forEach { id -> put(id ?: JSONObject.NULL) } }
                    .toString()
            it[routineStartKey] = plan.startDate.toString()
        }
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

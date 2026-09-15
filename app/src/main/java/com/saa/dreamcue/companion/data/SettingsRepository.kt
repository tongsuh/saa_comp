package com.saa.dreamcue.companion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dreamcue_settings")

data class DreamCueSettings(
    val audioUri: String = "", // empty means use built-in gentle_chime
    val audioTotalSeconds: Int = 10,
    val audioVolumePercent: Int = 40,
    val audioFadeInSeconds: Int = 3,
    val audioFadeOutSeconds: Int = 3,
    val vibrationTotalSeconds: Int = 6,
    val cooldownMinutes: Int = 30,
    val lastTriggerTimestamp: Long = 0L,
    val isGuardActive: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val KEY_AUDIO_URI = stringPreferencesKey("audio_uri")
        val KEY_AUDIO_TOTAL_SECONDS = intPreferencesKey("audio_total_seconds")
        val KEY_AUDIO_VOLUME_PERCENT = intPreferencesKey("audio_volume_percent")
        val KEY_AUDIO_FADE_IN_SECONDS = intPreferencesKey("audio_fade_in_seconds")
        val KEY_AUDIO_FADE_OUT_SECONDS = intPreferencesKey("audio_fade_out_seconds")
        val KEY_VIBRATION_TOTAL_SECONDS = intPreferencesKey("vibration_total_seconds")
        val KEY_COOLDOWN_MINUTES = intPreferencesKey("cooldown_minutes")
        val KEY_LAST_TRIGGER_TIMESTAMP = longPreferencesKey("last_trigger_timestamp")
        val KEY_IS_GUARD_ACTIVE = booleanPreferencesKey("is_guard_active")
    }

    val settingsFlow: Flow<DreamCueSettings> = context.dataStore.data.map { preferences ->
        DreamCueSettings(
            audioUri = preferences[PreferencesKeys.KEY_AUDIO_URI] ?: "",
            audioTotalSeconds = preferences[PreferencesKeys.KEY_AUDIO_TOTAL_SECONDS] ?: 10,
            audioVolumePercent = preferences[PreferencesKeys.KEY_AUDIO_VOLUME_PERCENT] ?: 40,
            audioFadeInSeconds = preferences[PreferencesKeys.KEY_AUDIO_FADE_IN_SECONDS] ?: 3,
            audioFadeOutSeconds = preferences[PreferencesKeys.KEY_AUDIO_FADE_OUT_SECONDS] ?: 3,
            vibrationTotalSeconds = preferences[PreferencesKeys.KEY_VIBRATION_TOTAL_SECONDS] ?: 6,
            cooldownMinutes = preferences[PreferencesKeys.KEY_COOLDOWN_MINUTES] ?: 30,
            lastTriggerTimestamp = preferences[PreferencesKeys.KEY_LAST_TRIGGER_TIMESTAMP] ?: 0L,
            isGuardActive = preferences[PreferencesKeys.KEY_IS_GUARD_ACTIVE] ?: false
        )
    }

    suspend fun getSettings(): DreamCueSettings = settingsFlow.first()

    suspend fun updateAudioSettings(
        audioUri: String,
        totalSeconds: Int,
        volumePercent: Int,
        fadeIn: Int,
        fadeOut: Int
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_AUDIO_URI] = audioUri
            preferences[PreferencesKeys.KEY_AUDIO_TOTAL_SECONDS] = totalSeconds
            preferences[PreferencesKeys.KEY_AUDIO_VOLUME_PERCENT] = volumePercent
            preferences[PreferencesKeys.KEY_AUDIO_FADE_IN_SECONDS] = fadeIn
            preferences[PreferencesKeys.KEY_AUDIO_FADE_OUT_SECONDS] = fadeOut
        }
    }

    suspend fun updateVibrationSettings(totalSeconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_VIBRATION_TOTAL_SECONDS] = totalSeconds
        }
    }

    suspend fun updateCooldown(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_COOLDOWN_MINUTES] = minutes
        }
    }

    suspend fun updateLastTriggerTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_LAST_TRIGGER_TIMESTAMP] = timestamp
        }
    }

    suspend fun updateGuardActive(active: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_IS_GUARD_ACTIVE] = active
        }
    }
}

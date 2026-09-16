package com.saa.dreamcue.companion.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saa.dreamcue.companion.data.DreamCueSettings
import com.saa.dreamcue.companion.data.SettingsRepository
import com.saa.dreamcue.companion.service.DreamCueGuardService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DreamCueViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<DreamCueSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DreamCueSettings()
    )

    val isGuardRunning: StateFlow<Boolean> = DreamCueGuardService.isRunning
    val isExecutingCue: StateFlow<Boolean> = DreamCueGuardService.isExecutingCue
    val cooldownRemainingSeconds: StateFlow<Int> = DreamCueGuardService.cooldownRemainingSeconds
    val isBleScanning: StateFlow<Boolean> = DreamCueGuardService.isBleScanning
    val lastTriggerSource: StateFlow<String> = DreamCueGuardService.lastTriggerSource

    fun startGuard(context: Context) {
        val intent = Intent(context, DreamCueGuardService::class.java).apply {
            action = DreamCueGuardService.ACTION_START_GUARD
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopGuard(context: Context) {
        val intent = Intent(context, DreamCueGuardService::class.java).apply {
            action = DreamCueGuardService.ACTION_STOP_GUARD
        }
        context.startService(intent)
    }

    fun triggerTestPreview(context: Context) {
        val intent = Intent(context, DreamCueGuardService::class.java).apply {
            action = DreamCueGuardService.ACTION_TEST_TRIGGER
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun updateAudio(uri: String, totalSeconds: Int, volumePercent: Int, fadeIn: Int, fadeOut: Int) {
        viewModelScope.launch {
            repository.updateAudioSettings(uri, totalSeconds, volumePercent, fadeIn, fadeOut)
        }
    }

    fun updateVibration(totalSeconds: Int) {
        viewModelScope.launch {
            repository.updateVibrationSettings(totalSeconds)
        }
    }

    fun updateCooldown(minutes: Int) {
        viewModelScope.launch {
            repository.updateCooldown(minutes)
        }
    }

    fun resetCooldown() {
        viewModelScope.launch {
            repository.updateLastTriggerTimestamp(0L)
        }
    }

    fun updateBleSettings(enabled: Boolean, targetUuid: String) {
        viewModelScope.launch {
            repository.updateBleSettings(enabled, targetUuid)
        }
    }

    fun updateSaaBroadcastEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSaaBroadcastEnabled(enabled)
        }
    }
}

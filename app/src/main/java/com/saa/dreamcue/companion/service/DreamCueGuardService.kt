package com.saa.dreamcue.companion.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.saa.dreamcue.companion.MainActivity
import com.saa.dreamcue.companion.R
import com.saa.dreamcue.companion.data.SettingsRepository
import com.saa.dreamcue.companion.engine.AudioFadeController
import com.saa.dreamcue.companion.engine.HuaweiPulseController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DreamCueGuardService : Service() {

    companion object {
        private const val TAG = "DreamCueGuardService"
        private const val GUARD_CHANNEL_ID = "dreamcue_guard_channel"
        private const val NOTIFICATION_ID = 10001

        const val ACTION_START_GUARD = "com.saa.dreamcue.START_GUARD"
        const val ACTION_STOP_GUARD = "com.saa.dreamcue.STOP_GUARD"
        const val ACTION_TEST_TRIGGER = "com.saa.dreamcue.TEST_TRIGGER"

        // Sleep as Android official broadcast intent
        const val ACTION_SAA_LUCID_CUE = "com.urbandroid.sleep.LUCID_CUE_ACTION"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isExecutingCue = MutableStateFlow(false)
        val isExecutingCue: StateFlow<Boolean> = _isExecutingCue.asStateFlow()

        private val _cooldownRemainingSeconds = MutableStateFlow(0)
        val cooldownRemainingSeconds: StateFlow<Int> = _cooldownRemainingSeconds.asStateFlow()
    }

    inner class LocalBinder : Binder() {
        fun getService(): DreamCueGuardService = this@DreamCueGuardService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var audioController: AudioFadeController
    private lateinit var pulseController: HuaweiPulseController

    private var wakeLock: PowerManager.WakeLock? = null
    private var isReceiverRegistered = false
    private var activeExecutionJob: Job? = null
    private var cooldownTickerJob: Job? = null

    private val lucidReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SAA_LUCID_CUE) {
                Log.i(TAG, "Captured Sleep as Android lucid dream broadcast: ${intent.action}")
                handleLucidCueTrigger(isTest = false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        audioController = AudioFadeController(applicationContext)
        pulseController = HuaweiPulseController(applicationContext)

        acquireWakeLock()
        createGuardNotificationChannel()
        registerLucidReceiver()
        startCooldownTicker()

        _isRunning.value = true
        Log.i(TAG, "DreamCueGuardService started and initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_GUARD -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TEST_TRIGGER -> {
                Log.i(TAG, "Received test trigger request from UI")
                handleLucidCueTrigger(isTest = true)
            }
            else -> {
                val notification = buildForegroundNotification("清醒梦伴侣守护中", "已挂载 SaA 监听与华为手环8高频微脉冲通道")
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        _isExecutingCue.value = false
        unregisterLucidReceiver()
        activeExecutionJob?.cancel()
        cooldownTickerJob?.cancel()
        audioController.stop()
        pulseController.cancelAllActive()
        releaseWakeLock()
        serviceScope.cancel()
        Log.i(TAG, "DreamCueGuardService destroyed and resources released")
    }

    fun triggerTestPreview() {
        handleLucidCueTrigger(isTest = true)
    }

    private fun handleLucidCueTrigger(isTest: Boolean) {
        serviceScope.launch(Dispatchers.Default) {
            val settings = settingsRepository.getSettings()

            if (!isTest) {
                val now = System.currentTimeMillis()
                val cooldownMs = settings.cooldownMinutes * 60 * 1000L
                val timeSinceLast = now - settings.lastTriggerTimestamp

                if (timeSinceLast < cooldownMs) {
                    val remainingMins = ((cooldownMs - timeSinceLast) / 60000L).coerceAtLeast(1)
                    Log.w(TAG, "Lucid cue dropped due to cooldown lock ($remainingMins mins remaining)")
                    return@launch
                }

                // Update last trigger time
                settingsRepository.updateLastTriggerTimestamp(now)
            }

            // Cancel any previous running cue
            activeExecutionJob?.cancel()

            activeExecutionJob = launch {
                _isExecutingCue.value = true
                Log.i(TAG, "Executing dream cue dual-channel action (isTest=$isTest)")

                val audioJob = launch {
                    audioController.playWithFade(
                        audioUriStr = settings.audioUri,
                        totalSeconds = settings.audioTotalSeconds,
                        maxVolumePercent = settings.audioVolumePercent,
                        fadeInSeconds = settings.audioFadeInSeconds,
                        fadeOutSeconds = settings.audioFadeOutSeconds
                    )
                }

                val pulseJob = launch {
                    pulseController.executePulseVibration(
                        totalSeconds = settings.vibrationTotalSeconds
                    )
                }

                audioJob.join()
                pulseJob.join()
                _isExecutingCue.value = false
                Log.i(TAG, "Completed dream cue dual-channel execution")
            }
        }
    }

    private fun startCooldownTicker() {
        cooldownTickerJob?.cancel()
        cooldownTickerJob = serviceScope.launch {
            while (isActive) {
                val settings = settingsRepository.getSettings()
                val now = System.currentTimeMillis()
                val cooldownMs = settings.cooldownMinutes * 60 * 1000L
                val elapsed = now - settings.lastTriggerTimestamp
                val remaining = if (elapsed < cooldownMs) {
                    ((cooldownMs - elapsed) / 1000L).toInt()
                } else {
                    0
                }
                _cooldownRemainingSeconds.value = remaining
                delay(1000)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerLucidReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(ACTION_SAA_LUCID_CUE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(lucidReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(lucidReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "Registered broadcast receiver for $ACTION_SAA_LUCID_CUE")
        }
    }

    private fun unregisterLucidReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(lucidReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
            isReceiverRegistered = false
        }
    }

    private fun createGuardNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GUARD_CHANNEL_ID,
                "清醒梦伴侣保活守护",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保证半夜锁屏下应用后台常驻，不被系统休眠"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, GUARD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dream_pulse)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DreamCue:CompanionWakeLock").apply {
                acquire()
            }
            Log.d(TAG, "Partial WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Partial WakeLock released")
            }
        }
        wakeLock = null
    }
}

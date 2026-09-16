package com.saa.dreamcue.companion.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
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
import com.saa.dreamcue.companion.engine.BleScanController
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
import java.util.concurrent.atomic.AtomicLong

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

        private val _isBleScanning = MutableStateFlow(false)
        val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()

        private val _lastTriggerSource = MutableStateFlow("尚未触发")
        val lastTriggerSource: StateFlow<String> = _lastTriggerSource.asStateFlow()
    }

    inner class LocalBinder : Binder() {
        fun getService(): DreamCueGuardService = this@DreamCueGuardService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var audioController: AudioFadeController
    private lateinit var pulseController: HuaweiPulseController
    private lateinit var bleScanController: BleScanController

    private var wakeLock: PowerManager.WakeLock? = null
    private var isLucidReceiverRegistered = false
    private var isBtReceiverRegistered = false
    private var activeExecutionJob: Job? = null
    private var cooldownTickerJob: Job? = null

    // In-memory atomic timestamp to prevent packet storm race conditions
    private val inMemoryLastTriggerTime = AtomicLong(0L)

    private val lucidReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SAA_LUCID_CUE) {
                Log.i(TAG, "Captured Sleep as Android lucid dream broadcast")
                handleLucidCueTrigger(source = "Sleep as Android 广播", isTest = false)
            }
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    Log.i(TAG, "Bluetooth turned ON: auto-resuming BLE scanning if enabled")
                    serviceScope.launch {
                        val settings = settingsRepository.getSettings()
                        if (settings.bleScanEnabled) {
                            val started = bleScanController.startScanning(settings.targetServiceUuid)
                            _isBleScanning.value = started
                        }
                    }
                } else if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                    Log.i(TAG, "Bluetooth turned OFF: pausing scanner")
                    _isBleScanning.value = false
                    bleScanController.stopScanning()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        audioController = AudioFadeController(applicationContext)
        pulseController = HuaweiPulseController(applicationContext)

        bleScanController = BleScanController(applicationContext) { scanResult ->
            val deviceAddress = scanResult.device?.address ?: "Unknown"
            Log.i(TAG, "Captured BLE Advertising packet with target UUID from $deviceAddress")
            handleLucidCueTrigger(source = "BLE 硬件广播 ($deviceAddress)", isTest = false)
        }

        acquireWakeLock()
        createGuardNotificationChannel()
        registerBluetoothStateReceiver()
        startCooldownTicker()

        _isRunning.value = true
        Log.i(TAG, "DreamCueGuardService created and initialized")
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
                handleLucidCueTrigger(source = "手动调试测试", isTest = true)
            }
            else -> {
                val notification = buildForegroundNotification(
                    title = "清醒梦双模守护中",
                    content = "已挂载 SaA 广播监听与低功耗蓝牙硬件级过滤扫描"
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                // Apply settings to configure triggers
                serviceScope.launch {
                    val settings = settingsRepository.getSettings()

                    if (settings.saaBroadcastEnabled) {
                        registerLucidReceiver()
                    } else {
                        unregisterLucidReceiver()
                    }

                    if (settings.bleScanEnabled) {
                        val started = bleScanController.startScanning(settings.targetServiceUuid)
                        _isBleScanning.value = started
                        Log.i(TAG, "BLE Scanner active: $started for UUID ${settings.targetServiceUuid}")
                    } else {
                        bleScanController.stopScanning()
                        _isBleScanning.value = false
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        _isExecutingCue.value = false
        _isBleScanning.value = false
        unregisterLucidReceiver()
        unregisterBluetoothStateReceiver()
        bleScanController.stopScanning()
        activeExecutionJob?.cancel()
        cooldownTickerJob?.cancel()
        audioController.stop()
        pulseController.cancelAllActive()
        releaseWakeLock()
        serviceScope.cancel()
        Log.i(TAG, "DreamCueGuardService destroyed and resources released")
    }

    fun triggerTestPreview() {
        handleLucidCueTrigger(source = "手动调试测试", isTest = true)
    }

    fun updateBleScanState(enabled: Boolean, targetUuid: String) {
        serviceScope.launch {
            if (enabled) {
                val started = bleScanController.startScanning(targetUuid)
                _isBleScanning.value = started
            } else {
                bleScanController.stopScanning()
                _isBleScanning.value = false
            }
        }
    }

    fun updateSaaReceiverState(enabled: Boolean) {
        if (enabled) {
            registerLucidReceiver()
        } else {
            unregisterLucidReceiver()
        }
    }

    private fun handleLucidCueTrigger(source: String, isTest: Boolean) {
        val now = System.currentTimeMillis()

        if (!isTest) {
            // Immediate atomic fast-check to drop BLE burst packets in milliseconds
            val fastLastTime = inMemoryLastTriggerTime.get()
            if (fastLastTime != 0L && (now - fastLastTime < 5000L)) {
                return
            }
        }

        serviceScope.launch(Dispatchers.Default) {
            val settings = settingsRepository.getSettings()

            if (!isTest) {
                val cooldownMs = settings.cooldownMinutes * 60 * 1000L
                val lastTime = inMemoryLastTriggerTime.get().coerceAtLeast(settings.lastTriggerTimestamp)
                val timeSinceLast = now - lastTime

                if (lastTime != 0L && timeSinceLast < cooldownMs) {
                    val remainingMins = ((cooldownMs - timeSinceLast) / 60000L).coerceAtLeast(1)
                    Log.w(TAG, "Trigger from '$source' dropped: inside ${settings.cooldownMinutes}m cooldown ($remainingMins mins remaining)")
                    return@launch
                }

                inMemoryLastTriggerTime.set(now)
                settingsRepository.updateLastTriggerTimestamp(now)
            }

            _lastTriggerSource.value = "$source (${formatCurrentTime()})"

            // Cancel any previous running cue
            activeExecutionJob?.cancel()

            activeExecutionJob = launch {
                _isExecutingCue.value = true
                Log.i(TAG, "Executing dream cue dual-channel action (source=$source, isTest=$isTest)")

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
                Log.i(TAG, "Completed dream cue dual-channel execution from $source")
            }
        }
    }

    private fun formatCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    private fun startCooldownTicker() {
        cooldownTickerJob?.cancel()
        cooldownTickerJob = serviceScope.launch {
            while (isActive) {
                val settings = settingsRepository.getSettings()
                val now = System.currentTimeMillis()
                val cooldownMs = settings.cooldownMinutes * 60 * 1000L
                val lastTime = inMemoryLastTriggerTime.get().coerceAtLeast(settings.lastTriggerTimestamp)
                val elapsed = now - lastTime
                val remaining = if (lastTime != 0L && elapsed < cooldownMs) {
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
        if (!isLucidReceiverRegistered) {
            val filter = IntentFilter(ACTION_SAA_LUCID_CUE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(lucidReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(lucidReceiver, filter)
            }
            isLucidReceiverRegistered = true
            Log.d(TAG, "Registered broadcast receiver for $ACTION_SAA_LUCID_CUE")
        }
    }

    private fun unregisterLucidReceiver() {
        if (isLucidReceiverRegistered) {
            try {
                unregisterReceiver(lucidReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering lucid receiver", e)
            }
            isLucidReceiverRegistered = false
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerBluetoothStateReceiver() {
        if (!isBtReceiverRegistered) {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(bluetoothStateReceiver, filter)
            isBtReceiverRegistered = true
            Log.d(TAG, "Registered bluetoothStateReceiver")
        }
    }

    private fun unregisterBluetoothStateReceiver() {
        if (isBtReceiverRegistered) {
            try {
                unregisterReceiver(bluetoothStateReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering bluetoothStateReceiver", e)
            }
            isBtReceiverRegistered = false
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

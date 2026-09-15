package com.saa.dreamcue.companion.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.saa.dreamcue.companion.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Collections

class HuaweiPulseController(private val context: Context) {

    companion object {
        private const val TAG = "HuaweiPulseController"
        const val PULSE_CHANNEL_ID = "huawei_band_pulse_channel"
        private const val PULSE_INTERVAL_MS = 1600L
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val activeNotificationIds = Collections.synchronizedList(mutableListOf<Int>())

    init {
        createPulseNotificationChannel()
    }

    private fun createPulseNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = notificationManager.getNotificationChannel(PULSE_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    PULSE_CHANNEL_ID,
                    "梦境触觉脉冲 (华为手环8专用)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "专门用于向华为手环下发物理微震动，已彻底静音，绝无通知杂音"
                    setSound(null, null) // Silent to prevent phone chime
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800) // 800ms vibration per pulse
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Created silent vibration notification channel")
            }
        }
    }

    suspend fun executePulseVibration(totalSeconds: Int) = withContext(Dispatchers.Default) {
        cancelAllActive()

        val totalMs = totalSeconds * 1000L
        val startTime = System.currentTimeMillis()
        var pulseCounter = 0

        Log.d(TAG, "Starting Huawei Band 8 pulse cycle for ${totalSeconds}s")

        try {
            while (isActive && (System.currentTimeMillis() - startTime < totalMs)) {
                pulseCounter++
                val notificationId = 20000 + pulseCounter
                activeNotificationIds.add(notificationId)

                val uniqueTimestamp = System.currentTimeMillis() % 100000
                val notification = NotificationCompat.Builder(context, PULSE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_dream_pulse)
                    .setContentTitle("梦境感知脉冲")
                    .setContentText("保持清醒知梦觉察 #$pulseCounter ($uniqueTimestamp)")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setOnlyAlertOnce(false)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(notificationId, notification)
                Log.d(TAG, "Dispatched pulse #$pulseCounter (id: $notificationId)")

                delay(PULSE_INTERVAL_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pulse vibration interrupted or failed", e)
        } finally {
            // Wait 500ms for wearable to process final vibration before clearing drawer
            delay(500)
            cancelAllActive()
            Log.d(TAG, "Huawei Band 8 pulse cycle finished, cleaned up all notifications")
        }
    }

    fun cancelAllActive() {
        synchronized(activeNotificationIds) {
            for (id in activeNotificationIds) {
                try {
                    notificationManager.cancel(id)
                } catch (e: Exception) {
                    Log.w(TAG, "Error cancelling notification $id", e)
                }
            }
            activeNotificationIds.clear()
        }
    }
}

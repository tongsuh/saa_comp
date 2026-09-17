package com.saa.dreamcue.companion.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saa.dreamcue.companion.ui.theme.CyanAccent
import com.saa.dreamcue.companion.ui.theme.DarkBorder
import com.saa.dreamcue.companion.ui.theme.DarkCard
import com.saa.dreamcue.companion.ui.theme.DarkSurface
import com.saa.dreamcue.companion.ui.theme.GoldAccent
import com.saa.dreamcue.companion.ui.theme.GoldDim
import com.saa.dreamcue.companion.ui.theme.GreenActive
import com.saa.dreamcue.companion.ui.theme.PureBlack
import com.saa.dreamcue.companion.ui.theme.RedStop
import com.saa.dreamcue.companion.ui.theme.TextPrimary
import com.saa.dreamcue.companion.ui.theme.TextSecondary
import com.saa.dreamcue.companion.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DreamCueScreen(
    viewModel: DreamCueViewModel,
    onRequestPermissions: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToggleScreensaverBrightness: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val isRunning by viewModel.isGuardRunning.collectAsState()
    val isExecuting by viewModel.isExecutingCue.collectAsState()
    val cooldownRemaining by viewModel.cooldownRemainingSeconds.collectAsState()
    val protectionRemaining by viewModel.protectionRemainingSeconds.collectAsState()
    val isBleScanning by viewModel.isBleScanning.collectAsState()
    val lastTriggerSource by viewModel.lastTriggerSource.collectAsState()
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val hasBtPermission by viewModel.hasBtPermission.collectAsState()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsState()

    var isScreensaverActive by remember { mutableStateOf(false) }

    // When guard stops, automatically exit screensaver
    LaunchedEffect(isRunning) {
        if (!isRunning) {
            isScreensaverActive = false
        }
    }

    // Keep screen brightness in sync with screensaver state
    DisposableEffect(isScreensaverActive) {
        onToggleScreensaverBrightness(isScreensaverActive)
        onDispose {
            onToggleScreensaverBrightness(false)
        }
    }

    // Hardware / gesture back handler when screensaver is active
    BackHandler(enabled = isScreensaverActive) {
        isScreensaverActive = false
    }

    if (isScreensaverActive) {
        ScreensaverOverlay(
            isExecuting = isExecuting,
            cooldownRemaining = cooldownRemaining,
            protectionRemaining = protectionRemaining,
            onStopCue = { viewModel.stopExecutingCue(context) },
            onExitScreensaver = { isScreensaverActive = false },
            onStopGuard = {
                viewModel.stopGuard(context)
                isScreensaverActive = false
            }
        )
        return
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported
            }
            viewModel.updateAudio(
                uri = uri.toString(),
                totalSeconds = settings.audioTotalSeconds,
                volumePercent = settings.audioVolumePercent,
                fadeIn = settings.audioFadeInSeconds,
                fadeOut = settings.audioFadeOutSeconds
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PureBlack
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "触梦伴侣",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SaA & 蓝牙硬件双模",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    StatusIndicator(isRunning = isRunning, isExecuting = isExecuting)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Missing Permission Warning Banner
                if (!hasPermissions) {
                    PermissionWarningCard(
                        hasBtPermission = hasBtPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestPermissions = onRequestPermissions,
                        onOpenSettings = onOpenSettings
                    )
                }

                // Status & Cooldown Card
                StatusCard(
                    isRunning = isRunning,
                    isExecuting = isExecuting,
                    isBleScanning = isBleScanning,
                    lastTriggerSource = lastTriggerSource,
                    cooldownRemaining = cooldownRemaining,
                    protectionRemaining = protectionRemaining,
                    onResetCooldown = { viewModel.resetCooldown(context) },
                    onSkipProtection = { viewModel.skipInitialProtection(context) }
                )

                // BLE Hardware Trigger Card
                BleConfigCard(
                    bleScanEnabled = settings.bleScanEnabled,
                    targetUuid = settings.targetServiceUuid,
                    isBleScanning = isBleScanning,
                    onBleScanEnabledChange = { enabled ->
                        viewModel.updateBleSettings(enabled, settings.targetServiceUuid)
                    }
                )

                // SaA Trigger Switch Card
                SaaConfigCard(
                    saaEnabled = settings.saaBroadcastEnabled,
                    onSaaEnabledChange = { viewModel.updateSaaBroadcastEnabled(it) }
                )

                // Audio Settings Card
                AudioConfigCard(
                    audioUri = settings.audioUri,
                    totalSeconds = settings.audioTotalSeconds,
                    volumePercent = settings.audioVolumePercent,
                    fadeIn = settings.audioFadeInSeconds,
                    fadeOut = settings.audioFadeOutSeconds,
                    onPickAudio = { filePickerLauncher.launch(arrayOf("audio/*")) },
                    onResetAudio = {
                        viewModel.updateAudio(
                            uri = "",
                            totalSeconds = settings.audioTotalSeconds,
                            volumePercent = settings.audioVolumePercent,
                            fadeIn = settings.audioFadeInSeconds,
                            fadeOut = settings.audioFadeOutSeconds
                        )
                    },
                    onTotalSecondsChange = {
                        viewModel.updateAudio(
                            uri = settings.audioUri,
                            totalSeconds = it,
                            volumePercent = settings.audioVolumePercent,
                            fadeIn = settings.audioFadeInSeconds,
                            fadeOut = settings.audioFadeOutSeconds
                        )
                    },
                    onVolumeChange = {
                        viewModel.updateAudio(
                            uri = settings.audioUri,
                            totalSeconds = settings.audioTotalSeconds,
                            volumePercent = it,
                            fadeIn = settings.audioFadeInSeconds,
                            fadeOut = settings.audioFadeOutSeconds
                        )
                    },
                    onFadeInChange = {
                        viewModel.updateAudio(
                            uri = settings.audioUri,
                            totalSeconds = settings.audioTotalSeconds,
                            volumePercent = settings.audioVolumePercent,
                            fadeIn = it,
                            fadeOut = settings.audioFadeOutSeconds
                        )
                    },
                    onFadeOutChange = {
                        viewModel.updateAudio(
                            uri = settings.audioUri,
                            totalSeconds = settings.audioTotalSeconds,
                            volumePercent = settings.audioVolumePercent,
                            fadeIn = settings.audioFadeInSeconds,
                            fadeOut = it
                        )
                    }
                )

                // Huawei Band 8 Vibration Card
                VibrationConfigCard(
                    totalSeconds = settings.vibrationTotalSeconds,
                    onTotalSecondsChange = { viewModel.updateVibration(it) }
                )

                // Initial Sleep Onset Protection Card
                InitialProtectionConfigCard(
                    enabled = settings.initialProtectionEnabled,
                    initialProtectionMinutes = settings.initialProtectionMinutes,
                    onEnabledChange = { viewModel.updateInitialProtectionEnabled(it) },
                    onInitialProtectionChange = { viewModel.updateInitialProtection(it) }
                )

                // Cooldown Setting Card (Default 20 mins)
                CooldownConfigCard(
                    enabled = settings.cooldownEnabled,
                    cooldownMinutes = settings.cooldownMinutes,
                    onEnabledChange = { viewModel.updateCooldownEnabled(it) },
                    onCooldownChange = { viewModel.updateCooldown(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Actions
            BottomActionBar(
                isRunning = isRunning,
                onEnterScreensaver = { isScreensaverActive = true },
                onToggleGuard = {
                    if (isRunning) {
                        viewModel.stopGuard(context)
                    } else {
                        if (!hasPermissions) {
                            onRequestPermissions()
                        } else {
                            viewModel.startGuard(context)
                        }
                    }
                },
                onTestPreview = {
                    viewModel.triggerTestPreview(context)
                }
            )
        }
    }
}

@Composable
fun StatusIndicator(isRunning: Boolean, isExecuting: Boolean) {
    val (dotColor, text) = when {
        isExecuting -> CyanAccent to "执行触梦中"
        isRunning -> GreenActive to "守护运行中"
        else -> TextTertiary to "待机中"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 16.dp)
            .background(DarkCard, RoundedCornerShape(16.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = TextPrimary, fontSize = 12.sp)
    }
}

@Composable
fun PermissionWarningCard(
    hasBtPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, Color(0xFFFFB74D), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "蓝牙扫描或必要权限未授予",
                    color = Color(0xFFFFB74D),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = buildString {
                    append("检测到以下权限尚未授予，会导致无法扫描 ESP32 广播或手环无震动：\n")
                    if (!hasBtPermission) {
                        append("• 【附近的设备 / 蓝牙扫描】：用于在后台持续监听 ESP32 广播包\n")
                    }
                    if (!hasNotificationPermission) {
                        append("• 【通知权限】：用于维持前台守护服务与华为手环8脉冲震动")
                    }
                }.trimEnd(),
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(19.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Text(
                        text = "点击立即授权",
                        color = PureBlack,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(19.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent)
                ) {
                    Text(text = "前往系统设置", color = GoldAccent, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    isRunning: Boolean,
    isExecuting: Boolean,
    isBleScanning: Boolean,
    lastTriggerSource: String,
    cooldownRemaining: Int,
    protectionRemaining: Int,
    onResetCooldown: () -> Unit,
    onSkipProtection: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isExecuting) CyanAccent else DarkBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isExecuting) Icons.Default.Bolt else Icons.Default.NightsStay,
                    contentDescription = null,
                    tint = if (isExecuting) CyanAccent else GoldAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "系统运行与就绪状态",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isRunning) {
                    val bleDesc = if (isBleScanning) "已挂载蓝牙芯片级硬件过滤扫描" else "蓝牙扫描未激活"
                    "双模守护运行中：$bleDesc，同时待命 Sleep as Android 广播信号。"
                } else {
                    "守护未开启：请就寝前点击底部【🌙 开启后台守护】。"
                },
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "最新触发：", color = TextTertiary, fontSize = 12.sp)
                Text(text = lastTriggerSource, color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Initial Sleep Onset Protection Countdown Banner
            AnimatedVisibility(visible = isRunning && protectionRemaining > 0) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(8.dp))
                            .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val mins = protectionRemaining / 60
                            val secs = protectionRemaining % 60
                            Text(
                                text = "入睡保护中: ${mins}分${secs}秒 (静默防误触)",
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(
                            onClick = onSkipProtection,
                            modifier = Modifier.height(28.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(text = "跳过", fontSize = 11.sp, color = CyanAccent)
                        }
                    }
                }
            }

            // Cooldown Lock Countdown Banner
            AnimatedVisibility(visible = cooldownRemaining > 0) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val mins = cooldownRemaining / 60
                            val secs = cooldownRemaining % 60
                            Text(
                                text = "冷却保护中: ${mins}分${secs}秒 (防连续惊醒)",
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(
                            onClick = onResetCooldown,
                            modifier = Modifier.height(28.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(text = "解除", fontSize = 11.sp, color = GoldAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BleConfigCard(
    bleScanEnabled: Boolean,
    targetUuid: String,
    isBleScanning: Boolean,
    onBleScanEnabledChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isBleScanning) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BLE 硬件广播过滤扫描",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = bleScanEnabled,
                    onCheckedChange = onBleScanEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureBlack,
                        checkedTrackColor = GoldAccent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "通过 ScanFilter 针对特定 128-bit Service UUID 进行底层芯片硬件过滤。即使半夜手机灭屏深度休眠，外设发送匹配广播包后也会毫秒级瞬间唤醒并执行触梦！",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "目标过滤 SERVICE UUID",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = targetUuid,
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }

            if (isBleScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(GreenActive)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "硬件过滤扫描处于活跃待命状态",
                        color = GreenActive,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SaaConfigCard(
    saaEnabled: Boolean,
    onSaaEnabledChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sleep as Android 广播监听",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "监听 com.urbandroid.sleep.LUCID_CUE_ACTION 意图广播",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Switch(
                checked = saaEnabled,
                onCheckedChange = onSaaEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PureBlack,
                    checkedTrackColor = GoldAccent
                )
            )
        }
    }
}

@Composable
fun AudioConfigCard(
    audioUri: String,
    totalSeconds: Int,
    volumePercent: Int,
    fadeIn: Int,
    fadeOut: Int,
    onPickAudio: () -> Unit,
    onResetAudio: () -> Unit,
    onTotalSecondsChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onFadeInChange: (Int) -> Unit,
    onFadeOutChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "梦境音频播放引擎",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${totalSeconds} 秒",
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Audio File Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (audioUri.isBlank()) "预设内置：轻柔知梦铃 (528Hz)" else "已选自定义外部音频",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                Row {
                    OutlinedButton(
                        onClick = onPickAudio,
                        modifier = Modifier.height(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(text = "选择音频", fontSize = 11.sp, color = GoldAccent)
                    }
                    if (audioUri.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = onResetAudio,
                            modifier = Modifier.height(28.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(text = "恢复内置", fontSize = 11.sp, color = TextTertiary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Slider: Total Duration
            SliderItem(
                title = "总播放时长",
                valueDisplay = "${totalSeconds} 秒",
                value = totalSeconds.toFloat(),
                range = 5f..60f,
                steps = 54,
                onValueChange = { onTotalSecondsChange(it.toInt()) }
            )

            // Slider: Max Volume
            SliderItem(
                title = "目标音量",
                valueDisplay = "$volumePercent%",
                value = volumePercent.toFloat(),
                range = 5f..100f,
                steps = 18,
                onValueChange = { onVolumeChange(it.toInt()) }
            )

            // Slider: Fade In
            SliderItem(
                title = "淡入渐入时长",
                valueDisplay = "${fadeIn} 秒",
                value = fadeIn.toFloat(),
                range = 0f..10f,
                steps = 9,
                onValueChange = { onFadeInChange(it.toInt()) }
            )

            // Slider: Fade Out
            SliderItem(
                title = "淡出渐隐时长",
                valueDisplay = "${fadeOut} 秒",
                value = fadeOut.toFloat(),
                range = 0f..10f,
                steps = 9,
                onValueChange = { onFadeOutChange(it.toInt()) }
            )
        }
    }
}

@Composable
fun VibrationConfigCard(
    totalSeconds: Int,
    onTotalSecondsChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Watch,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "华为手环 8 触梦脉冲",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${totalSeconds} 秒",
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val estimatedRounds = ((totalSeconds * 1000L) / 1600L).toInt().coerceAtLeast(1)
            Text(
                text = "⚡ 脉冲节拍折算：约连续触发 $estimatedRounds 轮物理微震（每轮 1.6 秒发射独立ID通知，手环震动 0.8 秒，彻底绕过华为折叠屏蔽；任务完毕自动秒级清空通知栏）。",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            SliderItem(
                title = "手环震动总时长",
                valueDisplay = "${totalSeconds} 秒",
                value = totalSeconds.toFloat(),
                range = 2f..30f,
                steps = 27,
                onValueChange = { onTotalSecondsChange(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "⚠️ 关键前置：请务必在【华为运动健康 App -> 设备 -> 华为手环8 -> 消息通知】中，打开【触梦伴侣】的通知推送开关！",
                color = GoldDim,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun InitialProtectionConfigCard(
    enabled: Boolean,
    initialProtectionMinutes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onInitialProtectionChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "入睡保护期 (首次触发延迟)",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureBlack,
                        checkedTrackColor = GoldAccent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!enabled) {
                Text(
                    text = "已关闭入睡保护期：守护启动后任何时刻收到广播均会立即响应触发（适合快速联调测试）。",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            } else {
                Text(
                    text = "开启后台守护后的前一段时间内完全静默，不响应任何广播。防止入睡阶段由于浅睡翻身或外设误触导致过早惊醒。建议睡前设为 60~90 分钟，日常测试设为 0 分钟立即生效。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                SliderItem(
                    title = "保护期时长",
                    valueDisplay = if (initialProtectionMinutes == 0) "立即就绪 (0分)" else "${initialProtectionMinutes} 分钟",
                    value = initialProtectionMinutes.toFloat(),
                    range = 0f..120f,
                    steps = 7, // 0, 15, 30, 45, 60, 75, 90, 105, 120
                    onValueChange = {
                        val stepped = (((it + 7.5f) / 15f).toInt() * 15).coerceIn(0, 120)
                        onInitialProtectionChange(stepped)
                    }
                )
            }
        }
    }
}

@Composable
fun CooldownConfigCard(
    enabled: Boolean,
    cooldownMinutes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onCooldownChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "防惊醒全局冷却时间",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureBlack,
                        checkedTrackColor = GoldAccent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!enabled) {
                Text(
                    text = "已关闭防惊醒冷却锁：每次触发后不进入静默锁定期，方便连续测试触发效果。",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            } else {
                Text(
                    text = "触发一次后自动进入 20 分钟静默期。无论是 BLE 外设的高频广播风暴，还是 SaA 在浅睡边缘的重复广播，在冷却期内均直接静默过滤，彻底杜绝连续惊醒。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                SliderItem(
                    title = "冷却间隔",
                    valueDisplay = "${cooldownMinutes} 分钟",
                    value = cooldownMinutes.toFloat(),
                    range = 5f..60f,
                    steps = 54,
                    onValueChange = { onCooldownChange(it.toInt()) }
                )
            }
        }
    }
}

@Composable
fun SliderItem(
    title: String,
    valueDisplay: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, color = TextSecondary, fontSize = 13.sp)
            Text(text = valueDisplay, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = GoldAccent,
                activeTrackColor = GoldAccent,
                inactiveTrackColor = DarkBorder
            )
        )
    }
}

@Composable
fun BottomActionBar(
    isRunning: Boolean,
    onEnterScreensaver: () -> Unit,
    onToggleGuard: () -> Unit,
    onTestPreview: () -> Unit
) {
    Surface(
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isRunning) {
                Button(
                    onClick = onEnterScreensaver,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🛡️ 进入夜间屏保 (极暗时钟)",
                        color = PureBlack,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onToggleGuard,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(21.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedStop)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "停止守护",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onTestPreview,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(21.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "模拟测试",
                            color = GoldAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Button(
                    onClick = onToggleGuard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Icon(
                        imageVector = Icons.Default.NightsStay,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🌙 开启后台守护 (双模监听)",
                        color = PureBlack,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onTestPreview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚡ 立即模拟触发测试 (体验音频与手环脉冲)",
                        color = GoldAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun ScreensaverOverlay(
    isExecuting: Boolean,
    cooldownRemaining: Int,
    protectionRemaining: Int,
    onStopCue: () -> Unit,
    onExitScreensaver: () -> Unit,
    onStopGuard: () -> Unit
) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE)
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now)
            delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            // Tap anywhere to stop cue if currently executing
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isExecuting) {
                    onStopCue()
                }
            }
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        // Top status & exit button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isExecuting) CyanAccent else GreenActive)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isExecuting) "触梦进行中" else "守护运行中",
                    color = Color(0xFF666666),
                    fontSize = 11.sp
                )
            }

            OutlinedButton(
                onClick = onExitScreensaver,
                modifier = Modifier.height(28.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF333333)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(text = "返回面板", color = Color(0xFF777777), fontSize = 11.sp)
            }
        }

        // Center: Dim clock and status
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime,
                color = Color(0xFF777777),
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = currentDate,
                color = Color(0xFF444444),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isExecuting) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF00232A), RoundedCornerShape(8.dp))
                        .border(1.dp, CyanAccent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "⚡ 正在触梦唤醒！点击屏幕任意位置立即停止",
                        color = CyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (cooldownRemaining > 0) {
                val mins = cooldownRemaining / 60
                val secs = cooldownRemaining % 60
                Text(
                    text = "防惊醒冷却中：${mins}分${secs}秒 (静默待命)",
                    color = Color(0xFF555555),
                    fontSize = 12.sp
                )
            } else if (protectionRemaining > 0) {
                val mins = protectionRemaining / 60
                val secs = protectionRemaining % 60
                Text(
                    text = "入睡保护中：${mins}分${secs}秒 (静默防误触)",
                    color = Color(0xFF555555),
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = "蓝牙硬件过滤扫描就绪 · 收到广播即刻唤醒",
                    color = Color(0xFF3B3B3B),
                    fontSize = 11.sp
                )
            }
        }

        // Bottom: Swipe to exit slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SwipeToExitSlider(
                onSwipeComplete = onStopGuard
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "极暗夜间屏保 · 滑动上方滑块退出守护模式",
                color = Color(0xFF333333),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun SwipeToExitSlider(
    modifier: Modifier = Modifier,
    onSwipeComplete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var dragOffsetX by remember { mutableStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF141414))
            .border(1.dp, Color(0xFF282828), RoundedCornerShape(26.dp))
    ) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val thumbWidthPx = with(density) { 46.dp.toPx() }
        val maxDragPx = (trackWidthPx - thumbWidthPx - with(density) { 6.dp.toPx() }).coerceAtLeast(1f)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ">>> 右滑退出守护模式 >>>",
                color = Color(0xFF555555),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .padding(3.dp)
                .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
                .size(46.dp)
                .clip(CircleShape)
                .background(GoldAccent)
                .pointerInput(maxDragPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffsetX >= maxDragPx * 0.75f) {
                                onSwipeComplete()
                            } else {
                                coroutineScope.launch {
                                    Animatable(dragOffsetX).animateTo(0f) {
                                        dragOffsetX = value
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                Animatable(dragOffsetX).animateTo(0f) {
                                    dragOffsetX = value
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetX = (dragOffsetX + dragAmount).coerceIn(0f, maxDragPx)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Swipe to exit",
                tint = PureBlack,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

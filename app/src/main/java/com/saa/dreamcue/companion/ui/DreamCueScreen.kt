package com.saa.dreamcue.companion.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saa.dreamcue.companion.ui.theme.CyanAccent
import com.saa.dreamcue.companion.ui.theme.DarkBorder
import com.saa.dreamcue.companion.ui.theme.DarkCard
import com.saa.dreamcue.companion.ui.theme.DarkSurface
import com.saa.dreamcue.companion.ui.theme.GoldAccent
import com.saa.dreamcue.companion.ui.theme.GoldDim
import com.saa.dreamcue.companion.ui.theme.GoldMuted
import com.saa.dreamcue.companion.ui.theme.GreenActive
import com.saa.dreamcue.companion.ui.theme.PureBlack
import com.saa.dreamcue.companion.ui.theme.RedStop
import com.saa.dreamcue.companion.ui.theme.TextPrimary
import com.saa.dreamcue.companion.ui.theme.TextSecondary
import com.saa.dreamcue.companion.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DreamCueScreen(viewModel: DreamCueViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val isRunning by viewModel.isGuardRunning.collectAsState()
    val isExecuting by viewModel.isExecutingCue.collectAsState()
    val cooldownRemaining by viewModel.cooldownRemainingSeconds.collectAsState()

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
                            text = "for SaA & 华为手环8",
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
                // Status & Cooldown Card
                StatusCard(
                    isRunning = isRunning,
                    isExecuting = isExecuting,
                    cooldownRemaining = cooldownRemaining,
                    onResetCooldown = { viewModel.resetCooldown() }
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

                // Cooldown Setting Card
                CooldownConfigCard(
                    cooldownMinutes = settings.cooldownMinutes,
                    onCooldownChange = { viewModel.updateCooldown(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Actions
            BottomActionBar(
                isRunning = isRunning,
                onToggleGuard = {
                    if (isRunning) viewModel.stopGuard(context) else viewModel.startGuard(context)
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
fun StatusCard(
    isRunning: Boolean,
    isExecuting: Boolean,
    cooldownRemaining: Int,
    onResetCooldown: () -> Unit
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
                    text = "系统运行状态",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isRunning) {
                    "广播监听已就绪：正在后台等待 Sleep as Android 发出 com.urbandroid.sleep.LUCID_CUE_ACTION 触发信号。"
                } else {
                    "守护未开启：请在就寝前点击底部【🌙 开启后台守护】。"
                },
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
fun CooldownConfigCard(
    cooldownMinutes: Int,
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

                Text(
                    text = "${cooldownMinutes} 分钟",
                    color = GoldAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "触发一次后自动锁定，防止 SaA 在同个浅睡周期多次误判连环轰炸造成惊醒。",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            SliderItem(
                title = "冷却间隔",
                valueDisplay = "${cooldownMinutes} 分钟",
                value = cooldownMinutes.toFloat(),
                range = 10f..60f,
                steps = 9,
                onValueChange = { onCooldownChange(it.toInt()) }
            )
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
            Button(
                onClick = onToggleGuard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) RedStop else GoldAccent
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.NightsStay,
                    contentDescription = null,
                    tint = if (isRunning) TextPrimary else PureBlack,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "⏹ 停止后台守护" else "🌙 开启后台守护 (监听 SaA)",
                    color = if (isRunning) TextPrimary else PureBlack,
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

# 触梦伴侣 (DreamCue Companion)

专为 **Sleep as Android (SaA)** 与 **低功耗蓝牙 (BLE) 硬件设备** 协同 **华为手环 8** 设计的清醒梦触梦伴侣应用（Android 原生 Kotlin + Jetpack Compose）。

---

## 核心特性

1. **双模灵敏触发体系**：
   - **模式 A：低功耗蓝牙 (BLE) 硬件广播过滤 (nRF Connect 级深层嗅探)**：
     - 监听指定的 128-bit Service UUID（`9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d`）。
     - 支持解析标准 Service UUIDs、Service Data，以及全广播载荷（Payload）大端序（Big-Endian）与小端序（Little-Endian）双向字节序列深层匹配。彻底规避 ESP32 将 128-bit UUID 截断置于 Scan Response 或部分手机蓝牙芯片底层过滤丢失的缺陷。
     - 灭屏深度休眠下依然能够毫秒级唤醒 CPU 触发触梦！
   - **模式 B：Sleep as Android 官方意图广播**：动态捕获 `com.urbandroid.sleep.LUCID_CUE_ACTION` 广播信号。
   - **双模并发协同**：任一信号命中即可触发，满足不同硬件生态与睡眠监测场景。

2. **入睡保护期 (首次延迟触发，0 ~ 120 分钟可配)**：
   - 守护启动后前 N 分钟内完全静默，不响应广播。防止入睡阶段由于浅睡翻身或外设误发导致过早惊醒。
   - 默认设为 0 分钟（方便日常测试即刻响应）；睡前推荐设为 60~90 分钟（待进入深睡/REM周期后再行触发）。
   - 界面提供实时倒计时显示与一键【跳过保护期】按钮。

3. **防惊醒全局冷却保护（默认 20 分钟）**：
   - 触发一次后自动进入 20 分钟倒计时静默锁。
   - 无论是外设高频广播风暴（每秒数十包），还是 SaA 连续误判，冷却期内一律静默拦截，避免连环打扰造成彻底惊醒。
   - 支持界面一键【解除】锁，方便调试。

4. **主动权限引导与就绪检查**：
   - 全面适配 Android 12+ 的【查找、连接并确定附近设备的位置 / 蓝牙扫描 (`BLUETOOTH_SCAN`)】与 Android 13+【通知权限 (`POST_NOTIFICATIONS`)】。
   - 界面内置醒目权限检测卡片，提供【点击立即授权】与【前往系统设置】直达通道，彻底避免后台静默漏扫。

5. **平滑渐变音频引擎 (`AudioFadeController`)**：
   - 循环播放提示音频（支持内置 528Hz 知梦微铃或用户本地音频）。
   - 可配置**总播放时长**、**目标音量**、**淡入渐入时长**与**淡出渐隐时长**。
   - 协程 50ms 线性插值，杜绝爆音与突兀截断。
   - 绑定 `AudioAttributes.USAGE_ALARM`，穿透系统普通免打扰限制。

6. **华为手环 8 动态脉冲发生器 (`HuaweiPulseController`)**：
   - 针对华为穿戴设备的闭源及华为运动健康的消息折叠屏蔽机制，采用**自增 Notification ID + 动态时间戳正文**的离散高频脉冲发射方案。
   - 高优先级静音通道（`setSound(null, null)`，绝不产生手机提示杂音）。
   - 每 1.6 秒发射一轮微震脉冲，精准控制手环物理震动总时长。
   - 任务结束自动调用 `cancelAll` 清除临时通知，清晨醒来通知栏干干净净。

7. **黑曜石 OLED 极简界面**：
   - 暗黑护眼配色，就寝前不刺眼。
   - 提供 **【⚡ 立即模拟触发测试】** 功能，白天即可一键试听音量渐变与体验手环节拍震感。

---

## 使用配置指引

### 1. 华为运动健康 App 设置
1. 打开【华为运动健康】App -> 设备 -> 华为手环 8 -> **消息通知**。
2. 确保开启通知总开关，并在下方应用列表中找到 **【触梦伴侣】**，**勾选开启**。

### 2. 触发端配置（按需启用）
- **若使用 BLE 外设（如 ESP32、智能睡眠眼罩、指环等）**：
  - 外设广播 128-bit Service UUID：`9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d`。
  - 在【触梦伴侣】界面开启【BLE 硬件广播过滤扫描】开关。
- **若配合 Sleep as Android 软件**：
  - 打开 SaA 设置 -> 睡眠追踪 -> **清醒梦 (Lucid dreaming)** 开启功能，并将提示音设为**无声/静音**。
  - 在【触梦伴侣】界面开启【Sleep as Android 广播监听】开关。

---

## 目录工程结构

```
saa_lucid_companion/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/saa/dreamcue/companion/
│   │   │   ├── DreamCueApp.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   │   └── SettingsRepository.kt
│   │   │   ├── engine/
│   │   │   │   ├── AudioFadeController.kt
│   │   │   │   ├── BleScanController.kt
│   │   │   │   └── HuaweiPulseController.kt
│   │   │   ├── service/
│   │   │   │   └── DreamCueGuardService.kt
│   │   │   └── ui/
│   │   │       ├── DreamCueScreen.kt
│   │   │       ├── DreamCueViewModel.kt
│   │   │       └── theme/
│   │   └── res/
│   │       ├── drawable/ic_dream_pulse.xml
│   │       ├── raw/gentle_chime.wav
│   │       └── values/
├── build.gradle.kts
├── settings.gradle.kts
└── .github/workflows/build-apk.yml
```

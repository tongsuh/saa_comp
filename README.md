# 触梦伴侣 (DreamCue Companion)

专为 **Sleep as Android (SaA)** 与 **华为手环 8** 设计的清醒梦触梦协同伴侣应用（Android 原生 Kotlin + Jetpack Compose）。

---

## 核心特性

1. **无缝监听 SaA 梦境广播**：
   - 动态捕获 `com.urbandroid.sleep.LUCID_CUE_ACTION` 广播信号。
   - 前台保活服务 + WakeLock，锁屏深度睡眠下依然稳定响应。
   - 内置防惊醒冷却锁（默认 30 分钟），避免同个浅睡周期连续打扰。

2. **平滑渐变音频引擎 (`AudioFadeController`)**：
   - 循环播放提示音频（支持内置 528Hz 知梦微铃或用户本地音频）。
   - 可配置**总播放时长**、**目标音量**、**淡入渐入时长**与**淡出渐隐时长**。
   - 协程 50ms 线性插值，杜绝爆音与突兀截断。
   - 绑定 `AudioAttributes.USAGE_ALARM`，穿透系统普通免打扰限制。

3. **华为手环 8 动态脉冲发生器 (`HuaweiPulseController`)**：
   - 针对华为穿戴设备的闭源及华为运动健康的消息折叠屏蔽机制，采用**自增 Notification ID + 动态时间戳正文**的离散高频脉冲发射方案。
   - 高优先级静音通道（`setSound(null, null)`，绝不产生手机提示杂音）。
   - 每 1.6 秒发射一轮微震脉冲，精准控制手环物理震动总时长。
   - 任务结束自动调用 `cancelAll` 清除临时通知，清晨醒来通知栏干干净净。

4. **黑曜石 OLED 极简界面**：
   - 暗黑护眼配色，就寝前不刺眼。
   - 提供 **【⚡ 立即模拟触发测试】** 功能，白天即可一键试听音量渐变与体验手环节拍震感。

---

## 使用配置两步指引

### 1. 华为运动健康 App 设置
1. 打开【华为运动健康】App -> 设备 -> 华为手环 8 -> **消息通知**。
2. 确保开启通知总开关，并在下方应用列表中找到 **【触梦伴侣】**，**勾选开启**。

### 2. Sleep as Android (SaA) 设置
1. 打开 SaA 设置 -> 睡眠追踪 -> **清醒梦 (Lucid dreaming)**。
2. 开启清醒梦功能，将自带提示音设为**无声/静音**（声音由本 App 接管平滑播放）。
3. 建议设置“推迟 (Later)”3~4 小时后，以便在后半夜 REM 梦境高峰期触发。
4. 睡前打开【触梦伴侣】点击 **`[ 🌙 开启后台守护 ]`** 即可。

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

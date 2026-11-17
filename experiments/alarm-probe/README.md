# 闹钟最小实验包

本目录是独立 Gradle 实验工程，不加入主项目 settings，也不恢复已回滚的播放器。包名为 `com.Huffer342.shiyin.alarmprobe`，显示名“闹钟实验”，拥有独立数据和授权。minSdk 34、compileSdk 37；两个 flavor 只改变 targetSdk（34 / 37），使用同一个 applicationId，依次覆盖安装比较。

## 实验链路

页面点击预约 → AlarmManager.setAlarmClock → 私有 ProbeService → startForeground(mediaPlayback) → 请求 USAGE_ALARM 焦点 → 原生 MediaPlayer 循环播放固定 M4A → 60 秒停止。

- 预约统一为 20 秒后（快速实验；历史 180 秒测试结果不代表新时间配置已经过设备验证）；分别提供无等待服务和前台等待服务两种入口。WaitingService 使用已获精确闹钟权限对应的 systemExempted 类型，显示持续通知；到点、取消或等待超时后停止。不启用全屏页。焦点最多申请 12 次，间隔 250 ms，全部失败则停止。
- “移除当前卡片”按钮通过 finishAndRemoveTask 移除自己的任务；与 vivo 上划清理分开测试，尚无成功结论。等待服务和无效启动校验的实现、验证结果见 E3 工作记录。
- 音频复用仓库的 dawn-aac.m4a（45 秒 AAC，44.1 kHz 单声道），播放器幅度为 0.15，不调整系统音量。
- 开始后以短时 PARTIAL_WAKE_LOCK 保证观察窗口；播放 60 秒停止，另有 70 秒服务超时和 75 秒唤醒锁上限。
- 同时只保留一次预约；校验运行标识与到期时间，拒绝旧实例、提前和迟到超过 30 秒的请求。不恢复重启后的预约。
- 自启动入口诊断增加 RECEIVE_BOOT_COMPLETED 和私有 BootProbeReceiver，仅记录系统启动事件；不启动服务、不补注册闹钟。该变更已验证 SDK 34 构建，后续 SDK 对照需重新构建两种 flavor，不能混用旧 APK。
- 无网络、媒体扫描、存储访问或全屏权限。启动 Activity 不处理外部 extras；服务不导出，PendingIntent 指向固定组件且不可变。
- 页面是实验控制面板，不是正式 App UI。实验使用 Android 14+，不改变正式 App 的 minSdk。
- 日志 tag 为 ShiYinProbe，同时追加到应用私有 events.jsonl。记录运行标识、时间、焦点、路由类型、播放位置及停止原因，不读取用户媒体库。

## 构建与运行

在仓库根目录，使用匹配的 JDK 和 SDK：

```sh
JAVA_HOME=/home/wushenhui/.local/opt/android-studio/jbr ANDROID_HOME=/home/wushenhui/Android/Sdk ./gradlew -p experiments/alarm-probe assembleSdk34Debug assembleSdk37Debug testSdk34DebugUnitTest testSdk37DebugUnitTest lintSdk37Debug
adb -s SERIAL install -r experiments/alarm-probe/build/outputs/apk/sdk37/debug/ShiYinAlarmProbe-sdk37-debug.apk
adb -s SERIAL shell am start -n com.Huffer342.shiyin.alarmprobe/.ProbeActivity
adb -s SERIAL logcat -d -s ShiYinProbe:I '*:S'
```

先在页面授权通知与精确闹钟，再预约。安装 targetSdk 34 时使用 sdk34 对应路径；每次覆盖安装后重新检查权限、AppOps 和 ROM 设置。若系统拒绝安装，不卸载现有应用绕过。

RunWindowTest 验证提前、过期和旧标识不能触发播放。它不能证明系统送达、出声或后台可靠性。

计划与实际结果见[工作记录](../../docs/工作记录/README.md)。本实验工程不会进入主 App 的 GitHub 发布构建。

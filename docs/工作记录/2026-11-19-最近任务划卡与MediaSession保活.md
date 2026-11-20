# 最近任务划卡与 vivo OriginOS 强停机制实测

状态：已完成真机机制验证与对比（2026-11-19）。

## 背景与目标

排查在第三方非白名单设备（vivo V2463A / OriginOS 5 / Android 15，`10AFA828MZ003A1`）上，开启「自启动」「关联启动」「允许后台高耗电」并在卡片加锁后，从系统最近任务将卡片向上划掉导致闹钟失效的原因，以及是否有应用层技术手段可以绕过系统单卡上划的强停。

## 真机实测与对照结论

1. **导出 `BOOT_COMPLETED` 接收器解锁 vivo「自启动」开关（已验证有效）**：
   - 此前 `ScheduleReceiver`（及 `alarm-probe` 的 `BootProbeReceiver`）设置了 `android:exported="false"`，导致 vivo `com.vivo.permissionmanager` 隐藏了该应用的**「自启动（开机启动 后台启动）」**开关。
   - 改为 `android:exported="true"` 并补充 `LOCKED_BOOT_COMPLETED`、`USER_PRESENT` 和 `category.DEFAULT` 后，vivo“应用信息 → 应用权限 → 查看所有权限”中出现「自启动」开关；开启后，应用从停止态恢复时会立即收到 `BOOT_COMPLETED` 广播。

2. **自动启动后台等待服务（已验证有效）**：
   - `ScheduleScheduler` 在 `save`、`delete`、`reschedule` 时自动调用 `AlarmStandbyService.sync(context, finalSchedules)`，`AlarmStandbyPolicy.shouldRestoreStandby` 不再依赖手动上滑标记 `standby_requested`。
   - 用户保存或启用闹钟后直接按 Home 退回桌面，后台等待服务与 `AlarmManager.setAlarmClock` 即可正常工作。

3. **vivo 最近任务（`com.vivo.recents`）单卡上划 vs 一键清理 vs 应用自关卡片的精确对比**：
   - **修正此前的 `adb shell input swipe` 坐标误判**：
     - 此前在 `11:10:12` 和 `11:12:27` 执行 `input swipe 200 2000 200 300` 时，ShiYin 卡片在 `com.vivo.recents:id/overview_panel2` 中处于左侧相邻预览位（并未处于屏幕正中央焦点位）。在 vivo 最近任务中，对非居中预览卡片上划**不会移除 ShiYin 卡片**，而是移除了当时居中的前一张卡片，因此 ShiYin 进程未被杀死。
     - 当先把 ShiYin 卡片滑到屏幕正中央焦点位（`bounds=[228,345][1212,2667]`），再向上划掉（`11:14:14`、用户手动测试 `11:24:02`、`11:30:42`）时，卡片真正从最近任务中移除。
   - **真机验证过的被测方案（当卡片在最近任务中被真正手动上划移除时）**：
     - `FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` 前台服务 + `START_STICKY`：被直接 `forceStopPackage`（`SIGKILL`，`onTaskRemoved` 不执行，通知移除，`AlarmManager` 闹钟全部清空）。
     - `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` 前台服务 + 激活 `MediaSession`（`STATE_PAUSED`）：被直接 `forceStopPackage`，闹钟清空。
     - `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` 前台服务 + 激活 `MediaSession`（`STATE_PLAYING`）+ 活跃循环播放的 `AudioTrack`（静音 PCM 音频流，`11:30:42` 实测）：依然被直接 `forceStopPackage`，闹钟清空。
   - **根本原因**：
     - 在 vivo OriginOS 5 中，用户在最近任务界面**手动把某张卡片向上划掉**（即使该卡片已加锁 `com.vivo.recents:id/lock`），系统层面执行的不是普通的 `ActivityTaskManager.removeTask`，而是由系统进程管理服务直接对该包调用 `forceStopPackage`（置为 `stopped=true, vStopped=true`）。
     - `forceStopPackage` 属于系统 UID 级强行停止，任何非系统内置白名单的第三方应用（无论处于何种前台服务类型、是否正在播放音频、是否开启自启动/高耗电、卡片是否加锁）都会被立即 `SIGKILL` 并注销所有 `AlarmManager` 闹钟、冻结所有广播和 `JobScheduler` 任务，直到用户再次手动启动该应用。

4. **在 vivo OriginOS 5 上单应用已实测可正常触发闹钟的 3 种后台方式**：
   - **方式 A（按 Home 退回桌面，不划卡）**：保存闹钟后直接按 Home 键或全面屏返回手势回到桌面，`AlarmStandbyService` 自动驻留，到点正常响铃。
   - **方式 B（卡片加锁 + 点击最近任务底部“清除全部 ×”按钮）**：在最近任务中给 ShiYin 卡片**加锁**（显示锁头图标），点击底部“清除全部”按钮时系统会保留已加锁的 ShiYin 卡片，不触发 `forceStopPackage`，`11:17:05` 实测准时弹出 `AlarmActivity` 响铃（注意：加锁仅防“清除全部”按钮，不能手动把加锁卡片往上划掉）。
   - **方式 C（不想保留最近任务卡片：应用内底部栏上滑 / 顶部“后台运行”按钮）**：用户在时音底部导航栏上滑（`11:23:28` 用户手动实测，`pid=29646`），应用自身调用 `finishAndRemoveTask()` 移除最近任务卡片，此时系统只回调 `AlarmStandbyService.onTaskRemoved()` 而不触发 `forceStopPackage`，`11:23:46` 准时触发闹钟并播放音频。

## 方案 A（可选同签名守护 APK）设计与真机验证

在排除无障碍模式、Shizuku 与桌面小组件后（且确认通知使用权 `NotificationListenerService` 仍属于同一主包，无法绕过单包 `forceStopPackage`），实施**可选同签名配套守护包 `:guard`**（`com.Huffer342.shiyin.guard`，“时音闹钟守护”）：

1. **核心机制与隐蔽性**：
   - 主应用内 `GuardSetupDialog`（首次访问闹钟栏目提示或从「设置 → 闹钟守护包」进入）通过 `FileProvider` 安装 Debug 内置的 `assets/shiyin-guard.apk`。
   - `:guard` 无桌面 `LAUNCHER` 图标，`GuardActivity` 设置 `excludeFromRecents="true"`，引导完成后调用 `finishAndRemoveTask()` 不留最近任务卡片。
   - 跨包同步与唤醒均由 `signature` 权限 `com.Huffer342.shiyin.permission.COMPANION_ALARM` 保护；主应用卸载时，`GuardReceiver` 收到 `ACTION_PACKAGE_FULLY_REMOVED` / `ACTION_PACKAGE_REMOVED` 后清空预约并（在已授通知权限时）发出通知引导用户一键卸载守护包。

2. **vivo OriginOS 5（`10AFA828MZ003A1`）实机对照验证**：
   - **初始对照（失败/延迟）**：当 `:guard` 仅授予 `SCHEDULE_EXACT_ALARM` + `POST_NOTIFICATIONS`、但系统后台耗电仍处于默认「智能控制后台耗电」且「自启动 / 关联启动」关闭时，`:guard` 注册的 `setAlarmClock` 仍会被 OriginOS 延迟或拦截（实测出现 +42 秒/+82 秒迟到以及 `1795081850` 漏响）。
   - **完整授权后（在守护系统应用信息中开启「自启动」「关联启动」并设为「允许后台高耗电」）**：
     - **场景 1 · 最近任务未加锁居中单卡上划移除**：预约 60 秒倒计时（`晨光 · AAC 示例`，`expectedAt=1795082259101`），将 `ShiYin` 卡片置于最近任务居中位 `[228,345][1212,2667]` 并向上划掉，`id=1004` 等待通知于 `1795082239` 移除；`1795082259` 零秒延迟触发 `ShiYinGuard: Firing companion wake`，经 `CompanionAlarmReceiver` 冷唤醒 `ShiYin`，`AlarmActivity` `+226ms` 展示并正常播放音频。
     - **场景 2 · 最近任务未加锁点击「清除全部 (×)」**：预约 60 秒倒计时（`晨光 · AAC 示例`，`expectedAt=1795082476275`），按 Home 后打开最近任务（`ShiYin` 未加锁），点击底部「清除全部-按钮」`(720, 2892)` 返回桌面，`ShiYin` 原进程与等待通知（`1795082440`）被清理；`1795082476` 零秒延迟触发 `GuardFireReceiver`，冷拉起 `com.Huffer342.shiyin`（新进程 `pid=608`），`AlarmActivity` `+156ms` 展示并播放音频。

3. **后续复测问题定位与待验证修复（18:46）**：
   - 用户拔线独立使用/复测划掉卡片后，收到「闹钟音频焦点被拒绝，未开始播放」通知——说明 `:guard` 已正常保活并准时拉起主应用 `PlaybackService`，但被 `PlaybackService.startAlarm` 中的 `check(requestAlarmFocus())` 拦截。
   - 根因与此前 [2026-11-17 OriginOS 记录](2026-11-17-OriginOS闹钟可靠性.md) 一致：Android 15 上 `targetSdk = 37` 的后台前台服务在未显示全屏页前调用 `requestAudioFocus` 可能返回 `0`，而原代码在调用 `AlarmPresentation.show()` 之前就执行 `check(requestAlarmFocus())` 并直接抛异常中止，导致全屏闹钟页与播放均未启动。
   - 已调整 `PlaybackService`：先调用 `AlarmPresentation.show()` 展示全屏闹钟通知/页面，再申请焦点并增加 15×200ms 异步重试；若首次未获焦点不再中止或暂停 `USAGE_ALARM` 闹钟播放。当前手机未连接 ADB，待用户重连或安装本轮构建后实测锁屏与解锁两种划卡场景下的实际出声。

4. **未验证边界与清理备注**：
   - 尚未真机验证：上述焦点修复后的锁屏/解锁实机响铃、设备重启后守护闹钟恢复、长时间待机/Doze、主应用卸载后守护通知一键卸载流程、Release 证书同签名双包构建。
   - 此前单包实验阶段遗留的未引用 stub/配置（`AlarmAppWidgetProvider.kt`、`AlarmKeepAliveAccessibilityService.kt`、`AlarmKeepAliveNotificationListenerService.kt`、`AlarmSyncService.kt` 及对应 `res/xml`、`res/layout` 文件）当前未在清单与代码中引用，待用户在工作区清理删除。

## 2026-11-20 焦点修复版真机复测

vivo V2463A（Android 15 / OriginOS 5），USB 连接。覆盖安装工作区 Debug 双包后，手机与本地产物的 SHA-256 均一致（主包前缀 `38170c69`，守护包 `8480b28a`）。系统显示两包签名相同，守护的精确闹钟、通知和同签名联动权限已授予。测试仅使用 MediaStore 编号 `1000019103` 的 `dawn-aac.m4a`（晨光 · AAC 示例），倒计时 20 秒，播放上限 1 分钟。

| 清理方式与到点状态 | 守护发出唤醒（设备时间） | 相对预约 | 主应用 PID 变化 |
| --- | --- | ---: | --- |
| 单卡上划，亮屏桌面 | 11:10:11.007 | +13 ms | 31552 → 16725 |
| 清除全部，亮屏桌面 | 11:11:08.723 | +149 ms | 16725 → 18284 |
| 单卡上划，锁屏熄屏 | 11:12:22.821 | +12 ms | 18284 → 19457 |

三轮清理后均查不到主应用的活动任务卡片，到点均出现 `AlarmActivity`，系统音频服务记录新 PID 的 `USAGE_ALARM` 音轨为 `started`、`mutedState:none`。锁屏轮在触发前查到 `mWakefulness=Asleep`。亮屏清除全部和锁屏单卡轮还查到响铃通知（ID 1003，`ringing` 渠道，importance 4）。

11:07:56 的准备轮使用原 PID，应用日志记录 `requestAlarmFocus result=1 granted=true`。该轮未计入划卡结果。上表三轮的系统焦点历史均有对应 PID 的请求，未取到应用返回码，无法据此判断重试是否执行。

**锁屏熄屏＋清除全部（14:13）**。同一对 Debug 安装包、同一条 20 秒「晨光」倒计时。14:12:50.630，系统 cleaner 同时终止主包 PID 27385 和守护 PID 8384。14:12:53 查不到主进程和主应用最近任务，电源状态为 `Asleep`，锁屏状态为 `showing=true`。系统事件记录了两次冷启动：

| 设备时间 | 观测 |
| --- | --- |
| 14:13:07.102 | 系统为 `GuardFireReceiver` 启动守护进程 25109 |
| 14:13:07.306 | 系统为 `CompanionAlarmReceiver` 启动主进程 11262 |
| 14:13:07.613 | 主进程请求闹钟音频焦点 |
| 14:13:08 | `AlarmActivity` 获得窗口焦点，出现 ID 1003 的响铃通知，`USAGE_ALARM` 音轨为 `started`、`mutedState:none`。用户当场确认听到「晨光」 |

此轮未取到预约毫秒值和应用层焦点返回码。四种有线组合均有冷启动、响铃页与音轨证据。前三轮期间用户也反馈过听到铃声，但未逐轮指认；拔线时的表现仍待独立验证。

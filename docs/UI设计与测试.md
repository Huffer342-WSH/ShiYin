# UI 设计与测试

界面以本地音频播放器为主，闹钟作为附加功能。沿用 Compose + Material 3，参考 [VLC 音频播放器](https://docs.videolan.me/vlc-user/android/3.5/en/audio/audio_player.html) 的库、迷你播放器、队列与更多菜单组织，以及 [APlayer](https://github.com/rRemix/APlayer) 的本地音乐布局；不移植其代码或资源。

## 页面约定

- 主入口为歌曲、播放列表、目录、闹钟，支持左右滑动切换主栏目。使用紧凑顶栏；不放产品名、宣传卡片和常驻帮助段落。
- 闹钟是独立导航栏目，设置和帮助放在更多菜单。首次启动展示可关闭的使用手册，之后可从更多菜单帮助或设置的使用说明重新查看；首页不常驻帮助文本。权限或文件失败属于必要状态提示。
- 歌曲列表直接呈现曲目、艺术家和时长。搜索按需展开；行尾菜单提供下一首、追加队列、加入播放列表、创建闹钟和“详细信息”。详细信息使用可滚动对话框；点击位置可复制路径，长按后弹出确认框，确认后由系统文件管理器打开所在目录。读取期间显示进度，缺失字段显示“未知”。
- 迷你播放器保留曲目与播放控制；点击打开完整播放页。完整页集中放进度、上一首/下一首、随机和循环，更多菜单包含睡眠定时、保存队列及停止。
- 持久播放列表与临时队列分开。列表可新建、重命名、多选管理歌曲、上移/下移、删除；删除列表不删除文件。
- 目录页首行固定显示“系统音乐库”，提供总开关、收录数量和已排除目录数；点击后按存储卷和 MediaStore 相对路径列出实际含音频的目录，每行使用“收录”开关。系统音乐库默认开启，`Recordings/` 及其子目录默认关闭。其余行管理用户授权的监控根目录。自选目录详情用“浏览 / 全部音频”切换层级视图和递归集合；长按进入多选，支持当前视图全选、长按滑选与边缘滚动，批量添加到指定或新建播放列表。子目录返回逐级退出，多选时先取消选择。移除目录只停止监控；授权失败显示在对应来源上。
- 闹钟编辑使用时间/日期选择器、重复规则、单次时长上限、淡入与试听。新建默认淡入 15 秒，旧记录默认不淡入。保存失败保留编辑内容，放弃修改前确认。
- 默认浅色主题使用白色页面，海蓝色（`#2984F0`）用于选中态和容器配色，闹钟日期和播放时间使用中性色，封面、波形和歌词标签通过字重区分选中状态。进度条和开关沿用主题色，深色模式使用暗蓝背景与浅色文字。启动图标为白底、海蓝钟环与深蓝音符，见 [SVG 源图](shiyin-icon.svg)。主题支持四套预设、自定义主色和系统/浅色/深色模式，Android 12 以上可选壁纸动态配色。颜色输入校验格式，主色文字对比度经过计算。

手机使用底部导航，宽窗口使用侧边导航。播放列表、目录与闹钟的新增按钮为 48dp，完整播放页的播放按钮为 52dp。点击区域至少 48dp，重复规则整行至少 56dp。列表、表单和选择面板可滚动；键盘与系统栏由 Scaffold/insets 处理。低高度窗口的时间输入使用横向紧凑布局，字段和操作按钮位于键盘上方。

## 后台运行与守护包入口

取消顶部和菜单中的后台运行按钮；手机底部 tab 栏上滑 48dp 后松手进入后台，普通点击仍切换栏目。手势区域提供无障碍自定义操作。播放/编辑等无 tab 栏页面先返回主栏目；歌曲首页返回仍采用后台退出流程。权限与后台入口移至设置，首次自动申请普通权限，拒绝后不反复弹窗。倒计时卡片主要显示剩余与总时长，点击从原时长重启。

可选守护包通过 `GuardSetupDialog`（[GuardDialogs.kt](../app/src/main/java/com/Huffer342/shiyin/ui/GuardDialogs.kt)）引导：首次切换至「闹钟」栏目且未安装守护包时自动弹出一次（可点击“暂不安装”跳过）；之后可随时从「设置 → 闹钟守护包」或「权限与后台」弹窗进入安装、打开守护设置/系统应用信息或一键卸载。

MainActivity 使用 singleTask，需验证重复点击图标、通知返回和设置页返回后不残留同类任务。无卡片不能只看界面消失，应同时检查系统最近任务、服务状态和触发日志。

## 播放页与动画回归

导航上滑提供跟手位移、阈值提示和松手动画；不足阈值回弹。任务保存不再触发全局忙碌样式，避免开关和加载条闪烁。完整播放页使用封面/波形/歌词三页横滑，进度标签跟随拖动预览。主题主色同时影响背景和容器层级。

2026-11-17 模拟器 PlaybackVisualTest 实际 OK (6 tests)，范围和限制见[工作记录](工作记录/2026-11-17-播放页波形与交互.md)。

## 项目内技能与实现

[mobile-android-design](../.agents/skills/mobile-android-design/SKILL.md) 来自 [wshobson/agents](https://github.com/wshobson/agents/tree/main/plugins/ui-design/skills/mobile-android-design)，保留 MIT 许可证、references 和 [来源记录](../.agents/skills/mobile-android-design/SOURCE.md)。依赖版本以项目版本目录和官方文档为准，不直接照抄技能示例版本。

- [MainActivity.kt](../app/src/main/java/com/Huffer342/shiyin/MainActivity.kt)：导航、目录选择器、系统权限与对话框。
- [PlayerScreens.kt](../app/src/main/java/com/Huffer342/shiyin/ui/PlayerScreens.kt)：歌曲、迷你/完整播放页及可编辑队列。
- [LibraryScreens.kt](../app/src/main/java/com/Huffer342/shiyin/ui/LibraryScreens.kt)：播放列表、目录、歌曲多选和睡眠定时。
- [ScheduleScreens.kt](../app/src/main/java/com/Huffer342/shiyin/ui/ScheduleScreens.kt)：闹钟编辑、淡入与单次上限。
- [SettingsScreen.kt](../app/src/main/java/com/Huffer342/shiyin/ui/SettingsScreen.kt)：精简设置、闹钟守护包入口、主题配置和按需帮助。
- [GuardDialogs.kt](../app/src/main/java/com/Huffer342/shiyin/ui/GuardDialogs.kt)、[GuardActivity.kt](../guard/src/main/java/com/Huffer342/shiyin/guard/GuardActivity.kt)：可选闹钟守护包安装、权限与 vivo 系统后台设置引导。
- [ThemeSettings.kt](../app/src/main/java/com/Huffer342/shiyin/ThemeSettings.kt)、[主题](../app/src/main/java/com/Huffer342/shiyin/ui/theme)：配色校验、对比度和系统栏外观。

## 模拟器测试音频

[test-fixtures/audio](../test-fixtures/audio) 保留四个低音量合成音频文件，无外部歌曲或采样，不打包进正式 APK。

| 文件 | 内容 | 时长 |
| --- | --- | --- |
| dawn-chords.wav | 淡入淡出的 C 大调合成和弦 | 45 秒 |
| soft-rain.mp3 | 固定种子的粉红噪声 | 60 秒 |
| evening-bells.flac | 周期衰减的调制音 | 30 秒 |
| dawn-aac.m4a | dawn-chords 转码，AAC 128 kb/s | 45 秒 |

生成命令在项目根目录执行，`-n` 避免覆盖已有文件：

```sh
ffmpeg -nostdin -n -f lavfi -i 'aevalsrc=0.035*(sin(2*PI*261.63*t)+sin(2*PI*329.63*t)+sin(2*PI*392*t))*(0.65+0.35*sin(2*PI*0.12*t)):s=44100:d=45' -af 'afade=t=in:d=2,afade=t=out:st=42:d=3' -metadata title='晨光 · 和弦练习' -metadata artist='ShiYin Test Studio' test-fixtures/audio/dawn-chords.wav
ffmpeg -nostdin -n -f lavfi -i 'anoisesrc=color=pink:amplitude=0.06:duration=60:sample_rate=44100:seed=342' -af 'afade=t=in:d=2,afade=t=out:st=57:d=3' -c:a libmp3lame -q:a 4 -metadata title='微雨 · 粉红噪声' -metadata artist='ShiYin Test Studio' test-fixtures/audio/soft-rain.mp3
ffmpeg -nostdin -n -f lavfi -i 'aevalsrc=0.07*sin(2*PI*(523.25+3*sin(2*PI*0.2*t))*t)*exp(-2*mod(t\,2)):s=44100:d=30' -af 'afade=t=in:d=1,afade=t=out:st=28:d=2' -c:a flac -metadata title='晚风 · 合成钟声' -metadata artist='ShiYin Test Studio' test-fixtures/audio/evening-bells.flac
```

M4A 样例由现有 WAV 转码：

```sh
ffmpeg -nostdin -n -i test-fixtures/audio/dawn-chords.wav -c:a aac -b:a 128k -metadata title='晨光 · AAC 示例' -metadata artist='ShiYin Test Studio' -movflags +faststart test-fixtures/audio/dawn-aac.m4a
adb -s emulator-5554 shell mkdir -p /sdcard/Music/ShiYin/AAC
adb -s emulator-5554 push test-fixtures/audio/dawn-aac.m4a /sdcard/Music/ShiYin/AAC/dawn-aac.m4a
```

使用 SAF 监控 Music/ShiYin 后会自动发现 AAC 子目录与文件，无需 MediaStore 广播。

在确认目标模拟器后导入，下面以 `emulator-5554` 为例：

```sh
adb -s emulator-5554 shell mkdir -p /sdcard/Music/ShiYin
adb -s emulator-5554 push test-fixtures/audio/dawn-chords.wav /sdcard/Music/ShiYin/
adb -s emulator-5554 push test-fixtures/audio/soft-rain.mp3 /sdcard/Music/ShiYin/
adb -s emulator-5554 push test-fixtures/audio/evening-bells.flac /sdcard/Music/ShiYin/
adb -s emulator-5554 shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/ShiYin/dawn-chords.wav
adb -s emulator-5554 shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/ShiYin/soft-rain.mp3
adb -s emulator-5554 shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/ShiYin/evening-bells.flac
```

广播成功仅代表请求已发出；须在时音中扫描并检查曲目、时长与播放进度。素材为低音量测试信号，仍应先确认系统音量再试听。本次模拟器对 WAV 的 RIFF 元数据未显示中文标题，列表使用 dawn-chords 和“未知艺术家”；MP3、FLAC 显示内嵌标题和艺术家。


## 验证记录与截图

目录扫描、导航、播放和主题的历史测试结果及截图见[验证记录](验证记录.md#目录与导航验证)。

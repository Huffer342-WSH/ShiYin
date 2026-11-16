# 模拟器测试音频

这些音频由 FFmpeg 合成，无外部歌曲或采样。用于扫描、格式解码、队列、进度和定时试听测试，不代表真实音乐质量或扬声器验收。

- dawn-chords.wav：45 秒低音量和弦，PCM WAV。
- soft-rain.mp3：60 秒低音量粉红噪声，MP3。
- evening-bells.flac：30 秒低音量调制音，FLAC。
- dawn-aac.m4a：上述 dawn-chords.wav 转码为 128 kb/s AAC，45 秒；导入 `Music/ShiYin/AAC/` 子目录，用于层级浏览和 M4A 解码验证。

导入位置：模拟器 `/sdcard/Music/ShiYin/`。生成和导入命令见 `docs/UI设计与测试.md`。素材不打包进正式 APK。

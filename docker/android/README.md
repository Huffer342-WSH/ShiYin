# Android 构建容器

适用于 Linux x86_64 + Docker Engine。本项目随源码提供 Dockerfile 和启动脚本，不要求宿主机安装 JDK、Android SDK 或 Gradle。镜像不包含源码、模拟器和 NDK，也不自动发布到镜像仓库。

## 使用

在项目根目录执行：

```sh
# 首次构建镜像，然后生成 APK、运行全部 JVM 单元测试和 Lint
bash docker/android/run.sh

# 只构建 APK
bash docker/android/run.sh :app:assembleDebug

# 也可生成设备测试 APK，但容器内不运行模拟器
bash docker/android/run.sh :app:assembleDebugAndroidTest
```

后续运行直接复用已有镜像、Gradle 依赖和任务缓存，避免每次联系镜像仓库。修改 Dockerfile 或入口脚本后，在项目根目录执行 `docker build --platform linux/amd64 -t shiyin-build:jdk25-sdk37 docker/android` 重建；随后再次运行入口。参数按原样传给 Gradle，不通过 eval。

前提：Docker daemon 已启动，当前用户可以运行 docker；宿主机需要 bash 和 flock（Linux util-linux 提供）。不使用 sudo 启动脚本，避免生成 root 所有的文件。首次运行需要网络下载镜像、SDK、Gradle 和 Maven 依赖，并自动接受 Android SDK 许可。

## 版本与隔离

- Temurin JDK 25.0.5+8：基础镜像按 digest 固定。
- Android Command-line Tools 15859902：按 [Google 下载页](https://developer.android.com/studio) 的 SHA-256 校验。
- SDK Platform 37.0、Build Tools 37.0.0，与正式 app 配置保持一致。
- Gradle 使用仓库 Wrapper（当前 9.6.0），沿用 Wrapper checksum。
- 不额外安装系统镜像、NDK、.NET。APT 安全包与 SDK 同一路径下的修订仍可能变化，因此不承诺逐字节可复现镜像；需要完全固定时应发布并引用构建后的镜像 digest。

源码只读挂载到 /source。容器按宿主 UID/GID 运行，先将源码同步到 .container/workspace，再在副本中生成指向容器 SDK 的 local.properties。不会覆盖 IDE 的 local.properties、app/build 或 .gradle。

| 本机目录 | 内容 |
| --- | --- |
| .container/workspace/app/build/outputs/apk/debug/app-debug.apk | Debug APK |
| .container/workspace/app/build/reports | 测试与 Lint 报告 |
| .container/gradle | Gradle 下载及任务缓存 |
| .container/home | 容器用户目录及调试签名 |
| .container/workspace | 构建副本和增量输出 |

.container 已加入 Git 忽略。启动脚本使用文件锁，阻止同一工作区同时启动两个容器构建。rsync 的删除操作只作用于生成的构建副本；不把个人工作放进 .container。

调试签名保存在 .container/home，删除它会改变后续容器生成的调试签名，不能保证与本机 IDE 或 GitHub Release 覆盖安装兼容。清理缓存时注意保留该目录。

## 离线使用

镜像、Gradle 发行包及依赖已经缓存后，可运行 `bash docker/android/run.sh --offline :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。离线模式不会替你下载缺失依赖；首次使用仍需要可用的网络。不要关闭 TLS 或 Wrapper 校验来绕过下载问题。

## 本次验证

2026-11-18 在 Linux amd64 实际构建镜像成功，大小约 1.46 GB。宿主和容器访问 Gradle 下载域名时均遇到 DNS 问题；复制已有发行包与依赖下载缓存后，容器内离线 APK 构建、JVM 单测、Lint、AndroidTest APK 构建通过，首次约 64 秒，缓存复跑约 5 秒。没有复用宿主编译输出或测试结果。完整记录见[工作记录](../../docs/工作记录/2026-11-18-项目构建容器.md)。

镜像未安装 platform-tools，AGP 会给出缺少该包的警告；此容器只构建 APK，不执行 adb 操作。未安装 NDK，第三方预编译 so 原样打包。首次空缓存联网构建尚待网络恢复后验证。

## CI 与限制

Build Image workflow 已将镜像发布至本仓库关联的 `ghcr.io/huffer342-wsh/shiyin-build` 包；[image.txt](image.txt) 固定 CI 使用的 digest。App CI 在该镜像内执行 Lint、单测和构建，模拟器 job 使用宿主 KVM 并下载同次 APK。镜像更新与跨运行 APK 复用见[构建与发布](../../docs/构建与发布.md)。本地脚本仍可自行构建本地镜像，不强制依赖私有 GHCR 登录。

当前只承诺 linux/amd64，未验证 ARM Mac 的跨架构模拟性能、Docker Desktop 或 Podman。构建过程中采用磁盘上的源码快照，IDE 未保存内容不会进入构建；运行开始后修改源码需在下一次运行验证。

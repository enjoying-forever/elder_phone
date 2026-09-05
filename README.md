# 看图电话（Android）

这是一个面向不识字或识字较少老人的原生 Android 电话应用。项目使用 Kotlin、Jetpack Compose 和系统通讯录/通话记录接口，不包含广告、账号、分析或跟踪功能。

## 环境要求

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 35.x
- Android Studio Ladybug 或更新版本也可直接打开本目录

如使用命令行，请先设置 `JAVA_HOME` 和 `ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）。

## 构建 APK

```bash
./gradlew assembleDebug
```

生成文件：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装

连接已开启 USB 调试的 Android 手机后执行：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可以把 APK 发送到手机并由家人协助安装。首次打开时，请由识字的家人按屏幕说明授予三个权限。

## 所需权限

- `CALL_PHONE`：按下拨打按钮后直接拨打电话。
- `READ_CONTACTS`：显示系统通讯录中的姓名、号码和照片。
- `READ_CALL_LOG`：显示手机系统的最近通话记录。

应用不请求短信、位置、设备标识、通讯录写入或其他无关权限。

## 使用说明

- 顶部有“拨号”和“通讯录”两个大标签，点按时会中文播报。
- 通话记录或联系人第一次点按会选中并播报；再次点按同一项才拨号。
- 通讯录下方箭头可逐项选择，列表会同步滚动。
- 顶部右侧浅灰色齿轮是播报音量设置，调整的是系统媒体/语音音量。
- debug 版本在模拟器真实联系人或通话记录为空时显示演示数据；release 版本不会注入演示数据。

## 真机检查

以下行为需要带电话能力的真实 Android 手机验证：直接拨号、无 SIM/飞行模式提示、厂商通话记录兼容性、系统联系人照片、中文 TTS 引擎声音及媒体音量联动。Android 设备若未安装中文语音数据，应用会显示醒目的“请让家人安装中文语音”提示。

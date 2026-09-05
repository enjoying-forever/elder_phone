# 看图电话 — Native Android App (NOT a website)

You are building a **native Android application** (Kotlin + Jetpack Compose). This is NOT a webpage, NOT a PWA, NOT Flutter Web, NOT React Native Web. Output an Android Studio / Gradle project that produces an APK.

Work in: `/workspace/kantu-phone`
Install JDK 17 and Android command-line SDK if missing (no JAVA_HOME yet). minSdk 24, target/compile current stable. Package: `com.kantu.phone`. App label: `看图电话`. No ads, no IAP, no analytics, no tracking.

Use your **subagents / other models** (worker, scout, reviewer) to implement. Default model is fine. Spawn workers for parallel file work if that helps. Do not stop at a mock or HTML preview.

## Product

For illiterate / low-literacy elderly in China. Every relevant control **must speak** via Android `TextToSpeech` (`Locale.CHINA`) on click. Independent TTS volume in hidden settings, using the system media/TTS stream so it stays consistent with system volume (`AudioManager.STREAM_MUSIC` slider in-app).

Two main pages (tabs or a 2-page pager). A **hidden settings** entry at the top-right (low-contrast / easy to miss, still tappable).

### Page 1 — Dialer + call history

Split screen 50/50 vertical.

**Top half — call history**
- Synced with phone call log + contact names/photos (`READ_CALL_LOG`, `READ_CONTACTS`).
- Scrollable list.
- First tap: row background darkens (selected), TTS the **name** if known, otherwise the **phone number**.
- Second tap on the **same** selected row: TTS `正在拨打{名字或号码}`, then place the call (`ACTION_CALL` + `CALL_PHONE`).
- If user taps a different row, that becomes the new selection (darken + TTS), does not call yet.

**Bottom half — keypad**
- Number input field **above** the keys, shows the typed number.
- Standard 0–9 keys. Layout:
  ```
  [ number display ]
  1 2 3
  4 5 6
  7 8 9
  清除  0  拨打
  ```
- Besides the 10 digit keys: **left = red 清除**, **right = green 拨打电话**.
- Every digit click: TTS that digit, append to the input.
- 清除: TTS `清除`, delete last digit (or long-press clear all if you add it — still TTS).
- Green 拨打: TTS `正在拨打{号码}`, then call. If empty, TTS `请输入号码`.
- Every key click has TTS.

### Page 2 — Contacts

Synced with the phone address book (`READ_CONTACTS`), two-way as far as reading + calling. Do not require write unless needed to show photos.

**Top half — contact list**
- Photo on the **left** of each row (system contact photo; placeholder if none).
- First tap: row darkens, TTS the name (or number if no name).
- Second tap on the same selected contact: TTS `正在拨打{谁}`, then call.

**Bottom half — extra controls for navigation**
- **Middle:** up and down arrow buttons (iterator). They move the selection through the list, keep the selected row in view (list scrolls in sync), and TTS the newly selected contact name.
- **Left:** 回到顶部 — jump to top of list, TTS `回到顶部`.
- **Right:** 拨打电话 — call the currently selected contact, TTS `正在拨打{谁}` then call.
- All of these speak.

### Hidden settings (top-right)

Only one setting: **this app's TTS / 播报 volume**, independent control in this app, bound to the system stream so it stays consistent with system volume. Slider + maybe mute. Opening settings TTS `设置`. Changing volume TTS a short sample (`音量` or a beep-like spoken word). Nothing else in settings. No ads, no account, no extra toggles.

### TTS rules (must)

Speak on: tab switch, history row tap, contact row tap, every keypad key, clear, call, iterator up/down, back-to-top, settings open, volume change, permission grant/deny outcomes, errors (`无法拨打`, `没有权限`, empty list).
Chinese only. Slow-ish speech rate (~0.85) for elderly. If Chinese TTS engine missing, still attempt and show a huge child-facing hint.

### Permissions (child-facing first run)

CALL_PHONE, READ_CONTACTS, READ_CALL_LOG. Request with Chinese explanations. After grant, never nag the elder UI. No SMS, no location, no IMEI, no query-all-packages, no WeChat accessibility, no launcher replacement.

### Navigation

Two large tabs: `拨号` | `通讯录`. Switching a tab TTS the tab name. Portrait lock. Huge tap targets, high contrast, large type. No tiny text as the only affordance.

### Quality

- Room or in-memory + ContentResolver observers so history/contacts stay in sync.
- Handle no SIM / airplane mode: TTS + huge visual `无法拨打`.
- Debug fake data if emulator has empty contacts so UI can be demoed; still wire real ContentResolver.
- README in Chinese: how to build APK (`./gradlew assembleDebug`), how to install, required permissions.
- `./gradlew assembleDebug` must succeed.

Do not produce a website. Do not stop until the Gradle debug APK builds, or you hit a hard environment blocker — then document the blocker and leave a complete source tree.

When done, report: project path, APK path if built, what still needs a real phone to verify.

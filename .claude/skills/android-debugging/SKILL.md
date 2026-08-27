---
name: android-debugging
description: Practical Android and ADB debugging for this repo — device state, app state, logcat, install/clear/force-stop, and a fixed outside-in debugging order. Use when a test fails on a device, the app crashes or ANRs, adb misbehaves, or an emulator will not cooperate.
---

# Android debugging

App under test: **`com.si13.forgetty`**. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Debugging order — follow it, do not skip ahead

```
TEST FAILURE
   → DEVICE STATE        is there a healthy, authorized device?
   → APP STATE           right build installed? clean state? dialog in the way?
   → LOGCAT              did it crash / ANR / throw?
   → NETWORK / BACKEND   Firestore reachable? offline banner? (only if relevant)
   → CODE / TEST         last — once everything above is ruled out
```

Most wasted debugging time comes from starting at the bottom. An "element not found" caused
by a crash is not a locator problem, and editing the selector will never fix it.

## Device state

```bash
adb devices -l                  # look for: device / offline / unauthorized / (empty)
adb get-state
adb shell getprop ro.build.version.sdk      # API level — app minSdk is 26
adb shell getprop ro.product.cpu.abi
adb reconnect                   # first thing to try when a device goes offline
adb kill-server && adb start-server
```

| Symptom | Meaning | Fix |
|---|---|---|
| empty list | no emulator/device running | start an emulator; `emulator -list-avds` |
| `offline` | adb lost the connection | `adb reconnect`, then restart the server |
| `unauthorized` | USB debugging not trusted | accept the dialog on the device |
| more than one device | commands become ambiguous | `adb -s <serial>` / `maestro --device <serial>` |

## App state

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb uninstall com.si13.forgetty
adb shell pm clear com.si13.forgetty                 # wipe all app data (full reset)
adb shell am force-stop com.si13.forgetty            # kill the process, keep data
adb shell pm list packages | grep si13          # is it installed at all?
adb shell dumpsys package com.si13.forgetty | grep -E "versionCode|firstInstallTime|flags"
adb shell am start -n com.si13.forgetty/.SplashActivity
adb shell dumpsys activity activities | grep -i si13    # what is actually on top?
```

Remember for this app:

- `pm clear` brings back **both** the login bottom sheet and the ~100-task debug seeder.
- The launcher activity is `SplashActivity` (~650 ms), not `MainActivity`.

## Logcat

```bash
adb logcat -c                                   # clear BEFORE the run, so output is scoped
# ... run the test ...
adb logcat -d > artifacts/logcat-<stamp>.txt    # -d = dump and exit, never tail forever

# targeted reads
adb logcat -d | grep -iE "fatal|androidruntime|si13"
adb logcat -d -s AndroidRuntime:E ActivityManager:W
adb logcat -d | grep -iE "anr in|not responding"
adb logcat -d --pid=$(adb shell pidof -s com.si13.forgetty)
```

`scripts/collect-logcat.sh` does the timestamped dump into `artifacts/`. Always use `-d`
in automation — an untermined `adb logcat` hangs CI forever.

## Failure categories and their signatures

| Category | How you recognize it | Where to look |
|---|---|---|
| **device** | empty/offline/unauthorized `adb devices` | `adb devices -l`, `adb reconnect` |
| **permission** | `SecurityException`, a runtime dialog blocking the UI | `adb shell dumpsys package com.si13.forgetty \| grep -A20 permissions` |
| **process** | app died mid-test, next step "not found" | `adb shell pidof com.si13.forgetty`, logcat |
| **crash** | `FATAL EXCEPTION` + stack trace | `adb logcat -d -s AndroidRuntime:E` |
| **ANR** | `ANR in com.si13.forgetty`, app frozen | logcat; `adb shell cat /data/anr/traces.txt` |
| **activity** | wrong screen on top, navigation didn't happen | `adb shell dumpsys activity activities` |
| **package** | `Unable to find explicit activity class` | `pm list packages`, reinstall |
| **install** | `INSTALL_FAILED_*` | signature mismatch → `adb uninstall` first; no space → clear storage |
| **emulator** | boots slowly, black screen, GPU errors | boot with `-gpu swiftshader_indirect -no-snapshot-load` |

Common permissions in this app: `POST_NOTIFICATIONS` (runtime, API 33+),
`ACCESS_NETWORK_STATE`, `INTERNET`. Grant one without a UI:

```bash
adb shell pm grant com.si13.forgetty android.permission.POST_NOTIFICATIONS
```

## Useful odds and ends

```bash
adb shell settings put global window_animation_scale 0     # animations off = stable tests
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

adb shell input keyevent KEYCODE_BACK
adb exec-out screencap -p > artifacts/screen.png
adb shell dumpsys deviceidle | head            # doze can defer alarms/reminders
adb shell df /data                             # low storage breaks installs
```

## Rules

- Clear logcat **before** the run, dump **after**. Untargeted logcat is noise, and noise
  gets skimmed.
- Attach the evidence to the finding. "It crashed" without the stack trace is not a report.
- One change at a time. Reinstalling, clearing data and editing the test all at once tells
  you nothing about which one mattered.
- Never `sudo`. Never install SDK components without asking the human first.

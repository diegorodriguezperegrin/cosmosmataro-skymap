---
description: Clean build, uninstall, install, and verify version
---

1. Clean build directory to remove stale APKs
   `Remove-Item app/build -Recurse -Force -ErrorAction SilentlyContinue`

2. Assemble Debug Build
   `./gradlew assembleDebug`

3. Uninstall existing package
   `adb uninstall org.cosmosmataro.skymap`

4. Install GMS Debug APK
   `adb install -r app/build/outputs/apk/gms/debug/app-gms-debug.apk`

5. Verify Installed Version
   `adb shell dumpsys package org.cosmosmataro.skymap | findstr version`

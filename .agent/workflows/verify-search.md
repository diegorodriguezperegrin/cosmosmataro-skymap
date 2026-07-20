---
description: Verify Inline Search Logic
---
1. Build Debug APK
// turbo
2. Install APK to Device
// turbo
3. Launch DynamicStarMapActivity (with correct package)
// turbo
4. Capture Screenshot of Launch

```powershell
./gradlew assembleDebug
& 'C:\Users\diego\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r -g app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
& 'C:\Users\diego\AppData\Local\Android\Sdk\platform-tools\adb.exe' shell am start -n org.cosmosmataro.skymap/com.google.android.stardroid.activities.DynamicStarMapActivity
Start-Sleep -Seconds 5
& 'C:\Users\diego\AppData\Local\Android\Sdk\platform-tools\adb.exe' shell screencap -p /sdcard/search_verify_workflow.png
& 'C:\Users\diego\AppData\Local\Android\Sdk\platform-tools\adb.exe' pull /sdcard/search_verify_workflow.png C:\Users\diego\.gemini\antigravity\brain\9f1c849d-2c33-4ef1-be2d-e10cb20013cd/search_verify_workflow.png
```

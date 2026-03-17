# ---- CONFIG ----
$device = 'adb-RFCX5083CEX-oFdOzS._adb-tls-connect._tcp'  # <-- your phone from `adb devices`
$pkg    = 'com.privacy.faraday'                               # <-- your applicationId

# ---- BUILD + INSTALL ----
.\gradlew :app:installDebug

# ---- LAUNCH ----
adb -s $device shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Installed + launched on $device"

# Implementation Steps

## Step 1 — Add dependencies to build.gradle (app level)
```
dependencies {
    // Retrofit + OkHttp
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

    // Compose + Material3
    implementation platform('androidx.compose:compose-bom:2024.02.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.activity:activity-compose:1.8.2'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-compose:2.7.0'

    // WorkManager
    implementation 'androidx.work:work-runtime-ktx:2.9.0'

    // DataStore
    implementation 'androidx.datastore:datastore-preferences:1.0.0'

    // Security (EncryptedSharedPreferences)
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'

    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

## Step 2 — AndroidManifest.xml additions
Add inside <manifest>:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Add inside <application>:
```xml
<activity android:name=".ConnectActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
<activity android:name=".MainActivity" android:exported="false" />
<service android:name="androidx.work.impl.background.systemalarm.SystemAlarmService"
    android:exported="false" />
```

## Step 3 — File placement
Place all 5 Kotlin files in:
app/src/main/java/com/example/nagiosmonitor/

- ConnectActivity.kt   ← launcher, login screen
- MainActivity.kt      ← main app shell with bottom nav
- NagiosScreens.kt     ← all 4 compose screens
- NagiosViewModel.kt   ← viewmodel + repository + retrofit
- NagiosNotifications.kt ← worker + notification logic

## Step 4 — Find your Nagios JSON API URL
Your Nagios already exposes:
http://YOUR_SERVER_IP/nagios/cgi-bin/statusjson.cgi?query=hostlist&details=true
http://YOUR_SERVER_IP/nagios/cgi-bin/statusjson.cgi?query=servicelist&details=true

Test in browser with your credentials first. If path differs, check:
http://YOUR_SERVER/nagios/cgi-bin/status.cgi  (the web UI path)
The JSON API is always at: /nagios/cgi-bin/statusjson.cgi

## Step 5 — Run
1. Launch app → ConnectActivity appears
2. Enter: http://YOUR_IP/nagios, username, password
3. Tap Connect → goes to MainActivity
4. Background worker polls every 15 min and fires notifications on new alerts

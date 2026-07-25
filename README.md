# WebCarry Power Dialer — Android Companion App

This app pairs with the **Power Dialer** module inside the WebCarry Enterprise
Booking WordPress plugin (staff dashboard → "Power Dialer" → "Connect Phone").
Once paired, calls and SMS placed from the website dashboard go out through
**this phone's own SIM and network**, and every call/SMS is reported back so
it shows up in the dashboard's Call Log and Contacts screens.

## What this app needs, and why

| Permission | Why |
|---|---|
| Camera | To scan the pairing QR code shown on the dashboard (one time). |
| Call Phone | To dial numbers the dashboard asks it to dial. |
| Read Phone State / Read Call Log | To detect when a call ends, its duration, and whether it was answered/missed, so this can be reported to the dashboard. |
| Send / Read / Receive SMS | To send SMS the dashboard asks it to send, and to report incoming SMS. |
| Notifications | To show the "Connected" ongoing notification (required for a foreground service on Android 8+). |
| Boot Completed | So pairing survives a phone restart without the user having to reopen the app. |

The app is **not** a default dialer/SMS replacement — it works alongside
whatever apps you already use for calls and texting.

## 1. Requirements

- [Android Studio](https://developer.android.com/studio) (Koala or newer recommended)
- A physical Android phone (Android 8.0 / API 26 or newer) — an emulator
  cannot place real SIM calls or send real SMS, so use a real device.
- The WebCarry plugin update installed and activated on your WordPress site
  (see the plugin zip's own README).
- Your WordPress site must be reachable over **HTTPS** from the phone (a
  normal live site with SSL is fine; for local testing without SSL, use a
  tunnel like `ngrok http 443` or `cloudflared tunnel`, or temporarily set
  `android:usesCleartextTraffic="true"` in `AndroidManifest.xml` for testing
  only).

## 2. Open and build the project

1. Extract this project folder.
2. Open Android Studio → **Open** → select the `PowerDialerApp` folder.
3. Let Gradle sync (first sync downloads dependencies — needs internet).
4. Connect your Android phone via USB with **USB debugging** enabled
   (Settings → About phone → tap "Build number" 7 times → Developer options →
   USB debugging), or use Android Studio's wireless debugging.
5. Click **Run ▶** and select your phone. The app installs and launches.

### Building an installable APK directly (no Play Store)

`Build` menu → `Build Bundle(s) / APK(s)` → `Build APK(s)`. Android Studio
will show a notification with a "locate" link to the generated
`app-debug.apk`. Copy that file to any phone and open it to install — the
phone will ask you to allow "install from this source" the first time; that
is expected for apps installed outside the Play Store.

For a proper release build (recommended once you're happy with testing):
`Build` → `Generate Signed Bundle / APK` → follow the wizard to create your
own signing key, then distribute the resulting APK the same way.

> **Why not the Play Store?** Google Play has restricted which apps may
> request SMS/Call Log permissions since 2019 — only a small set of approved
> categories (like being the user's default SMS/Phone app) qualify. Direct
> APK installation (exactly like installing an internal company app) avoids
> that restriction entirely and is completely normal for internal business
> tools like this one.

## 3. Pairing a phone

1. On the WordPress staff dashboard, click **Power Dialer** → the **Connect
   Phone** tab shows a QR code.
2. Open this app on the employee's phone → grant the requested permissions →
   tap **Scan QR Code From Dashboard**.
3. Scan the code. The dashboard should show "Connected" within a few
   seconds, along with the device model.
4. (Recommended) Enter the phone's own SIM number in the app so it shows up
   on the dashboard too — Android does not reliably expose this
   automatically on modern versions/carriers.
5. Tap **Allow Background Activity** and disable battery optimization for
   this app, so Android doesn't kill the background connection.

## 4. How it behaves day-to-day

- Clicking **Call** or **SMS** next to a contact on the dashboard sends a
  request to the paired phone within a few seconds; the phone places the
  call / sends the SMS using its own SIM, exactly as if the employee dialed
  it themselves.
- When a call ends (whether placed from the dashboard, dialed manually on
  the phone, or an incoming call), the app reports the number, direction,
  result (answered/missed/no-answer/rejected), and duration back to the
  dashboard automatically.
- Incoming SMS are reported back the same way.
- If the phone loses internet access, queued dashboard requests simply wait
  until it reconnects; nothing is lost.

## 5. One phone number, one phone

Each staff member should pair their **own** phone under their **own**
dashboard login. A device can be unpaired any time from either side:

- **From the phone:** open the app → **Disconnect This Phone**.
- **From the dashboard:** Power Dialer → Connect Phone → **Disconnect
  Phone**.

## 6. Project structure

```
PowerDialerApp/
  app/src/main/java/com/webcarry/powerdialer/
    MainActivity.kt              — pairing UI, permissions, QR scan
    PowerDialerApp.kt            — notification channel setup
    api/                         — Retrofit models + client for
                                    wp-json/wcab-power-dialer/v1/*
    prefs/SecurePrefs.kt         — encrypted local storage of the
                                    site URL + device token
    sync/CallSmsSyncService.kt   — foreground service: heartbeat, queue
                                    polling, placing calls/SMS
    sync/CallStateReceiver.kt    — detects call answered/missed/duration
    sync/SmsReceiver.kt          — detects incoming SMS
    sync/BootReceiver.kt         — restarts the connection after reboot
  app/src/main/res/              — layout, strings, theme, icon
```

## 7. Troubleshooting

- **"Pairing failed"** — the QR code expires after 5 minutes; go back to the
  dashboard and generate a new one.
- **Calls dial but never get logged as answered/missed** — make sure "Read
  Call Log" permission is granted (Settings → Apps → WebCarry Power Dialer →
  Permissions).
- **App stops working after a while** — open your phone's battery settings
  and exclude this app from battery optimization / "sleeping apps" (naming
  varies by phone brand — Xiaomi/Oppo/Vivo/Samsung all have their own extra
  battery-saving screens beyond stock Android's).
- **SMS not sending on a dual-SIM phone** — Android will use whichever SIM
  is set as the default for SMS in your phone's SIM settings.

# Xiao AI Window Handoff (experimental)

This is an **actual APK prototype**, but it is **not verified to catch Xiao AI's floating bubble** on HyperOS 3. It uses a foreground service only when the user turns it on.

- No root, Accessibility, microphone, screenshots, screen recording, or notification listening.
- It requires **android.permission.DUMP** (a sensitive developer permission) granted once via ADB.
- Checks `dumpsys window windows` every 800ms while the screen is on and tries to identify a visible window owned by Xiaomi VoiceAssist.
- On a new matching window, requests launching Gemini and posts a clickable notification fallback.
- Does not force-stop Xiao AI and cannot dispatch a Back gesture without a privileged mechanism.
- Does not record raw window dumps, and makes no network requests.

## Install and grant

Install APK, then use ADB:

```bat
adb shell pm grant com.ldd.xiaoaiwindowhandoff android.permission.DUMP
```

Open the app and enable **Bật theo dõi Xiao AI**. You can stop it in the app any time.
If the ADB grant is rejected by this OEM, the app cannot do the detection.

To revoke the sensitive permission:

```bat
adb shell pm revoke com.ldd.xiaoaiwindowhandoff android.permission.DUMP
```

The app does not install accessibility services. Banking/VNeID compatibility has not been verified, and `DUMP` exposes sensitive system state. Inspect the source and weigh privacy risk before using on a primary device.

## Limitations

WindowManager does not have to represent a Xiaomi hotword bubble as a separately visible window. The detection heuristics may miss it or mistake an ordinary Xiaomi screen for voice UI. The OS may block background activity launches, and opening Gemini does not imply Xiao AI has been closed. No guaranteed `小爱同学` → Gemini experience.

This prototype is independent of the GeminiGuard accessibility toggle fix and can be removed without affecting default assistant settings.

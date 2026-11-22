# Android release checklist

## Before first installation
- [ ] GitHub Actions build completes successfully.
- [ ] Download the `dastyar-android-debug-*` artifact from the successful workflow run.
- [ ] Install on a test device and confirm the package launches.
- [ ] Grant permissions one by one; verify denying an optional permission does not block unrelated features.
- [ ] Test microphone foreground-service start/stop and persistent notification.
- [ ] Test wake word recognition in Persian and measure battery use over at least 1 hour.
- [ ] Test contact matching with Persian and Latin spellings, duplicate names, and multiple numbers.
- [ ] Test call and SMS confirmations, including cancel/decline paths.
- [ ] Test reminder delivery after app process death and device reboot.
- [ ] Test accessibility only after the user explicitly enables it in Android Settings.
- [ ] Test encrypted API-key and memory persistence; verify no secret appears in logs.
- [ ] Test on Xiaomi HyperOS with battery restrictions both enabled and disabled.

## Before public distribution
- [ ] Review current Google Play target API and sensitive-permission policies.
- [ ] Replace debug signing with a securely managed release signing key.
- [ ] Never commit keystore files or passwords to GitHub.
- [ ] Review the alpha AndroidX Security Crypto dependency and plan migration to a maintained Keystore-backed solution.
- [ ] Complete a privacy policy and data-safety disclosure appropriate to actual data flows.
- [ ] Perform a release build and inspect the merged manifest.

## Current status
This source package is a development candidate, not a verified release. No APK is included because a real Gradle build has not yet run in this environment.

# Stage 6 — Security review and permission hardening

## Changes applied

- Runtime dangerous permissions are now requested independently in the Home screen. The user can grant microphone, contacts, call, send-SMS, read-SMS, and notification access separately.
- Removed `QUERY_ALL_PACKAGES`; app discovery is limited to launcher activities through a `<queries>` intent declaration.
- Changed the boot reminder receiver to `android:exported="false"` because it is an internal receiver and does not need to accept broadcasts from other apps.
- Kept cleartext traffic disabled and app backup disabled as before.
- Kept the Accessibility service protected by `BIND_ACCESSIBILITY_SERVICE`; it remains user-enabled from Android Settings.
- Kept explicit confirmation for sensitive actions according to the selected confirmation mode.

## Security observations / remaining work

- `READ_SMS` and `SEND_SMS` are still declared because the current product includes those explicit features. They are no longer requested together with unrelated permissions.
- `SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS`, notification-policy access, and battery-optimization exemption are special accesses. Android requires user action in Settings; they must remain optional and feature-specific.
- Accessibility can expose screen content and perform UI actions. The service should be enabled only when the user wants screen-control features. Do not use it to bypass Android security dialogs or perform hidden actions.
- API credentials and approved memories use EncryptedSharedPreferences. The current AndroidX Security Crypto dependency is an alpha release and should be reviewed before production release; a migration plan to a maintained Android Keystore-backed storage implementation is recommended.
- The current app uses targetSdk 34 and compileSdk 34. Target SDK policy and current Play requirements must be checked before any public distribution.
- No static review can prove runtime security. Device tests are still required, especially on Xiaomi HyperOS.

## Validation performed

- Manifest XML well-formedness checked.
- Permission declarations and exported components reviewed.
- Kotlin source changes checked textually.
- Full Gradle build and on-device tests were not run in this environment.

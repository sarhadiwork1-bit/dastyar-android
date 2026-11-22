# Stages 6–8 — Security, CI build and integration package

## Stage 6: Security and permissions
- Independent runtime permission requests.
- Removed broad package visibility permission.
- Restricted the internal boot receiver.
- Added a security review with remaining production risks.

## Stage 7: Build pipeline
- Updated GitHub Actions with minimal repository permissions, concurrency control, JDK 17, Gradle 8.9, manifest XML validation, debug APK build, and artifact upload.
- Artifact is available only after a successful GitHub Actions run.

## Stage 8: Integration and handoff
- Consolidated source and documentation into one handoff package.
- Added a device test and release checklist.
- No APK is claimed as built because this environment has no network access to resolve Android/Gradle dependencies and no Android SDK build environment.
- Web development remains intentionally deferred until the Android app passes build and device validation, as previously agreed.

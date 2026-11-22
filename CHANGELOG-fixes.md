# Fixes applied (post stage 6–8 handoff)

## 1. Accessibility service configuration
- Expanded `accessibilityEventTypes` to include window content changes, clicks, focus and text changes.
- Added flags: `flagReportViewIds`, `flagIncludeNotImportantViews`.
- Enabled `canPerformGestures="true"` for more reliable UI control.
- Linked `settingsActivity` to MainActivity.

Previous config only listened to `typeWindowStateChanged`, which is too weak for reliable screen reading and interaction on modern Android / HyperOS.

## 2. Default Claude model ID
- Changed default from the invalid / outdated `claude-sonnet-5-5` to the valid API model ID `claude-sonnet-5`.
- Users can still override the model string in Settings.

## 3. Documentation consistency
- README artifact name aligned with the GitHub Actions workflow (`dastyar-android-debug-*`).

## Notes
- Full Gradle build and on-device test still required after uploading to GitHub.
- The source tree root for the repository must be the contents of the `dastyar/` folder (including `.github/`), not the outer `stage5/` folder.

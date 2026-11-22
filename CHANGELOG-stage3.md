# Stage 3 — Phone capability safety and execution reliability

## Changes
- Calls now require confirmation under the recommended balanced confirmation mode.
- Quick-settings toggles now require confirmation under balanced mode.
- Text entry through Accessibility now requires confirmation under balanced mode and rejects empty or oversized input.
- SMS sending rejects empty messages and messages over 500 characters; the confirmation prompt contains the complete message so the user is not asked to approve a truncated preview.
- SMS results no longer claim delivery: the tool reports that the request was handed to Android's SMS service and that delivery is not confirmed.
- Duplicate contact matches with multiple phone numbers are treated as ambiguous; the assistant must ask the user to choose instead of silently selecting the first number.
- Audit logs suppress contact lookups, call targets, SMS content, read messages, screen text, and typed content.
- URL opening rejects invalid hosts and URLs containing embedded user credentials.
- The configured blocked-app list is enforced when launching an app by package name.

## Verification limits
- Source-level review and ZIP integrity checks only.
- Android compilation, runtime permission flows, SMS delivery broadcasts, OEM behavior, and device testing have not been performed.
- Android/HyperOS may restrict or alter some quick-settings tiles and background behavior.

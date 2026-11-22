# Stage 5 — AI session and private memory foundation

- Added a local memory store backed by the app's existing EncryptedSharedPreferences.
- Memory is saved only through an explicit `save_memory` tool call and a mandatory in-app confirmation, independent of the general confirmation mode.
- Added `list_memory` and exact-match `forget_memory` tools; forgetting also requires confirmation.
- Added limits (40 items, 240 characters each), duplicate detection, and basic rejection of obvious credentials, API keys, one-time codes, and payment-card security data.
- Only user-approved memory items are injected into the agent system context; arbitrary chat history is not promoted to memory.
- Memory content is excluded from audit-log arguments.
- Renamed the user-facing AI action from `ask_claude` to provider-neutral `ask_ai`; the configured provider is still Anthropic in this build.
- Added settings-screen disclosure of local memory count and storage behavior.

## Not yet completed / requires later work

- This stage does not add a second live AI vendor adapter. `AiProvider` remains the extension boundary; Anthropic is the only configured implementation.
- No cloud synchronization or web memory access is implemented.
- Compile, API integration, encrypted-storage migration, and device tests have not been run in this environment.

## Stage 6 — Security and permission review
- Added independent runtime permission requests in the Home screen.
- Removed broad package visibility permission and declared launcher intent visibility instead.
- Restricted the internal boot receiver to non-exported.
- Added `SECURITY_REVIEW_STAGE6.md` documenting remaining security and release checks.

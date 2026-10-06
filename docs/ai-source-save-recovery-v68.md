# AI source save recovery V68

## Proven gap

The generated report remains available when `BookSourceStoreV36` rejects a write, so the user can
retry safely. The V67 screen, however, still labelled that action as a first save. When a later
write succeeded, the report disappeared without an AI-page confirmation and the previous
`aiError` could remain visible. A recovered write could therefore look like another failure.

## Change

- A report with a terminal save error now exposes **重试保存书源** or
  **重试保存搜索书源** while keeping the generated report intact.
- A confirmed write atomically clears the report, progress, stopped flag, and stale AI error, then
  publishes **书源已保存** with the exact source name.
- The confirmation is cleared by the next accepted generation attempt, an invalid new request, or
  explicit stop. URL and test-book inputs remain owned by the existing `rememberSaveable` route.
- Parsing, source validation, optimistic store comparison, transaction failure handling, browser
  transport, and source identity rules are unchanged.

This follows Android's UI-layer guidance that UI state should expose durable operation outcomes and
the action needed to recover, rather than asking the composable to infer them. The implementation is
independent. Legado commit `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) and Readium
commit `1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) remain behavior and lifecycle
references only; no code or native bridge was copied.

## Regression coverage

- `AiSourceSaveRecoveryV68Test#successfulRetryClearsTheFailedSaveAndPublishesTheStoredSource`
- `AiSourceSaveRecoveryV68Test#failedSaveUsesAnExplicitRetryLabelForEitherSourceKind`
- `SourceBrowserSessionV56DeviceTest#failedAiSourceSaveShowsRetryThenAConfirmedSavedState`

The device test renders the real Compose screen, clicks the retry action, transitions through the
production state reducer, and verifies that the stale failure disappears while both generation
modes remain usable. Existing store atomicity and complete browser/reader process-recovery suites
remain the authority for real storage and process behavior.

## Verification boundary

The full unfiltered Reader suite, complete Browser group, JVM suite, Debug build, lint, and both
independent force-stop recoveries must pass on the final commit before this batch is accepted. Real
AI providers, third-party login/CAPTCHA, physical devices, API 28 renderer reclamation, and real
split/freeform windows remain outside the authorized evidence.

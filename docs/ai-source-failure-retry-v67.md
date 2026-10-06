# AI source failure retry mode V67

## User-visible gap

An AI source build can fail after the user deliberately selected browser mode for dynamic content,
session cookies, or a visible verification step. The error remained visible and both generation modes
were available, but their labels and order reverted to the initial form. A retry therefore gave no
indication which transport had just failed and made it easy to restart in normal mode accidentally.

## Change

- `OnlineBooksStateV36.aiLastUseBrowser` records the transport only after a valid attempt is accepted.
- A terminal failure makes that same transport the primary, explicitly labelled retry action.
- The other transport remains available as a secondary **改用…模式** action; no automatic fallback or
  hidden network request was added.
- Explicit stop still shows the V65 recovery state with the original two choices, and a new attempt still
  clears stale steps, report, error, and stop state without changing unrelated search or shelf data.

The implementation follows Android's guidance that screen error state may carry metadata for the action
which retries the failed operation, while keeping the ViewModel as the screen-state source of truth:

- [Android UI layer: error state and retry actions](https://developer.android.com/topic/architecture/ui-layer)
- [Android Compose state hoisting](https://developer.android.com/develop/ui/compose/state-hoisting)

The established comparison pins remain [Legado `3a7c4da`](https://github.com/gedoor/legado/tree/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba)
under GPL-3.0 and [Readium Kotlin Toolkit `1b1f6b3`](https://github.com/readium/kotlin-toolkit/tree/1b1f6b308a7b6f968b2bf1e66c84912879466f75)
under BSD-3-Clause. They were used only to compare explicit source/session lifecycle behavior; no GPL
implementation or native bridge was copied.

## Regression coverage

- `AiSourceFailureRetryV67Test#acceptedStartRecordsItsModeAndClearsOnlyThePreviousAttemptState`
- `AiSourceFailureRetryV67Test#terminalFailureRetriesTheSameTransportFirstAndKeepsTheAlternative`
- `SourceBrowserSessionV56DeviceTest#failedAiGenerationRetriesItsBrowserModeFirstAndStillOffersNormalMode`

The device test renders the real Compose screen for both browser and normal failures, keeps each error
visible, clicks both retry choices, and verifies the selected transport rather than replacing it with a
mock success callback.

## Acceptance status

Fresh unfiltered Reader, Browser, JVM, Debug/lint, EPUB process recovery, and browser process recovery
must complete on the final commit before this batch is considered accepted. Earlier V66 results are
historical baselines only.

## Preserved first Android failure

The first Android run for implementation commit `c80bf038`,
[37197721849](https://github.com/xgl34222220-ops/langhuan/actions/runs/37197721849), compiled the
production and test sources, then executed 722 JVM tests with one failure and no skipped tests. The
failure was the retained V65 source-contract test
`AiSourceCancellationRecoveryV65Test#stopActionPublishesVisibleRecoveryAndTheNextStartClearsIt`:
it looked for the previous inline `copy(aiError = null, aiStopped = false)` text after V67 moved that
same transition into the directly tested `aiSourceStartingStateV67` reducer. No product assertion or
stop/retry behavior failed.

The corrective change keeps the original V65 test and replaces only that stale source-text lookup with
an assertion that `buildWithAi` actually invokes the V67 start reducer. The reducer's exact clearing and
preservation behavior remains covered by
`AiSourceFailureRetryV67Test#acceptedStartRecordsItsModeAndClearsOnlyThePreviousAttemptState`; the
assertion is not removed or weakened. The original failed verification artifact is retained as
`Langhuan-verification-reports` 11301940411, 294,515 bytes, SHA-256
`41bc90b62d0eb6047d327f544d6c35373b1d560d140442327d865367e5f8b3b8`.

The same implementation commit's Browser run
[37197721870](https://github.com/xgl34222220-ops/langhuan/actions/runs/37197721870) completed 18 / 0
failed / 0 skipped and both real process phases, but it cannot replace a fresh Browser run for the
corrective commit. Its retained artifact is `ai-source-browser-qa` 11302101002, 41,995,812 bytes,
SHA-256 `60d937e8399d2e04dd88d0691070fd0fa8e204124b11cba26d873dc69eb2ef4b`.

The implementation commit's unfiltered Reader run
[37197721829](https://github.com/xgl34222220-ops/langhuan/actions/runs/37197721829) also completed 150 / 0
failed / 0 skipped, including the different-PID EPUB seed/force-stop/restore check with its saved Locator.
It likewise remains historical rather than final-commit evidence. Its retained artifact is
`reader-device-qa` 11301716887, 60,140,360 bytes, SHA-256
`3f55c777f36cea89ea3ea8a737e1b2c4b07121d503dec4a6f1b7bb24675c6bbd`.

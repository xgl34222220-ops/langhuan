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

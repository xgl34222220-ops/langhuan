# Source timeout diagnostics V69

## Proven gap

The source engine already bounded direct and browser requests, and search retry already preserved
successful rows. A real `SocketTimeoutException`, however, still exposed transport text such as
`Read timed out` or `synthetic timeout` in the current V3 search and AI-source generation states.
That copy was inconsistent with the Chinese UI, could disclose a low-level host string, and did not
tell the user that the failed source could be retried.

## Change

- `sourceFailureMessageV69` recognizes a socket timeout anywhere in a bounded cause chain and maps
  it to **书源请求超时，网站响应较慢；请稍后重试** before it enters screen state.
- Ordinary HTTP and rule diagnostics remain available, with newlines removed and length bounded.
- The active multi-source search keeps successful rows and retries only the failed source exactly as
  before. AI generation keeps its existing stage, paid-model-call, cancellation, partial-rule, and
  browser-session boundaries; only user-visible timeout copy changes.
- Raw exception messages and host text are retained only as exception causes for diagnostics, not
  rendered to the user.

Android's UI-layer guidance models errors as screen state carrying both an understandable message
and a recovery action. OkHttp separately defines call/read time limits, so a timeout is treated as a
transport outcome rather than selector evidence. The implementation is independent. Legado commit
`3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) and Readium commit
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) remain behavior and lifecycle
references only; no GPL code or native bridge was copied.

References:

- Android UI layer: <https://developer.android.com/topic/architecture/ui-layer>
- Android state holders: <https://developer.android.com/topic/architecture/ui-layer/stateholders>
- OkHttp 4.12.0 client timeout source: <https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/OkHttpClient.kt>
- Legado fixed reference: <https://github.com/gedoor/legado/tree/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba>
- Readium fixed reference: <https://github.com/readium/kotlin-toolkit/tree/1b1f6b308a7b6f968b2bf1e66c84912879466f75>

## Regression coverage

- `SourceFailurePresentationV69Test#nestedTimeoutBecomesBoundedActionableChineseCopy`
- `SourceFailurePresentationV69Test#ordinaryFailureKeepsUsefulDiagnosticButRemovesNewlines`
- `AiRuleTransportV55Test#uncachedSearchTimeoutMustNotBecomeSelectorCorrection`
- `AiRuleTransportV55Test#nextContentPageTimeoutDoesNotSpendAnotherContentModelCall`
- `SourceSearchRecoveryV57DeviceTest#slowSourceTimeoutIsExplainedAndRetryRecovers`

The device test runs the real ViewModel, default browser-mode source engine, isolated WebView
transport, and Compose controls against the reserved legal fixture domain. It injects an actual
`SocketTimeoutException`, verifies that raw English/host text is absent and the Chinese diagnosis is
visible, then taps the existing retry action and confirms that only that source is fetched again.

## Verification boundary

The final commit requires the full unfiltered Reader suite, complete Browser group, JVM suite,
Debug build, lint, and both independent force-stop recoveries. Real AI providers, third-party
login/CAPTCHA, physical devices, API 28 renderer reclamation, real slow public websites, and real
split/freeform windows remain outside the authorized evidence.

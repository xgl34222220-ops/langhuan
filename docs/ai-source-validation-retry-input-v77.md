# AI source retry input truthfulness V77

## User-visible gap

V76 correctly discarded its in-memory validation checkpoint when `buildWithAi` received a different
site, book name, or transport. The visible action, however, only read a Boolean from the last failed
attempt. Because the editable URL and test-book fields are composable state, a user could change
either field and still see **保留已通过规则重试**. Clicking then started a fresh attempt, so the action
promised reuse which the ViewModel intentionally and correctly refused.

## Change

- The terminal retry state now carries the exact trimmed site URL, trimmed test-book name, and
  ordinary/browser transport which own the in-memory checkpoint.
- The same-mode action promises retained rules only while the currently visible fields still match
  that identity. Editing either field immediately changes it to the ordinary same-mode retry label.
- Whitespace at the field edges is treated the same way as `buildWithAi`; switching transport never
  shares a checkpoint.
- Reverting both fields to the same normalized values can show the promise again because the
  ViewModel still owns the same in-memory checkpoint and will perform the promised reuse.
- Starting, stopping, saving, successful completion, invalid input, and storage failure clear the
  visible identity together with the Boolean capability. The identity is not persisted.

Generation tokens, cancellation order, live page refetching, rule invalidation, provider calls,
source validation and storage transactions, browser profile/session boundaries, saved-source
identity, and V3 layout are unchanged.

## References and adoption boundary

- [Android state production](https://developer.android.com/topic/architecture/ui-layer/state-production)
  treats user input and state-holder output as the inputs to derived UI state. V77 derives the retry
  label from both instead of publishing a stale capability promise.
- [Android UI events](https://developer.android.com/topic/architecture/ui-layer/events) requires UI
  actions to describe the business event that will actually be handled. The label now matches the
  exact retry key already enforced by the ViewModel.
- Fixed behavior references remain Legado
  [`3a7c4daaf79c652b8e60d6aad80f665bbf6cacba`](https://github.com/apgk/legado/tree/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba)
  ([GPL-3.0](https://github.com/apgk/legado/blob/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba/LICENSE))
  and Readium
  [`1b1f6b308a7b6f968b2bf1e66c84912879466f75`](https://github.com/readium/kotlin-toolkit/tree/1b1f6b308a7b6f968b2bf1e66c84912879466f75)
  ([BSD-3-Clause](https://github.com/readium/kotlin-toolkit/blob/1b1f6b308a7b6f968b2bf1e66c84912879466f75/LICENSE)).
  V77 is an independent state/label correction and copies no GPL code or native bridge.

## Regression evidence required for the final commit

- `AiValidationRetryInputV77Test` proves exact normalized input and transport matching, and proves
  edited input downgrades only the reuse promise while preserving both retry modes.
- `SourceBrowserSessionV56DeviceTest#editedValidationInputRemovesTheCheckpointPromiseBeforeRetry`
  edits the real Compose text field, observes the label change before clicking, and verifies the
  edited values and original transport are delivered to the retry callback.
- The unfiltered Reader and Browser device groups, all JVM tests, Debug build, lint, EPUB process
  recovery, and browser process recovery must pass on the final commit. Earlier green runs are not
  substituted.

## Not claimed

This does not persist unfinished rules across process death, validate a real AI account, bypass site
verification, retry failures automatically, prove a physical device, or fix historical emulator
disconnects and Compose first-layout races.

## Validation history before the final commit

- Reader run [37275857219 attempt 1](https://github.com/xgl34222220-ops/langhuan/actions/runs/37275857219/attempts/1)
  passed the 22 reader prechecks, 2 editor tests and 11 creative tests, then lost the emulator
  during the unfiltered group with `AdbCommandRejectedException: device offline`. It completed 96
  tests successfully before the empty infrastructure failure; 64 tests and EPUB process recovery
  did not run. Artifact `11331270788` is 50,000,208 bytes with SHA-256
  `d4d8279a54484c47648d1712017f0d7ae45e01934812fe51dd790cf9b2208a03`.
- Attempt 2 executed all 160 unfiltered tests: 159 passed and the new V77 device method failed only
  after its screenshot/window evidence had already proved the downgraded label visible and enabled.
  The evidence helper's shell-copy work was followed by the test Activity being replaced and
  destroyed, so the next Compose touch lookup found no host node. Artifact `11334012632` is
  64,136,428 bytes with SHA-256
  `bac17c11ab7e0bbc236ca41bb2757bf1965b5e55e2e83f78bd42085f56996f5a`.
  The synchronization correction moves evidence capture after the unchanged real click and callback
  assertion; it changes no product code, removes no assertion, and narrows no suite.


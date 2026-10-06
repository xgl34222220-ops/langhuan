# AI provider unchanged-save revision V84

## Proven gap

V81 stops an active source-generation attempt when the exact provider revision changes. The
provider repository previously advanced that revision for every save, even when the normalized
address, protocol, model, temperature, JSON capability and retained encrypted credential were all
unchanged. Saving the same service from another entry point could therefore present a false
“service modified” stop to the active attempt.

## Change

- A provider revision now advances only when its effective configuration or credential changes.
- A blank credential continues to mean “retain the encrypted key”; explicitly resubmitting the same
  key is also unchanged.
- Selecting the same provider as default and normalizing a trailing slash do not impersonate a
  configuration edit.
- A different model, endpoint, protocol, temperature, capability or explicit credential still
  advances the revision and retains V81 cancellation semantics.
- Provider observation, V82 ordering, model/API-key boundaries, generation cancellation, validated
  rule checkpoints, save transactions, input state and browser sessions are unchanged.

## Independent acceptance

- `AiProviderConfigurationRevisionV84Test#unchangedSaveAndDefaultSelectionKeepTheConfigurationRevision`
  covers retained and explicitly identical encrypted credentials plus routing-only default intent.
- `AiProviderConfigurationRevisionV84Test#modelOrCredentialChangeStillAdvancesTheConfigurationRevision`
  preserves real-edit and new-provider invalidation.
- `SourceBrowserSessionV56DeviceTest#unchangedProviderSaveKeepsTheAcceptedAttemptRunning` starts a
  real isolated browser request, saves its exact Room/encrypted provider again through the
  repository contract, and verifies that the same revision, active identity and usable stop action
  remain on the rendered Compose page.

The device method uses original isolated HTML and synthetic local service endpoints. It does not
claim real-AI execution, public-network behaviour, or withdrawal of a remotely accepted request.

## References and licence boundary

Android state-production and UI-event guidance:
https://developer.android.com/topic/architecture/ui-layer/state-production
https://developer.android.com/topic/architecture/ui-layer/events

Legado commit `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0), Readium commit
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause), and OkHttp 4.12.0
(Apache-2.0) remain behavioural, lifecycle and cancellation-semantics references only. No GPL code,
rule or native bridge is copied.

# AI provider attempt identity V80

## Proven gap

V79 resolves one provider configuration from a current Room snapshot before the AI builder starts. That
configuration is intentionally fixed for the accepted attempt. The provider card, however, continued to
render the live default-provider Flow. If the user changed the default while the website validation or
model call was still running, the card changed to the new service even though the request, API key,
model and possible billing still belonged to the already resolved service.

## Change

- Repository resolution now returns the provider id, display label and configuration from the same
  ordered Room snapshot. API-key material remains inside the existing repository/key-store boundary.
- The ViewModel records only the resolved display identity in an in-memory active-attempt field.
- While an attempt is running, the provider card shows that exact identity. The live provider Flow keeps
  updating independently, and the card returns to the current default immediately after success,
  failure or explicit cancellation.
- No provider choice is changed mid-attempt. Model protocol, request configuration, V76/V77 validation
  checkpoints, cancellation generation, save transaction, remembered inputs, browser transport and
  session boundaries are unchanged.

## Independent acceptance

- `AiProviderAttemptIdentityV80Test` proves canonical name/model labelling and that an active attempt
  wins only while it is running.
- `SourceBrowserSessionV56DeviceTest#activeAttemptKeepsItsResolvedProviderIdentityDuringDefaultSwitch`
  writes two unique services through real Room/encrypted storage, starts the real ViewModel in browser
  mode, blocks an original isolated WebView fixture after the attempt has resolved, switches the real
  database default, and proves both state and Compose continue to show the attempt service. Explicit
  cancellation clears the transient identity and immediately exposes the new default.

The device method deliberately stops before a model response. It does not claim a real AI account or
public website was exercised.

## References and licence boundary

Android state-production guidance requires UI state to represent the actual operation source of truth:
https://developer.android.com/topic/architecture/ui-layer/state-production

Legado commit `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0) and Readium commit
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause) remain fixed behavioural and lifecycle
references only. No GPL code, rules or native bridge is copied.

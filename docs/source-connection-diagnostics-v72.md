# Source connection diagnostics and recovery V72

## Gap

Timeout, TLS/certificate, ordinary DNS lookup, and application-controlled DNS blocks already have
bounded user-visible explanations. A real `ConnectException` still used V69's generic message path,
which could persist a host name, resolved address, and port such as OkHttp's `Failed to connect to`
detail in the current V3 search and AI-source validation states.

## Change

- `sourceFailureMessageV69` recognizes `ConnectException` anywhere in its bounded cause chain and
  publishes `无法连接网站服务；请检查网址和端口，确认网站可用后重试`.
- The copy does not echo a host name, IP address, port, or platform/client-specific wording.
- Timeout, TLS/certificate, `SourceDnsBlockedV54`, and ordinary DNS classifications keep their
  existing precedence and precise behavior.
- The original throwable remains available to internal control flow. Retry, cancellation,
  generation, validation, storage, browser-session, and checked-DNS behavior are unchanged.

## References and license boundary

- Java SE 17 `ConnectException`: <https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/ConnectException.html>
- Android network guidance: <https://developer.android.com/develop/connectivity/network-ops/connecting>
- OkHttp 4.12.0 source and Apache-2.0 license: <https://github.com/square/okhttp/tree/parent-4.12.0>
- Legado fixed reference `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba`, GPL-3.0.
- Readium Kotlin Toolkit fixed reference `1b1f6b308a7b6f968b2bf1e66c84912879466f75`, BSD-3-Clause.

Oracle defines `ConnectException` as a socket connection attempt failure, typically a remote refusal
because no process is listening. Android recommends encapsulating network operations and exposing
their result through a clean repository/UI boundary. This implementation is independent Kotlin
presentation logic. It does not copy GPL code, probe ports, bypass network or TLS controls, add a
fallback endpoint, change permissions, or add a native bridge.

## Regression coverage

- `SourceFailurePresentationV69Test#refusedConnectionUsesSafeActionableCopyWithoutEndpointDetails`
- `AiRuleTransportV55Test#uncachedSearchRefusedConnectionMustNotBecomeSelectorCorrection`
- `SourceSearchRecoveryV57DeviceTest#refusedConnectionIsExplainedWithoutEndpointAndRetryRecovers`

The device test uses the real ViewModel, default browser-mode source engine, isolated WebView legal
fixture, actual `ConnectException`, visible Compose error card, and the existing retry action. It
verifies that host, address, and port details are absent, only the failed source is requested again,
and the recovered row appears.

## Not claimed

This controlled failure injection does not claim a live public endpoint refusal, router/firewall
compatibility, third-party login/CAPTCHA access, physical-device coverage, API 28 renderer reclaim,
or split-screen/freeform coverage.

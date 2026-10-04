# Source DNS diagnostics and recovery V71

## Gap

V54 already rejects empty, benchmark-range Fake-IP, and non-public DNS answers before a source can
connect. Those application-controlled blocks carry specific, bounded evidence. A resolver-level
`UnknownHostException`, however, still fell through V69's ordinary error path and could put a raw
host name and platform resolver text into the current V3 error card.

## Change

- `sourceFailureMessageV69` recognizes an ordinary `UnknownHostException` anywhere in its bounded
  cause chain and publishes `无法解析网站地址；请检查网址、网络或私人 DNS 设置后重试`.
- The message does not echo the unresolved host or platform-specific resolver detail.
- `SourceDnsBlockedV54` is checked first and keeps its existing precise empty/Fake-IP/non-public
  diagnosis. A security block is not mislabeled as a transient lookup failure.
- The original throwable remains available to internal control flow. Retry, cancellation,
  generation, validation, storage, browser-session, and checked-DNS behavior are unchanged.

## References and license boundary

- Java SE 21 `UnknownHostException`: <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/UnknownHostException.html>
- Android network and DNS guidance: <https://developer.android.com/develop/connectivity/network-ops/connecting>
- Android insecure DNS guidance: <https://developer.android.com/privacy-and-security/risks/bad-dns>
- OkHttp 4.12.0 source and Apache-2.0 license: <https://github.com/square/okhttp/tree/parent-4.12.0>
- Legado fixed reference `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba`, GPL-3.0.
- Readium Kotlin Toolkit fixed reference `1b1f6b308a7b6f968b2bf1e66c84912879466f75`, BSD-3-Clause.

The implementation is independent Kotlin presentation logic. It does not copy GPL code, add a
native bridge, replace the system resolver, add a fallback resolver, bypass the V54 public-address
check, or weaken TLS hostname verification.

## Regression coverage

- `SourceFailurePresentationV69Test#ordinaryDnsFailureIsSafeButAnApplicationBlockKeepsItsSpecificReason`
- `AiRuleTransportV55Test#uncachedSearchDnsFailureMustNotBecomeSelectorCorrection`
- `SourceSearchRecoveryV57DeviceTest#dnsLookupFailureIsExplainedWithoutHostnameAndRetryRecovers`

The device test uses the real ViewModel, default browser-mode source engine, isolated WebView legal
fixture, actual `UnknownHostException`, visible Compose error card, and the existing retry action. It
verifies that only the failed source is requested again and that the recovered row appears.

## Not claimed

This controlled failure injection does not claim a live public DNS outage, private-DNS-provider
compatibility, third-party login/CAPTCHA access, physical-device coverage, API 28 renderer reclaim,
or split-screen/freeform coverage.

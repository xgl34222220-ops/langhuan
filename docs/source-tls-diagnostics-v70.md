# Source TLS diagnostics V70

## Proven gap

HTTP status failures and controlled DNS blocks already had bounded diagnostics. A real TLS handshake
or certificate validation failure still followed the generic V69 path, so the current V3 search and
AI-source validation states could render low-level certificate subjects, hostnames, trust-store text,
or provider-specific English messages. Those details are not a safe recovery instruction and must
not become a reason to bypass certificate verification.

## Change

- `sourceFailureMessageV69` now recognizes `SSLException` and `CertificateException` anywhere in its
  existing bounded cause chain and maps them to **网站安全连接验证失败；请检查网址和系统时间，确认无误后再重试**.
- Raw exception objects and causes remain intact for diagnostics; only persistent user-visible copy
  is normalized.
- No trust manager, hostname verifier, CA store, Network Security Configuration, browser session,
  redirect policy, or network permission changed. There is no “continue anyway” action.
- Search still keeps completed rows and retries only the failed source. AI validation still treats a
  TLS failure as transport evidence and does not spend a second model call on selector correction.

Android documents unknown CAs, self-signed certificates, and missing intermediate CAs as causes of
`SSLHandshakeException`, and recommends controlled Network Security Configuration rather than
runtime verification bypasses. Java defines an SSL handshake failure as a connection that cannot
negotiate the required security level and is no longer usable. The implementation is independent.
Legado commit `3a7c4daaf79c652b8e60d6aad80f665bbf6cacba` (GPL-3.0), Readium commit
`1b1f6b308a7b6f968b2bf1e66c84912879466f75` (BSD-3-Clause), and OkHttp 4.12.0
(Apache-2.0) remain behavior, lifecycle, and transport references only; no code or native bridge was
copied.

References:

- Android TLS guidance: <https://developer.android.com/privacy-and-security/security-ssl>
- Android Network Security Configuration: <https://developer.android.com/privacy-and-security/security-config>
- Java `SSLHandshakeException`: <https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/SSLHandshakeException.html>
- OkHttp 4.12.0 fixed source: <https://github.com/square/okhttp/tree/parent-4.12.0>
- OkHttp Apache-2.0 license: <https://github.com/square/okhttp/blob/parent-4.12.0/LICENSE.txt>
- Legado fixed reference: <https://github.com/gedoor/legado/tree/3a7c4daaf79c652b8e60d6aad80f665bbf6cacba>
- Readium fixed reference: <https://github.com/readium/kotlin-toolkit/tree/1b1f6b308a7b6f968b2bf1e66c84912879466f75>

## Regression coverage

- `SourceFailurePresentationV69Test#nestedTlsFailureUsesSafeActionableCopyWithoutCertificateDetails`
- `AiRuleTransportV55Test#uncachedSearchTlsFailureMustNotBecomeSelectorCorrection`
- `SourceSearchRecoveryV57DeviceTest#tlsCertificateFailureIsExplainedWithoutBypassAndRetryRecovers`

The device test runs the real ViewModel, default browser-mode source engine, isolated WebView
transport, and Compose controls against the reserved legal fixture domain. It injects an actual
`SSLHandshakeException` with a `CertificateException` cause, verifies the safe visible diagnosis and
absence of raw certificate details or a bypass action, then taps the existing retry action and
confirms recovery.

## Verification boundary

The final commit requires the unfiltered Reader suite, complete Browser group, JVM suite, Debug
build, lint, and both independent force-stop recoveries. This controlled exception path does not
claim a live public certificate outage or custom-CA deployment. Real AI providers, third-party
login/CAPTCHA, physical devices, API 28 renderer reclamation, real split/freeform windows, and the
historical emulator disconnect remain outside the authorized evidence.

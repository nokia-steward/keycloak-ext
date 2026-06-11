# Changelog

All notable changes to the `email-mtls` extension are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Release tags are extension-prefixed: `email-mtls/v<semver>`.

## [Unreleased]

## [1.0.0] - TBD

### Added

- Initial release of the `email-mtls` Keycloak email sender provider.
- Optional mutual TLS (mTLS): Keycloak presents a client certificate on the SMTP
  TLS connection for both STARTTLS (587) and implicit TLS / SMTPS (465).
- mTLS client keystore configured via runtime SPI options (`key-store`,
  `key-store-password`, `key-store-type`), mirroring Keycloak's `https-key-store-*`
  style.
- Keystore formats PKCS12, JKS, and BCFKS, with type auto-detection from the file
  extension and the FIPS-aware default (BCFKS under `fips-mode=strict`).
- Behavior parity with `DefaultEmailSenderProvider` when no keystore is
  configured; reuse of Keycloak's standard truststore for server validation and
  of the built-in SMTP authenticators (NONE / BASIC / TOKEN XOAUTH2).
- Fail-fast, secret-free error handling for invalid keystore configuration.
- Zero runtime dependencies beyond what Keycloak ships.
- Tests: keystore-loading unit tests, a parity test (incl. GreenMail delivery),
  a JSSE mTLS handshake test, and an optional Keycloak Testcontainers smoke test.

### Compatibility

- Tested with Keycloak 26.6.3 on Java 17 and 21 (compiled to Java 17 bytecode).

[Unreleased]: https://github.com/nokia/keycloak-ext/compare/email-mtls/v1.0.0...HEAD
[1.0.0]: https://github.com/nokia/keycloak-ext/releases/tag/email-mtls/v1.0.0

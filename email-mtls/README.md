# Keycloak Email Provider with mTLS (`email-mtls`)

A Keycloak `EmailSenderProvider` that behaves **exactly** like the built-in
`DefaultEmailSenderProvider`, but adds optional **mutual TLS (mTLS)** support for
the SMTP connection: Keycloak presents a **client certificate** to the SMTP
server during the TLS handshake. It works for both **STARTTLS** (typically port
587) and **implicit TLS / SMTPS** (typically port 465).

- Provider id: `email-mtls`
- Maven coordinates: `com.nokia.keycloak:keycloak-email-mtls`
- License: Apache-2.0
- Distribution: GitHub Releases only (JAR + SHA-256 checksum)

## What it does

- When **no** mTLS keystore is configured, behavior is identical to the default
  provider (same SMTP handling, same config keys, same errors). This is covered
  by an automated parity test.
- When an mTLS keystore **is** configured, Keycloak additionally presents the
  client certificate from that keystore on the SMTP TLS connection.
- mTLS is **orthogonal** to SMTP authentication: the existing auth types
  (`none`, `password`/BASIC, `token`/XOAUTH2) continue to work unchanged, with or
  without mTLS.
- Server-certificate validation continues to use **Keycloak's standard
  truststore** (`https-trust-store-*` / `--truststore-paths`). This extension does
  not add any custom truststore handling.

## Important: internal SPI caveat

The Keycloak email-sender SPI (`emailSender`) is marked **internal**
(`isInternal() == true`). When a third party implements it, Keycloak logs a
warning at startup:

```
KC-SERVICES0047: <id> (<class>) is implementing the internal SPI emailSender. This SPI is internal and may change without notice
```

This is expected and accepted. The risk is mitigated by pinning to a specific
Keycloak version and re-running the full test suite (including the parity test)
before publishing a release for a new Keycloak version. See the compatibility
matrix below.

## Compatibility matrix

| Extension version | Tested Keycloak | Java (bytecode / CI) |
|---|---|---|
| 1.0.0 | 26.6.3 | 17 (compiled) / tested on 17 and 21 |

The JAR is compiled to Java 17 bytecode for the broadest compatibility. Keycloak
26.6 supports JDK 17, 21, and 25; the official container image uses JDK 21.

## Installation

1. Download `keycloak-email-mtls-<version>.jar` from the GitHub Release
   (verify it against the published `.sha256` checksum).
2. Copy it into Keycloak's `providers/` directory:
   ```bash
   cp keycloak-email-mtls-<version>.jar $KEYCLOAK_HOME/providers/
   ```
3. Select the provider and build:
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh build --spi-email-sender--provider=email-mtls
   ```
   Selecting the provider is a **build-time** option, so it requires `kc.sh
   build` (or `start --optimized` after a build, or `start --auto-build`).
4. Start Keycloak with the mTLS keystore options (these are **runtime** options;
   see below) and your usual realm SMTP settings.

## Configuration reference

Provider selection (build-time):

| Purpose | CLI option | Environment variable | Default |
|---|---|---|---|
| Select provider | `--spi-email-sender--provider=email-mtls` | `KC_SPI_EMAIL_SENDER__PROVIDER=email-mtls` | `default` |

mTLS client keystore (runtime; read once at startup):

| Purpose | CLI option | Environment variable | Default |
|---|---|---|---|
| Keystore path | `--spi-email-sender--email-mtls--key-store=<path>` | `KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE` | none (-> default behavior) |
| Keystore password | `--spi-email-sender--email-mtls--key-store-password=<pw>` | `KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_PASSWORD` | none |
| Keystore type | `--spi-email-sender--email-mtls--key-store-type=<PKCS12\|JKS\|BCFKS>` | `KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_TYPE` | auto-detect; PKCS12 (BCFKS under `fips-mode=strict`) |

Notes:

- The mTLS keystore is a **separate file**, independent of Keycloak's TLS
  (`https-key-store-*`) keystore. Only **one keystore per Keycloak instance** is
  supported (no per-realm keystores).
- **Supported formats:** PKCS12, JKS, BCFKS. PEM certificate/key pairs are **not**
  supported.
- **Type auto-detection** (when `key-store-type` is omitted) mirrors Keycloak and
  is based on the file extension: `.p12` / `.pfx` / `.pkcs12` -> PKCS12, `.jks` ->
  JKS, `.bcfks` -> BCFKS. If the extension is not recognized, the JVM default
  keystore type is used (PKCS12 normally, or BCFKS when Keycloak runs in
  `fips-mode=strict`). For any other extension (for example `.keystore`), set
  `key-store-type` explicitly.
- The keystore password may optionally be stored encrypted via Keycloak's
  **KeyStore Config Source** (`--config-keystore`), the same mechanism used for
  other server secrets. This is not required.
- If a keystore path is configured but the path, password, or type is invalid,
  startup **fails fast** with a clear message. Secrets are never logged or
  included in error messages.

## Worked examples

### STARTTLS (port 587)

```bash
# Build once: select the provider.
bin/kc.sh build --spi-email-sender--provider=email-mtls

# Run with the mTLS client keystore (runtime options).
export KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE=/etc/keycloak/smtp-client.p12
export KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_PASSWORD='********'
# type omitted -> auto-detected as PKCS12 from the .p12 extension

bin/kc.sh start --optimized
```

In the realm's email settings, set **Enable StartTLS** = on and **Port** = 587.

### Implicit TLS / SMTPS (port 465)

```bash
bin/kc.sh build --spi-email-sender--provider=email-mtls

export KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE=/etc/keycloak/smtp-client.bcfks
export KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_PASSWORD='********'
export KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_TYPE=BCFKS

bin/kc.sh start --optimized
```

In the realm's email settings, set **Enable SSL** = on and **Port** = 465.

In both cases the SMTP server must be configured to request/require a client
certificate, and Keycloak's truststore must trust the SMTP server certificate
(use the standard `--truststore-paths` / `https-trust-store-*` configuration).

## How it works (implementation approach)

This extension follows the **minimal-override** principle so that future changes
to Keycloak's default email provider are easy to absorb.

- `MtlsEmailSenderProviderFactory` (package `com.nokia.keycloak.email.mtls`)
  **extends** `DefaultEmailSenderProviderFactory`. It reuses the built-in SMTP
  authenticators (NONE / BASIC / TOKEN) via the public `getEmailAuthenticators()`
  accessor, reads the mTLS keystore options, and returns the `email-mtls`
  provider id.
- `MtlsEmailSenderProvider` **extends** `DefaultEmailSenderProvider` and
  overrides only the package-private `buildEmailProperties(...)` method. It calls
  `super.buildEmailProperties(...)` and, when mTLS is configured and TLS is in
  use, replaces `mail.smtp.ssl.socketFactory` with a combined factory built from
  `SSLContext.init(clientKeyManagers, truststoreTrustManagers, null)`. The
  truststore part is taken from Keycloak's own `JSSETruststoreConfigurator`, so
  server validation is unchanged.

### Why the provider class is in package `org.keycloak.email`

The only seam in `DefaultEmailSenderProvider` for injecting an `SSLSocketFactory`
into the Jakarta Mail `Session` is the **package-private** method
`buildEmailProperties(Map, String)`. A package-private method can only be
overridden from the **same package**, so `MtlsEmailSenderProvider` deliberately
lives in `org.keycloak.email` (a "split package" contributed by this JAR). All
mTLS-specific code lives in `com.nokia.keycloak.email.mtls`.

This relies on Keycloak's Quarkus runtime loading `providers/*.jar` in the same
classloader as `keycloak-services` (true for Keycloak 26.x), so the override
dispatches correctly. The optional integration test (`-Pit`) validates this at
runtime by starting a real Keycloak container with the provider selected. If a
future Keycloak release ever broke this assumption, the documented fallback is to
re-implement the minimal `send()` path instead of subclassing.

## Certificate rotation

The keystore is loaded **once at startup**. Rotating the client certificate
requires a **Keycloak restart**. Hot reload is not supported.

## FIPS

The extension is FIPS-aware. Under `fips-mode=strict`, Keycloak sets the JVM
default keystore type to BCFKS; this extension reuses that default, so an
unspecified `key-store-type` resolves to BCFKS. BCFKS support uses the
BouncyCastle/BCFIPS provider that Keycloak already ships (no new runtime
dependency).

## Troubleshooting

- **No client certificate is sent.** Ensure (a) the provider is selected and a
  `kc.sh build` was run, (b) the realm email settings enable SSL or StartTLS
  (mTLS only applies once TLS is negotiated), and (c) the `key-store` path is set
  at runtime.
- **Startup fails with "mTLS keystore not found ...".** Check the `key-store`
  path is correct and readable by the Keycloak process.
- **Startup fails with "Failed to open the mTLS keystore ...".** Usually a wrong
  `key-store-password` or `key-store-type`. The keystore content must match the
  declared/auto-detected type.
- **Server-certificate validation fails.** Add the SMTP server's CA to Keycloak's
  truststore (`--truststore-paths`). This extension does not bypass server
  validation.
- Passwords are never logged. Increase log verbosity for
  `com.nokia.keycloak.email.mtls` and `org.keycloak.email` to see non-secret
  diagnostics.

## Building from source

Requires JDK 17+ (the build targets Java 17 bytecode). Uses the Maven Wrapper.

```bash
cd email-mtls
./mvnw verify                 # unit + parity + mTLS handshake tests
./mvnw verify -Pit            # also run the Keycloak Testcontainers smoke test (needs Docker)
```

The build produces `target/keycloak-email-mtls-<version>.jar` (a single plain
JAR with zero runtime dependencies beyond what Keycloak ships).

## Testing notes

- **Unit tests** cover keystore loading for PKCS12/JKS/BCFKS, type
  auto-detection, the FIPS default, and fail-fast error handling.
- **Parity test** proves `buildEmailProperties` is identical to the default
  provider and that mail is delivered through an in-memory SMTP server
  (GreenMail, the same tool Keycloak uses for email tests).
- **mTLS handshake test** proves the client certificate is presented to a server
  that requires client authentication (`setNeedClientAuth(true)`). Because the
  same socket factory is used by Jakarta Mail for both SMTPS and the STARTTLS
  upgrade, this single test validates both transport modes. GreenMail does not
  natively enforce client-certificate authentication, which is why this proof is
  done at the JSSE layer.

## License

Apache-2.0. See the repository-root [LICENSE](../LICENSE) and [NOTICE](../NOTICE).

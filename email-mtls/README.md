# Keycloak Email Provider with mTLS (`email-mtls`)

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Keycloak `EmailSenderProvider` that behaves exactly like the built-in default provider, but adds optional **mutual TLS (mTLS)** support. Keycloak will present a client certificate to the SMTP server during the TLS handshake. It supports both STARTTLS and implicit TLS.

## Features

- **mTLS Support**: Adds client certificate presentation to SMTP connections.
- **Dedicated Keystore**: Uses a dedicated SMTP client keystore, completely separate from the Keycloak frontend TLS keystore.
- **100% Parity**: Acts exactly like the default Keycloak email provider when no keystore is configured.
- **Format Support**: Supports `PKCS12`, `JKS`, and `BCFKS` keystores.
- **FIPS Aware**: Integrates with Keycloak's `fips-mode=strict`.

## Limitations

- **Single Keystore**: Only **one** SMTP client keystore per Keycloak instance is supported (e.g., no per-realm keystores).
- **Certificate Rotation**: Restart Keycloak to rotate the client certificate. Hot reload is not supported.

## Prerequisites

- **Java**: 17+
- **Keycloak**: 26.6.3 or higher

The extension version follows the `<keycloak.version>-<release.number>` scheme.
| Extension Version | Tested Keycloak | Java Version |
|---|---|---|
| `26.6.3-1` | `26.6.3` | `17+` |

## Installation

1. Download the latest `keycloak-email-mtls-<version>.jar` from the [GitHub Releases](https://github.com/nokia/keycloak-ext/releases).
1. Copy the `.jar` file to your Keycloak `providers/` directory:
   ```bash
   cp keycloak-email-mtls-<version>.jar $KEYCLOAK_HOME/providers/
   ```
1. Rebuild Keycloak (optional, but recommended for production):
   ```bash
   $KEYCLOAK_HOME/bin/kc.sh build
   ```
   You can skip this step only if your Keycloak start command performs an auto-build.

## Operating Modes

`email-mtls` has two modes:

- **No mTLS mode (no TLS or one-way TLS):** If no SMTP client keystore is configured, the provider behaves like Keycloak's built-in email provider. Use the normal Keycloak realm email settings only.
- **mTLS mode:** If an SMTP client keystore is configured, Keycloak presents a client certificate to the SMTP server during the TLS handshake. Use the mTLS configuration below.

The instructions below apply to mTLS mode.

## mTLS Configuration

Configure the mTLS client keystore using these environment variables or CLI options at runtime.

| Environment Variable | CLI Option | Purpose |
|---|---|---|
| `KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE` | `--spi-email-sender--email-mtls--key-store` | Keystore path |
| `KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_PASSWORD` | `--spi-email-sender--email-mtls--key-store-password` | Keystore password |
| `KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_TYPE` | `--spi-email-sender--email-mtls--key-store-type` | Keystore type (Optional, auto-detected by file extension) |

> **Notes:**
>
> - Supported types are `PKCS12`, `JKS`, and `BCFKS`. PEM files are not supported.
> - Type is auto-detected from the file extension (`.p12`, `.jks`, `.bcfks`).

## mTLS Quick Start

1. **Configure the SMTP server for mTLS:** It must have its own server certificate and private key. Enable client certificate authentication: add the client (Keycloak) certificate or its CA to the SMTP server truststore.
1. **Create a Keycloak SMTP Keystore:** Prepare a keystore file with the client certificate and its private key. Keycloak will present this certificate to the SMTP server. In this example, use `smtp-client.p12` with password `secret_password`.
1. **Configure Keycloak truststore:** Add the SMTP server certificate or its CA to Keycloak's truststore. Use the standard Keycloak `--truststore-paths` CLI option or `KC_TRUSTSTORE_PATHS` environment variable.
1. **Configure Keycloak SMTP keystore:** This example shows how to use environment variables, but CLI options accept similar values:
   ```bash
   export KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE=/etc/keycloak/smtp-client.p12
   export KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_PASSWORD='secret_password'
   # Optional: Auto-detected from file extension (.p12, .jks, .bcfks).
   # Only required if your keystore uses a non-standard extension.
   # export KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_TYPE=PKCS12
   ```
1. **Start Keycloak:**
   ```bash
   bin/kc.sh start --spi-email-sender--provider=email-mtls
   ```
1. **Configure Email provider in Keycloak Admin Console:** Open Keycloak **Realm Settings** -> **Email** and configure based on your SMTP server requirements:
   - **For STARTTLS (Port 587):** Set **Enable StartTLS** to `ON` and **Port** to `587`.
   - **For Implicit TLS (Port 465):** Set **Enable SSL** to `ON` and **Port** to `465`.
   - Click **Test connection** to verify mTLS is working.

## mTLS Troubleshooting

- **Email sending fails:** If you encounter errors when sending an email, check both the Keycloak server logs and your SMTP server logs to identify the root cause.
- **mTLS is not enabled:** If `KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE` is not set, this provider behaves like the default Keycloak email provider.
- **Check mTLS keystore loaded:** After startup, verify in the Keycloak logs that the keystore was loaded successfully. Look for: `mTLS client keystore loaded for SMTP connection`.
- **TLS or mTLS validation fails on Keycloak side:** Check **Enable SSL** or **Enable StartTLS** in the realm **Email** settings. Check that the SMTP client keystore contains the client certificate and private key. Check that Keycloak's truststore contains the SMTP server certificate or its CA.
- **TLS or mTLS validation fails on SMTP server side:** Check that the SMTP server keystore is configured. Check that the SMTP server truststore contains the client (Keycloak) certificate or its CA.

## Building from Source

1. Clone the repository and open the `email-mtls` directory.
   ```bash
   git clone https://github.com/nokia/keycloak-ext.git
   cd keycloak-ext/email-mtls
   ```
1. Run the tests and build the JAR.
   ```bash
   ./mvnw verify
   ```
1. Run integration tests (Requires Docker).
   ```bash
   ./mvnw -Pit verify
   ```

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change. Please see [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines.

## License

Distributed under the Apache 2.0 License. See [LICENSE](../LICENSE) for more information.

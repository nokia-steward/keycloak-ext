# Security Policy

## Reporting a vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

If you believe you have found a security vulnerability in any extension in this
repository, report it through one of the following private channels:

1. **GitHub private security advisories** (preferred): open a draft advisory via
   the repository's **Security -> Advisories -> Report a vulnerability** page.
   This keeps the report private until a fix is available.
2. **Nokia PSIRT** (Product Security Incident Response Team): for coordinated
   disclosure through Nokia, follow the process published at
   <https://www.nokia.com/about-us/security-and-privacy/product-security/>.

Please include:

- the affected extension and version (e.g. `email-mtls` 1.0.0),
- the Keycloak version and environment,
- a description of the issue and its impact,
- steps to reproduce or a proof of concept, and
- any suggested remediation.

We will acknowledge your report, investigate, and keep you informed of progress.
Please give us a reasonable time to remediate before any public disclosure.

## Supported versions

Security fixes are provided for the latest released version of each extension.
Because each extension is versioned and released independently, see the relevant
extension's `CHANGELOG.md` and the README compatibility matrix for the currently
supported version and the Keycloak version it targets.

| Extension | Supported versions |
|---|---|
| `email-mtls` | Latest released `1.x` |

## Handling of secrets

These extensions are designed to **never log passwords** and to avoid emitting
secret values in error messages. If you discover a case where a secret could be
exposed (in logs, exceptions, or otherwise), please treat it as a security issue
and report it privately as described above.

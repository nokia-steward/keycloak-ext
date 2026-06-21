# nokia/keycloak-ext

A monorepo open-source extensions for [Keycloak](https://www.keycloak.org) maintained by Nokia. Each extension lives in its own subfolder, has its own version and `CHANGELOG.md`, and is released independently.

## Extensions

| Extension | Description |
|---|---|
| `email-mtls` | Email sender provider with mutual TLS (mTLS) client-certificate support for SMTP. |

> More independent extensions may be added later in sibling folders.

## Repository model

- **Monorepo.** All extensions share one Issues list and one Releases page.
  Extension releases are distinguished by the release name and Git tag, both of
  which include the extension name. For example, release `email-mtls 26.6.3-1`
  is created from tag `email-mtls/v26.6.3-1`, which points to a release commit
  on `main`.
- **Path-filtered CI.** Each extension has its own workflow filtered to its
  subfolder, so a change to one extension does not trigger unrelated builds.
- **Releases via GitHub Releases only.** Artifacts are not published to a Maven
  repository. Each release attaches the built JAR plus a SHA-256 checksum.

## Community & governance

- [LICENSE](LICENSE) - Apache-2.0 (shared)
- [CONTRIBUTING.md](CONTRIBUTING.md) - workflow and DCO sign-off requirement
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) - expected behavior and enforcement

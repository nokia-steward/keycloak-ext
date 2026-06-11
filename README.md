# nokia/keycloak-ext

A monorepo of independent, open-source [Keycloak](https://www.keycloak.org)
extensions maintained by Nokia. Each extension lives in its own subfolder, has
its own version and `CHANGELOG.md`, and is released independently using
extension-prefixed tags.

## Extensions

| Extension | Description | Folder | Tags |
|---|---|---|---|
| `email-mtls` | Email sender provider with mutual TLS (mTLS) client-certificate support for SMTP. | [`email-mtls/`](email-mtls/) | `email-mtls/v<semver>` |

> More independent extensions may be added later in sibling subfolders.

## Repository model

- **Monorepo.** All extensions share one Issues list and one Releases page.
  Extensions are distinguished by the **tag prefix** (e.g. `email-mtls/v1.0.0`)
  and by labels (e.g. `ext:email-mtls`).
- **Path-filtered CI.** Each extension has its own workflow filtered to its
  subfolder, so a change to one extension does not trigger unrelated builds.
- **Releases via GitHub Releases only.** Artifacts are not published to a Maven
  repository. Each release attaches the built JAR plus a SHA-256 checksum.

## Getting started

See the extension's own README for installation and configuration, e.g.
[`email-mtls/README.md`](email-mtls/README.md).

To build and test an extension locally (Java 17+ required):

```bash
cd email-mtls
./mvnw verify
```

## Community & governance

- [LICENSE](LICENSE) - Apache-2.0 (shared)
- [NOTICE](NOTICE)
- [CONTRIBUTING.md](CONTRIBUTING.md) - workflow and DCO sign-off requirement
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- [SECURITY.md](SECURITY.md) - how to report vulnerabilities
- [docs/GITHUB_GUIDE.md](docs/GITHUB_GUIDE.md) - role-based GitHub usage guide

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

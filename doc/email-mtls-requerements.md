# Keycloak Extension — Email Provider with mTLS Support

> Requirements & project specification.
> Status: draft. Acronyms are defined in the Glossary (§19). A non-normative index of key decisions is in §15.

## 1. Overview

Create a public Keycloak extension that implements an email sender provider equivalent to Keycloak's built-in `DefaultEmailSenderProvider`, but adds **mutual TLS (mTLS)** support for the SMTP connection: Keycloak presents a client certificate to the SMTP server.

The extension will be published as a public Nokia open-source project and offered for listing on https://www.keycloak.org/extensions.

- Provider id: `email-mtls`
- Target Keycloak: 26.6.3
- License: Apache-2.0
- Distribution: GitHub Releases only

## 2. In scope

- A Keycloak `EmailSenderProvider` implementation (`email-mtls`) with optional mTLS, plus its `EmailSenderProviderFactory`.
- One deployable JAR for the Keycloak `providers/` folder.
- The extension lives in the `email-mtls/` subfolder of the existing monorepo `nokia/keycloak-ext` (other, independent extensions may be added later in sibling subfolders).
- Source code, build script (Maven), unit tests, integration test(s) where feasible, documentation, CI, releases, and a defined development/maintenance process.
- Support for the latest Keycloak version, 26.6.3.

## 3. Out of scope

- Per-realm email keystores. Only one keystore per Keycloak instance is supported.
- Truststore handling. Keycloak already supports truststore configuration and it must be reused.
- Admin Console UI changes.
- Hot reload of the keystore. Keycloak restart is required for certificate rotation.
- Support for Keycloak versions older than 26.6.3.
- PEM certificate/key pairs as a certificate source (keystore files only — see §5.3).
- SBOM and artifact signing for releases (may be added later — see §6).

## 4. Target environment & compatibility

- **Keycloak:** 26.6.3 (pinned). See §8 for the maintenance/compatibility policy.
- **Java:** compile to **Java 17** bytecode (the lowest Keycloak-supported LTS, for broadest compatibility). Keycloak 26.6 supports JDK 17, 21, and 25; the official container image uses **JDK 21**. CI builds/tests on **17 and 21**.
- **Internal SPI risk (important):** the email-sender SPI (`emailSender`) is marked *internal* in Keycloak (`isInternal() == true`). Keycloak logs a warning when a third party implements it (`KC-SERVICES0047 ... This SPI is internal and may change without notice`). This is accepted; it is mitigated by pinning to 26.6.3 and the upgrade-retest process in §8.

## 5. Functional requirements

### 5.1 Provider identity & selection

- Provider id is `email-mtls`, returned by the factory's `getId()`.
- Selected via standard Keycloak SPI configuration. This is a **build-time** option (requires `kc.sh build`, or `start --optimized` after a build, or `start --auto-build`). The exact option and env var are listed once in §11.

### 5.2 Behavior parity

- When **no** mTLS keystore is configured, behavior must match Keycloak's `DefaultEmailSenderProvider` exactly (same SMTP handling, same config keys, same error behavior).
- All existing realm-level email settings remain unchanged and fully supported: host, port, auth (none/password/token-XOAUTH2), SSL, STARTTLS, from/sender, reply-to, and related SMTP settings.
- mTLS is **orthogonal** to SMTP authentication: the client certificate is presented at the TLS layer; the existing auth types (NONE, BASIC/password, TOKEN/XOAUTH2) continue to work unchanged, with or without mTLS.

### 5.3 mTLS keystore configuration

- The mTLS client keystore is a **separate file**, independent of Keycloak's TLS (`https-key-store-*`) keystore.
- Configured in the **same style** as Keycloak's TLS keystore (env vars + CLI parameters, mirroring `https-key-store-*` naming). These are **runtime** options (read at provider startup), so the password is never persisted into Keycloak's plaintext build state. The exact options and env vars are listed once in §11.
- **Supported keystore formats:** PKCS12, JKS, BCFKS (full parity with Keycloak's TLS keystore). PEM certificate/key pairs are **not** supported.
- **Type detection / default:** if `key-store-type` is not set, the type is **auto-detected from the file extension** (e.g. `.p12/.pkcs12/.pfx` → PKCS12, `.jks/.keystore` → JKS), mirroring Keycloak. Default is **PKCS12**; when Keycloak runs in **`fips-mode=strict`**, the default is **BCFKS**, mirroring Keycloak.
- The keystore password, being a standard Keycloak config option, may optionally be stored encrypted via Keycloak's **KeyStore Config Source** (`--config-keystore`), the same mechanism used for other server secrets. (Not required.)

### 5.4 TLS transport

- The client certificate must be presented for **both** SMTP TLS modes: **STARTTLS** (typically port 587) and **implicit TLS / SMTPS** (typically port 465).
- The standard Keycloak **truststore** mechanism must continue to be used for server-certificate validation (no custom truststore handling).

### 5.5 Lifecycle, logging & error handling

- Keystore is loaded once at provider startup. Certificate rotation requires a Keycloak restart.
- Passwords must **never** be logged. Usernames and other sensitive values must not be logged at INFO level.
- If no keystore path is configured → behave exactly like `DefaultEmailSenderProvider`.
- If a keystore path is configured but the path, password, or type is invalid → fail fast with a **clear, actionable error message** (no secret values in the message).

## 6. Non-functional requirements

- **Zero runtime dependencies** beyond what Keycloak already ships. Keycloak dependencies are declared with Maven `provided` scope; test-only dependencies use `test` scope. (BouncyCastle/BCFIPS, used for BCFKS, is already shipped by Keycloak — no new runtime dependency.)
- **License:** Apache-2.0.
- **Distribution:** GitHub Releases only (not published to a Maven repository).
- **Release artifacts:** the JAR plus a **SHA-256 checksum** file. (SBOM and GPG/cosign signing are out of scope for now; see §3.)
- **Logging:** clear, actionable error messages for invalid keystore configuration; never log secrets.
- **Security posture:** FIPS-aware (BCFKS under `fips-mode=strict`); fail-closed on misconfiguration.

## 7. Maintenance strategy (minimal-override principle)

- The provider should **override only the parts of the default provider that are required for mTLS**, so that future changes to Keycloak's default email provider are easy to absorb. Prefer extending/delegating over copying logic.
- Caveat: the `emailSender` SPI is internal and some methods of `DefaultEmailSenderProvider` are not designed for subclassing. The implementation strategy (subclass vs. re-implement the SMTP `Session`/`Transport` setup) follows the default directive in §17.5; document the chosen approach in `email-mtls/README.md`.
- Pin to Keycloak 26.6.3. When bumping Keycloak, re-run the full test suite (incl. the parity test in §9) before releasing a new compatible version, and update the compatibility matrix.

## 8. Versioning & release policy

- **SemVer** (`MAJOR.MINOR.PATCH`), first public release `1.0.0`.
- **Monorepo tags are prefixed per extension:** `email-mtls/v1.0.0`. (GitHub repos cannot be nested, so all extensions share one Releases page and are distinguished by tag prefix and release title.)
- Each extension keeps its **own** `CHANGELOG.md` and version, independent of sibling extensions.
- `email-mtls/README.md` must include a **Keycloak compatibility matrix** (extension version ↔ tested Keycloak version ↔ Java).
- Release notes are derived from `CHANGELOG.md`.

## 9. Testing

- **Tooling constraint:** use only test tools/packages that Keycloak itself uses. Keycloak uses **JUnit** and, for email, the **GreenMail** in-memory SMTP server; review the latest Keycloak email tests and the Keycloak test framework before choosing the integration approach.
- **Unit tests (required):** keystore loading (PKCS12/JKS/BCFKS), type auto-detection, FIPS default, invalid-config error handling, and the "no keystore → behaves like default" path.
- **Parity test (required):** prove that with no mTLS keystore configured, the provider behaves identically to `DefaultEmailSenderProvider`.
- **Integration test (best-effort, if feasible):** start Keycloak with the JAR (e.g. Keycloak test framework / Testcontainers as used by Keycloak) and send mail through an SMTP server. Ideally prove mTLS end-to-end against an SMTP server **configured to require a client certificate**. Note: GreenMail supports TLS but client-certificate (mTLS) testing needs extra setup — call out the chosen approach in the test docs.

## 10. Project organization on GitHub

### 10.1 Repository model (monorepo)

- The repo `https://github.com/nokia/keycloak-ext` already exists (currently it contains only a root `LICENSE`, on the `main` branch).
- This extension lives in the **`email-mtls/` subfolder**. Future, independent extensions go in sibling subfolders.
- **Tags are extension-prefixed:** `email-mtls/v<semver>`.
- **CI is path-filtered** (`paths: email-mtls/**`) so changes to one extension don't trigger unrelated builds.
- Note on "independent projects": a monorepo means a shared Issues list and a shared Releases page. Use **labels** (e.g. `ext:email-mtls`) and the tag prefix to keep extensions separated.

### 10.2 Repository layout

```
nokia/keycloak-ext
├── LICENSE                      # exists (Apache-2.0) — shared
├── NOTICE                       # create — "Copyright <year> Nokia" + third-party notices
├── README.md                    # create — monorepo index linking to each extension
├── CONTRIBUTING.md              # create — workflow + DCO sign-off requirement
├── CODE_OF_CONDUCT.md           # create
├── SECURITY.md                  # create — Nokia PSIRT + GitHub private advisories
├── .github/
│   ├── CODEOWNERS               # /email-mtls/ -> @nokia/keycloak-ext-maintainers
│   ├── dependabot.yml           # Maven + GitHub Actions updates
│   ├── pull_request_template.md
│   ├── ISSUE_TEMPLATE/          # bug_report, feature_request
│   └── workflows/
│       ├── email-mtls-ci.yml        # build + test on PRs/pushes (path-filtered)
│       ├── email-mtls-release.yml   # build + publish GitHub Release on tag
│       ├── codeql.yml               # code scanning
│       └── dco.yml                  # DCO sign-off check (or use the DCO GitHub App)
└── email-mtls/
    ├── README.md                # install, configuration reference, compatibility matrix
    ├── CHANGELOG.md
    ├── pom.xml
    └── src/
        ├── main/java/...
        ├── main/resources/META-INF/services/org.keycloak.email.EmailSenderProviderFactory
        └── test/java/...
```

Note: GitHub recognizes community-health files (LICENSE, CODE_OF_CONDUCT, SECURITY, CONTRIBUTING) at the **repo root**, so these stay shared at root in a monorepo.

### 10.3 User roles (corrected GitHub model)

Because the repo is owned by the **`nokia` organization**, the repo itself is owned by the org — there is no single "repo owner" user. Map the three intended tiers to GitHub permission levels, ideally granted via **teams** rather than to individuals:

| Intended tier | GitHub role | Can do | Cannot do |
|---|---|---|---|
| **owner** (mvk375) | Repository **Admin** (e.g. team `keycloak-ext-admins`) — *not* the same as org-level Owner over all Nokia repos | Manage settings, rulesets, releases, merge; **bypass** branch rules (see §10.4) | n/a (full repo control) |
| **maintainers** (mvk375 + designees) | **Maintain** role (team `keycloak-ext-maintainers`) | Merge PRs, manage issues/labels, publish releases | Delete repo, change visibility, change protected settings |
| **other contributors** | No direct write. Public repo = **Read** for everyone; they **fork + open PRs**. (Internal Nokia folks may optionally get Triage/Write.) | Propose changes via PR, open issues | Push to repo branches, merge |

Note: "owner" here means repository **Admin** (not the organization-level Owner over all Nokia repos), and access is granted through **teams**, not to individuals.

### 10.4 Branch protection (ruleset on `main`)

- Require a **pull request before merging**; require **≥1 approval**; require **review from Code Owners**; **dismiss stale approvals** on new commits; **require conversation resolution**.
- Require **status checks to pass**: the CI build/test job, and the **DCO check** (per §10.6). Require branches to be up to date before merge.
- **Block force-pushes** and branch **deletion**.
- **Restrict who can push to `main`** to PR merges only — **except** mvk375, who is on the ruleset **bypass list** and may push directly to `main`.
  - Trade-off to be aware of: direct admin pushes skip review and CI gating. Recommend using it only for emergencies and documenting when it's used.
- Optional/nice-to-have: require **linear history** and **signed commits**.

### 10.5 Release control

- Only **Maintain**+ roles can publish releases.
- Restrict who can create the release tags with a **tag protection rule** on `email-mtls/v*`.
- Optionally run the release workflow through a protected **GitHub Environment** (`release`) that requires a maintainer to approve the run; the workflow's `GITHUB_TOKEN` needs `contents: write` to create the Release.

### 10.6 Community & governance files

- `CONTRIBUTING.md`: branching model, how to run build/tests, PR expectations, and the **DCO** requirement — every commit needs a `Signed-off-by:` line (`git commit -s`). Enforced by a DCO check on PRs.
- `SECURITY.md`: how to report vulnerabilities (route to **Nokia PSIRT**, and enable **GitHub private security advisories**); supported versions.
- `CODE_OF_CONDUCT.md`, `NOTICE`, issue/PR templates as in the layout above.

### 10.7 CI / automation

- **CI workflow** (`email-mtls-ci.yml`): triggers on **`pull_request`** and **`push` to `main`**, path-filtered to `email-mtls/**`. Builds with the Maven wrapper and runs unit (+ integration if feasible) tests on a **JDK 17 and 21 matrix**. This is the required status check that gates merges. (Note: building on PRs — not "on merge" — is what allows it to protect `main`.)
- **Release workflow** (`email-mtls-release.yml`): triggers on pushing a tag `email-mtls/v*` (and `workflow_dispatch`). Builds the JAR (Java 17), runs tests, generates a **SHA-256 checksum**, creates a GitHub Release (notes from `CHANGELOG.md`), and attaches the JAR + checksum.
- **Dependabot** (Maven + Actions) and **CodeQL** scanning enabled (free on public repos).

## 11. Configuration reference

| Purpose | CLI option | Environment variable | When | Default |
|---|---|---|---|---|
| Select provider | `--spi-email-sender--provider=email-mtls` | `KC_SPI_EMAIL_SENDER__PROVIDER=email-mtls` | build-time | `default` |
| Keystore path | `--spi-email-sender--email-mtls--key-store=<path>` | `KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE` | runtime | none (→ default behavior) |
| Keystore password | `--spi-email-sender--email-mtls--key-store-password=<pw>` | `KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_PASSWORD` | runtime | none |
| Keystore type | `--spi-email-sender--email-mtls--key-store-type=<PKCS12\|JKS\|BCFKS>` | `KC_SPI_EMAIL_SENDER__EMAIL_MTLS__KEY_STORE_TYPE` | runtime | auto-detect; PKCS12 (BCFKS if `fips-mode=strict`) |

## 12. Listing on keycloak.org/extensions

- Listing is done by opening a **pull request to `keycloak/keycloak-web`** adding a JSON entry based on `extension-template.json` (or by opening an issue in the Keycloak issue tracker requesting it). Track this as a launch task once `1.0.0` is released.

## 13. Documentation deliverables

- **`email-mtls/README.md`** must cover: what it is; the internal-SPI caveat; **compatibility matrix**; install (drop JAR into `providers/`, run `kc.sh build`); full configuration reference (§11); a worked example for STARTTLS and SMTPS; certificate-rotation note (restart required); troubleshooting.
- **GitHub usage guide** (§14): role-based, step-by-step instructions with git commands and GitHub GUI steps.

## 14. GitHub usage guide (to be produced)

The guide must be written for the three roles in §10.3 and include both git commands and GitHub GUI steps for at least the following.

### 14.1 Initial project setup (one-time, Admin: mvk375)
- GUI: create the `keycloak-ext-admins` and `keycloak-ext-maintainers` **teams** and assign repo roles (Admin / Maintain).
- GUI: add the `main` **ruleset** per §10.4 (PR required, approvals, CODEOWNERS, required CI + DCO checks, block force-push, bypass list = mvk375).
- GUI: enable **Dependabot**, **CodeQL**, and **private security advisories**; add the **tag protection** rule `email-mtls/v*`.
- Git: scaffold the `email-mtls/` subfolder, root community files, and `.github/` workflows on a branch, then open a PR.

### 14.2 Laptop preparation (developer)
- Install **JDK 17** (and optionally 21), Git, and an IDE.
- `git clone https://github.com/nokia/keycloak-ext.git`
- Configure your identity and DCO sign-off:
  - `git config user.name "..."`, `git config user.email "..."`
  - use `git commit -s` (the DCO `Signed-off-by` line is required).
- Build/test locally: `cd email-mtls && ./mvnw verify`.

### 14.3 Typical development cycle (contributor / maintainer)
1. `git switch -c feature/<short-description>` (branch naming: `feature/*`, `fix/*`, `docs/*`).
2. Make changes; `cd email-mtls && ./mvnw verify`.
3. `git add -A && git commit -s -m "..."`.
4. `git push -u origin feature/<short-description>`.
5. GUI: open a **Pull Request** into `main`. CI + DCO run automatically; request review.
6. A **maintainer** reviews; once approved and checks are green, **squash-merge** the PR.
7. Delete the merged branch. (Outside contributors do steps 1–5 from a **fork**.)

### 14.4 Cutting a release (maintainer)
1. Update `email-mtls/CHANGELOG.md` and the version in `email-mtls/pom.xml`; merge via PR.
2. Tag and push: `git tag email-mtls/v1.0.0 && git push origin email-mtls/v1.0.0`.
   - (Or GUI: **Releases → Draft a new release**, create the tag `email-mtls/v1.0.0`.)
3. The release workflow builds the JAR, generates the SHA-256 checksum, and publishes the GitHub Release with both attached.
4. Verify the Release assets; announce/update the keycloak.org/extensions listing if needed (§12).

## 15. Decisions index (non-normative)

Authoritative wording for each item is in the referenced section; this is only a quick index.

- Repo model — monorepo, prefixed tags, path-filtered CI → §10.1, §8
- Admin (mvk375) may bypass the `main` pull-request rule → §10.4
- mTLS on both STARTTLS and SMTPS → §5.4
- mTLS orthogonal to existing SMTP auth → §5.2
- Java 17 target; CI on 17 + 21 → §4
- Keystore: separate file, Keycloak `https-key-store-*` config style → §5.3, §11
- Keystore formats PKCS12 / JKS / BCFKS; auto-detect; FIPS strict → BCFKS → §5.3
- PEM not supported → §3, §5.3
- DCO sign-off enforced → §10.6
- GitHub Releases only; JAR + SHA-256 checksum → §6, §8
- License Apache-2.0; Nokia OSI publish approval obtained → §6, §16

## 16. Open questions / to confirm

- Confirm the precise KeyStore Config Source alias for the SPI password option during implementation.
- Confirm Nokia naming/trademark and `NOTICE` content with the Nokia OSI team before first public release.
- Branch strategy details (e.g. whether to gate release tags behind a protected environment) — finalize in §10.5.

(The implementation approach — subclass vs. reimplement — has a default directive in §17.5.)

## 17. Implementation specifics (for the implementer / AI)

These pin down choices an implementer would otherwise have to guess.

### 17.1 Naming & coordinates
- Maven: `groupId` `com.nokia.keycloak`, `artifactId` `keycloak-email-mtls`, `version` `1.0.0-SNAPSHOT` (released as `1.0.0`). Output JAR: `keycloak-email-mtls-<version>.jar`.
- Java base package: `com.nokia.keycloak.email.mtls`.

### 17.2 Classes & registration
- `MtlsEmailSenderProvider implements org.keycloak.email.EmailSenderProvider`.
- `MtlsEmailSenderProviderFactory implements org.keycloak.email.EmailSenderProviderFactory`, with `getId()` returning `email-mtls`.
- Register via the service file from §10.2 (`META-INF/services/org.keycloak.email.EmailSenderProviderFactory`) containing one line:
  `com.nokia.keycloak.email.mtls.MtlsEmailSenderProviderFactory`

### 17.3 Configuration keys (as read in code)
- The factory reads its options from `Config.Scope` using the property names `key-store`, `key-store-password`, `key-store-type` (the segment after `spi-email-sender--email-mtls--`; full CLI/env forms are in §11).
- Provider selection (`--spi-email-sender--provider`) is handled by Keycloak, not read by the factory.
- FIPS default: derive the default keystore type from Keycloak's `fips-mode` (BCFKS when `strict`, otherwise PKCS12); when `key-store-type` is unset, prefer extension-based auto-detection (§5.3).

### 17.4 Dependencies (Maven)
- `provided` scope, pinned to 26.6.3: the Keycloak SPI artifacts exposing the email SPI (e.g. `keycloak-server-spi`, `keycloak-server-spi-private`) and `keycloak-services` (for `DefaultEmailSenderProvider`). Use the Keycloak BOM where possible. No new runtime dependencies (§6).
- `test` scope: JUnit and GreenMail (§9).

### 17.5 Implementation approach (default directive)
- Prefer **subclassing** `DefaultEmailSenderProvider` and overriding only the SMTP `Session`/`Transport` construction to inject an `SSLSocketFactory` built from the client keystore (`KeyManagerFactory` → `SSLContext`). Apply it to **both** STARTTLS and SMTPS via the relevant Jakarta Mail `mail.smtp.*` socket-factory properties.
- If subclassing is not feasible (required methods are private/final), reimplement the minimal send path and document the reason in `email-mtls/README.md` (§7).

### 17.6 Reference implementation
- Match behavior parity against keycloak/keycloak at tag `26.6.3`: `services/src/main/java/org/keycloak/email/DefaultEmailSenderProvider.java` and `DefaultEmailSenderProviderFactory.java`.

## 18. Acceptance criteria (Definition of Done)

1. With no keystore configured, behavior is identical to `DefaultEmailSenderProvider` (parity test passes).
2. With a PKCS12 client keystore, mail is delivered to an SMTP server that **requires** a client certificate over **STARTTLS** (587).
3. The same succeeds over **implicit TLS / SMTPS** (465).
4. JKS and BCFKS keystores load successfully; when `key-store-type` is omitted, the type is auto-detected from the file extension.
5. With `fips-mode=strict` and no `key-store-type`, the default type is BCFKS.
6. An invalid keystore path, password, or type causes a fail-fast error with a clear message; no secret value appears in the message or logs.
7. Existing SMTP auth (none / password / XOAUTH2) works unchanged, with and without mTLS.
8. The JAR adds no runtime dependencies, drops into `providers/`, `kc.sh build` succeeds, and the provider is selectable via the §11 options.
9. Passwords are never logged at any level; other sensitive values are not logged at INFO.
10. Unit tests, the parity test, and (if feasible) the mTLS integration test pass in CI on JDK 17 and 21.

## 19. Glossary

- **mTLS** — mutual TLS; both client and server present X.509 certificates.
- **SPI** — Service Provider Interface; Keycloak's extension mechanism.
- **SMTP / STARTTLS / SMTPS** — mail transport. STARTTLS upgrades a plain connection to TLS (usually port 587); SMTPS is implicit TLS from connect (usually port 465).
- **PKCS12 / JKS / BCFKS** — Java keystore file formats; BCFKS is the BouncyCastle FIPS keystore.
- **FIPS** — U.S. cryptographic standard; Keycloak's `fips-mode=strict` changes crypto defaults.
- **XOAUTH2** — OAuth2 token-based SMTP authentication.
- **DCO** — Developer Certificate of Origin; a per-commit `Signed-off-by` line.
- **CLA** — Contributor License Agreement.
- **OSI** — the Nokia internal open-source review/approval team referenced here.
- **PSIRT** — Product Security Incident Response Team (vulnerability handling).
- **SBOM** — Software Bill of Materials.
- **SemVer** — Semantic Versioning (`MAJOR.MINOR.PATCH`).
- **CODEOWNERS** — GitHub file mapping paths to required reviewers.
- **BOM** — Maven Bill of Materials (aligns dependency versions).
- **CI** — Continuous Integration.

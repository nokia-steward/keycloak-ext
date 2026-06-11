# GitHub usage guide (nokia/keycloak-ext)

This guide explains how to work with the repository for each role. It covers both
git commands and the GitHub web UI (GUI) steps. The repository is a **monorepo**:
all extensions share one Issues list and one Releases page, and are distinguished
by an **extension-prefixed tag** (for example `email-mtls/v1.0.0`) and by labels
(for example `ext:email-mtls`).

## Roles and permissions

Because the repository is owned by the **`nokia` organization**, there is no
single "repo owner" user. Map the intended tiers to GitHub permissions, granted
through **teams** rather than to individuals.

| Intended tier | GitHub role | Can do | Cannot do |
|---|---|---|---|
| owner (mvk375) | Repository **Admin** (team `keycloak-ext-admins`) | Manage settings, rulesets, releases, merge; bypass branch rules | n/a (full repo control) |
| maintainers (mvk375 + designees) | **Maintain** (team `keycloak-ext-maintainers`) | Merge PRs, manage issues/labels, publish releases | Delete repo, change visibility/protected settings |
| other contributors | **Read** (public) | Propose changes via fork + PR, open issues | Push to repo branches, merge |

"owner" here means repository **Admin**, not the organization-level Owner over
all Nokia repositories.

## 14.1 Initial project setup (one-time, Admin: mvk375)

### Teams and repo roles (GUI)

1. In the `nokia` org: **Teams -> New team** -> create `keycloak-ext-admins`.
2. Create `keycloak-ext-maintainers`.
3. In the repo: **Settings -> Collaborators and teams -> Add teams**:
   - `keycloak-ext-admins` -> role **Admin**.
   - `keycloak-ext-maintainers` -> role **Maintain**.

### Branch protection ruleset on `main` (GUI)

Repo **Settings -> Rules -> Rulesets -> New branch ruleset**:

- Target branch: `main`.
- Require a **pull request before merging**; require **>= 1 approval**; require
  **review from Code Owners**; **dismiss stale approvals** on new commits;
  **require conversation resolution**.
- Require **status checks to pass** and require branches to be **up to date**:
  add the CI job (the `build` checks from `email-mtls-ci.yml`) and the **DCO**
  check.
- **Block force pushes** and branch **deletion**.
- **Restrict who can push** so changes land via PR merges only, **except** add
  `mvk375` to the ruleset **bypass list** (may push directly to `main`).
  - Trade-off: direct admin pushes skip review and CI gating. Use only for
    emergencies and note when it is used.
- Optional: require **linear history** and **signed commits**.

### Security and automation (GUI)

- **Settings -> Code security**: enable **Dependabot alerts**, **Dependabot
  security updates**, **CodeQL** (code scanning), and **Private vulnerability
  reporting** (private security advisories).
- `dependabot.yml`, `codeql.yml`, and the DCO check are already present in
  `.github/`.

### Tag protection (GUI)

- **Settings -> Tags -> New rule** (or a tag ruleset): protect the pattern
  `email-mtls/v*` so only Maintain+ can create release tags.
- Optional: gate the release workflow behind a protected **Environment**
  (`release`) that requires a maintainer to approve the run.

### Scaffold via PR (git)

```bash
git clone https://github.com/nokia/keycloak-ext.git
cd keycloak-ext
git switch -c chore/scaffold-email-mtls
# add the email-mtls/ module, root community files, and .github/ workflows
git add -A
git commit -s -m "Add email-mtls extension scaffold and CI"
git push -u origin chore/scaffold-email-mtls
# then open a PR into main (see 14.3)
```

## 14.2 Laptop preparation (developer)

1. Install **JDK 17** (and optionally 21), **Git**, and an IDE.
2. Clone the repository:
   ```bash
   git clone https://github.com/nokia/keycloak-ext.git
   cd keycloak-ext
   ```
3. Configure your identity and DCO sign-off:
   ```bash
   git config user.name "Your Name"
   git config user.email "your.email@example.com"
   ```
   Use `git commit -s` so each commit carries a `Signed-off-by` line (required).
4. Build and test the extension (uses the Maven Wrapper; no separate Maven
   install needed):
   ```bash
   cd email-mtls
   ./mvnw verify
   ```

## 14.3 Typical development cycle (contributor / maintainer)

1. Create a branch (naming: `feature/*`, `fix/*`, `docs/*`):
   ```bash
   git switch -c feature/<short-description>
   ```
2. Make changes, then build and test:
   ```bash
   cd email-mtls && ./mvnw verify
   ```
3. Commit with sign-off:
   ```bash
   git add -A
   git commit -s -m "email-mtls: <concise description>"
   ```
4. Push:
   ```bash
   git push -u origin feature/<short-description>
   ```
5. **GUI:** open a **Pull Request** into `main`. CI and the DCO check run
   automatically; request a review.
6. A **maintainer** reviews. Once approved and checks are green, **squash-merge**
   the PR.
7. Delete the merged branch.

Outside contributors perform steps 1-5 from a **fork**:

```bash
# On GitHub: click "Fork".
git clone https://github.com/<you>/keycloak-ext.git
git remote add upstream https://github.com/nokia/keycloak-ext.git
# branch, commit -s, push to your fork, then open a PR from the fork into nokia/keycloak-ext:main
```

## 14.4 Cutting a release (maintainer)

1. Update `email-mtls/CHANGELOG.md` (move items from "Unreleased" into the new
   version) and set the version in `email-mtls/pom.xml`. Merge via PR.
2. Tag and push (the tag is extension-prefixed):
   ```bash
   git switch main && git pull
   git tag email-mtls/v1.0.0
   git push origin email-mtls/v1.0.0
   ```
   Or via **GUI**: **Releases -> Draft a new release -> Choose a tag** ->
   create `email-mtls/v1.0.0`.
3. The release workflow (`email-mtls-release.yml`) builds the JAR (Java 17), runs
   tests, generates a **SHA-256** checksum, and publishes a GitHub Release with
   the JAR and checksum attached. Release notes are derived from the changelog.
4. Verify the Release assets (JAR + `.sha256`). If needed, update the
   keycloak.org/extensions listing (open a PR to `keycloak/keycloak-web` adding a
   JSON entry, or open an issue in the Keycloak tracker requesting the listing).

## Notes

- Only **Maintain+** roles can publish releases.
- Keep PRs scoped to a single extension subfolder where possible; CI is
  path-filtered so unrelated extensions are not rebuilt.
- Every commit must be signed off (`git commit -s`). The DCO check enforces this
  on PRs.

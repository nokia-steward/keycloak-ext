# Contributing to nokia/keycloak-ext

Thanks for your interest in contributing! This repository is a monorepo of
independent Keycloak extensions. Please read this guide before opening a pull
request.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By
participating, you agree to uphold it.

## Developer Certificate of Origin (DCO)

All commits **must** be signed off under the
[Developer Certificate of Origin](https://developercertificate.org/). This
certifies that you wrote the patch or otherwise have the right to submit it
under the project's license.

Add a `Signed-off-by` line to every commit by using the `-s` flag:

```bash
git commit -s -m "email-mtls: fix keystore type auto-detection"
```

This appends a line such as:

```
Signed-off-by: Your Name <your.email@example.com>
```

Configure your identity once:

```bash
git config user.name "Your Name"
git config user.email "your.email@example.com"
```

The DCO check runs automatically on every pull request and must pass before a PR
can be merged.

## Branching model

- `feature/<short-description>` for new features
- `fix/<short-description>` for bug fixes
- `docs/<short-description>` for documentation-only changes

`main` is protected: changes land via reviewed pull requests only.

## Development cycle

1. `git switch -c feature/<short-description>`
2. Make your changes.
3. Build and test the affected extension:
   ```bash
   cd email-mtls
   ./mvnw verify
   ```
4. `git add -A && git commit -s -m "..."`
5. `git push -u origin feature/<short-description>`
6. Open a pull request into `main`. CI and the DCO check run automatically.
7. A maintainer reviews; once approved and checks are green, the PR is
   squash-merged and the branch is deleted.

Outside contributors should fork the repository and open a PR from their fork.

## Prerequisites

- JDK 17 (and optionally 21). The build targets Java 17 bytecode.
- Git and an IDE of your choice.

The build uses the Maven Wrapper (`./mvnw`), so a separate Maven installation is
not required.

## Pull request expectations

- Keep PRs focused and reasonably small.
- Include tests for new behavior and bug fixes.
- Update the relevant `CHANGELOG.md` and documentation.
- Never commit secrets (passwords, real keystores, credentials).
- Respect the per-extension scope: a PR should normally touch a single
  extension subfolder plus shared root files when necessary.

## Releases

Releases are cut by maintainers. See
[docs/GITHUB_GUIDE.md](docs/GITHUB_GUIDE.md) (section "Cutting a release") and the
extension's `CHANGELOG.md`.

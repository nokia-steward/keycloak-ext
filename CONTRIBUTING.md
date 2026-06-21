# Contributing to nokia/keycloak-ext

Thanks for your interest in contributing! This repository is a monorepo of
independent Keycloak extensions. Please read this guide before opening a pull
request.

## Developer's Certificate of Origin

Any contributions to `nokia/keycloak-ext` must only contain code that can
legally be contributed to this project, and which the project can distribute
under its license.

Prior to contributing, please read the
[Developer Certificate of Origin](https://developercertificate.org/) and
sign-off all commits with the `--signoff` option provided by `git commit`. For
example:

```bash
git commit --signoff --message "email-mtls: fix keystore type auto-detection"
```

This option adds a `Signed-off-by` trailer at the end of the commit log message:

```
Signed-off-by: Your Name <your.email@example.com>
```

The short form is equivalent:

```bash
git commit -s -m "email-mtls: fix keystore type auto-detection"
```

Configure your identity once:

```bash
git config user.name "Your Name"
git config user.email "your.email@example.com"
```

The DCO check runs automatically on every pull request and must pass before a PR
can be merged.

## Use of Generative AI

Generative AI tools may be used to assist in writing code, tests, or
documentation, provided that you fully understand every change you submit. The
goal is to keep contributions to this repository consistent, maintainable, and
reviewable.

If you use generative AI to assist with your contribution, all of the following
are required:

1. **You understand the change.** You must be able to explain what your code does
   and why. Submitting AI-generated code you do not understand is not acceptable.
1. **You engage with review feedback.** You are expected to respond to questions
   and comments from reviewers. If you use AI to help draft responses, you must
   edit and proofread them to ensure they are accurate and address the reviewer's
   point.
1. **You can revise the code yourself.** If a reviewer requests changes, you are
   responsible for addressing them, even if your AI tool is unable to produce a
   suitable fix.
1. **You disclose AI agent usage.** Include a note in the PR description if you
   used an AI agent to generate a complete solution from a prompt. You do not
   need to mention simple AI autocomplete.
1. **You ensure licensing compliance.** All generated code must be releasable
   under this project's license. You are responsible for verifying that the tools
   you use do not introduce additional licensing restrictions.

## Branching model

Do not make changes directly on `main`. It is protected: work should happen on
a development branch, then be submitted as a pull request for maintainer review.

Use branch names that identify the affected extension, for example
`email-mtls/<short-description>`. For repository-wide changes, use
`repo/<short-description>`.

## Prerequisites

- JDK 17 or 21
- Git and an IDE of your choice.

The build uses the Maven Wrapper (`./mvnw`), so a separate Maven installation is
not required.

## First-time setup

Clone the repository once before starting development:

```bash
git clone https://github.com/nokia/keycloak-ext.git
cd keycloak-ext
```

Outside contributors should fork the repository first, clone their fork, and
open a PR back to `nokia/keycloak-ext`.

## Development cycle

1. `git switch main && git pull --ff-only`
1. `git switch -c email-mtls/<short-description>`
1. Make your changes.
1. Build and test the affected extension:
   ```bash
   cd email-mtls
   ./mvnw verify
   ```
1. `git add -A && git commit -s -m "..."`
1. `git push -u origin email-mtls/<short-description>`
1. Open a pull request into `main`. CI and the DCO check run automatically.
1. A maintainer reviews; once approved and checks are green, the PR is
   squash-merged and the branch is deleted.

## Spotless

Spotless is used to check and apply formatting for Java sources and basic
whitespace rules for module XML and Markdown files. The normal build runs
`spotless:check`, so formatting issues will fail `./mvnw verify` and CI.

To check formatting locally before sending a PR, run:

```bash
cd email-mtls
./mvnw spotless:check
```

You can use your IDE to fix these issues, or let Spotless fix them:

```bash
./mvnw spotless:apply
```

Review the changes after running `spotless:apply`, then run `./mvnw verify`
again before pushing.

## Pull request expectations

- Keep PRs focused and reasonably small.
- Include tests for new behavior and bug fixes.
- Update the relevant `CHANGELOG.md` and documentation.
- Never commit secrets (passwords, real keystores, credentials).
- Respect the per-extension scope: a PR should normally touch a single
  extension subfolder plus shared root files when necessary.

## Releases

Releases are cut by maintainers.

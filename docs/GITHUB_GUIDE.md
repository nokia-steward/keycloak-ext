# GitHub guide (nokia/keycloak-ext)

This is the administration and contribution runbook for the repository. It is a
**monorepo**: all extensions share one Issues list and one Releases page, and are
distinguished by an **extension-prefixed tag** (for example `email-mtls/v26.6.3-1`)
and by labels (for example `ext:email-mtls`).

Quick links by task:

- [Roles and permissions](#roles-and-permissions)
- [One-time project setup (admin)](#one-time-project-setup-admin)
- [Local development on a Windows laptop](#local-development-on-a-windows-laptop)
- [Initial push vs subsequent pushes](#initial-push-vs-subsequent-pushes)
- Admin tasks:
  [push directly to main](#task-push-changes-directly-to-main-admin) ·
  [dev branch + PR](#task-develop-on-a-branch-and-merge-via-pr) ·
  [run CI manually](#task-run-ci-manually) ·
  [add a maintainer](#task-add-a-maintainer) ·
  [cut a release](#task-cut-a-release)

## Roles and permissions

Because the repository is owned by the **`nokia` organization**, there is no
single "repo owner" user. Map the intended tiers to GitHub permissions, granted
through **teams** rather than to individuals.

| Intended tier | GitHub role | Can do | Cannot do |
|---|---|---|---|
| maintainers (`mvk375` + designees) | Repository **Admin** (team `keycloak-ext-maintainers`) | Manage settings, rulesets, releases, pull requests, issues, and labels | n/a (full repo control) |
| other contributors | **Read** (public) | Propose changes via fork + PR, open issues | Push to repo branches, merge |

"owner" here means repository **Admin**, not the organization-level Owner over
all Nokia repositories. For this small project, one maintainer team starts with
Admin access and covers both admin and maintainer tasks.

> Identity note: make sure the git email you commit with (for example
> `michael.kuznetsov@nokia.com`) is added to the `mvk375` GitHub account
> (**Settings -> Emails**) and that `mvk375` is a member of the maintainer team. Otherwise
> commits will not attribute to your account and the branch-rule bypass will not
> apply to your pushes.

## One-time project setup (admin)

Do this once, in roughly this order. Several items must be ordered carefully so
the **first push is not blocked** (see notes).

### 1. Teams and repo roles (GUI)

- **Purpose:** create the GitHub team that owns repository administration and maintenance.
- **Use when:** setting up the repository for the first time, or recreating access in a new org.
- **Who:** a Nokia GitHub organization owner or someone allowed to create teams and grant repo access.

1. In the `nokia` org: **Teams -> New team** -> create `keycloak-ext-maintainers`.
   - Description:
     `Maintainers for nokia/keycloak-ext. Manage repository settings, rulesets, releases, pull requests, issues, and labels.`
   - Parent team: **None**.
1. In the repo: **Settings -> Collaborators and teams -> Add teams**:
   - `keycloak-ext-maintainers` -> role **Admin**.

If the project grows, split responsibilities later by adding a
`keycloak-ext-admins` team with **Admin** access and reducing
`keycloak-ext-maintainers` to **Maintain** access.

> The `keycloak-ext-maintainers` team must exist **and have repo access before**
> you enable "Require review from Code Owners" (step 3), because
> [`.github/CODEOWNERS`](../.github/CODEOWNERS) references
> `@nokia/keycloak-ext-maintainers`. If the team does not exist, GitHub reports
> CODEOWNERS as invalid and the rule can never be satisfied.

### 2. First push (see [Initial push vs subsequent pushes](#initial-push-vs-subsequent-pushes))

- **Purpose:** publish the local repository to GitHub before branch rules can block the first push.
- **Use when:** the GitHub repository is empty or has not received the local `main` branch yet.
- **Who:** the initial repository admin or maintainer doing the first publication.

Push the repository **before** turning on "restrict who can push", or make sure
`mvk375` is on the bypass list, so the very first push to `main` is not rejected.
Let CI, DCO, and CodeQL run once so their check names become selectable in the
next step.

### 3. Branch protection ruleset on `main` (GUI)

- **Purpose:** protect `main` so normal changes go through reviewed pull requests and required checks.
- **Use when:** after the first push and after CI check names have appeared at least once.
- **Who:** a repository admin.

Repo **Settings -> Rules -> Rulesets -> New branch ruleset**:

- Target branch: `main`.
- Require a **pull request before merging**; require **>= 1 approval**; require
  **review from Code Owners**; **dismiss stale approvals** on new commits;
  **require conversation resolution**.
- Require **status checks to pass** and require branches to be **up to date**.
  Add these checks (selectable only after they have run once - see step 2):
    - `build (JDK 17)` (from `email-mtls-ci.yml`)
    - `DCO sign-off check` (from `dco.yml`)
    - optionally `Analyze (java)` (from `email-mtls-codeql.yml`)
- The DCO requirement is enforced by [`.github/workflows/dco.yml`](../.github/workflows/dco.yml),
  not by a built-in GitHub setting. That workflow checks every PR commit for a
  `Signed-off-by:` line. Making `DCO sign-off check` a required status check is
  what prevents merging unsigned-off commits into `main`.
- **Block force pushes** and branch **deletion**.
- **Restrict who can push** so changes land via PR merges only, **except** add
  `mvk375` to the ruleset **bypass list** (may push directly to `main`).
    - Trade-off: direct admin pushes skip review and CI gating on the push itself
      (CI still re-runs on `main` afterward). Use for emergencies and note when used.
- Optional: require **linear history** and **signed commits**.

### 4. Tag protection and release gating (GUI)

- **Purpose:** control who can create release tags and optionally require approval before publishing releases.
- **Use when:** before the first public release tag is created.
- **Who:** a repository admin.

- **Settings -> Rules -> Rulesets -> New tag ruleset**: protect the pattern
  `*/v*` (covers `email-mtls/v*` and every future `<ext>/v*`) so only members
  of `keycloak-ext-maintainers` can create release tags.
- Optional: gate releases behind a protected **Environment** named `release`
  (**Settings -> Environments**) that requires a maintainer to approve each run.

### 5. Security and automation (GUI)

- **Purpose:** enable repository security features and automation used by issues, dependencies, and code scanning.
- **Use when:** during initial setup, and again when adding new extensions or labels.
- **Who:** a repository admin.

- **Settings -> Code security**: enable **Dependabot alerts**, **Dependabot
  security updates**, **CodeQL / code scanning**, and **Private vulnerability
  reporting**.
- `dependabot.yml`, `email-mtls-codeql.yml`, and the `dco.yml` check are already
  in `.github/`.
- **Labels:** create the labels listed in [`.github/labels.yml`](../.github/labels.yml)
  (Issues -> Labels, or `gh label create "ext:email-mtls" --color 1d76db`). The
  issue forms and the issue-labeler workflow rely on `ext:*` labels.

> No repository **secrets** are required. The release workflow uses the built-in
> `GITHUB_TOKEN` (it declares `permissions: contents: write`). Just ensure Actions
> are enabled for the repo and the org does not cap the token below `contents: write`.

## Local development on a Windows laptop

### Get a credential to authenticate git over HTTPS

- **Purpose:** let local `git` authenticate to GitHub over HTTPS.
- **Use when:** setting up a Windows laptop or when Git asks for credentials.
- **Who:** any maintainer or contributor who pushes branches.

Recommended: a **fine-grained Personal Access Token (PAT)**.

1. GitHub -> **Settings -> Developer settings -> Personal access tokens ->
   Fine-grained tokens -> Generate new token**.
1. Set **Resource owner** = `nokia`, **Repository access** = `nokia/keycloak-ext`.
1. **Permissions**: *Contents* = Read and write (add *Pull requests* = Read and
   write if you use the `gh` CLI to open PRs).
1. Generate and copy the token once; store it in a password manager.

> Org PATs may require an org owner to **approve** the token before it works.

How the token is actually used on Windows:

- **Git Credential Manager** ships with Git for Windows. On your first `clone`/
  `push` it opens a browser to sign in and then caches the credential in
  **Windows Credential Manager** - so you usually never paste the PAT.
- If git ever prompts for a username/password on an HTTPS remote, paste the
  **PAT as the password** (never your account password).

Alternative: **SSH keys**.

```powershell
ssh-keygen -t ed25519 -C "michael.kuznetsov@nokia.com"
# Add the public key (.pub) under GitHub -> Settings -> SSH and GPG keys,
# then use the git@github.com:nokia/keycloak-ext.git remote URL.
```

### One-time git configuration

- **Purpose:** set the identity and line-ending behavior used by local commits.
- **Use when:** configuring a development machine for the first time.
- **Who:** any maintainer or contributor who commits to this repository.

```powershell
git config --global user.name  "Michael Kuznetsov"
git config --global user.email "michael.kuznetsov@nokia.com"
git config --global core.autocrlf input   # keep committed files LF (matches .gitattributes)
git config --global init.defaultBranch main
```

### Build and test locally

- **Purpose:** verify the extension before opening a pull request or pushing a fix.
- **Use when:** after code, test, documentation, or build changes under `email-mtls/`.
- **Who:** anyone changing the extension.

Uses the Maven Wrapper (no separate Maven install needed). JDK 17+ required.

```powershell
cd email-mtls
.\mvnw.cmd -B -ntp verify
```

### Day-to-day push / pull commands (PowerShell)

- **Purpose:** show the normal branch, test, commit, push, and rebase workflow.
- **Use when:** doing regular development after the first repository setup is complete.
- **Who:** maintainers and contributors working from a local clone.

```powershell
# start a branch
git switch -c email-mtls/short-description

# work, then build/test
cd email-mtls; .\mvnw.cmd -B -ntp verify; cd ..

# commit with DCO sign-off (required)
git add -A
git commit -s -m "email-mtls: concise description"

# first push of a new branch sets the upstream
git push -u origin email-mtls/short-description
# later pushes on the same branch are just:
git push

# keep your branch current with main
git switch main; git pull --ff-only
git switch email-mtls/short-description; git rebase main

# verify your identity on the last commit
git log -1 --format='%an <%ae>'
```

Then open a Pull Request on GitHub (see
[dev branch + PR](#task-develop-on-a-branch-and-merge-via-pr)).

## Initial push vs subsequent pushes

The local clone currently has **no remote** configured and a single commit on
`main`. The first publish is different from everyday pushes.

### One-time initial publish

- **Purpose:** create the public GitHub repository and push the initial local history.
- **Use when:** publishing this repository for the first time.
- **Who:** the initial repository admin or maintainer.

```powershell
# 0) Commit everything first (signed off). Confirm a clean tree:
git status
git add -A
git commit -s -m "email-mtls: repository scaffold and CI"   # if anything is uncommitted

# 1) Create the empty GitHub repo WITHOUT a README/license (avoids first-push conflicts):
#    GUI: New repository -> nokia/keycloak-ext -> Public -> do not initialize.
#    Or with the CLI:
gh repo create nokia/keycloak-ext --public --source . --remote origin --push
```

If you used the GUI to create the empty repo, wire it up manually instead of the
`gh ... --push` line:

```powershell
git remote add origin https://github.com/nokia/keycloak-ext.git
git branch -M main
git push -u origin main
```

> Ordering matters: apply the `main` ruleset and "restrict who can push" **after**
> this first push (or make sure `mvk375` is on the bypass list first), otherwise
> the protected-branch rule can reject the initial push. Let CI run once before
> marking checks required (step 3 of setup).

### Subsequent pushes

- **Purpose:** explain the normal push model after the remote exists.
- **Use when:** after the initial `origin` remote and `main` branch are already published.
- **Who:** all maintainers and contributors.

The remote and upstream now exist, so day-to-day work is just:

- Contributors and maintainers: push a branch and open a PR (`git push`).
- Admin only, for emergencies: `git push origin main` directly (allowed by the
  bypass list).

`git push -u` is only needed the first time you push a given branch.

## Task: push changes directly to main (admin)

- **Purpose:** document the emergency path for bypassing the normal pull request flow.
- **Use when:** only for urgent fixes where a pull request would be too slow.
- **Who:** a repository admin on the ruleset bypass list.

Allowed only for `mvk375` (on the ruleset bypass list). Prefer a PR; use direct
push for emergencies and note when you do.

```powershell
git switch main
git pull --ff-only
# make the change
git add -A
git commit -s -m "email-mtls: emergency fix"
git push origin main
```

CI (`push` trigger) re-runs on `main` after the push, so a broken build is still
surfaced - it just is not gated beforehand.

## Task: develop on a branch and merge via PR

- **Purpose:** describe the normal contribution path.
- **Use when:** making any planned code, documentation, CI, or release-preparation change.
- **Who:** everyone, including maintainers.

This is the normal path for everyone, including maintainers.

1. Branch: `git switch -c email-mtls/<short-description>`. Use the affected
   extension as the branch prefix; for repository-wide changes, use
   `repo/<short-description>`.
1. Change, build/test (`.\mvnw.cmd verify`), and commit with `-s` (DCO).
1. `git push -u origin email-mtls/<short-description>`.
1. On GitHub, **open a Pull Request into `main`**. CI, DCO, and CodeQL run
   automatically; request a review.
1. A **maintainer** reviews. Once approved and checks are green, **squash-merge**.
1. Delete the merged branch.

Outside contributors do steps 1-3 from a **fork**:

```powershell
# On GitHub: click "Fork".
git clone https://github.com/<you>/keycloak-ext.git
cd keycloak-ext
git remote add upstream https://github.com/nokia/keycloak-ext.git
# branch, commit -s, push to your fork, then open a PR into nokia/keycloak-ext:main
```

> CI trigger rationale (GitHub best practice): the required build/test runs on
> **`pull_request`** (so it gates merges) and on **`push` to `main`** (so direct
> admin pushes and merge results are verified). Integration tests and releases
> are **manual** - they are slower and need Docker or a tag.

## Task: run CI manually

### Integration tests (manual)

- **Purpose:** run the Docker-based end-to-end test suite.
- **Use when:** validating release candidates, SMTP/mTLS behavior, or changes to integration-test code.
- **Who:** maintainers, or contributors with Docker available locally.

`email-mtls-it.yml` is `workflow_dispatch` only.

- GUI: **Actions -> "email-mtls integration tests" -> Run workflow** (pick the
  branch; runs on JDK 17).
- CLI: `gh workflow run email-mtls-it.yml --ref main`

Locally (needs Docker):

```powershell
cd email-mtls
.\mvnw.cmd -Pit verify
```

### Release (manual)

- **Purpose:** run the release workflow for an existing release tag.
- **Use when:** a versioned `email-mtls/v*` tag already exists and should be published as a GitHub Release.
- **Who:** maintainers with permission to run release workflows.

The release workflow runs automatically on a `email-mtls/v*` tag, or can be run
manually against an existing tag:

- CLI: `gh workflow run email-mtls-release.yml -f tag=email-mtls/v26.6.3-1`
- GUI: **Actions -> "email-mtls Release" -> Run workflow** and enter the tag.

## Task: add a maintainer

- **Purpose:** grant a person maintainer and admin access through the team.
- **Use when:** onboarding a new trusted maintainer.
- **Who:** a repository admin or organization owner who can manage team membership.

1. In the `nokia` org, add the person to the **`keycloak-ext-maintainers`** team
   (**Teams -> keycloak-ext-maintainers -> Add a member**).
1. That grants the **Admin** role on the repo and, via
   [`.github/CODEOWNERS`](../.github/CODEOWNERS), makes them a **code owner for
   all paths** - so they can review, approve, merge, cut releases, and manage
   repository settings.
1. If they should push directly to `main`, add their username to the ruleset
   bypass list.

No code change is needed to add a maintainer; CODEOWNERS uses the team handle, so
new members are covered automatically.

## Task: cut a release

- **Purpose:** publish a versioned extension release with a JAR and checksum.
- **Use when:** releasing a tested version for a specific Keycloak version.
- **Who:** maintainers.

Each extension releases independently using its own tag prefix.

1. Update the extension's version in [`email-mtls/pom.xml`](../email-mtls/pom.xml):
   bump `keycloak.version` and/or `release.number`.
   - Same Keycloak, another build: `26.6.3-1` -> `26.6.3-2` (bump `release.number`).
   - New Keycloak: set `keycloak.version` and reset `release.number` to `1`
     (e.g. `26.7.1-1`).
1. Move the changes from `## [Unreleased]` into a new
   `## [<version>]` section in [`email-mtls/CHANGELOG.md`](../email-mtls/CHANGELOG.md)
   (the release workflow extracts notes by matching `[<version>]`).
1. Merge the version and changelog changes via a PR.
1. Tag and push (tag = `email-mtls/v<version>`):
   ```powershell
   git switch main; git pull --ff-only
   git tag email-mtls/v26.6.3-1
   git push origin email-mtls/v26.6.3-1
   ```
   Or GUI: **Releases -> Draft a new release -> Choose a tag** -> create
   `email-mtls/v26.6.3-1`.
1. The release workflow (`email-mtls-release.yml`) builds the JAR (Java 17), runs
   tests, computes a **SHA-256** checksum, and publishes a GitHub Release titled
   `email-mtls 26.6.3-1` with `keycloak-email-mtls-26.6.3-1.jar` and its `.sha256`
   attached. Release notes come from the changelog.
1. Verify the Release assets. Optionally update the keycloak.org/extensions
   listing (open a PR to `keycloak/keycloak-web`, or an issue in the Keycloak
   tracker).

## Adding a new extension

- **Purpose:** point maintainers to the checklist for adding another extension module.
- **Use when:** creating a new sibling extension in this monorepo.
- **Who:** maintainers adding or reviewing a new extension.

See [ADDING_AN_EXTENSION.md](ADDING_AN_EXTENSION.md) for the full checklist
(new module, per-extension workflows, dependabot/CODEOWNERS/labels wiring, and
the `mvnw` executable-bit step).

## Notes

- Members of `keycloak-ext-maintainers` can review, merge, publish releases, and
  administer the repository.
- Keep PRs scoped to a single extension subfolder where possible; CI is
  path-filtered so unrelated extensions are not rebuilt.
- Every commit must be signed off (`git commit -s`). The DCO check enforces this
  on PRs.

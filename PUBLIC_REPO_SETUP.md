# Public repository setup

This repository is being prepared as a clean public development copy of the private PSE project. The private repository and its branches remain separate and unchanged.

## Automation workflow

`.github/workflows/android-build.yml` is configured to:

- Build an unsigned debug APK on each push to `pse-phone-auto-v2-test`.
- Use GitHub-hosted `ubuntu-latest` runners.
- Retain uploaded APK artifacts for seven days.
- Build a signed release only after a manual workflow dispatch with `build_signed_release` enabled.
- Post one `PSE_BUILD_SUCCESS` or `PSE_BUILD_FAILURE` event to `#pse-development` for each development push.

## One-time repository settings

Before enabling the development loop:

1. In **Settings → Secrets and variables → Actions**, add `SLACK_BOT_TOKEN` as a repository secret. The bot needs permission to post in `#pse-development`; never put the token in a file or commit.
2. If signed APKs are needed, add `PSE_KEYSTORE_BASE64`, `PSE_KEYSTORE_PASSWORD`, `PSE_KEY_ALIAS`, and `PSE_KEY_PASSWORD` as repository secrets. These are not needed for ordinary debug builds.
3. In the GitHub account billing settings, set budgets for Actions, Packages, Git LFS, and AI Credits to **$0** and enable usage blocking. Confirm the setting is active before the first build.
4. Keep the default branch as `main`; PSE development and the V4 loop use `pse-phone-auto-v2-test`.

## Clean source snapshot requirements

Import source as a new root snapshot commit on `pse-phone-auto-v2-test`. Do not copy private commit history or other branches. Review the complete snapshot before pushing.

Exclude:

- Every `runtime-validation-*` folder and all screenshots, UI dumps, device logs, evidence captures, temporary backups, and real `.sav` files.
- ZIP/APK/AAB build archives and generated build output.
- Keystores, signing properties, private keys, tokens, passwords, environment files, and credential exports.
- Legacy Slack reporter workflows or scripts that embed tokens or send duplicate events.

Retain the app source, Android resources, required test fixtures that are synthetic and non-sensitive, Gradle files, PSE tools used by development validation, and the current Switch-first roadmap/work status.

## Safe activation order

1. Complete the clean snapshot review and secret scan.
2. Push the sanitized source plus this workflow to `pse-phone-auto-v2-test`.
3. Confirm one successful public Actions build and artifact retention.
4. Confirm Slack reports the matching repository, branch, SHA, run ID, attempt, and build result.
5. Only then retarget the existing PSE Phone Auto V4 automation to this public repository. Preserve its event filters, deduplication, and Switch-first priorities. Do not create a second automation.

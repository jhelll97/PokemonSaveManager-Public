# Pokémon Save Manager

Pokémon Save Manager is an Android application for inspecting and managing supported Pokémon save files.

## Current development priority

Switch-save support is the active priority. The project preserves existing read-only viewers and adds narrowly scoped title-specific writes with explicit review, source validation, verified backups, checksum handling, reopen verification, and rollback protections. The current write work is BDSP text-speed editing to a new copy.

See [PSE_ROADMAP.md](PSE_ROADMAP.md) for the active development constraints.

## Build

The public development branch is `pse-phone-auto-v2-test`. Each push compiles the unsigned Android debug APK with a standard GitHub-hosted runner. Artifacts are retained for seven days.

Signed release APKs are built only from a manual workflow dispatch when the signing option is explicitly enabled. Signing values belong in GitHub Actions repository secrets and must never be committed.

The build workflow posts an event-deduplicable success or failure result to the configured Slack development channel.

## Source and test data

Do not add personal save files, device captures, runtime-validation artifacts, credentials, signing material, or generated APKs to this repository. Test fixtures must be synthetic and safe to publish.

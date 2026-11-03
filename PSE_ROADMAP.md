# PSE Development Roadmap

## Current priority — Gen 1 feature parity across Switch games (Generations 7–9)

Gen 1 is the completed reference game for the shared PSE feature set, feature order, and screen layout. This roadmap is limited to Switch-save titles in Generations 7–9. Do not start feature work for other generations until the shared and title-specific Switch work below is complete.

### Phase 1 — Inventory the Gen 1 baseline

- Inventory the actual Gen 1 screens, actions, and working features in PSE, in the exact order they appear. That inventory is the definitive parity checklist; do not invent a different shared feature set.
- Record the matching Gen 1 layout and interaction pattern for each feature so Switch screens can use the same navigation, order, layout, terminology, and controls.
- Map each applicable Gen 1 feature to every Switch title. Mark a feature “not applicable” only when that title has no equivalent native save data, and record the reason. Do not show placeholders or imply unsupported data was read or edited.

The baseline follows the existing Gen 1 feature set, including applicable save opening and identity/integrity handling, trainer/profile, party and storage, Pokémon details and editing, Pokédex, inventory/items, progression/badges, events, and PSE Bank/transfer routes.

The observed Gen 1 home editor order is:

1. Trainer Data
2. Pokémon Boxes
3. PSE Bank
4. Safe Backups
5. Items
6. Pokédex
7. Events

The validated shared parity step is **Trainer Data**. Every supported Switch family exposes it first with the Gen 1 label and control treatment, backed by that title's validated native trainer reader.

The validated shared parity steps are **Trainer Data** and **Pokémon Boxes**. The Boxes card opens the same Party / PC Boxes choice for every supported Switch family, then dispatches to that title's validated native party and storage reader.

The active shared parity step is **PSE Bank**. Native byte-preserving deposit, list, inspect, and remove flows are available for Let's Go PB7, Sword/Shield PK8, BDSP PB8, and Legends: Arceus PA8 records without changing the source save. Scarlet/Violet and Legends: Z-A remain absent from the Bank UI until their own native record adapters are implemented and validated.

### Phase 2 — Implement the shared Gen 1 features in the same order

Follow the exact Gen 1 feature order from first screen to last. Implement each applicable feature for the Switch titles using the same PSE layout and interaction pattern, adapting only the native data handling. Complete the shared feature for every applicable Switch title before advancing to the next Gen 1 feature.

A feature is complete only when its usable UI is connected to working native save-data handling. Parser-only changes, placeholders, or a feature in only one title do not count as shared parity.

| Generation | Switch games |
|---|---|
| 7 | Pokémon: Let’s Go, Pikachu! / Let’s Go, Eevee! |
| 8 | Pokémon Sword / Shield; Brilliant Diamond / Shining Pearl; Legends: Arceus |
| 9 | Pokémon Scarlet / Violet; Legends: Z-A |

Keep title-specific parsing and save structures separate where native formats differ, while preserving the same PSE presentation.

### Phase 3 — Add each title’s unique save features

Only after the full shared Gen 1 feature set is working in every applicable Switch title, add features unique to the game being worked on. Confirm each against that title’s native save data and provide usable UI in the matching PSE layout.

### Out of scope

- Do not add PSE controls for preferences players can change in the game itself, including music, sound-effects, or voice volume and text speed.
- Do not prioritize other generations while this Switch roadmap is active.
- Do not build, sign, upload, or report APK artifacts unless explicitly requested.

### Save-integrity requirements

For any save edit, retain source identity handling, a verified recovery backup, a narrowly scoped native change, required checksum handling, before/after review, persisted reopen validation, and rollback or failed-copy cleanup. These protections are part of making a save-editing feature work; keep the original source unchanged unless that specific write path is separately implemented and validated.

### Checks and builds

- Do not add tests, lint, static-analysis, smoke-test, QA, or other check jobs, and do not make check-only commits.
- Keep the already-required automatic Android compile on each development commit. Do not add extra manual checks ahead of the feature work.
- Preserve the save-integrity protections above as part of each applicable feature.

## Public repository safeguards

- Work only on `pse-phone-auto-v2-test`; keep `main`, `pse-phone-auto`, and the original private repository unchanged.
- Use only new, matching build events. Verify repository, branch, SHA, run ID, attempt, result, and current branch HEAD. Deduplicate events and do not replay or overlap work.
- Keep the public snapshot free of real save files, runtime captures, credentials, signing material, and private repository history.

# PSE Development Roadmap

## Current priority — Gen 1 feature parity across Switch games (Generations 7–9)

Gen 1 is the completed reference game for the shared PSE feature set and screen layout. This roadmap is limited to Switch-save titles in Generations 7–9. Do not start feature work for other generations until the shared and title-specific Switch work below is complete.

### Phase 1 — Map the Gen 1 baseline

- Inventory the existing Gen 1 screens, actions, and working features in PSE. Use that inventory as the definitive parity checklist; do not invent a different shared feature set.
- Reuse the same PSE navigation, screen order, layout, and interaction pattern for each Switch title. Adapt the data readers and writers to that title’s native save format behind the shared UI.
- Map each applicable Gen 1 feature to every Switch title. Mark a feature “not applicable” only when that title has no equivalent native save data, and record the reason. Do not show placeholders or imply unsupported data was read or edited.

The checklist covers each shared Gen 1 feature where the title supports it, including save opening and identity/integrity handling, trainer/profile, party and storage, Pokémon details and editing, Pokédex, inventory/items, progression/badges, events, and PSE Bank/transfer routes.

### Phase 2 — Complete shared-feature parity for every Switch title

Work through the shared checklist across all listed games before adding title-only features. A feature is complete only when its usable UI and native data handling are both present for every applicable title.

| Generation | Switch games |
|---|---|
| 7 | Pokémon: Let’s Go, Pikachu! / Let’s Go, Eevee! |
| 8 | Pokémon Sword / Shield; Brilliant Diamond / Shining Pearl; Legends: Arceus |
| 9 | Pokémon Scarlet / Violet; Legends: Z-A |

Preserve the Gen 1 layout and terminology so the same feature is in the same place across games. Keep title-specific parsing and save structures separate where their native formats differ.

### Phase 3 — Add each title’s unique save features

Only after the shared Gen 1 checklist is complete across all applicable Switch titles, add features that are unique to the game being worked on. Confirm each feature against that title’s native save data and provide its usable UI in the matching PSE layout.

### Out of scope

- Do not add PSE controls for preferences that players can change in the game itself, including music, sound-effects, or voice volume and text speed.
- Do not prioritize other generations while this Switch roadmap is active.
- Do not build, sign, upload, or report APK artifacts unless explicitly requested.

### Save-integrity requirements

For any save edit, retain source identity checks, a verified recovery backup, a narrowly scoped native change, required checksum handling, before/after review, persisted reopen validation, and rollback or failed-copy cleanup. Keep the original source unchanged unless that specific write path is separately implemented and validated.

Normal repository build validation continues as configured. Do not add a separate broad test phase ahead of the feature roadmap; defer extra checks until requested or needed for the safety of a specific save write.

## Public repository safeguards

- Work only on `pse-phone-auto-v2-test`; keep `main`, `pse-phone-auto`, and the original private repository unchanged.
- Use only new, matching build events. Verify repository, branch, SHA, run ID, attempt, result, and current branch HEAD. Deduplicate events and do not replay or overlap work.
- Keep the public snapshot free of real save files, runtime captures, credentials, signing material, and private repository history.

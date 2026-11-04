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

The completed shared parity steps are **Trainer Data**, **Pokémon Boxes**, **PSE Bank**, **Safe Backups**, **Items**, **Pokédex**, and **Events**. Native byte-preserving Bank deposit, list, inspect, and remove flows are available for Let's Go PB7, Sword/Shield PK8, BDSP PB8, Legends: Arceus PA8, Scarlet/Violet PK9, and Legends: Z-A PA9 records without changing the source save. Every Switch family has the Gen 1-ordered Safe Backups screen with verified source-snapshot creation, native title/integrity validation, byte-for-byte inspection, explicit removal, and protected restore-to-source. Restore requires an unchanged bound source, explicit before/after review, a verified persisted recovery snapshot, a whole-document exact-byte write, native reopen validation, and verified automatic rollback on failure. Every Switch family also has Gen 1-shaped Items quantity, removal, and add-item controls backed by its native pocket catalogue and item records. Let's Go edits recalculate block 0's native CRC; Sword/Shield edits change one decoded item word and recalculate the native SHA-256 footer; BDSP edits change one fixed 16-byte item record, or only its count word, and recalculates the native whole-file MD5; Legends: Arceus edits change one decoded four-byte active inventory record and preserve the independent favorite flag; Scarlet/Violet and Legends: Z-A edits change one fixed decoded 16-byte item record and use the native item-0 empty template for removal. Container-based edits rebuild the native encryption and SHA-256 footer. Every edit writes only a reviewed new copy, retains a verified recovery snapshot, reopens the persisted copy, and removes a failed copy when possible. Legends: Z-A additionally keeps locked pockets, special-count records, mismatched records, and unusual flags read-only. Pokédex parity is complete across all six Switch families. Let's Go adds one reviewed seen or caught variant and optional caught language while preserving registration and size data and recalculating block 4's CRC. Sword/Shield adds one normal native form index in a selected regional record, optional caught language, preserves display, counter, and Gigantamax metadata, and rebuilds the encrypted container and SHA-256 footer. BDSP adds one gender/shiny registration, optional caught language, preserves form, Spinda, and regional/national progression data, and recalculates the whole-file MD5. Legends: Arceus adds only one native two-byte obtained count while preserving every research flag, task counter, selected display field, global value, and unrelated species. Scarlet/Violet adds one reviewed native form registration for original or DLC-era records and preserves display selections and unknown bits. Legends: Z-A adds one reviewed native form registration while preserving Mega, Alpha, display selections and unknown fields. Container editors rebuild native encryption and the SHA-256 footer. Each editor saves only to a verified new copy and leaves the source unchanged. Events parity is complete. Let's Go imports one supplied checksum-valid fateful-event PB7 into the next unused storage record, preserves the record exactly, increments only the native storage count, and recalculates the two affected block CRCs. Sword/Shield imports one supplied checksum-valid fateful-event PK8 into the first empty PC-box slot, preserves all other PC and party data, and rebuilds the encrypted container and SHA-256 footer. BDSP imports one supplied checksum-valid fateful-event PB8 into the first empty PC-box slot, preserves all other save bytes, and recalculates the native whole-file MD5. Legends: Arceus imports one supplied checksum-valid fateful-event PA8 into the first empty pasture slot, preserves all other pasture and party data, and rebuilds the encrypted container and native SHA-256 footer. Scarlet/Violet imports one supplied checksum-valid fateful-event PK9 into the first empty PC-box slot, preserves the dedicated ride Pokémon and all other PC and party data, and rebuilds the encrypted container and native SHA-256 footer. Legends: Z-A imports one supplied checksum-valid fateful-event PA9 into the first empty PC-box slot, preserves the slot's native footer metadata and all other PC and party data, and rebuilds the encrypted container and native SHA-256 footer. All six imports use the Gen 1-shaped Events navigation, explicit review, verified recovery backup, persisted reopen validation and failed-copy cleanup, while making no distribution-provenance claim. With all seven shared parity steps complete, Phase 3 is active and title-specific work begins with Let's Go.

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

Let's Go title-specific work has started with Capture Records. PSE reads the complete native block 12 record and can change one reviewed per-species caught counter from 0 to 9,999, one Professor Oak transfer counter from 0 to 999,999,999, or the separate all-species caught total from 0 to 999,999,999 in a new copy. Each edit changes only that four-byte counter and block 12's CRC; unselected totals and counters, other species, and all unrelated bytes remain unchanged. Source identity, verified recovery backup, persisted reopen validation, and failed-copy cleanup are required.

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

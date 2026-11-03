# PSE Development Roadmap

## Active priority — Switch saves first, gradual writes

- Continue Switch-save development before returning to generation-specific work.
- Preserve existing Switch read-only viewers, Gen 1 behavior, and cumulative generation requirements.
- Add one bounded, title-specific write at a time with usable UI/API, explicit before/after review, source identity checks, native validation, verified recovery backup, exact byte scope, checksum recalculation, persisted reopen verification, and rollback/failed-copy cleanup.
- BDSP retains thirteen reviewed native preference edits plus music-volume editing. Current candidate adds sound-effects-volume editing (0–10) to a new copy; unsupported saved values are blocked. Source replacement and other write features remain unavailable until separately implemented and validated.
- Pause new Gen 3 features and Gen 6/Gen 7 converter work.
- Every feature change must include its usable UI, API wiring, and relevant validation/integrity protections.
- Do not build, sign, upload, dispatch, or report APK artifacts unless explicitly requested. Automatic public validation compiles debug sources and runs lint without APK packaging or artifact upload. The signed-release path remains available only through an explicit manual request.

## Public repository safeguards

- Work only on `pse-phone-auto-v2-test`; keep `main` and the private repository unchanged.
- Use new, matching build events only. Verify repository, branch, SHA, run ID, attempt, result, and current branch HEAD. Deduplicate events and do not replay or overlap work.
- Keep the public snapshot free of real save files, runtime captures, credentials, signing material, and private repository history.

## Current bounded candidate

- Add sound-effects-volume selection to the BDSP Edit options chooser and existing reviewed new-copy editor. Only byte `0x79BB1` and the native digest may change. Music, voice, other options and the source remain unchanged.
- Use a conservative supported range of 0–10; native storage is one byte. Layout evidence: [PKHeX ConfigSave8b](https://github.com/kwsch/PKHeX/blob/0660d1e1f4924b8478daec73281bb8071a82ad19/PKHeX.Core/Saves/Substructures/Gen8/BS/ConfigSave8b.cs), VolumeSFX at config +0x3D.
- Preserve native validation, source identity, synced verified recovery backup, empty destination checks, exact persisted reopen and failed-copy cleanup. No source replacement.
- Parent `13183dcd663338a735d7891b098d25677f8fc00a` passed run `41700319619`, attempt 1, push, including compilation and blocking lint. Candidate CI and device/provider acceptance remain pending; await its own new Slack event before another push.

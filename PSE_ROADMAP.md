# PSE Development Roadmap

## Active priority — Switch saves first, gradual writes

- Continue Switch-save development before returning to generation-specific work.
- Preserve existing Switch read-only viewers, Gen 1 behavior, and cumulative generation requirements.
- Add one bounded, title-specific write at a time with usable UI/API, explicit before/after review, source identity checks, native validation, verified recovery backup, exact byte scope, checksum recalculation, persisted reopen verification, and rollback/failed-copy cleanup.
- Current write scope: BDSP text-speed editing to a new copy. Source replacement and other write features remain unavailable until each is separately implemented and validated.
- Pause new Gen 3 features and Gen 6/Gen 7 converter work.
- Every feature change must include its usable UI, API wiring, and relevant validation/integrity protections.
- Do not build, sign, upload, dispatch, or report APK artifacts unless explicitly requested. Automatic public validation compiles debug sources and runs lint without APK packaging or artifact upload. The signed-release path remains available only through an explicit manual request.

## Public repository safeguards

- Work only on `pse-phone-auto-v2-test`; keep `main` and the private repository unchanged.
- Use new, matching build events only. Verify repository, branch, SHA, run ID, attempt, result, and current branch HEAD. Deduplicate events and do not replay or overlap work.
- Keep the public snapshot free of real save files, runtime captures, credentials, signing material, and private repository history.

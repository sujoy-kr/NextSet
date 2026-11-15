# Release QA

This is an implementation and regression audit, not a claim of physical-device certification. The latest screenshot capture and feature regressions target an isolated API 29 emulator; the earlier baseline used API 37. Build/test counts are recorded from the final reports, not inferred from test files.

## Latest feature audit (2026-11-15)

- All **41 unit tests** passed. Debug APK, Android test APK and minified/resource-shrunk unsigned release builds passed.
- **12 focused API 29 regression cases verified**: 4 planning UI, 2 layout/timezone, 3 execution and 3 alert cases. The 9 UI cases passed in the focused run; all 3 alert cases passed in the final independent rerun after fixing asynchronous-post test synchronization.
- Android lint: **0 errors, 15 existing dependency/tooling warnings**, including the skipped third-party Lifecycle rules described below. No baseline was added.
- The opt-in `DocumentationScreenshotsTest` passed on an API 29 emulator and produced **11 new 720 × 1280 screenshots**. Every capture was visually reviewed; PNG completeness, documentation links and shell syntax were checked.
- The screenshot fixtures are synthetic demo workouts/history shown by the actual app. The timer demo uses a one-second rest. The capture test requires an empty installation and never clears existing workout data; `scripts/capture-screenshots.sh` replaces the gallery only after successful capture and validation.
- Fixed weekday/sequence picker restoration and start-date dialog lifecycle handling, including an unsaved selected date across recreation. Planning-mode chips and weekly navigation now wrap at large text sizes.
- Fixed weekly analysis caching across timezone changes; app state refreshes the current zone and completed-set aggregation uses it. Unit and UI regressions cover a set crossing a local week boundary.
- A missing or inaccessible custom tone now falls back to the app default; system default ringtone/notification URIs remain delegated to Android even when the app cannot read their underlying files directly. Tone access runs off the UI thread, notification-channel creation is covered by permission error handling, and offered persistent read grants are retained. Rest alert and reminder sound/vibration modes remain independently controlled by Android channel settings.
- UI assertions now await the displayed state after asynchronous Room updates, lazy-list tests scroll to uncomposed rows through their parent list, and notification-mode checks await distinct posted notifications. This removes timing-dependent test failures without relaxing the behavior assertions.
- Updated the README with both planning modes, weekly-count semantics, duration-estimate inputs, sound behavior and a reproducible screenshot workflow.

## Earlier API 37 baseline (2026-11-15)

- Debug build, 32 unit tests and 25 API 37 emulator tests passed.
- Android lint: 0 errors, 15 dependency/tooling warnings. The third-party Lifecycle registry reports two custom rules as skipped due to lint API incompatibility; no baseline hides this.
- Minified/resource-shrunk unsigned release build passed. Its manifest has the expected NextSet identity, API 24 minimum/API 37 target, no debug flag and no INTERNET permission.
- The optimized APK retains the original adaptive/monochrome icon and bundled RIFF chime. ZIP alignment and all eight native libraries' ELF LOAD alignment were checked for 16 KiB compatibility. This is packaging verification, not a physical 16 KiB-device test.
- Local documentation/screenshot links, the MIT license and whitespace checks passed. No release signing credentials were generated and nothing was published.

## Audit fixes

- Execution checkpoint, status, snapshot and completed sets are now observed in one Room transaction scoped to one session. This avoids transient mismatched progress and whole-history queries on every execution emission.
- Once an execution command is accepted, its database/alarm transition finishes even if its caller is cancelled by navigation or Activity removal. A cancellation regression exercises this boundary.
- Template child inserts abort and roll back on identifier collisions instead of replacing another template's children. Model validation rejects duplicate/blank identifiers. Legacy session mutation APIs cannot desynchronize a checkpoint owned by the execution engine.
- Reminder invalidation watches session starts/endings rather than each completed-set write. Snapshot decoding is dispatched off the UI thread. Template reordering updates changed positions only.
- Daily reminder intents return to Home even when the app is already on another screen. Intent requests wait for healthy local storage before navigation. Consumed intents do not replay on rotation.
- History selection and rest-editor input survive recreation. Short sessions retain seconds in the displayed duration. Empty history avoids an irrelevant empty calendar.
- Execution provides a merged accessible action target and distinct pause/resume labels. Partial-hold feedback resets immediately. Countdown text fits its available width with Compose text auto-sizing, including system font scaling. Rest-complete red changes immediately to avoid white text fading over a pale background.
- The original vector mark now has legacy, adaptive and themed/monochrome launcher support. Unused preview/Room KTX dependencies and starter keep rules were removed. Machine state, runner scripts, build output and signing files are ignored.
- The repository uses the requested MIT license. README, run instructions, permission explanations and signing/upgrade steps describe actual behavior.

## Automated journey coverage

| Area | Verification |
| --- | --- |
| Empty launch, create, save, weekly assignment, local today's plan, preview | Navigation UI tests |
| Superset creation, movement reorder, unequal sets, final zero-rest override | Template editor UI test and sequence tests |
| Ordinary and unequal superset order, per-set defaults/overrides, zero/final rest | Pure planner/reducer tests |
| Short taps, partial holds, one-second completion, full workout, actual history | Execution UI tests |
| Rest expiry waits at 00:00; rotation, skip, pause/resume, undo, abandonment | Reducer, persistence and execution UI tests |
| Concurrent/stale holds, duplicate insertion/alerts, immutable snapshots | Execution persistence tests |
| Atomic progress observations, cancelled callers, isolated template writes | Release regression tests |
| Process/repository restart, paused rest, expired deadlines, boot/clock changes | Reducer and disk-reopen tests |
| Reminder outliers, midnight, recent-history window, manual timing, suppression, duplicates, DST | Reminder policy tests |
| OS alarm without a screen ticker and one notification per rest | Alarm delivery integration test |
| Missed/manual workout leaves assignments unchanged | Scheduling/persistence tests |
| Sequence wraparound, future start dates, long rest cycles, duration estimates | Planning unit tests |
| Weekly planned/actual volume, deleted template history and timezone boundaries | Planning unit tests and layout regression |
| Weekday, sequence and unsaved start-date picker recreation | Planning UI regression |
| Narrow screens at 200% text size | Planning layout regression |
| Selected-tone channels and missing-tone fallback | Planning UI/notification integration regression |
| Schema 1 → 2 and 2 → 3 preserve records; fresh creation and reopen | Room migration and disk tests |
| Reminder intent, Settings/Home navigation and recreation | Navigation regression tests |

Run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Instrumented tests use a development application installation and can uninstall it. Do not run them on a device holding workout data you need to retain.

## Manual emulator review

Review Home, templates/editor, schedule, preparation, execution, history and settings in both themes. Exercise keyboard entry and small/large text, check that controls remain reachable, and verify red completion is readable. Repository screenshots are real emulator captures with test-entered data.

The release audit also exercised the main screens in light/dark themes, a real first-launch/create/assign/start flow, portrait and landscape at 200% text size, and scrolling the end confirmation at that size. A same-signed debug APK update preserved the active snapshot, template, assignment and dark preference. These are emulator checks, not physical-device results.

Earlier implementation-stage manual checks additionally killed the background process during a rest and rebooted the emulator during another rest: the completion notification and saved remainder recovered without marking another set complete. Current regression tests retain coverage of the underlying clock and persistence policies.

## Physical-device checklist — still required

- [ ] API 24–25 installation, Java-time desugaring, legacy icon and notification settings fallback.
- [ ] At least one current Android device and one manufacturer with aggressive battery policies.
- [ ] Normal, vibrate and silent modes; notification volume zero; DND all/priority/none; hardware with no vibrator.
- [ ] Notification permission denied/granted/revoked; per-channel mute/custom sound; exact-alarm access denied/granted/revoked during rest.
- [ ] Lock the phone during rest, enter idle/Doze, reopen after expiry, verify one alert and acknowledgment-only advancement. Verify force-stop behavior separately: reopening is required by Android.
- [ ] Actual reboot before/after deadline and while paused; unlock to enable credential-protected storage recovery.
- [ ] Local midnight, timezone and wall-clock changes while foreground/background/paused.
- [ ] TalkBack/Switch Access custom actions, keyboard focus, largest font/display scaling and landscape/tablet layouts.
- [ ] Install a same-signed release over a previous version containing templates, schedules, history, preferences and an active session. Do not uninstall; verify all data and restored alarms.
- [ ] Check the maintainer-signed, optimized APK's chime, launcher masks/themed icon and offline operation.

## Lint and limits

No lint baseline or broad issue suppression is used. Remaining warnings may concern deliberately pinned dependency versions and an incompatible third-party Lifecycle custom lint API; affected custom rules are reported as skipped by lint. Do not describe these as application errors or as fully executed rules. Re-evaluate toolchain/library compatibility during a future dependency update.

Alert delivery remains subject to Android permissions, DND, idle quotas and OEM behavior. Exact-alarm denial has an explicitly described inexact fallback. Reboot plus an unrelated wall-clock correction cannot be reconstructed perfectly. No physical audio/haptic, OEM or API 24 test is claimed without running it.

# Execution and reliability

## One plan and one persisted state machine

`WorkoutTemplate.sequence()` creates ordered `Set` and `Rest` steps for standalone exercises and supersets. A null per-set rest uses the applicable block default: between sets, between movements in a round, between rounds, or after the block. An explicit per-set override replaces that default, including zero. Zero rest emits no Rest step. Superset rounds omit movements whose sets are exhausted. Final workout rests are retained deliberately.

The preparation screen is the not-started state: no session exists yet. Start captures an immutable template snapshot and enters EXERCISE. A successful one-second hold records exactly that set and enters the next step. A timer expiration enters REST_DONE, never the next exercise. REST_DONE waits for a hold; skipping rest explicitly advances it. A final acknowledgment or zero-rest final set completes the session. Abandonment is explicit and terminal.

PAUSED remembers its previous phase and frozen timer remainder. Resume restores that phase and, for an active rest, creates a new monotonic deadline from the remainder. Undo goes back to the last completed set and removes that record (and any later records) in the same database transaction. Terminal sessions remain immutable; undo is available while training, including during final rest.

Every meaningful transition is committed with its completed-set insertion/deletion in one Room transaction. Once accepted, the runtime completes the persistence/alarm transition even if the screen caller is cancelled; the active session is observed through one transactional Room relation. Revisions reject stale gestures and timer alarms. Primary keys make completed-set insertion idempotent. Multiple start attempts resume the single active session instead of creating duplicates. User navigation and template edits do not change the captured execution plan.

## Time accounting

Within one boot, `SystemClock.elapsedRealtime()` drives countdowns and total elapsed time. The UI computes `deadline - now`, rounded upward for display; it does not decrement a counter. Only the countdown component updates periodically, and its display ticker stops while not started/visible. Rotation and recomposition do not create deadlines.

Progress stores boot count, monotonic deadline, wall deadline, frozen remaining duration, accumulated elapsed duration and clock anchors. While backgrounded, locked or process-dead, an unpaused rest continues to elapse. Returning after expiry shows REST_DONE with 00:00; it never restarts the rest or automatically marks another set complete. Paused rest stays frozen indefinitely, including across reboot.

Elapsed workout duration includes pauses, background time and device-off time, until completion/abandonment. Within a boot, wall-clock changes cannot affect it. Across reboot there is no surviving Android monotonic clock: the engine uses non-negative wall-time difference for total duration and the saved wall deadline for rest, clamped to the last saved remainder. If the wall clock was also changed across reboot, exact reconstruction is impossible. In particular, a rollback cannot grant more than the saved remainder. Original start/end timestamps remain actual wall timestamps and may appear out of order after a manual clock correction; recorded elapsed duration stays non-negative.

Older active sessions lacking an execution checkpoint resume paused at their first uncompleted set. Newly created execution sessions always have a checkpoint atomically with creation.

## OS alerts and restrictions

The UI and AlarmManager receiver both call the same serialized runtime. Completion is claimed in Room once per rest, then posted with a deterministic notification tag and `onlyAlertOnce`. Stale alarms cannot advance a newer set, replay another completion or mark sets complete. Acknowledging/undoing/ending clears the corresponding rest notification. A crash in the tiny interval between claiming and posting can suppress an alert; no database can atomically commit an Android notification. The stored rest-complete state still recovers correctly.

Android notification channels own sound and vibration. NextSet uses a bundled short offline completion chime, requests no DND bypass and respects permission, channel, volume and ringer restrictions. If notifications are blocked there is no background sound workaround. Successful-hold haptics are also suppressed in silent/DND modes.

A user-granted exact-alarm permission enables `setExactAndAllowWhileIdle` with an elapsed-real-time wakeup deadline. Without permission (or if access changes during scheduling), `setAndAllowWhileIdle` is used and may be delayed. Alarms, not a background coroutine, wake the process. No permanent wake lock or battery-optimization exemption is requested. Android idle quotas, force-stop behavior and manufacturer power policies still apply. Revocation can kill the process and cancel exact alarms; reopening rechecks access and reconstructs the appropriate fallback. Reboot and package-replacement receivers restore future work. Receivers perform bounded asynchronous work and log failures; they never destroy storage to recover.

## Daily reminders

Automatic timing uses distinct start timestamps from at most 30 recent sessions, restricted to the last 60 days. Local minutes are compared using circular distance `min(abs(a-b), 1440-abs(a-b))`. Each sample has weight `1 + exp(-ageDays/14)`: recency matters but a single new sample cannot outweigh two older samples. The observed minute minimizing weighted total circular distance is chosen. Fewer than three samples uses 18:00. A manual minute-of-day replaces learning.

Only one future inexact alarm is scheduled. Delivery revalidates current settings, weekday assignment, date, active/completed sessions and the date delivery ledger. The ledger prevents repeated reminders after restarts or duplicate broadcasts. Refreshes happen after data/settings changes, on app return, after reboot/upgrade, and after clock/timezone changes. DST gaps move forward; overlaps use the first local occurrence. Past reminders are not manufactured on launch. Starts/completions from a manually chosen workout never modify weekday assignments.

## Device validation

Automated tests cover reducers, sequences, clock/reboot recovery, reminder policy, database durability/migrations, concurrent/stale holds, acknowledgment, undo and navigation. Physical-device testing should additionally exercise audible/haptic output in normal/vibrate/silent modes, notification-channel changes and DND, exact-alarm grant/revocation, lock-screen delivery under Doze, OEM battery policies, real reboot and accessibility services. These policies are controlled by Android; passing emulator tests is not a guarantee of unrestricted background delivery on every device.

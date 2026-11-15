# Build and release NextSet

## Reproduce the build

Use the repository wrapper, SDK Platform 37, Build Tools 36.0.0 and the checked-in JDK 25 daemon configuration. Keep your SDK path in ignored `local.properties`. No internet permission is needed by the app; dependency and toolchain downloads need development-machine network access.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Start an emulator/connect a test device before instrumented tests. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Unsigned release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`. Gradle test tasks may uninstall the tested app; use a development device without personal workout data.

Review the [QA checklist](QA.md), test reports and lint report before distribution. Check the generated release manifest for the expected `com.sujoy.nextset` package, API 24 minimum, no debug flag and no INTERNET permission. The package identity is intentionally unchanged from the original project.

## Sign with a maintainer-owned key

No signing credentials are generated, checked in or configured in this repository. Use Android Studio's **Build → Generate Signed App Bundle or APK → APK** flow with your own keystore, or use the SDK tools below. The key path and alias are examples to replace; passwords are requested interactively.

With Build Tools 36.0.0 on your PATH:

```sh
zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release-unsigned.apk
apksigner sign --ks /secure/path/nextset-release.jks --ks-key-alias nextset \
  --out NextSet-1.0.apk app/build/outputs/apk/release/app-release-unsigned.apk
apksigner verify --verbose --print-certs NextSet-1.0.apk
adb install -r NextSet-1.0.apk
```

Gradle already aligns the APK. If alignment needs correction, run `zipalign -P 16 -f -v 4 INPUT.apk ALIGNED.apk` **before signing**, and sign the aligned output. Modifying an APK after signing invalidates its signature. See Android's [apksigner](https://developer.android.com/tools/apksigner) and [zipalign](https://developer.android.com/tools/zipalign) references.

Use a clean test device or an installation signed with the same certificate. A release key cannot update a debug-signed installation. Uninstalling to switch certificates deletes local workouts/history because no export exists yet.

Verify installation, first launch, a short complete workout, an abandoned workout, background/locked rest alerts and reminders on the signed build. Review notification channels and the original chime resource after shrinking. Keep signing keys/passwords outside the repository and back them up securely.

## Safe upgrades

1. Keep `applicationId` and signing identity stable. Increment `versionCode`; set the intended `versionName`.
2. If changing Room entities, increment the database version, export its schema and register a non-destructive migration. Never delete the old schema files or enable destructive fallback.
3. Run all unit, instrumented and migration tests. Install the candidate with `adb install -r` over a same-signed previous version containing templates, assignments, sessions and settings.
4. Confirm that history snapshots, completed sets, schedules and preferences remain intact. Resume an unfinished session; verify its plan is the saved snapshot.
5. Verify reminder/alarm reconstruction after package replacement. Test reboot after user unlock and permission revocation/regrant.
6. Distribute the signed APK, release notes and appropriate source/license files manually only after verification. This project does not publish to GitHub or Google Play.

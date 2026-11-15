# Contributing to NextSet

NextSet is a fully offline Android workout tracker, licensed under MIT. Keep contributions focused on a concrete user problem; discuss major changes in an issue before implementation.

## Development

Open the root folder in Android Studio, install SDK Platform 37 / Build Tools 36.0.0 and use the checked-in Gradle wrapper and JDK configuration. See [README.md](README.md) for run instructions and [docs/RELEASE.md](docs/RELEASE.md) for signing.

Use Kotlin and existing Compose/Material 3 conventions. Keep business logic in the domain/repository layers, state in lifecycle-aware ViewModels and data in Room/DataStore. Favor simple solutions and explicit error handling. Maintain readable contrast, accessible labels/actions, comfortable targets and the exceptionally minimal workout screen.

Do not add accounts, analytics, advertising, network permissions or dependencies without a concrete need consistent with the offline product. Do not rewrite unrelated features.

## Verify a change

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleRelease
```

Use a test emulator/device; instrumented tests can replace/uninstall their app. Add regression tests for behavioral fixes. Timing/notification changes need reducer, persistence/recovery and scheduling tests plus the relevant [device checks](docs/QA.md). Describe exactly what ran and any unverified device behavior in your pull request.

Commit schema exports and migrations together. Preserve old snapshots, stable identifiers and user data. Never enable destructive database fallback or silently rewrite historical records. Changes to existing serialization formats need explicit version handling.

## Pull requests

Explain the concrete trigger and resulting behavior, include relevant verification results, and attach actual screenshots for visual changes. Keep screenshots free of private data; do not fabricate statistics. Formatting should follow the existing Kotlin style. Keep SDK paths, IDE state, build output, runner scripts, signing material and private workout databases out of commits.

By submitting a contribution, you agree to license it under this project's MIT license. Dependencies retain their original licenses. Report vulnerabilities privately to the maintainer through the repository's private security reporting mechanism if enabled; do not invent or use an unlisted contact address.

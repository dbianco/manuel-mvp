# Spec: CI workflow (T021)

## User Scenarios

### Primary user story

As a contributor, I want every pull request against `main` (and every push to `main`) to automatically run the JVM unit test suite and Android Lint, so a red build is visible before anyone merges (the company constitution's "Tests run in CI before merge and a red build blocks the merge").

### Acceptance scenarios

1. **Given** a pull request opened against `main`, **When** the workflow runs, **Then** it checks out the repository (including the `third_party/llama.cpp`/`third_party/whisper.cpp` submodules, so the native build config resolves cleanly even though these tasks don't themselves invoke a native build), sets up JDK 17 and the Android SDK, and runs `./gradlew testDebugUnitTest lintDebug`.
2. **Given** a push directly to `main`, **When** the workflow runs, **Then** it runs the same checks (validates `main` itself stays green, e.g. after a squash-merge).
3. **Given** either job fails, **When** it completes, **Then** the failure is visible as a failed GitHub Actions check on the PR/commit, and test/lint reports are uploaded as build artifacts for inspection.

## Functional Requirements

- **FR-001**: The system MUST add `.github/workflows/ci.yml`, triggered on `pull_request` targeting `main` and on `push` to `main`.
- **FR-002**: The workflow MUST check out the repository with `submodules: recursive` (fetching `third_party/llama.cpp`/`third_party/whisper.cpp`, registered by T002 but not committed as content) — defensive against any Gradle/AGP configuration-phase check of the `externalNativeBuild` CMake path even when the invoked tasks don't themselves build native code.
- **FR-003**: The workflow MUST set up JDK 17 (matching `app/build.gradle.kts`'s `compileOptions`) and an Android SDK environment sufficient to resolve `compileSdk = 37`.
- **FR-004**: The workflow MUST run `./gradlew testDebugUnitTest` (the JVM unit test suite — the ~90 tests written T005-T017) and `./gradlew lintDebug` (Android Lint), matching the task's literal scope ("corriendo unit tests y lint") — it MUST NOT run instrumented tests (`connectedAndroidTest`/`MainScreenTest`, T019), which need a real device/emulator and are out of scope for this workflow.
- **FR-005**: The workflow MUST upload test and lint reports as a build artifact on every run (pass or fail), for inspection without needing to reproduce a failure locally.
- **FR-006**: The workflow MUST use `concurrency` to cancel a superseded run for the same branch/PR (a new push to the same PR cancels its predecessor's in-flight run), avoiding wasted CI minutes.

## Success Criteria

- **SC-001**: `.github/workflows/ci.yml` is valid YAML (checked with a YAML parser) and its structure matches GitHub Actions' documented workflow schema (`on`, `jobs.<id>.runs-on`, `.steps`, etc.) by manual review against well-established, standard action usage (`actions/checkout@v4`, `actions/setup-java@v4`, `android-actions/setup-android@v3`, `gradle/actions/setup-gradle@v4`, `actions/upload-artifact@v4`).
- **SC-002**: This CANNOT be verified by actually running the workflow in this sandbox (no `act`/local GitHub Actions runner is being spun up for this — see Clarifications) — its real correctness is only confirmed once pushed and a live GitHub Actions run executes it.

## Clarifications

- 2026-09-14: **Unverifiable in this sandbox, one tier further than the native/Compose files.** Unlike `whisper_jni.cpp`/`llama_jni.cpp` (reviewed against a real, locally-available header) or `MainScreen.kt` (reviewed against a well-known stable API), this workflow's actual correctness depends on GitHub-hosted runner specifics (preinstalled SDK components, exact action version behaviors) that can only be confirmed by a live run. `docker` is available locally but `act` (a local GitHub Actions runner) is not installed, and installing it plus emulating a GitHub-hosted Ubuntu runner's Android SDK setup accurately is disproportionate effort for this task — the workflow is written to well-established, standard patterns instead and will be validated for real the first time a PR triggers it.
- 2026-09-14: **Whether `testDebugUnitTest`/`lintDebug` actually need the NDK is unconfirmed.** `app/build.gradle.kts` declares `externalNativeBuild { cmake { ... } }`, but unit-test/lint tasks compile against JVM bytecode and don't package native `.so` libraries — they should not depend on the `externalNativeBuildDebug` task tree. This workflow deliberately does NOT install an NDK explicitly (no verified valid NDK version string for this project's fictional `compileSdk = 37`/tomorrow's Android SDK release channel), betting that AGP defers any NDK toolchain resolution until an actual native-build task is invoked. If a live CI run proves this wrong (a "NDK not found" configuration-time failure), the fix is to add an explicit NDK install step referencing whatever NDK version the live error message names.
- 2026-09-14: **Branch protection (making this check *required* before merge) is a separate, human/repo-admin action** (GitHub repo Settings → Branches → protection rules, or an API/Terraform call) — not something a workflow YAML file can configure for itself. This task only adds the workflow that *produces* a pass/fail check; making it block merges is out of scope here and left to the user.
- 2026-09-14: **`push: branches: [main]` is a small addition beyond the task's literal "en cada PR" wording** — validating that `main` itself stays green after a merge is such a standard, low-cost part of "CI on PRs" that omitting it would be unusual; flagged here for transparency rather than silently expanding scope.

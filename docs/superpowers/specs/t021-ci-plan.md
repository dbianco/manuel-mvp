# Plan: CI workflow (T021)

## Technical Context

- Format: GitHub Actions workflow YAML, `.github/workflows/ci.yml`.
- Primary dependencies (GitHub Actions marketplace, all well-established, widely-used, official/verified publishers): `actions/checkout@v4`, `actions/setup-java@v4` (Temurin distribution), `android-actions/setup-android@v3`, `gradle/actions/setup-gradle@v4` (dependency caching + wrapper validation), `actions/upload-artifact@v4`.
- Runner: `ubuntu-latest` (GitHub-hosted).
- Testing tools: none needed to write this file — it IS the CI config.

## Constitution Check

- No personal data in logs: not applicable — CI logs are build/test output only.
- Every outbound HTTP call has an explicit timeout and a retry budget: not applicable at this abstraction level — the workflow's own steps (checkout, dependency downloads) are GitHub-managed infrastructure calls, not application code.
- Database schema changes: not applicable.
- Public API changes: not applicable.
- Secrets come from the environment or the secret manager, never from source: satisfied by construction — this workflow needs no secrets at all (no signing, no publishing, no external service calls).
- Tests run in CI before merge and a red build blocks the merge: this task directly implements the "tests run in CI" half; the "blocks the merge" half needs a repo-admin branch-protection setting, out of scope here (see spec.md's Clarifications).
- Accessibility: not applicable.

## Project Structure

```
.github/workflows/
└── ci.yml   # new
```

## Research

- **`gradle/actions/setup-gradle@v4` over manually caching `~/.gradle`**: this action (the current officially-recommended successor to the deprecated `gradle/gradle-build-action`) handles Gradle dependency/wrapper caching and also validates the `gradle-wrapper.jar` checksum against a known-good list, a supply-chain-security good practice directly relevant to the company constitution's dependency-handling concerns.
- **`android-actions/setup-android@v3` over manually scripting `sdkmanager` calls**: this action installs Android cmdline-tools and accepts SDK licenses; it deliberately does NOT hardcode specific `platforms;android-NN`/`build-tools;X.Y.Z` component strings here, since AGP's own automatic SDK component download (the default behavior in modern AGP, given `sdkmanager`+accepted licenses are present) can fetch whatever `compileSdk`/build-tools version the project's `build.gradle.kts` actually declares — this avoids hardcoding a component version string for `compileSdk = 37` that this session has no way to verify actually exists as a downloadable SDK component.
- **No explicit NDK installation step**: see spec.md's Clarifications — deliberately deferred, on the bet that `testDebugUnitTest`/`lintDebug` don't need the native toolchain; flagged as the most likely single point of failure on the first live run, with a named fix if it turns out wrong.
- **`concurrency` block**: `group: ci-${{ github.workflow }}-${{ github.ref }}`, `cancel-in-progress: true` — a new push to the same PR branch cancels the previous run's job rather than letting both run to completion, standard CI-cost-saving practice.
- **Report upload always runs** (`if: always()`), not just on failure, so a passing run's reports (e.g. lint's informational findings even on a technically-passing build) are also inspectable without needing to fail intentionally to see them.

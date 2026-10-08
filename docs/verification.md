# Verification — 2026-10-08

Host: macOS ARM64. Kotlin Toolchain 0.13.0, Kotlin 2.4.20,
kotlinx-benchmark 0.5.0, JMH 1.37, JDK 25.0.4 and Node.js 24.18.0.

## Executed locally

- `./kotlin test -m benchmark`: 6 tests passed.
- `./kotlin do benchmarkSmoke -m jvm`: 6 parameterized benchmark results.
- `./kotlin do benchmarkSmoke -m macos`: 6 parameterized benchmark results.
- `./kotlin do benchmarkSmoke -m js`: 6 parameterized benchmark results.
- `./kotlin do benchmarkSmoke -m wasm-js`: 6 parameterized benchmark results.
- `./kotlin do benchmarkSmoke -m wasm-wasi`: 6 parameterized benchmark results.
- `kotlinr scripts/verify-reports.main.kts jvm macos js wasm-js wasm-wasi`: all
  benchmark names, parameter combinations, warmup/measurement counts, units and
  finite raw samples matched the expected fixture.
- `kotlinr scripts/test-integration.main.kts`: independently copied consumer passed
  with an anchored name filter, overridden parameter and spaces in its directory.
  A mixed passing/failing JS suite returned failure and left no successful report
  or metadata from the previous invocation.
- `ktc-plugins validate --project-dir <project>`: producer manifest accepted;
  the plugin is self-contained and includes its template, KSP service and licenses.

Local build logs are in `build/verification/` (ignored by Git).
The smoke profile intentionally does not establish a performance baseline.

## CI coverage configured, not executed in this session

The workflow defines JVM + host Native runs on Linux x64, macOS ARM64 and
Windows x64, plus JS, Wasm JS and Wasm WASI on Node.js 24/Linux.
Linux and Windows native binaries were not executed locally. Linux ARM64 is
accepted as a runner target but has no dedicated CI runner in this initial setup.
No claims are made about iOS/tvOS/watchOS/Android device execution or browser engines.

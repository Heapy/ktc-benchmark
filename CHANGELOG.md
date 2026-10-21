# Changelog

## 0.1.2 — 2026-10-21

- Forward child stdout and stderr through the parent Kotlin Toolchain task logger.
- Drain both output streams concurrently, preserving final output and process timeouts.
- Add regression tests for captured output, large streams, failed processes and timeouts.

## 0.1.0 — 2026-10-08

- Kotlin Toolchain build plugin and KSP processor for JVM/JMH, desktop Native,
  Kotlin/JS, Wasm JS and Wasm WASI benchmarks.
- Shared benchmark sources, configuration, explicit benchmark/smoke commands,
  isolated target builds, parameterized execution and JSON reports.

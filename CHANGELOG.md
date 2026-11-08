# Changelog

## 0.2.0 — 2026-11-08

- Update KotlinPoet to 2.4.0, KSP API and processor runtime to 2.3.13, and kotlinx.serialization to 1.12.0.
- Keep kotlinx-benchmark 0.5.0 and JMH 1.37; benchmark commands and report format are unchanged.

## 0.1.2 — 2026-10-21

- Forward child stdout and stderr through the parent Kotlin Toolchain task logger.
- Drain both output streams concurrently, preserving final output and process timeouts.
- Add regression tests for captured output, large streams, failed processes and timeouts.

## 0.1.0 — 2026-10-08

- Kotlin Toolchain build plugin and KSP processor for JVM/JMH, desktop Native,
  Kotlin/JS, Wasm JS and Wasm WASI benchmarks.
- Shared benchmark sources, configuration, explicit benchmark/smoke commands,
  isolated target builds, parameterized execution and JSON reports.

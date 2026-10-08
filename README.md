# ktc-benchmark

Multiplatform benchmarks for **Kotlin Toolchain 0.13.0**, using
[`kotlinx-benchmark` 0.5.0](https://github.com/Kotlin/kotlinx-benchmark/tree/v0.5.0)
and **JMH 1.37** on the JVM. No Gradle build or Gradle plugin is involved.

Write benchmarks once in a KMP library using `kotlinx.benchmark` annotations.
KSP generates the platform harness; small application modules link and run it.
The build plugin uses the package `io.heapy.ktc.plugins.benchmark`.

| Target | Engine | Example runner |
|---|---|---|
| JVM | JMH, generated Java harness, forked JVMs | `jvm` |
| macOS ARM64 | kotlinx-benchmark Native, process per benchmark/parameter combination | `macos` |
| Linux x64 / ARM64 | kotlinx-benchmark Native | `linux` / a runner targeting `linuxArm64` |
| Windows x64 | kotlinx-benchmark Native | `windows` |
| Kotlin/JS | kotlinx-benchmark built-in runner on Node.js | `js` |
| Kotlin/Wasm JS | kotlinx-benchmark on Node.js | `wasm-js` |
| Kotlin/Wasm WASI | kotlinx-benchmark on Node.js WASI | `wasm-wasi` |

Run Native executables on their matching host. Node.js **24** is the web runner
baseline. The Toolchain provisions the compilers and JDK; Node must be on PATH
or configured with `nodeExecutable`. This is an independent integration, not an
official JetBrains plugin. Apache-2.0 licensed; see [NOTICE](NOTICE) for upstream
generator attribution.

## Try it

```sh
./kotlin test -m benchmark
./kotlin do benchmarkSmoke -m jvm
./kotlin do benchmarkSmoke -m macos       # macOS ARM64
./kotlin do benchmarkSmoke -m js
./kotlin do benchmarkSmoke -m wasm-js
./kotlin do benchmarkSmoke -m wasm-wasi
python3 scripts/verify-reports.py jvm macos js wasm-js wasm-wasi
```

On Linux, use `-m linux`. On Windows use `kotlin.bat` and `-m windows`.
`benchmarkSmoke` uses one warmup and one measurement iteration of 50 ms. It
tests the integration, **not performance**. For measurements run:

```sh
./kotlin do benchmark -m jvm
```

Defaults are three warmup iterations, five measurement iterations, one second
per iteration, average time in nanoseconds, and one JVM fork/thread. Benchmark
commands always execute, including when the sources have not changed. They are
explicit commands and do not run during ordinary builds or tests.

Results are written to `build/tasks/_<runner>_benchmark@benchmark/results.json`
(or `_benchmarkSmoke@benchmark/`), with raw measurement samples and a companion
`run.json` recording the target and profile. Each invocation replaces that
command's previous report. Copy reports elsewhere before another run when you
want a history. Compare results under controlled hardware/runtime conditions;
JMH and non-JVM engines have different measurement and statistical behavior.

## Install

From your consumer project, install the tagged source release with
[ktc-plugins](https://github.com/Heapy/ktc-plugins):

```sh
./ktc-plugins add Heapy/ktc-benchmark --tag v0.1.0
```

Then configure the benchmark library and runner modules as shown below. Enable
`benchmark` on the executable runners, and apply the template to the benchmark
library. Commit the installed sources and installer lockfile.

For manual installation, copy `plugins/benchmark/`, `LICENSE`, and `NOTICE` into your project. Keep the
plugin directory intact: it contains the build plugin, KSP processor service,
JVM runner template and reusable module template. No external local module is
required. `ktc-plugin.yaml` declares the source distribution for `ktc-plugins`.

Register the plugin, benchmark library and executable runners:

```yaml
# project.yaml
modules:
  - benchmarks
  - plugins/benchmark
  - runners/*
plugins:
  - //plugins/benchmark
```

Create a benchmark library and choose your targets:

```yaml
# benchmarks/module.yaml
product:
  type: kmp/lib
  platforms: [jvm, macosArm64, js, wasmJs, wasmWasi]
apply:
  - //plugins/benchmark/benchmark.module-template.yaml
dependencies:
  - //your-library
```

The template supplies the runtime dependency, local KSP processor, JVM JMH
dependencies, all-open configuration and Wasm processor options. Keep its
version pins aligned with the plugin. Put annotated benchmark classes in this
module's `src/`; they may call code from your production dependencies. Annotation
discovery covers these source declarations, not arbitrary compiled dependency
classes. Use `src@jvm`, `src@native`, etc. for target-specific benchmarks.

```kotlin
package example

import kotlinx.benchmark.*

@State(Scope.Benchmark)
open class SumBenchmark {
    @Param("16", "64")
    var size: Int = 0

    private lateinit var values: List<Int>

    @Setup
    fun prepare() { values = List(size) { it } }

    @Benchmark
    fun sum(): Int = values.sum()
}
```

Create one application runner for each target:

```yaml
# runners/jvm/module.yaml
product: jvm/app
dependencies:
  - //benchmarks
plugins:
  benchmark:
    enabled: true
    platform: jvm
```

```kotlin
// runners/jvm/src/main.kt (the same forwarding entry point on every target)
fun main(args: Array<String>) {
    io.heapy.ktc.plugins.benchmark.generated.main(args)
}
```

Other products/settings: `macos/app` + `macosArm64`, `linux/app` + `linuxX64`,
`windows/app` + `mingwX64`, `js/app` + `js`, `wasm-js/app` + `wasmJs`, and
`wasm-wasi/app` + `wasmWasi`. For Linux ARM64 use
`product: {type: linux/app, platforms: [linuxArm64]}` and `platform: linuxArm64`.
The library must also declare the runner's target. Runner module names are their
directory basenames and must be unique. See [runners](runners) for working files.

## Configuration

Settings go in each runner's `plugins.benchmark` block:

```yaml
plugins:
  benchmark:
    enabled: true
    platform: jvm
    warmups: 3
    iterations: 5
    iterationTime: 1
    iterationTimeUnit: s
    outputTimeUnit: ns
    mode: avgt
    includes: ['.*SumBenchmark.*']
    excludes: []
    params:
      size: ['16', '64']
    jvmForks: 1
    jvmThreads: 1
    jvmArgs: [-Xms512m, -Xmx512m]
    nodeExecutable: node
    timeoutSeconds: 3600
```

`platform` is required. The other defaults are shown above, except that
`includes`, `params`, and `jvmArgs` default to empty collections. Units: `ns`,
`us`, `ms`, `s`, `m`; modes: `avgt` and `thrpt`. Filters are regular expressions.
Configuration values must be single-line; parameter overrides cannot contain commas.
The Native runtime also rejects `=` and commas in annotation parameter defaults.
Keep at least one warmup: the Native engine uses it to calibrate iteration work.
Timing settings override annotation defaults, consistently across targets.
`jvmForks`, `jvmThreads`, and `jvmArgs` affect JVM only. Native runs one isolated
process for each selected benchmark and parameter combination. `timeoutSeconds`
limits each child process, including compilation and individual Native runs.

## How it integrates with Toolchain

Toolchain 0.13's public custom-task artifact API only exposes JVM artifacts.
This plugin therefore builds/runs the selected application through the project's
own `kotlin` wrapper, with an explicit separate `--build-dir` inside the task's
output directory. It never invokes `benchmark` recursively. KSP uses Toolchain's
normal compilation pipeline on each target. Native and web builds use the release
variant. JVM uses the normal classpath launcher (JVM bytecode optimization is
unchanged), then JMH compiles its generated harness using the runner's full JDK
and forks measurement JVMs.

Non-JVM artifact discovery is tied to **Toolchain 0.13.0**. Wasm WASI adapts a
copy of the generated launcher to preopen only the report/configuration directory.
Review these contracts when upgrading Toolchain. Builds for different commands
have separate outputs and incremental state; downloaded toolchains and dependencies
still share the usual caches.

## Scope

This first version covers the JVM, desktop Native, JS and both Wasm families.
It is not a complete implementation of every kotlinx-benchmark Gradle DSL option.
There is no mobile-device launcher, browser/custom-engine runner, benchmark.js
engine, async JS benchmark support, Native per-iteration forks, comparison/threshold
command, or configurable JMH profilers. Non-JVM benchmarks use the upstream
portable annotations and restrictions: public zero-argument state constructors,
public benchmark methods with either no parameters or one `Blackhole`, and
primitive/string `@Param` properties. Non-JVM state classes must be top-level.
The standard built-in runner is synchronous.

The example exercises parameter combinations, setup/teardown, return-value and
explicit blackhole consumption, and strings with spaces/quotes/dollar signs.
CI runs plugin tests, real host Native and JVM benchmarks, and all three web targets;
report verification checks the expected benchmark identities, parameters and raw
samples. Smoke numbers are deliberately not used as a performance baseline.

Upstream references: [benchmark runtime and generator](https://github.com/Kotlin/kotlinx-benchmark/tree/v0.5.0),
[JMH](https://github.com/openjdk/jmh/tree/1.37),
[Kotlin Toolchain](https://github.com/JetBrains/kotlin-toolchain/tree/v0.13.0).

See [verification evidence](docs/verification.md) for locally executed checks and CI-only coverage.

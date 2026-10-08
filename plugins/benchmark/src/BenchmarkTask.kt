package io.heapy.ktc.plugins.benchmark

import kotlinx.serialization.json.*
import org.jetbrains.amper.plugins.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun runBenchmark(
    @Input(inferTaskDependency = false) projectDir: Path,
    moduleName: String,
    settings: BenchmarkSettings,
    @Output outputDir: Path,
    smoke: Boolean,
) {
    val output = outputDir.toAbsolutePath().normalize().createDirectories()
    val nested = output.resolve("toolchain-build")
    val config = output.resolve("benchmark.conf")
    val report = output.resolve("results.pending.json")
    val publishedReport = output.resolve("results.json")
    publishedReport.deleteIfExists()
    report.deleteIfExists()
    output.resolve("run.json").deleteIfExists()
    validate(settings)
    require(moduleName.matches(Regex("[A-Za-z0-9_.-]+"))) { "Unsupported runner module name: $moduleName" }
    val wrapper = projectDir.resolve(if (isWindows()) "kotlin.bat" else "kotlin")
    val versionLine = wrapper.readLines().firstOrNull { it.startsWith(if (isWindows()) "set kotlin_cli_version=" else "kotlin_cli_version=") }
    check(versionLine?.substringAfter('=')?.trim() == "0.13.0") {
        "ktc-benchmark currently supports the Kotlin Toolchain 0.13.0 wrapper; review artifact contracts before upgrading"
    }
    val cli = toolchain(projectDir)
    val options = listOf("--project-dir", projectDir.toString(), "--build-dir", nested.toString(),
        "-m", moduleName, "--platform", settings.platform)
    execute(cli + "build" + options + (if (settings.platform == "jvm") emptyList() else listOf("--variant", "release")),
        projectDir, settings.timeoutSeconds)
    val manifest = findArtifact(nested.resolve("generated")) { it.name == "ktc-benchmark-manifest.json" }
    val selection = selectBenchmarks(manifest, settings)
    config.writeText(configuration(settings, smoke,
        if (settings.platform == "wasmWasi") "/benchmark/results.pending.json" else report.toString(), selection.includes, emptyList()))
    if (settings.platform == "jvm") {
        execute(cli + "run" + options + listOf("--working-dir", output.toString(), "--", config.toString()),
            projectDir, settings.timeoutSeconds)
    } else {
        when (settings.platform) {
            "js", "wasmJs", "wasmWasi" -> runWeb(nested, output, moduleName, settings)
            else -> runNative(nested, output, moduleName, settings)
        }
    }
    verifyReport(report)
    verifySelection(report, selection)
    Files.move(report, publishedReport, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    output.resolve("run.json").writeText(buildJsonObject {
        put("module", moduleName)
        put("platform", settings.platform)
        put("profile", if (smoke) "smoke" else "main")
        put("toolchain", "0.13.0")
        put("runtime", "0.5.0")
        put("variant", if (settings.platform == "jvm") "default" else "release")
        put("createdAt", java.time.Instant.now().toString())
    }.toString())
    println("Benchmark report: $publishedReport")
    if (smoke) println("Smoke profile verifies execution only; do not use these timings for performance comparisons.")
}

internal fun findArtifact(build: Path, predicate: (Path) -> Boolean): Path {
    val found = Files.walk(build).use { paths ->
        paths.filter { it.isRegularFile() && predicate(it) }.toList()
    }
    check(found.size == 1) { "Expected one linked benchmark artifact, found ${found.size}: $found" }
    return found.single()
}

private fun runWeb(build: Path, output: Path, moduleName: String, settings: BenchmarkSettings) {
    val artifact = findArtifact(build) {
        it.parent.name == "kotlin-output" && it.name in setOf("$moduleName.js", "$moduleName.mjs")
    }
    if (settings.platform == "wasmWasi") {
        val original = artifact.readText()
        val marker = "new WASI({"
        check(original.split(marker).size == 2) { "Unknown Kotlin 0.13 WASI launcher format: $artifact" }
        val directoryLiteral = JsonPrimitive(output.toString()).toString()
        val patched = artifact.resolveSibling("benchmark-wasi.mjs")
        patched.writeText(original.replace(marker, "$marker preopens: { '/benchmark/': $directoryLiteral },"))
        execute(listOf(settings.nodeExecutable, patched.toString(), "/benchmark/benchmark.conf"), output, settings.timeoutSeconds)
    } else {
        // Upstream JS splits process.argv on spaces. Use a relative, whitespace-free config argument.
        execute(listOf(settings.nodeExecutable, artifact.toString(), "benchmark.conf"), output, settings.timeoutSeconds)
    }
}

@OptIn(ExperimentalPathApi::class)
private fun runNative(build: Path, output: Path, moduleName: String, settings: BenchmarkSettings) {
    val suffix = if (settings.platform == "mingwX64") ".exe" else ".kexe"
    val executable = findArtifact(build) { it.name == "$moduleName$suffix" && it.none { part -> part.toString().endsWith(".dSYM") } }
    val descriptions = output.resolve("descriptions")
    descriptions.deleteRecursively()
    descriptions.createDirectories()
    val progress = output.resolve("progress.xml").also { it.deleteIfExists() }
    val base = listOf(executable.toString(), output.resolve("benchmark.conf").toString())
    execute(base + listOf("--list", progress.toString(), descriptions.toString()), output, settings.timeoutSeconds)
    val runs = Files.list(descriptions).use { it.filter { path -> path.isRegularFile() }.sorted().toList() }
    check(runs.isNotEmpty()) { "No benchmarks matched the configured filters" }
    val allResults = output.resolve("samples.txt")
    val samples = runs.mapIndexed { index, run ->
        val result = output.resolve("samples-$index.txt").also { it.deleteIfExists() }
        execute(base + listOf("--benchmark", progress.toString(), run.toString(), result.toString()), output, settings.timeoutSeconds)
        check(result.exists() && result.readText().isNotBlank()) { "Benchmark failed: ${run.readText()}. See $progress" }
        val values = result.readText()
        check(values.split(',').all { it.trim().toDoubleOrNull()?.isFinite() == true }) { "Invalid benchmark samples: $values" }
        "$run: ${values.split(',').joinToString(", ") { it.trim() }}"
    }
    allResults.writeText(samples.joinToString("\n"))
    execute(base + listOf("--store-results", progress.toString(), allResults.toString()), output, settings.timeoutSeconds)
}

internal fun verifyReport(report: Path) {
    check(report.isRegularFile()) { "Benchmark runtime did not write $report" }
    val results = Json.parseToJsonElement(report.readText()).jsonArray
    check(results.isNotEmpty()) { "No benchmark results; check includes/excludes and runtime diagnostics" }
    results.forEach { entry ->
        val metric = entry.jsonObject.getValue("primaryMetric").jsonObject
        check(metric.getValue("score").jsonPrimitive.doubleOrNull?.isFinite() == true) { "Non-finite benchmark score" }
        val raw = metric.getValue("rawData").jsonArray
        check(raw.isNotEmpty() && raw.all { row -> row.jsonArray.isNotEmpty() && row.jsonArray.all { it.jsonPrimitive.doubleOrNull?.isFinite() == true } }) { "Missing or invalid benchmark measurement samples" }
    }
}

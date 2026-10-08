#!/usr/bin/env kotlinr
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0")

import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

val repo = __FILE__.canonicalFile.parentFile.parentFile
val windows = System.getProperty("os.name").startsWith("Windows")

fun temporary(prefix: String, block: (File) -> Unit) {
    val directory = Files.createTempDirectory(prefix).toFile()
    try { block(directory) } finally { directory.deleteRecursively() }
}

fun write(root: File, path: String, text: String) {
    root.resolve(path).apply { parentFile.mkdirs(); writeText(text) }
}

fun copy(source: File, destination: File) {
    check(source.copyRecursively(destination, overwrite = true)) { "Could not copy $source to $destination" }
    if (!windows) {
        source.walkTopDown().filter { it.isFile && it.canExecute() }.forEach { file ->
            val target = if (source.isDirectory) destination.resolve(file.relativeTo(source)) else destination
            check(target.setExecutable(true, false)) { "Could not preserve executable permission: $target" }
        }
    }
}

data class CommandResult(val exitCode: Int, val output: String)
fun command(directory: File, arguments: List<String>, environment: Map<String, String?> = emptyMap(), timeout: Long = 600): CommandResult {
    val log = Files.createTempFile("ktc-command-", ".log").toFile()
    try {
        val process = ProcessBuilder(arguments).directory(directory).redirectErrorStream(true).redirectOutput(log).apply {
            environment.forEach { (key, value) -> if (value == null) environment().remove(key) else environment()[key] = value }
        }.start()
        try {
            check(process.waitFor(timeout, TimeUnit.SECONDS)) { "Timed out: $arguments\n${log.readText()}" }
            return CommandResult(process.exitValue(), log.readText())
        } finally {
            if (process.isAlive) {
                process.descendants().use { children -> children.forEach { it.destroyForcibly() } }
                process.destroyForcibly().waitFor()
            }
        }
    } finally { log.delete() }
}

fun toolchain(project: File, vararg arguments: String, succeeds: Boolean = true, diagnostic: String? = null): String {
    val wrapper = project.resolve(if (windows) "kotlin.bat" else "kotlin").absolutePath
    val invocation = if (windows) listOf("cmd.exe", "/c", wrapper) else listOf("sh", wrapper)
    val result = command(project, invocation + arguments)
    check((result.exitCode == 0) == succeeds) { "Unexpected exit ${result.exitCode}: ${arguments.toList()}\n${result.output}" }
    check(diagnostic == null || diagnostic in result.output) { "Missing diagnostic $diagnostic:\n${result.output}" }
    println("PASS: ${arguments.joinToString(" ")} (${if (succeeds) "success" else "expected failure"})")
    return result.output
}

temporary("ktc benchmark consumer ") { root ->
    for (name in listOf("plugins", "benchmarks", "runners/js", "kotlin", "kotlin.bat", "LICENSE", "NOTICE")) copy(repo.resolve(name), root.resolve(name))
    write(root, "project.yaml", "modules: [benchmarks, plugins/benchmark, runners/js]\nplugins: [//plugins/benchmark]\n")
    val module = root.resolve("runners/js/module.yaml")
    module.appendText("    includes: ['^sample\\.CollectionBenchmark\\.loop$']\n    params:\n      size: ['32']\n")
    toolchain(root, "do", "benchmarkSmoke", "-m", "js")
    val output = root.resolve("build/tasks/_js_benchmarkSmoke@benchmark")
    val reports = Json.parseToJsonElement(output.resolve("results.json").readText()).jsonArray
    check(reports.size == 1 && reports.single().jsonObject.getValue("params").jsonObject == buildJsonObject { put("size", "32") }) { reports }
    println("Copied consumer: anchored filter, parameter override and paths with spaces passed")
    write(root, "benchmarks/src/FailureBenchmark.kt", """
        package sample
        import kotlinx.benchmark.*
        @State(Scope.Benchmark)
        open class FailureBenchmark {
            @Benchmark fun passes(): Int = 42
            @Benchmark fun fails(): Int = error("expected benchmark failure")
        }
    """.trimIndent() + "\n")
    module.writeText(module.readText().substringBefore("    includes:") + "    includes: ['^sample\\.FailureBenchmark\\..*$']\n")
    toolchain(root, "do", "benchmarkSmoke", "-m", "js", succeeds = false, diagnostic = "Incomplete benchmark report")
    check(!output.resolve("results.json").exists()) { "Stale success report survived a failed invocation" }
    check(!output.resolve("run.json").exists()) { "Stale success metadata survived a failed invocation" }
    println("Partially failed runtime: nonzero status and no stale success report")
}

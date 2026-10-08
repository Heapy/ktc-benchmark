package io.heapy.ktc.plugins.benchmark

import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal fun execute(command: List<String>, directory: Path, timeoutSeconds: Int) {
    val process = ProcessBuilder(command).directory(directory.toFile()).inheritIO().start()
    try {
        check(process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)) {
            "Process timed out after $timeoutSeconds seconds: ${command.first()}"
        }
        check(process.exitValue() == 0) { "Process failed (${process.exitValue()}): ${command.take(4).joinToString(" ")}" }
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        throw exception
    } finally {
        if (process.isAlive) {
            try {
                val children = process.descendants().use { it.toList() }
                children.asReversed().forEach { it.destroyForcibly() }
            } finally {
                process.destroyForcibly()
            }
        }
    }
}

internal fun toolchain(projectDir: Path): List<String> = if (isWindows()) {
    listOf("cmd.exe", "/d", "/c", projectDir.resolve("kotlin.bat").toString())
} else {
    listOf(projectDir.resolve("kotlin").toString())
}

internal fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

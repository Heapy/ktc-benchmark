package io.heapy.ktc.plugins.benchmark

import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal fun execute(command: List<String>, directory: Path, timeoutSeconds: Int) {
    // inheritIO bypasses the System streams intercepted by the Toolchain task logger.
    val stdout = System.out
    val stderr = System.err
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds.toLong())
    val process = ProcessBuilder(command).directory(directory.toFile())
        .redirectInput(ProcessBuilder.Redirect.INHERIT).start()
    val readers = Executors.newFixedThreadPool(2) { action ->
        Thread(action, "benchmark-process-output").apply { isDaemon = true }
    }
    try {
        val output = listOf(
            readers.submit { forwardOutput(process.inputStream, stdout) },
            readers.submit { forwardOutput(process.errorStream, stderr) },
        )
        check(process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)) {
            "Process timed out after $timeoutSeconds seconds: ${command.first()}"
        }
        // Drain both pipes before returning or reporting a non-zero exit, including unterminated lines.
        output.forEach { it.get((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS) }
        check(process.exitValue() == 0) { "Process failed (${process.exitValue()}): ${command.take(4).joinToString(" ")}" }
    } catch (exception: TimeoutException) {
        throw IllegalStateException("Process output timed out after $timeoutSeconds seconds: ${command.first()}", exception)
    } catch (exception: ExecutionException) {
        throw IllegalStateException("Cannot forward process output: ${command.first()}", exception.cause)
    } catch (exception: InterruptedException) {
        Thread.currentThread().interrupt()
        throw exception
    } finally {
        try {
            if (process.isAlive) {
                try {
                    val children = process.descendants().use { it.toList() }
                    children.asReversed().forEach { it.destroyForcibly() }
                } finally {
                    process.destroyForcibly()
                }
            }
        } finally {
            readers.shutdownNow()
            process.inputStream.close()
            process.errorStream.close()
        }
    }
}

private fun forwardOutput(source: InputStream, destination: PrintStream) {
    source.use {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            destination.write(buffer, 0, count)
            destination.flush()
        }
    }
}

internal fun toolchain(projectDir: Path): List<String> = if (isWindows()) {
    listOf("cmd.exe", "/d", "/c", projectDir.resolve("kotlin.bat").toString())
} else {
    listOf(projectDir.resolve("kotlin").toString())
}

internal fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows")

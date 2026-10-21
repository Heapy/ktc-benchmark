package io.heapy.ktc.plugins.benchmark

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.test.*

class ProcessesTest {
    @Test
    fun forwardsBothStreamsToTheCurrentSystemStreams() {
        val (out, err) = captureOutput { execute(childCommand("output"), Path.of("."), 30) }
        assertEquals("stdout: привет\nlast stdout", out)
        assertEquals("stderr: ошибка\nlast stderr", err)
    }

    @Test
    fun drainsBothPipesWithoutBlockingOnLargeOutput() {
        val (out, err) = captureOutput { execute(childCommand("large"), Path.of("."), 30) }
        assertEquals("o".repeat(1_048_576), out)
        assertEquals("e".repeat(1_048_576), err)
    }

    @Test
    fun forwardsDiagnosticsBeforeReportingFailure() {
        val (out, err) = captureOutput {
            val failure = assertFailsWith<IllegalStateException> {
                execute(childCommand("failure"), Path.of("."), 30)
            }
            assertContains(failure.message.orEmpty(), "Process failed (7)")
        }
        assertEquals("stdout: привет\nlast stdout", out)
        assertEquals("stderr: ошибка\nlast stderr", err)
    }

    @Test
    fun timeoutStillStopsAProcessWithOpenOutputPipes() {
        captureOutput {
            val failure = assertFailsWith<IllegalStateException> {
                execute(childCommand("sleep"), Path.of("."), 2)
            }
            assertContains(failure.message.orEmpty(), "Process timed out")
        }
    }

    private fun childCommand(mode: String): List<String> = listOf(
        Path.of(System.getProperty("java.home"), "bin", if (isWindows()) "java.exe" else "java").toString(),
        "-cp", System.getProperty("java.class.path").split(File.pathSeparator)
            .joinToString(File.pathSeparator) { Path.of(it).toAbsolutePath().toString() },
        ProcessOutputFixture::class.java.name, mode,
    )

    private fun captureOutput(action: () -> Unit): Pair<String, String> {
        val originalOut = System.out
        val originalErr = System.err
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        PrintStream(out, true, Charsets.UTF_8).use { stdout ->
            PrintStream(err, true, Charsets.UTF_8).use { stderr ->
                try {
                    System.setOut(stdout)
                    System.setErr(stderr)
                    action()
                } finally {
                    System.setOut(originalOut)
                    System.setErr(originalErr)
                }
            }
        }
        return out.toString(Charsets.UTF_8) to err.toString(Charsets.UTF_8)
    }
}

object ProcessOutputFixture {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.single() == "large") {
            repeat(128) {
                System.out.print("o".repeat(8192))
                System.err.print("e".repeat(8192))
            }
            return
        }
        System.out.print("stdout: привет\nlast stdout")
        System.err.print("stderr: ошибка\nlast stderr")
        when (args.single()) {
            "failure" -> exitProcess(7)
            "sleep" -> Thread.sleep(60_000)
        }
    }
}

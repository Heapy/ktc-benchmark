package io.heapy.ktc.plugins.benchmark

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.*

class SelectionTest {
    private val manifest = """[
      {"name":"sample.Suite.fast","runtimeName":"sample.Suite.sample.Suite.fast","params":{"size":["16","64"]}},
      {"name":"sample.Suite.slow","runtimeName":"sample.Suite.sample.Suite.slow","params":{"size":["16","64"]}}
    ]"""

    @Test
    fun anchoredFiltersUsePublicNamesAndParameterOverrides() {
        val file = Files.createTempFile("benchmarks", ".json")
        try {
            file.writeText(manifest)
            val settings = object : BenchmarkSettings {
                override val platform = "js"
                override val includes = listOf("^sample\\.Suite\\.fast$")
                override val params = mapOf("size" to listOf("32"))
            }
            val selection = selectBenchmarks(file, settings)
            assertEquals(setOf(BenchmarkCase("sample.Suite.fast", mapOf("size" to "32"))), selection.cases)
            assertEquals(listOf("^sample\\.Suite\\.sample\\.Suite\\.fast$"), selection.includes)
            assertFalse(selection.includes.single().contains("\\Q"))
        } finally { Files.delete(file) }
    }

    @Test
    fun partialAndDuplicateReportsFailEvenWhenRuntimeReturnsSuccess() {
        val file = Files.createTempFile("benchmarks", ".json")
        val selection = Selection(emptyList(), setOf(
            BenchmarkCase("a", mapOf("size" to "16")), BenchmarkCase("b", emptyMap())))
        try {
            file.writeText("""[{"benchmark":"a","params":{"size":"16"}}]""")
            assertFailsWith<IllegalStateException> { verifySelection(file, selection) }
            file.writeText("""[{"benchmark":"a","params":{"size":"16"}},{"benchmark":"a","params":{"size":"16"}}]""")
            assertFailsWith<IllegalStateException> { verifySelection(file, selection) }
            file.writeText("""[{"benchmark":"a","params":{"size":"16"}},{"benchmark":"b"}]""")
            verifySelection(file, selection)
        } finally { Files.delete(file) }
    }

    @Test
    fun unmatchedFiltersAndUnknownParametersFail() {
        val file = Files.createTempFile("benchmarks", ".json")
        try {
            file.writeText(manifest)
            assertFailsWith<IllegalStateException> {
                selectBenchmarks(file, object : BenchmarkSettings {
                    override val platform = "jvm"
                    override val includes = listOf("absent")
                })
            }
            assertFailsWith<IllegalArgumentException> {
                selectBenchmarks(file, object : BenchmarkSettings {
                    override val platform = "jvm"
                    override val params = mapOf("typo" to listOf("1"))
                })
            }
        } finally { Files.delete(file) }
    }
}

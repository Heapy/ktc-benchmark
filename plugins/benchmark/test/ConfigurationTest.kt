package io.heapy.ktc.plugins.benchmark

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.*

class ConfigurationTest {
    @Test
    fun rejectsInvalidConfigurationBeforeStartingProcesses() {
        val invalid = listOf(
            object : BenchmarkSettings { override val platform = "android" },
            object : BenchmarkSettings { override val platform = "jvm"; override val warmups = 0 },
            object : BenchmarkSettings { override val platform = "jvm"; override val jvmForks = 0 },
            object : BenchmarkSettings { override val platform = "jvm"; override val iterations = -1 },
            object : BenchmarkSettings { override val platform = "jvm"; override val includes = listOf("[") },
            object : BenchmarkSettings { override val platform = "js"; override val includes = listOf("x\nmode:thrpt") },
            object : BenchmarkSettings { override val platform = "js"; override val params = mapOf("size" to emptyList<String>()) },
        )
        invalid.forEach { settings -> assertFailsWith<IllegalArgumentException> { validate(settings) } }
    }

    @Test
    fun smokeOverridesDurationsButRetainsFiltersAndParameters() {
        val settings = object : BenchmarkSettings {
            override val platform = "macosArm64"
            override val includes = listOf("sample.*")
            override val excludes = listOf(".*slow.*")
            override val params = mapOf("size" to listOf("16", "64"))
        }
        val config = configuration(settings, true, "/path with spaces/results.json")
        assertContains(config, "warmups:1\n")
        assertContains(config, "iterationTime:50\niterationTimeUnit:ms\n")
        assertContains(config, "include:sample.*\n")
        assertContains(config, "exclude:.*slow.*\n")
        assertContains(config, "param:size=16\nparam:size=64\n")
        assertContains(config, "reportFile:/path with spaces/results.json\n")
    }

    @Test
    fun rejectsMissingEmptyAndInvalidReports() {
        val report = Files.createTempDirectory("benchmark-report").resolve("results.json")
        try {
            assertFailsWith<IllegalStateException> { verifyReport(report) }
            listOf("[]", "[{\"primaryMetric\":{\"score\":\"NaN\",\"rawData\":[[1]]}}]",
                "[{\"primaryMetric\":{\"score\":1,\"rawData\":[[]]}}]").forEach {
                report.writeText(it)
                assertFailsWith<IllegalStateException> { verifyReport(report) }
            }
            report.writeText("[{\"primaryMetric\":{\"score\":1.25,\"rawData\":[[1,1.5]]}}]")
            verifyReport(report)
        } finally {
            Files.deleteIfExists(report)
            Files.delete(report.parent)
        }
    }
}

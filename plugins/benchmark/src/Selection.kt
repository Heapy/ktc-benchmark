package io.heapy.ktc.plugins.benchmark

import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.readText

internal data class BenchmarkCase(val name: String, val params: Map<String, String>)
internal data class Selection(val includes: List<String>, val cases: Set<BenchmarkCase>)

internal fun selectBenchmarks(manifest: Path, settings: BenchmarkSettings): Selection {
    val includes = settings.includes.map(::Regex)
    val excludes = settings.excludes.map(::Regex)
    val selected = Json.parseToJsonElement(manifest.readText()).jsonArray.map { it.jsonObject }.filter {
        val name = it.getValue("name").jsonPrimitive.content
        (includes.isEmpty() || includes.any { regex -> regex.containsMatchIn(name) }) &&
            excludes.none { regex -> regex.containsMatchIn(name) }
    }
    check(selected.isNotEmpty()) { "No benchmarks matched the configured filters" }
    val parameterNames = selected.flatMap { it.getValue("params").jsonObject.keys }.toSet()
    require(settings.params.keys.all { it in parameterNames }) { "Unknown parameter override; selected parameters: $parameterNames" }
    val cases = selected.flatMap { entry ->
        var combinations: List<Map<String, String>> = listOf(emptyMap())
        entry.getValue("params").jsonObject.forEach { (key, defaults) ->
            val values = settings.params[key] ?: defaults.jsonArray.map { it.jsonPrimitive.content }
            require(values.isNotEmpty()) { "No values for benchmark parameter $key" }
            if (settings.platform !in setOf("jvm", "js", "wasmJs", "wasmWasi")) {
                require(values.none { '=' in it || ',' in it || '\n' in it || '\r' in it }) {
                    "Native runtime parameter values cannot contain '=', commas or line breaks: $key"
                }
            }
            combinations = combinations.flatMap { combination -> values.map { combination + (key to it) } }
        }
        combinations.map { BenchmarkCase(entry.getValue("name").jsonPrimitive.content, it) }
    }.toSet()
    val patterns = selected.map {
        // Portable regex escaping: Regex.escape uses Java-only \Q...\E, unsuitable for JS.
        "^" + it.getValue("runtimeName").jsonPrimitive.content.replace(Regex("[\\\\.^$|?*+()\\[\\]{}]")) { match -> "\\" + match.value } + "$"
    }
    return Selection(patterns, cases)
}

internal fun verifySelection(report: Path, selection: Selection) {
    val results = Json.parseToJsonElement(report.readText()).jsonArray
    val actual = results.map { entry ->
        val value = entry.jsonObject
        BenchmarkCase(value.getValue("benchmark").jsonPrimitive.content,
            value["params"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty())
    }
    check(actual.size == selection.cases.size && actual.toSet() == selection.cases) {
        "Incomplete benchmark report. Missing: ${selection.cases - actual.toSet()}; unexpected: ${actual.toSet() - selection.cases}"
    }
}

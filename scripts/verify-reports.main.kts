#!/usr/bin/env kotlinr
// Validate real benchmark results without asserting timings. Arguments: module names (default: jvm).
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0")

import kotlinx.serialization.json.*

val root = __FILE__.canonicalFile.parentFile.parentFile
val expected = buildSet {
    for (method in listOf("loop", "sequence")) for (size in listOf("16", "64")) add("sample.CollectionBenchmark.$method" to mapOf("size" to size))
    for (value in listOf("a b", "quote\" dollar$ slash\\")) add("sample.ParameterBenchmark.consume" to mapOf("text" to value))
}
for (module in args.toList().ifEmpty { listOf("jvm") }) {
    val directory = root.resolve("build/tasks/_${module}_benchmarkSmoke@benchmark")
    val records = Json.parseToJsonElement(directory.resolve("results.json").readText()).jsonArray
    val actual = records.map { element ->
        val row = element.jsonObject
        row.getValue("benchmark").jsonPrimitive.content to row["params"]?.jsonObject.orEmpty().mapValues { it.value.jsonPrimitive.content }
    }.toSet()
    check(actual == expected) { "$module missing ${expected - actual}; unexpected ${actual - expected}" }
    check(records.size == expected.size) { "$module duplicate results" }
    for (element in records) {
        val row = element.jsonObject
        check(row.getValue("measurementIterations").jsonPrimitive.int == 1)
        check(row.getValue("warmupIterations").jsonPrimitive.int == 1)
        val metric = row.getValue("primaryMetric").jsonObject
        val score = metric.getValue("score").jsonPrimitive.double
        check(score.isFinite() && score >= 0)
        check(metric.getValue("scoreUnit").jsonPrimitive.content == "ns/op")
        val raw = metric.getValue("rawData").jsonArray
        check(raw.isNotEmpty() && raw.all { group -> group.jsonArray.isNotEmpty() && group.jsonArray.all { it.jsonPrimitive.double.let { score -> score.isFinite() && score >= 0 } } })
    }
    val metadata = Json.parseToJsonElement(directory.resolve("run.json").readText()).jsonObject
    check(metadata.getValue("module").jsonPrimitive.content == module && metadata.getValue("profile").jsonPrimitive.content == "smoke")
    println("$module: ${records.size} parameterized benchmark results verified")
}

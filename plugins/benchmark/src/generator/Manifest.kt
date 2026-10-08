package io.heapy.ktc.plugins.benchmark.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import kotlinx.serialization.json.*

internal class Manifest {
    private val entries = linkedMapOf<String, JsonObject>()
    private val files = mutableSetOf<KSFile>()

    fun add(type: KSClassDeclaration, jvm: Boolean) {
        type.containingFile?.let(files::add)
        val className = requireNotNull(type.qualifiedName).asString()
        val parameters = buildJsonObject {
            type.getAllProperties().forEach { property ->
                val annotation = property.annotationOrNull("kotlinx.benchmark.Param")
                    ?: property.annotationOrNull("org.openjdk.jmh.annotations.Param")
                if (annotation != null) {
                    val values = annotation.argumentValueOrNull<List<String>>("value").orEmpty()
                    put(property.simpleName.asString(), JsonArray(values.map(::JsonPrimitive)))
                }
            }
        }
        type.getAllFunctions().filter {
            it.hasAnnotation("kotlinx.benchmark.Benchmark") || it.hasAnnotation("org.openjdk.jmh.annotations.Benchmark")
        }.forEach { function ->
            val name = "$className.${function.simpleName.asString()}"
            entries[name] = buildJsonObject {
                put("name", name)
                // Upstream SuiteExecutor matches suite.name + '.' + benchmark.name.
                put("runtimeName", if (jvm) name else "$className.$name")
                put("params", parameters)
            }
        }
    }

    fun write(output: CodeGenerator) {
        if (files.isEmpty()) return
        output.createNewFile(Dependencies(true, *files.toTypedArray()), "", "ktc-benchmark-manifest", "json")
            .bufferedWriter().use { it.write(JsonArray(entries.toSortedMap().values.toList()).toString()) }
    }
}

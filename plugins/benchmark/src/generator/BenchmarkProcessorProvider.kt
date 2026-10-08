package io.heapy.ktc.plugins.benchmark.generator

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.isAbstract
import com.squareup.kotlinpoet.CodeBlock
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile

/** The same self-contained module is both a Toolchain plugin and a KSP processor. */
class BenchmarkProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val backend = environment.options["ktc.benchmark.backend"] ?: when {
            environment.platforms.any { it is JvmPlatformInfo } -> "jvm"
            environment.platforms.any { it is NativePlatformInfo } -> "native"
            else -> "js"
        }
        if (backend == "jvm") return JvmProcessor(environment.codeGenerator)
        val platform = when (backend) {
            "native" -> Platform.NativeBuiltIn
            "js" -> Platform.JsBuiltIn
            "wasm" -> Platform.WasmBuiltIn
            else -> error("Unknown ktc.benchmark.backend: $backend")
        }
        val generator = SuiteSourceGenerator("benchmark", environment.codeGenerator, platform)
        return object : SymbolProcessor {
            override fun process(resolver: Resolver): List<KSAnnotated> {
                generator.generate(resolver)
                return emptyList()
            }
            override fun finish() = generator.generateRunnerMain()
        }
    }
}

private class JvmProcessor(private val output: CodeGenerator) : SymbolProcessor {
    private val classes = sortedSetOf<String>()
    private val manifest = Manifest()
    private val files = mutableSetOf<KSFile>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        for (annotation in listOf("kotlinx.benchmark.State", "org.openjdk.jmh.annotations.State")) {
            resolver.getSymbolsWithAnnotation(annotation).filterIsInstance<KSClassDeclaration>().filter { !it.isAbstract() }.forEach {
                manifest.add(it, jvm = true)
                classes += requireNotNull(it.qualifiedName).asString()
                it.containingFile?.let(files::add)
            }
        }
        return emptyList()
    }

    override fun finish() {
        if (classes.isEmpty()) return
        manifest.write(output)
        val dependencies = Dependencies(true, *files.toTypedArray())
        val packageName = "io.heapy.ktc.plugins.benchmark.generated"
        output.createNewFile(dependencies, packageName, "BenchmarkSuite").bufferedWriter().use {
            it.write("package $packageName\n\nfun main(args: Array<String>) {\n")
            it.write("    runJmh(args, listOf(" + classes.joinToString { name -> CodeBlock.of("%S", name).toString() } + "))\n}\n")
        }
        output.createNewFile(dependencies, packageName, "JmhRunner").use { destination ->
            checkNotNull(javaClass.getResourceAsStream("/benchmark/JmhRunner.kt.txt"))
                .use { it.copyTo(destination) }
        }
    }
}

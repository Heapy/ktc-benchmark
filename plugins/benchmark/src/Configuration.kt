package io.heapy.ktc.plugins.benchmark

internal val supportedPlatforms = setOf("jvm", "macosArm64", "linuxX64", "linuxArm64", "mingwX64", "js", "wasmJs", "wasmWasi")

internal fun validate(settings: BenchmarkSettings) {
    require(settings.platform in supportedPlatforms) { "Unsupported benchmark platform: ${settings.platform}" }
    require(settings.warmups >= 1) { "warmups must be at least 1 (Native calibration requires warmup)" }
    require(settings.iterations >= 1) { "iterations must be positive" }
    require(settings.iterationTime >= 1) { "iterationTime must be positive" }
    require(settings.jvmForks >= 1) { "jvmForks must be positive; fork isolation is required" }
    require(settings.jvmThreads >= 1) { "jvmThreads must be positive" }
    require(settings.timeoutSeconds >= 1) { "timeoutSeconds must be positive" }
    require(settings.iterationTimeUnit in setOf("ns", "us", "ms", "s", "m")) { "Invalid iterationTimeUnit" }
    require(settings.outputTimeUnit in setOf("ns", "us", "ms", "s", "m")) { "Invalid outputTimeUnit" }
    require(settings.mode in setOf("avgt", "thrpt")) { "mode must be avgt or thrpt" }
    (settings.includes + settings.excludes).forEach { Regex(it); lineValue(it) }
    settings.params.forEach { (name, values) ->
        require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Invalid parameter name: $name" }
        require(values.isNotEmpty()) { "Parameter $name has no values" }
        values.forEach { lineValue(it); require(',' !in it) { "Parameter values cannot contain commas" } }
    }
    settings.jvmArgs.forEach(::lineValue)
}

internal fun lineValue(value: String): String {
    require('\n' !in value && '\r' !in value && '\u0000' !in value) { "Configuration values must be single-line" }
    return value
}

internal fun configuration(settings: BenchmarkSettings, smoke: Boolean, report: String, includes: List<String> = settings.includes, excludes: List<String> = settings.excludes): String = buildString {
    validate(settings)
    appendLine("name:benchmark")
    appendLine("configurationName:${if (smoke) "smoke" else "main"}")
    appendLine("reportFile:${lineValue(report)}")
    appendLine("reportFormat:json")
    appendLine("traceFormat:text")
    appendLine("compilationMode:release")
    appendLine("warmups:${if (smoke) 1 else settings.warmups}")
    appendLine("iterations:${if (smoke) 1 else settings.iterations}")
    appendLine("iterationTime:${if (smoke) 50 else settings.iterationTime}")
    appendLine("iterationTimeUnit:${if (smoke) "ms" else settings.iterationTimeUnit}")
    appendLine("outputTimeUnit:${settings.outputTimeUnit}")
    appendLine("mode:${settings.mode}")
    appendLine("jvmForks:${settings.jvmForks}")
    appendLine("jvmThreads:${settings.jvmThreads}")
    includes.forEach { appendLine("include:$it") }
    excludes.forEach { appendLine("exclude:$it") }
    settings.params.forEach { (key, values) -> values.forEach { appendLine("param:$key=$it") } }
    settings.jvmArgs.forEach { appendLine("jvmArg:$it") }
}

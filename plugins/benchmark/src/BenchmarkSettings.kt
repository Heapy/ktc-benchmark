package io.heapy.ktc.plugins.benchmark

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface BenchmarkSettings {
    /** The leaf target of this executable runner module. */
    val platform: String
    val warmups: Int get() = 3
    val iterations: Int get() = 5
    val iterationTime: Int get() = 1
    val iterationTimeUnit: String get() = "s"
    val outputTimeUnit: String get() = "ns"
    val mode: String get() = "avgt"
    val includes: List<String> get() = emptyList()
    val excludes: List<String> get() = emptyList()
    val params: Map<String, List<String>> get() = emptyMap()
    val jvmForks: Int get() = 1
    val jvmThreads: Int get() = 1
    val jvmArgs: List<String> get() = emptyList()
    val nodeExecutable: String get() = "node"
    val timeoutSeconds: Int get() = 3600
}

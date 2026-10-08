package sample

import kotlinx.benchmark.*

@State(Scope.Benchmark)
open class CollectionBenchmark {
    @Param("16", "64")
    var size: Int = 0

    private lateinit var values: List<Int>

    @Setup
    fun setUp() {
        values = List(size) { it + 1 }
    }

    @Benchmark
    fun loop(): Int {
        var sum = 0
        for (value in values) sum += value
        return sum
    }

    @Benchmark
    fun sequence(): Int = values.asSequence().sum()

    @TearDown
    fun tearDown() {
        check(values.size == size)
    }
}

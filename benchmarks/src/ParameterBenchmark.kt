package sample

import kotlinx.benchmark.*

@State(Scope.Benchmark)
open class ParameterBenchmark {
    @Param("a b", "quote\" dollar$ slash\\")
    var text: String = ""

    @Benchmark
    fun consume(blackhole: Blackhole) {
        check(text == "a b" || text == "quote\" dollar$ slash\\")
        blackhole.consume(text.length)
    }
}

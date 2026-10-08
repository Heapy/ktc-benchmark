#!/usr/bin/env python3
"""Validate the example's real benchmark results, without asserting timings."""
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    (f"sample.CollectionBenchmark.{method}", (("size", size),))
    for method in ("loop", "sequence") for size in ("16", "64")
} | {
    ("sample.ParameterBenchmark.consume", (("text", value),))
    for value in ("a b", 'quote" dollar$ slash\\')
}

for module in sys.argv[1:] or ["jvm"]:
    directory = ROOT / "build" / "tasks" / f"_{module}_benchmarkSmoke@benchmark"
    records = json.loads((directory / "results.json").read_text())
    actual = {(row["benchmark"], tuple(sorted(row.get("params", {}).items()))) for row in records}
    assert actual == EXPECTED, (module, "missing", EXPECTED - actual, "unexpected", actual - EXPECTED)
    assert len(records) == len(EXPECTED), (module, "duplicate results")
    for row in records:
        assert row["measurementIterations"] == 1
        assert row["warmupIterations"] == 1
        metric = row["primaryMetric"]
        assert math.isfinite(metric["score"]) and metric["score"] >= 0
        assert metric["scoreUnit"] == "ns/op"
        raw = metric["rawData"]
        assert raw and all(samples and all(math.isfinite(x) and x >= 0 for x in samples) for samples in raw)
    metadata = json.loads((directory / "run.json").read_text())
    assert metadata["module"] == module and metadata["profile"] == "smoke"
    print(f"{module}: {len(records)} parameterized benchmark results verified")

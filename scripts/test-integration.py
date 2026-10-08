#!/usr/bin/env python3
"""Exercise an independently copied consumer and a partially failing JS suite."""
from pathlib import Path
import json
import shutil
import subprocess
import tempfile

SOURCE = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="ktc benchmark consumer ") as directory:
    root = Path(directory)
    for name in ("plugins", "benchmarks"):
        shutil.copytree(SOURCE / name, root / name)
    shutil.copytree(SOURCE / "runners/js", root / "runners/js")
    for name in ("kotlin", "kotlin.bat", "LICENSE", "NOTICE"):
        shutil.copy2(SOURCE / name, root / name)
    (root / "project.yaml").write_text("modules: [benchmarks, plugins/benchmark, runners/js]\nplugins: [//plugins/benchmark]\n")
    module = root / "runners/js/module.yaml"
    module.write_text(module.read_text() + "    includes: ['^sample\\.CollectionBenchmark\\.loop$']\n    params:\n      size: ['32']\n")
    wrapper = "kotlin.bat" if __import__("os").name == "nt" else "kotlin"
    command = [str(root / wrapper), "do", "benchmarkSmoke", "-m", "js"]
    result = subprocess.run(command, cwd=root, capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    output = root / "build/tasks/_js_benchmarkSmoke@benchmark"
    reports = json.loads((output / "results.json").read_text())
    assert len(reports) == 1 and reports[0]["params"] == {"size": "32"}, reports
    print("Copied consumer: anchored filter, parameter override and paths with spaces passed", flush=True)

    # The upstream runtime catches individual benchmark exceptions and may exit 0.
    # A mixed successful/failing suite must nevertheless fail this build command.
    (root / "benchmarks/src/FailureBenchmark.kt").write_text('''package sample
import kotlinx.benchmark.*
@State(Scope.Benchmark)
open class FailureBenchmark {
    @Benchmark fun passes(): Int = 42
    @Benchmark fun fails(): Int = error("expected benchmark failure")
}
''')
    module.write_text(module.read_text().split("    includes:")[0] + "    includes: ['^sample\\.FailureBenchmark\\..*$']\n")
    result = subprocess.run(command, cwd=root, capture_output=True, text=True)
    assert result.returncode != 0, result.stdout + result.stderr
    assert "Incomplete benchmark report" in result.stdout + result.stderr, result.stdout + result.stderr
    assert not (output / "results.json").exists(), "Stale success report survived a failed invocation"
    assert not (output / "run.json").exists(), "Stale success metadata survived a failed invocation"
    print("Partially failed runtime: nonzero status and no stale success report", flush=True)

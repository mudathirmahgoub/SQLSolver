#!/usr/bin/env python3
"""Run custom liastar cvc5 on every SQL benchmark (137 files across the
in-place cvc5/{calcite,spark} and the moved unsupported/{calcite,spark}).
Writes cvc5_all.csv with filename,result,duration. The 'filename' is keyed by
basename so it can be joined with sql_solver.csv (which uses original paths)."""
import csv, subprocess, time, glob, os

CVC5 = "/Users/mahgoubyahia/cvc5/liastar/build/bin/cvc5"
TLIMIT_MS = 100_000
roots = ["cvc5/calcite", "cvc5/spark", "unsupported/calcite", "unsupported/spark"]
files = []
for r in roots:
    files += sorted(glob.glob(os.path.join(r, "*.smt2")))

rows = []
for f in files:
    start = time.time()
    try:
        out = subprocess.run([CVC5, f"--tlimit={TLIMIT_MS}", f],
                             capture_output=True, text=True, timeout=TLIMIT_MS/1000 + 10)
        dur = time.time() - start
        first = (out.stdout.strip().splitlines() or [""])[0].strip().lower()
        if first in ("sat", "unsat", "unknown"):
            result = first
        elif "interrupted" in (out.stdout + out.stderr).lower() or "timeout" in (out.stdout+out.stderr).lower():
            result = "timeout"
        else:
            result = first if first else "error"
    except subprocess.TimeoutExpired:
        dur = time.time() - start
        result = "timeout"
    rows.append((f, result, f"{dur:.3f}"))
    print(f"{f},{result},{dur:.3f}", flush=True)

with open("cvc5_all.csv", "w", newline="") as fh:
    w = csv.writer(fh)
    w.writerow(["filename", "result", "duration"])
    w.writerows(rows)

from collections import Counter
print("=== cvc5_all summary ===", dict(Counter(r[1] for r in rows)), "total", len(rows))

#!/usr/bin/env python3
"""Re-run the Normaliz-enabled (fixed) cvc5 on all 137 SQL benchmarks.
Classifies each as sat / unsat / timeout / crash / unknown and writes
cvc5_normaliz_all.csv. 'crash' = the fixed extension's InternalError/segfault
(a robustness bug, distinct from a sound sat/unsat)."""
import csv, subprocess, time, glob, os
from collections import Counter

CVC5 = "/Users/mahgoubyahia/cvc5/liastar/build/bin/cvc5"  # rebuilt with USE_NORMALIZ=ON
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
                             capture_output=True, text=True, timeout=TLIMIT_MS/1000 + 15)
        dur = time.time() - start
        blob = (out.stdout + out.stderr).lower()
        first = (out.stdout.strip().splitlines() or [""])[0].strip().lower()
        if first in ("sat", "unsat", "unknown"):
            result = first
        elif "internalerror" in blob or "fatal failure" in blob or "segfault" in blob \
                or "terminated by the c++" in blob or out.returncode not in (0,):
            result = "crash"
        elif "interrupted" in blob or "timeout" in blob:
            result = "timeout"
        else:
            result = first if first else "crash"
    except subprocess.TimeoutExpired:
        dur = time.time() - start
        result = "timeout"
    rows.append((f, result, f"{dur:.3f}"))
    print(f"{f},{result},{dur:.3f}", flush=True)

with open("cvc5_normaliz_all.csv", "w", newline="") as fh:
    w = csv.writer(fh)
    w.writerow(["filename", "result", "duration"])
    w.writerows(rows)
print("=== cvc5 (normaliz) summary ===", dict(Counter(r[1] for r in rows)), "total", len(rows))

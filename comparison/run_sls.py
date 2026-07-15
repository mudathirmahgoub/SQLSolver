#!/usr/bin/env python3
"""Run sls-reachability (smt_to_sls.py) on corpus dumps, in parallel.

Same per-file semantics as ~/sls-reachability/run_sql.py (its venv python, 100s
timeout, sat/unsat scraped from stdout, nonzero exit -> error) but with a
process pool, and suite-selectable. Requires the custom liastar cvc5 Python
bindings (Kind.STAR_CONTAINS) on PYTHONPATH — handled below.

Usage: comparison/run_sls.py [--timeout S] [--jobs N] [--out CSV] [suite ...]
Writes sls_results.csv at the repo root by default (filename,result,duration).
"""
import argparse
import csv
import os
import subprocess
import time
from concurrent.futures import ProcessPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
SLS = os.path.expanduser("~/sls-reachability")
SMT_TO_SLS = os.path.join(SLS, "smt_to_sls.py")
VENV_PY = os.path.join(SLS, ".venv", "bin", "python3")
BINDINGS = os.path.expanduser("~/cvc5/liastar/build-python/src/api/python")
SUITES = ["linear"]


def run_one(args):
    path, timeout = args
    env = dict(os.environ, PYTHONPATH=BINDINGS)
    start = time.time()
    try:
        proc = subprocess.run([VENV_PY, SMT_TO_SLS, path], capture_output=True,
                              text=True, timeout=timeout, env=env)
    except subprocess.TimeoutExpired:
        return path, "timeout", time.time() - start
    duration = time.time() - start
    if proc.returncode != 0:
        return path, "error", duration
    for line in proc.stdout.splitlines():
        if line.strip() in ("sat", "unsat"):
            return path, line.strip(), duration
    return path, "unknown", duration


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--timeout", type=int, default=100)
    ap.add_argument("--jobs", type=int, default=1,
                help="worker processes; 1 = sequential (default, fair timings), N>1 = parallel")
    ap.add_argument("--out", default=os.path.join(REPO, "sls_results.csv"))
    ap.add_argument("suites", nargs="*", default=SUITES)
    args = ap.parse_args()

    files = []
    for suite in args.suites:
        d = os.path.join(REPO, "cvc5", suite)
        files += sorted(os.path.join(d, f) for f in os.listdir(d)
                        if f.endswith(".smt2"))
    print(f"{len(files)} files, timeout {args.timeout}s, {args.jobs} workers")

    results = {}
    with ProcessPoolExecutor(max_workers=args.jobs) as ex:
        for i, (path, res, dur) in enumerate(
                ex.map(run_one, [(f, args.timeout) for f in files]), 1):
            results[path] = (res, dur)
            print(f"[{i}/{len(files)}] {os.path.relpath(path, REPO)}: {res} ({dur:.2f}s)",
                  flush=True)

    with open(args.out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["filename", "result", "duration"])
        for path in files:
            res, dur = results[path]
            w.writerow([os.path.relpath(path, REPO), res, f"{dur:.3f}"])
    print(f"wrote {args.out}")


if __name__ == "__main__":
    main()

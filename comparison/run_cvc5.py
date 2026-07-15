#!/usr/bin/env python3
"""Run the liastar cvc5 binary on every dump in the corpus.

For each benchmark in cvc5/linear/ this writes the raw solver output to the
sibling .txt file and appends a classified row to ../cvc5_all.csv
(filename,result,duration) with result in
{sat, unsat, unknown, timeout, crash, parse_error, other}.

Usage: comparison/run_cvc5.py [--timeout SECONDS] [suite ...]   default: all
"""
import argparse
import csv
import os
import subprocess
import sys
import time
from concurrent.futures import ProcessPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
CVC5 = os.path.expanduser("~/cvc5/liastar/build/bin/cvc5")
SUITES = ["linear"]


def classify(text, returncode):
    low = text.lower()
    first = next((ln.strip().lower() for ln in text.splitlines() if ln.strip()), "")
    if first in ("sat", "unsat", "unknown"):
        return first
    if "interrupted by timeout" in low:
        return "timeout"
    if ("fatal failure" in low or "internal error" in low or "segfault" in low
            or "terminated by the c++ runtime" in low or returncode not in (0, None)):
        return "crash"
    if "parse error" in low or first.startswith("(error"):
        return "parse_error"
    return "other"


def run_one(args):
    path, timeout = args
    start = time.time()
    try:
        proc = subprocess.run(
            [CVC5, f"--tlimit={timeout * 1000}", path],
            capture_output=True, text=True, timeout=timeout + 20)
        out, code = proc.stdout + proc.stderr, proc.returncode
    except subprocess.TimeoutExpired as e:
        out = ((e.stdout or b"").decode(errors="replace")
               + (e.stderr or b"").decode(errors="replace")
               + "\n(killed: hard timeout)\n")
        code = None
        # a hard kill past --tlimit means the solver wedged; count as timeout
        duration = time.time() - start
        with open(os.path.splitext(path)[0] + ".txt", "w") as f:
            f.write(out)
        return path, "timeout", duration
    duration = time.time() - start
    with open(os.path.splitext(path)[0] + ".txt", "w") as f:
        f.write(out)
    return path, classify(out, code), duration


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--timeout", type=int, default=100)
    ap.add_argument("--jobs", type=int, default=8)
    ap.add_argument("suites", nargs="*", default=SUITES)
    args = ap.parse_args()

    if not os.path.exists(CVC5):
        sys.exit(f"missing cvc5 binary: {CVC5}")

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

    with open(os.path.join(REPO, "cvc5_all.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["filename", "result", "duration"])
        for path in files:
            res, dur = results[path]
            w.writerow([os.path.relpath(path, REPO), res, f"{dur:.3f}"])
    print("wrote cvc5_all.csv")


if __name__ == "__main__":
    main()

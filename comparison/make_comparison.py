#!/usr/bin/env python3
"""Assemble the SQL-benchmark solver comparison and draw cactus plots, in the
style of paper-fmcad26-liastar/scripts/plot.py.

Inputs (all keyed by "<suite>/<basename>", suite in {calcite, spark, tpc-c, tpc-h}):
  ../sql_solver.csv    SQLSolver's LIA* solver on the dumps (comparison/run_roundtrip.sh)
  ../cvc5_all.csv      custom liastar cvc5 binary on the dumps (comparison/run_cvc5.py)
  ../sls_results.csv   sls-reachability via smt_to_sls.py (comparison/run_sls.sh)

Outputs (in this directory):
  comparison.csv          wide per-instance table
  summary.csv             per-solver solved/sat/unsat/timeout/error counts
  cactus_plot.png         cactus (linear x)
  cactus_plot_log.png     cactus (log x, readable across the dynamic range)
  Also prints the soundness cross-check and coverage stats.

NOTE on interpreting disagreements: use comparison/starfree_check.py for ground
truth on reducible instances — a raw sat/unsat disagreement alone does not say
which solver is wrong.
"""
import csv, os
from collections import OrderedDict, Counter

HERE = os.path.dirname(os.path.abspath(__file__))
SQLSOLVER_DIR = os.path.dirname(HERE)
SLS_RESULTS = os.path.join(SQLSOLVER_DIR, "sls_results.csv")
if not os.path.exists(SLS_RESULTS):
    SLS_RESULTS = os.path.expanduser("~/sls-reachability/results.csv")
SOLVED = {"sat", "unsat"}


def key_of(path):
    return "/".join(path.replace("\\", "/").split("/")[-2:])  # suite/basename


def load(path):
    out = {}
    if not os.path.exists(path):
        print(f"[warn] missing {path}")
        return out
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            fn = row.get("filename")
            if not fn:
                continue
            res = (row.get("result") or "").strip().lower()
            try:
                dur = float(row.get("duration") or "nan")
            except ValueError:
                dur = float("nan")
            out[key_of(fn)] = (res, dur)
    return out


sqlsolver = load(os.path.join(SQLSOLVER_DIR, "sql_solver.csv"))
# cvc5 must be built WITH Normaliz (int.star-contains is compiled out otherwise).
cvc5 = load(os.path.join(SQLSOLVER_DIR, "cvc5_all.csv"))
sls = load(SLS_RESULTS)
keys = list(OrderedDict.fromkeys(list(cvc5) + list(sqlsolver) + list(sls)))
N = len(keys)

# ---- wide comparison.csv ----
with open(os.path.join(HERE, "comparison.csv"), "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["filename", "sqlsolver result", "sqlsolver duration",
                "cvc5 result", "cvc5 duration", "sls result", "sls duration"])
    for k in keys:
        sr, sd = sqlsolver.get(k, ("missing", ""))
        cr, cd = cvc5.get(k, ("missing", ""))
        lr, ld = sls.get(k, ("missing", ""))
        w.writerow([k, sr, sd, cr, cd, lr, ld])

# ---- summary.csv ----
solvers = [("SQLSolver", sqlsolver), ("cvc5 (Normaliz)", cvc5), ("SLS-reachability", sls)]
with open(os.path.join(HERE, "summary.csv"), "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["solver", "n", "solved", "sat", "unsat", "timeout", "crash", "error", "missing"])
    for name, d in solvers:
        c = Counter(v[0] for v in d.values())
        solved = sum(1 for v in d.values() if v[0] in SOLVED)
        w.writerow([name, len(d), solved, c.get("sat", 0), c.get("unsat", 0),
                    c.get("timeout", 0), c.get("crash", 0), c.get("error", 0), c.get("missing", 0)])
        print(f"  {name:18s} solved={solved:3d}/{N}  {dict(c)}")

# ---- soundness cross-check ----
print("\n=== soundness cross-check (agreement where both return sat/unsat) ===")
disagree = []
for a, da in solvers:
    for b, db in solvers:
        if a >= b:
            continue
        both = agree = 0
        for k in keys:
            ra = da.get(k, ("", 0))[0]; rb = db.get(k, ("", 0))[0]
            if ra in SOLVED and rb in SOLVED:
                both += 1
                if ra == rb:
                    agree += 1
                else:
                    disagree.append((k, a, ra, b, rb))
        print(f"  {a:16s} vs {b:16s}: both={both:3d} agree={agree:3d} disagree={both-agree:3d}")
if disagree:
    print("  --- disagreements ---")
    seen = set()
    for k, a, ra, b, rb in disagree:
        if k in seen:
            continue
        seen.add(k)
        trip = " | ".join(f"{s}={d.get(k,('-',))[0]}" for s, d in solvers)
        print(f"    {k}: {trip}")

# ---- coverage ----
def solved_set(d): return {k for k in keys if d.get(k, ("",))[0] in SOLVED}
S = {n: solved_set(d) for n, d in solvers}
print("\n=== coverage ===")
print(f"  solved by cvc5 or sls but NOT SQLSolver: {len((S['cvc5 (Normaliz)']|S['SLS-reachability']) - S['SQLSolver'])}")
print(f"  solved by none: {N - len(set().union(*S.values()))}")

# ---- cactus plots ----
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

styles = [
    ("SQLSolver",        sqlsolver, {"marker": "o", "color": "#D55E00"}),
    ("cvc5 (Normaliz)",   cvc5,      {"marker": "s", "color": "#0072B2"}),
    ("SLS-reachability", sls,       {"marker": "D", "color": "#009E73"}),
]
mk = dict(linewidth=2, markersize=7, markevery=0.1, markeredgecolor="black", markeredgewidth=0.5)


def draw(logx, out):
    plt.figure(figsize=(10, 6))
    for label, d, st in styles:
        durs = sorted(v[1] for v in d.values() if v[0] in SOLVED and v[1] == v[1])
        if not durs:
            continue
        cum = np.cumsum(durs)
        plt.plot(cum, range(1, len(cum) + 1), label=f"{label} ({len(durs)} solved)", **st, **mk)
    if logx:
        plt.xscale("log")
    plt.xlabel("Cumulative time (s)" + (" [log]" if logx else ""), fontsize=16)
    plt.ylabel("Number of solved instances", fontsize=16)
    plt.title(f"Cactus Plot: linear LIA* SQL benchmarks ({N} instances)",
              fontsize=15)
    plt.grid(True, which="both", linestyle="--", alpha=0.5)
    plt.legend(fontsize=14, borderpad=1.0, labelspacing=0.8, frameon=True)
    plt.tight_layout()
    plt.savefig(os.path.join(HERE, out), dpi=300)
    plt.close()


draw(False, "cactus_plot.png")
draw(True, "cactus_plot_log.png")
print("\nwrote comparison.csv, summary.csv, cactus_plot.png, cactus_plot_log.png")

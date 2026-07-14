#!/usr/bin/env python3
"""Select the corpus benchmarks whose star predicates are within cvc5's
supported fragment, and summarize the three solvers on exactly that set.

Kept: files where every int.star-contains lambda body is LINEAR — no products
of two variable terms, no division, no uninterpreted-function application —
and contains no nested int.star-contains. Star-free files qualify trivially.

Output:
  cvc5/linear/<suite>-<basename>.smt2   flat copies of the kept benchmarks
  comparison/linear_comparison.csv      per-file results of the three solvers
  stdout                                per-solver summary + all discrepancies

Usage: comparison/filter_linear.py
"""
import csv
import os
import re
import shutil
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from starfree_check import tokenize, parse  # noqa

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUTDIR = os.path.join(REPO, "cvc5", "linear")
SUITES = ["calcite", "spark", "tpc-c", "tpc-h"]
OPS = {"and", "or", "not", "=>", "=", "<", "<=", ">", ">=", "+", "-", "*", "/",
       "ite", "int.star-contains", "lambda", "true", "false", "distinct", "let"}
NUM = re.compile(r"^-?\d+$")
SOLVED = {"sat", "unsat"}


def has_var(n):
    if isinstance(n, str):
        return not NUM.match(n) and n not in OPS
    return any(has_var(x) for x in n)


def body_ok(n):
    """Lambda body stays in the linear star fragment, with no nested star."""
    if isinstance(n, str):
        return True
    head = n[0] if n and isinstance(n[0], str) else None
    if head == "int.star-contains":
        return False  # nested star
    if head == "*" and sum(1 for a in n[1:] if has_var(a)) >= 2:
        return False  # non-linear product
    if head == "/":
        return False
    if (head is not None and head not in OPS and not NUM.match(head)
            and len(n) > 1):
        return False  # uninterpreted function application
    return all(body_ok(x) for x in n)


def stars_ok(node):
    if isinstance(node, str):
        return True
    if node and node[0] == "int.star-contains":
        return body_ok(node[1][2]) and all(stars_ok(x) for x in node[2:])
    return all(stars_ok(x) for x in node)


def load(path, name_cols=("filename", "sqlsolver file")):
    out = {}
    if not os.path.exists(path):
        print(f"[warn] missing {path}")
        return out
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            fn = next((row[c] for c in name_cols if row.get(c)), "")
            if fn:
                key = "/".join(fn.replace("\\", "/").split("/")[-2:])
                out[key] = (row.get("result") or "").strip().lower()
    return out


def main():
    shutil.rmtree(OUTDIR, ignore_errors=True)
    os.makedirs(OUTDIR)

    kept = []
    for suite in SUITES:
        d = os.path.join(REPO, "cvc5", suite)
        if not os.path.isdir(d):
            continue
        for fn in sorted(os.listdir(d)):
            if not (fn.endswith(".smt2") and fn.startswith("query")):
                continue
            forms = parse(tokenize(open(os.path.join(d, fn)).read()))
            ok = all(stars_ok(form[1]) for form in forms
                     if isinstance(form, list) and form and form[0] == "assert")
            if ok:
                kept.append(f"{suite}/{fn}")
                shutil.copy(os.path.join(d, fn),
                            os.path.join(OUTDIR, f"{suite}-{fn}"))
    print(f"kept {len(kept)} linear, nesting-free benchmarks -> {OUTDIR}\n")

    sqls = load(os.path.join(REPO, "sql_solver.csv"))
    cvc5 = load(os.path.join(REPO, "cvc5_all.csv"))
    sls = load(os.path.join(REPO, "sls_results.csv"))
    solvers = [("SQLSolver", sqls), ("cvc5 (Normaliz)", cvc5),
               ("SLS-reachability", sls)]

    with open(os.path.join(HERE, "linear_comparison.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["filename", "sqlsolver", "cvc5", "sls"])
        for k in kept:
            w.writerow([k, sqls.get(k, ""), cvc5.get(k, ""), sls.get(k, "")])

    print("=== summary on the filtered set ===")
    for name, d in solvers:
        c = Counter(d.get(k, "missing") for k in kept)
        solved = sum(c[r] for r in SOLVED)
        print(f"  {name:18s} solved={solved:3d}/{len(kept)}  {dict(c)}")

    print("\n=== pairwise agreement (both answered sat/unsat) ===")
    disagree = set()
    for i, (a, da) in enumerate(solvers):
        for b, db in solvers[i + 1:]:
            both = agree = 0
            for k in kept:
                ra, rb = da.get(k, ""), db.get(k, "")
                if ra in SOLVED and rb in SOLVED:
                    both += 1
                    agree += ra == rb
                    if ra != rb:
                        disagree.add(k)
            print(f"  {a:16s} vs {b:16s}: both={both:3d} agree={agree:3d} "
                  f"disagree={both - agree:3d}")

    if disagree:
        print("\n=== discrepancies ===")
        for k in sorted(disagree):
            print(f"  {k}: sqlsolver={sqls.get(k, '-')} | cvc5={cvc5.get(k, '-')}"
                  f" | sls={sls.get(k, '-')}")
    else:
        print("\nno discrepancies on the filtered set")


if __name__ == "__main__":
    main()

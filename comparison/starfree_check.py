#!/usr/bin/env python3
"""Independent soundness cross-check via exact star elimination.

For dumps whose every int.star-contains lambda body is a conjunction of
HOMOGENEOUS linear equalities over the lambda binders (only forms  (= x y)  and
(= x 0)), star membership has a closed form: the solution set of such a body is
closed under vector addition and contains the zero vector, so its sum-closure
equals the set itself. Hence

    (int.star-contains (lambda (v1..vd) BODY) p1 .. pd)
        <=>  BODY[v := p]  /\  p1 >= 0 /\ ... /\ pd >= 0     (N-summand semantics)

Replacing every star this way yields a plain QF_LIA formula whose sat/unsat any
off-the-shelf solver can decide — ground truth that does not depend on any
star-aware decision procedure. A second variant without the >= 0 conjuncts
covers Z-summand semantics.

The script compares that ground truth against
  - cvc5's answer on the starred file (cvc5_all.csv)     -> cvc5 soundness bugs
  - SQLSolver's round-trip answer     (sql_solver.csv)   -> SQLSolver soundness bugs
  - SLS's answer                      (sls_results.csv)  -> SLS soundness bugs
and writes starfree_check.csv plus a printed summary. Files with at least one
non-homogeneous lambda body are skipped (reduction not applicable).

Usage: comparison/starfree_check.py [--timeout SECONDS] [suite ...]
"""
import argparse
import csv
import os
import subprocess
import sys
from concurrent.futures import ProcessPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
CVC5 = os.path.expanduser("~/cvc5/liastar/build/bin/cvc5")
SUITES = ["calcite", "spark", "tpc-c", "tpc-h"]
SOLVED = {"sat", "unsat"}


# ---------- tiny s-expression parser ----------
def tokenize(text):
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == ";":
            while i < n and text[i] != "\n":
                i += 1
        elif c in "()":
            out.append(c); i += 1
        elif c.isspace():
            i += 1
        else:
            j = i
            while j < n and not text[j].isspace() and text[j] not in "();":
                j += 1
            out.append(text[i:j]); i = j
    return out


def parse(tokens):
    def rec(i):
        if tokens[i] == "(":
            lst, i = [], i + 1
            while tokens[i] != ")":
                node, i = rec(i)
                lst.append(node)
            return lst, i + 1
        return tokens[i], i + 1
    forms, i = [], 0
    while i < len(tokens):
        node, i = rec(i)
        forms.append(node)
    return forms


def to_sexp(node):
    if isinstance(node, str):
        return node
    return "(" + " ".join(to_sexp(x) for x in node) + ")"


# ---------- star elimination ----------
def conjuncts(node):
    if isinstance(node, list) and node and node[0] == "and":
        out = []
        for c in node[1:]:
            out += conjuncts(c)
        return out
    return [node]


def is_homogeneous_body(body, binders):
    """Only (= a b) with a, b each a binder or the literal 0."""
    for lit in conjuncts(body):
        if not (isinstance(lit, list) and len(lit) == 3 and lit[0] == "="):
            return False
        for arg in lit[1:]:
            if not (arg == "0" or (isinstance(arg, str) and arg in binders)):
                return False
    return True


def substitute(node, subst):
    if isinstance(node, str):
        return subst.get(node, node)
    return [substitute(x, subst) for x in node]


def eliminate(node, nonneg, stats):
    """Replace star-contains nodes; return None if a body is not homogeneous."""
    if isinstance(node, str):
        return node
    if node and node[0] == "int.star-contains":
        lam, points = node[1], node[2:]
        binders = [b[0] for b in lam[1]]
        body = lam[2]
        if len(binders) != len(points) or not is_homogeneous_body(body, set(binders)):
            stats["inapplicable"] += 1
            return None
        stats["stars"] += 1
        repl = substitute(body, dict(zip(binders, points)))
        parts = [repl]
        if nonneg:
            parts += [[">=", p, "0"] for p in points]
        return ["and"] + parts if len(parts) > 1 else repl
    out = []
    for x in node:
        y = eliminate(x, nonneg, stats)
        if y is None:
            return None
        out.append(y)
    return out


def reduce_file(path, nonneg):
    forms = parse(tokenize(open(path).read()))
    stats = {"stars": 0, "inapplicable": 0}
    # ALL rather than QF_LIA: dumps may declare uninterpreted functions
    out = ["(set-logic ALL)"]
    for form in forms:
        if isinstance(form, list) and form and form[0] == "assert":
            body = eliminate(form[1], nonneg, stats)
            if body is None:
                return None, stats
            out.append(to_sexp(["assert", body]))
        elif isinstance(form, list) and form and form[0] == "set-logic":
            continue
        else:
            out.append(to_sexp(form))
    return "\n".join(out) + "\n", stats


def solve(text, timeout):
    proc = subprocess.run([CVC5, f"--tlimit={timeout * 1000}", "--lang=smt2", "-"],
                          input=text, capture_output=True, text=True,
                          timeout=timeout + 20)
    first = next((ln.strip() for ln in proc.stdout.splitlines() if ln.strip()), "")
    return first if first in ("sat", "unsat", "unknown") else "solver_error"


def load_results(path):
    out = {}
    if not os.path.exists(path):
        print(f"[warn] missing {path}")
        return out
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            fn = row.get("filename") or row.get("sqlsolver file") or ""
            if fn:
                key = "/".join(fn.replace("\\", "/").split("/")[-2:])
                out[key] = (row.get("result") or "").strip().lower()
    return out


def check_one(item):
    """Worker: reduce one file and decide both variants."""
    path, key, timeout = item
    red_n, stats = reduce_file(path, nonneg=True)
    if red_n is None:
        return key, "inapplicable", "", ""
    red_z, _ = reduce_file(path, nonneg=False)
    return key, "ok", solve(red_n, timeout), solve(red_z, timeout)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--timeout", type=int, default=60)
    ap.add_argument("--jobs", type=int, default=8)
    ap.add_argument("suites", nargs="*", default=SUITES)
    args = ap.parse_args()

    cvc5 = load_results(os.path.join(REPO, "cvc5_all.csv"))
    sqls = load_results(os.path.join(REPO, "sql_solver.csv"))
    sls = load_results(os.path.join(REPO, "sls_results.csv"))

    work = []
    for suite in args.suites:
        d = os.path.join(REPO, "cvc5", suite)
        for fn in sorted(os.listdir(d)):
            if fn.endswith(".smt2") and fn.startswith("query"):
                work.append((os.path.join(d, fn), f"{suite}/{fn}", args.timeout))

    rows, bugs = [], {"cvc5": [], "sqlsolver": [], "sls": []}
    with ProcessPoolExecutor(max_workers=args.jobs) as ex:
        for key, status, gt_n, gt_z in ex.map(check_one, work):
            if status == "inapplicable":
                rows.append([key, "inapplicable", "", "", cvc5.get(key, ""),
                             sqls.get(key, ""), sls.get(key, "")])
                continue
            rows.append([key, "ok", gt_n, gt_z, cvc5.get(key, ""),
                         sqls.get(key, ""), sls.get(key, "")])
            if gt_n in SOLVED:
                if cvc5.get(key) in SOLVED and cvc5[key] != gt_n:
                    bugs["cvc5"].append((key, cvc5[key], gt_n))
                if sqls.get(key) in SOLVED and sqls[key] != gt_n:
                    # SQLSolver solves over Z; only flag if wrong under BOTH
                    if gt_z in SOLVED and sqls[key] != gt_z:
                        bugs["sqlsolver"].append((key, sqls[key], f"N:{gt_n}/Z:{gt_z}"))
                if sls.get(key) in SOLVED and sls[key] != gt_n:
                    bugs["sls"].append((key, sls[key], gt_n))
            print(f"{key}: ground truth N={gt_n} Z={gt_z} | cvc5={cvc5.get(key,'-')} "
                  f"sqlsolver={sqls.get(key,'-')} sls={sls.get(key,'-')}", flush=True)

    with open(os.path.join(HERE, "starfree_check.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["filename", "reduction", "ground_truth_N", "ground_truth_Z",
                    "cvc5", "sqlsolver", "sls"])
        w.writerows(rows)

    applicable = sum(1 for r in rows if r[1] == "ok")
    print(f"\n{applicable}/{len(rows)} files reducible (all lambda bodies homogeneous)")
    for solver, items in bugs.items():
        print(f"\n=== {solver}: {len(items)} answers contradicting ground truth ===")
        for key, got, truth in items:
            print(f"  {key}: {solver}={got}, ground truth={truth}")


if __name__ == "__main__":
    main()

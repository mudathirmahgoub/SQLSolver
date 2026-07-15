#!/usr/bin/env python3
"""Build the linear benchmark corpus from the pipeline's dump output.

Selects the dumps whose star predicates are within the linear fragment —
every int.star-contains lambda body is a conjunction/boolean combination of
LINEAR atoms (no products of two variable terms, no division, no
uninterpreted-function application) with no nested int.star-contains —
and copies them into the flat corpus directory cvc5/linear/ as
<suite>-<basename>.smt2. Star-free dumps qualify trivially. All dump files
(kept or not) are then pruned from the suite directories: cvc5/linear/ is
the single benchmark corpus.

Usage: comparison/filter_linear.py [--no-prune]
"""
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from starfree_check import tokenize, parse  # noqa

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUTDIR = os.path.join(REPO, "cvc5", "linear")
SUITES = ["calcite", "spark", "tpc-c", "tpc-h"]
OPS = {"and", "or", "not", "=>", "=", "<", "<=", ">", ">=", "+", "-", "*", "/",
       "ite", "int.star-contains", "lambda", "true", "false", "distinct", "let"}
NUM = re.compile(r"^-?\d+$")


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


def main():
    prune = "--no-prune" not in sys.argv
    shutil.rmtree(OUTDIR, ignore_errors=True)
    os.makedirs(OUTDIR)

    kept = dropped = 0
    for suite in SUITES:
        d = os.path.join(REPO, "cvc5", suite)
        if not os.path.isdir(d):
            continue
        for fn in sorted(os.listdir(d)):
            if not (fn.endswith(".smt2") and fn.startswith("query")):
                continue
            path = os.path.join(d, fn)
            forms = parse(tokenize(open(path).read()))
            ok = all(stars_ok(form[1]) for form in forms
                     if isinstance(form, list) and form and form[0] == "assert")
            if ok:
                shutil.copy(path, os.path.join(OUTDIR, f"{suite}-{fn}"))
                kept += 1
            else:
                dropped += 1
            if prune:
                os.remove(path)
                txt = path[:-5] + ".txt"
                if os.path.exists(txt):
                    os.remove(txt)
    print(f"linear corpus: {kept} benchmarks -> {OUTDIR}"
          f" ({dropped} out-of-fragment dumps excluded"
          f"{', suite dirs pruned' if prune else ''})")


if __name__ == "__main__":
    main()

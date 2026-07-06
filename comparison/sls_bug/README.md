# sls-reachability adapter bug — star semantics discarded

`~/sls-reachability/smt_to_sls.py::cvc5_to_z3` translates a
`(int.star-contains (lambda ...) p1 .. pd)` atom into **its lambda body
alone**: the binders resolve to the same-named *global* constants and the
point arguments `p1..pd` — the vector that must equal a sum of summands — are
discarded (lines 103-106). Only a top-level conjunct star is treated as the
star predicate, and `star_variables` is set to every declared symbol
(line 211).

## Minimal repro

`sever_repro.smt2`:

    y = 3  ∧  y ∈ {(a) | a = 5}*

Ground truth: y must be a finite sum of 5s, i.e. y ∈ {0, 5, 10, ...} —
**unsat**. The adapter answers **sat**: dropping the point argument turns
"y is a sum of 5s" into "some variable a equals 5".

    PYTHONPATH=~/cvc5/liastar/build-python/src/api/python \
      ~/sls-reachability/.venv/bin/python3 ~/sls-reachability/smt_to_sls.py \
      sever_repro.smt2          # -> sat (spurious)

## Corpus impact (see ../REPORT.md §5.3)

On the 360-dump corpus: 209 `error`s (multi-star / nested-star files the
adapter cannot represent) and 9 spurious `sat`s contradicted by SQLSolver's
unsat (6 also by cvc5's unsat):
`calcite/query{039,044,051,082,115,120,148,156,190}-call-0.smt2`.

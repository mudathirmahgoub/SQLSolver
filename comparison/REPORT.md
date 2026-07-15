# Linear LIA\* benchmark comparison — SQLSolver vs cvc5 vs SLS-reachability

*Generated 2026-07-06. Reproduce everything with `comparison/run_all.sh`.*

## 1. The benchmark corpus: `cvc5/linear/` (114 files)

Running the SQLSolver pipeline on the four SQL-equivalence suites (calcite,
spark, tpc-c, tpc-h) exports every LIA\* solver call to SMT-LIB
(`int.star-contains`). Each formula asserts "a counterexample database
exists", so **unsat = the SQL pair is equivalent**; the intended semantics is
over nonnegative integers (tuple multiplicities). `filter_linear.py` keeps the
dumps whose star predicates are within the fragment all three solvers accept —
every lambda body linear (no products of variables, no division, no
uninterpreted functions) and no star nested inside another star — giving
**114 benchmarks**, named `<suite>-<queryNNN-call-K>.smt2` (56 calcite,
43 spark, 15 tpc-h). Out-of-fragment dumps are excluded at generation time and
are not part of this comparison.

Star *parameters* (variables shared by all summands of a star and by the
enclosing formula) cannot be expressed inside `int.star-contains`'s closed
lambda, so the exporter (`Cvc5LiaStarSolver.translate`) eliminates them
exactly before export (nesting depth ≤ 2, 30 s budget); each file's header
comment states whether elimination was exact. All 114 corpus files are either
exact or parameter-free, so they are equisatisfiable with what the pipeline
solved.

## 2. The two translation layers

**Exporter (SQLSolver → SMT-LIB), `Cvc5LiaStarSolver.translate`.** A free
variable in a star body denotes one value shared by every summand and equal
to its occurrences outside the star. The closed-lambda encoding cannot say
that, so the exporter first applies SQLSolver's own exact parameter removal —
e.g. a shared conditional factors out of the sum
(`Σᵢ ite(c, tᵢ, 0) = ite(c, Σᵢ tᵢ, 0)` when `c` is summand-independent), and
parameter constraints are asserted once outside the star. Example from
`spark-query090-call-2.smt2`: the parameter `u21` (constrained `u21 = 1`
outside the star) now appears in `(= u0 (ite (= u21 1) var1 0))` *outside*
closed lambdas, instead of being bound per-summand inside the lambda with an
unconstrained fresh sum (which weakened the formula).

**SLS adapter (SMT-LIB → z3), `~/sls-reachability/smt_to_sls.py`.**
`sls_solver(phi, B, set_vars)` decides `phi ∧ set_vars ∈ {v | B(v)}*`, where
`set_vars` serve as both the membership point and B's coordinates. Each star
atom therefore gets fresh coordinates `q`; its lambda body is translated over
them; the atom is replaced in phi by linking equalities `point = q` (exact for
positive occurrences); several stars combine into one via the padded-union
construction `C(v) = ⋁ᵢ (Bᵢ(vᵢ) ∧ ⋀_{k≠i} v_k = 0)`, since
`(q_1..q_n) ∈ C* ⟺ ⋀ᵢ qᵢ ∈ Bᵢ*`; summand coordinates are constrained
nonnegative. Example — `y = 3 ∧ y ∈ {(a) | a = 5}*` translates to
`phi = [y = 3, y = q]`, `B = (q = 5 ∧ q ≥ 0)`: membership makes `q` a sum of
5s, so `y = 3` is unsat. Shapes outside the scheme (stars under negative
polarity, nested stars) are rejected with an error rather than mistranslated.

**Uninterpreted functions outside star bodies** (table-row functions like
`emp`, null markers like `IsNull` — 15 corpus files) are accepted by both
front-ends: SQLSolver's reader (`SmtToSqlSolver`) maps `APPLY_UF` to its
`LiaFuncImpl` term (solved as a z3 uninterpreted function), and the SLS
adapter maps `declare-fun` symbols to z3 `Function`s applied at their call
sites. Inside star bodies UFs remain out of fragment for all solvers and such
dumps are excluded from the corpus by `filter_linear.py`.

## 3. Results (114 benchmarks, 100 s per file, sequential protocol)

All three solvers process the corpus **sequentially** — one file at a time,
one solver at a time — so the reported durations are contention-free and
comparable (`run_all.sh --parallel` and `run_cvc5.py`/`run_sls.py --jobs N`
give the faster parallel mode; answers are identical, only timings differ).

| solver | solved | sat | unsat | timeout | total time | median/file | slowest solved |
|---|---|---|---|---|---|---|---|
| **SQLSolver (LiaSolver on the corpus)** | **114** | 70 | 44 | — | 4.9 s | 0.01 s | 1.33 s |
| cvc5 (liastar, Normaliz) | 113 | 69 | 44 | 1 | 105 s | 0.04 s | 0.34 s |
| SLS-reachability | 113 | 69 | 44 | 1 | 153 s | 0.14 s | 10.0 s |

- Every benchmark is solved by every solver except one timeout each: cvc5 on
  `calcite-query050-call-0`, SLS on `calcite-query127-call-0` (both solved by
  the other two). No crashes, no errors. (Both front-ends accept
  uninterpreted functions outside star bodies — see §2.)
- Excluding the 100 s timeouts, cvc5 finishes the other 113 files in 5.4 s
  total and SLS in 53 s; SQLSolver is the fastest overall and the only solver
  with full coverage.
- Cactus plots: `cactus_plot.png`, `cactus_plot_log.png`; per-instance table:
  `comparison.csv`; per-solver counts: `summary.csv`.

**Ground truth** (`starfree_check.py`): 48/114 files have only
homogeneous-equality star bodies, where membership reduces *exactly* to a
star-free formula. On those, all three solvers have
**zero wrong answers** (`starfree_check.csv`).

**Agreement** where both solvers answered: cvc5–SLS **112/112**,
SQLSolver–cvc5 112/113, SQLSolver–SLS 112/113.

## 4. The one remaining discrepancy

`linear/calcite-query026-call-4.smt2`: SQLSolver `sat` vs cvc5 `unsat` and
SLS `unsat`. SQLSolver's answer is unsound here for two compounding reasons
in `LiaSolver`: (a) `solve()` reports the SAT of `checkOverapp()` — an
over-approximation, sound only on its UNSAT side — as a definite SAT
(`LiaSolver.java:72-75`); (b) neither approximation constrains summand
vectors to be nonnegative, so models can assign negative "row counts" to
star coordinates (this file's model sets a star point coordinate to −1).
Fix direction: add `≥ 0` constraints on summand variables in the star
expansions and report over-approximation SAT as UNKNOWN.

## 5. Reproduction

```
comparison/run_all.sh              # everything below, sequential (default)
comparison/run_all.sh --parallel   # same, overlapping solvers + 8 workers
comparison/regenerate_dumps.sh     # pipeline -> per-suite dumps
comparison/filter_linear.py        # linear corpus -> cvc5/linear/ (prunes dumps)
comparison/run_roundtrip.sh        # SQLSolver  -> sql_solver.csv
comparison/run_cvc5.py [--jobs N]  # cvc5       -> cvc5_all.csv + .txt
comparison/run_sls.py  [--jobs N]  # SLS        -> sls_results.csv
comparison/starfree_check.py       # ground truth -> starfree_check.csv
comparison/make_comparison.py      # comparison.csv, summary.csv, cactus plots
```

Requirements: `./gradlew fatJar`; liastar cvc5 at `~/cvc5/liastar/build/bin/cvc5`
(Normaliz on) with Python bindings at `~/cvc5/liastar/build-python` (for SLS);
`~/sls-reachability`.

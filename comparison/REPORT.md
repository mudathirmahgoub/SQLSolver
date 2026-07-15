# Linear LIA\* benchmark comparison — SQLSolver vs cvc5 vs SLS-reachability

*Generated 2026-07-06. Reproduce everything with `comparison/run_all.sh`.*

## 1. The benchmark corpus: `cvc5/linear/` (29 files)

Running the SQLSolver pipeline on the four SQL-equivalence suites (calcite,
spark, tpc-c, tpc-h) exports every LIA\* solver call to SMT-LIB
(`int.star-contains`). Each formula asserts "a counterexample database
exists", so **unsat = the SQL pair is equivalent**; the intended semantics is
over nonnegative integers (tuple multiplicities). `filter_linear.py` keeps the
dumps whose *entire formula* is linear integer arithmetic:

- no product of two variable terms and no division — anywhere, inside or
  outside star bodies;
- no uninterpreted-function application — anywhere;
- no star nested inside another star's lambda body.

`dedup_linear.py` then removes duplicates — both identical files and files
that are the same benchmark up to variable renaming (the pipeline dumps one
file per parameter-removal config per solver call, so many dumps repeat; a
canonical form plus an exact backtracking alpha-equivalence check — variable
bijection, commutative arguments as multisets, associative flattening, star
dimensions up to permutation — catches renamed copies, including identical
formulas exported from different SQL queries).

That gives **29 unique benchmarks** (24 calcite, 5 spark; no tpc-h dump is
fully linear), named `<suite>-<queryNNN-call-K>.smt2`. Out-of-fragment dumps
and duplicates are excluded at generation time and are not part of the
comparison. The duplicate → kept-representative mapping is recorded in
`duplicates.csv` (rewritten by `dedup_linear.py` on every corpus rebuild).

Star *parameters* (variables shared by all summands of a star and by the
enclosing formula) cannot be expressed inside `int.star-contains`'s closed
lambda, so the exporter (`Cvc5LiaStarSolver.translate`) eliminates them
exactly before export (nesting depth ≤ 2, 30 s budget); each file's header
comment states whether elimination was exact. All 29 corpus files are either
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
outside the star) appears in `(= u0 (ite (= u21 1) var1 0))` *outside*
closed lambdas, instead of being bound per-summand inside the lambda with an
unconstrained fresh sum (which would weaken the formula).

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

## 3. Results (29 benchmarks, 100 s per file, sequential protocol)

All three solvers process the corpus **sequentially** — one file at a time,
one solver at a time — so durations are contention-free and comparable
(`run_all.sh --parallel` / `--jobs N` give the faster parallel mode; answers
are identical, only timings differ).

| solver | solved | sat | unsat | total time | median/file |
|---|---|---|---|---|---|
| SQLSolver (LiaSolver on the corpus) | **29/29** | 8 | 21 | 2.0 s | 0.01 s |
| cvc5 (liastar, Normaliz) | **29/29** | 8 | 21 | 1.9 s | 0.04 s |
| SLS-reachability | **29/29** | 8 | 21 | 14.7 s | 0.13 s |

**Complete consensus**: every solver solves every benchmark, with identical
verdicts on all 29 files — no timeouts, no crashes, no errors, and **zero
disagreements**.

**Ground truth** (`starfree_check.py`): 7/29 files have only
homogeneous-equality star bodies, where membership reduces *exactly* to a
star-free formula decidable without any star procedure. All three solvers
have **zero wrong answers** on that subset (`starfree_check.csv`).

Cactus plots: `cactus_plot.png`, `cactus_plot_log.png`; per-instance table:
`comparison.csv`; per-solver counts: `summary.csv`.

## 4. Reproduction

```
comparison/run_all.sh              # everything below, sequential (default)
comparison/run_all.sh --parallel   # same, overlapping solvers + 8 workers
comparison/regenerate_dumps.sh     # pipeline -> per-suite dumps
comparison/filter_linear.py        # linear corpus -> cvc5/linear/ (prunes dumps)
comparison/dedup_linear.py         # drop duplicates (incl. renamed copies)
comparison/run_roundtrip.sh        # SQLSolver  -> sql_solver.csv
comparison/run_cvc5.py [--jobs N]  # cvc5       -> cvc5_all.csv + .txt
comparison/run_sls.py  [--jobs N]  # SLS        -> sls_results.csv
comparison/starfree_check.py       # ground truth -> starfree_check.csv
comparison/make_comparison.py      # comparison.csv, summary.csv, cactus plots
```

Requirements: `./gradlew fatJar`; liastar cvc5 at `~/cvc5/liastar/build/bin/cvc5`
(Normaliz on) with Python bindings at `~/cvc5/liastar/build-python` (for SLS);
`~/sls-reachability`.

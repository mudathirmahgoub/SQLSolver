#!/bin/bash
# Full refresh of the linear-benchmark solver comparison, end to end:
#   1. regenerate_dumps.sh   SQLSolver pipeline -> per-suite smt2 dumps
#   2. filter_linear.py      keep the linear/nesting-free dumps -> cvc5/linear/
#                            (single flat corpus; suite dump files are pruned)
#   3. run_roundtrip.sh      SQLSolver LIA* solver on the corpus -> sql_solver.csv
#      run_cvc5.py           liastar cvc5 on the corpus          -> cvc5_all.csv + .txt
#      run_sls.py            sls-reachability on the corpus      -> sls_results.csv
#   4. starfree_check.py     exact-reduction ground-truth cross-check
#   5. make_comparison.py    comparison.csv / summary.csv / cactus plots
#
# By default every solver processes the corpus SEQUENTIALLY (one file at a
# time, one solver at a time) so the reported durations are contention-free
# and comparable across solvers. Pass --parallel to overlap the three solver
# legs and use 8 worker processes inside run_cvc5.py / run_sls.py — much
# faster wall clock, but per-file timings then include CPU contention.
#
# Requires: ./gradlew fatJar done, ~/cvc5/liastar built (binary + python
# bindings in build-python), ~/sls-reachability checked out.
set -euo pipefail
cd "$(dirname "$0")"

PARALLEL=0
[ "${1:-}" = "--parallel" ] && PARALLEL=1

./regenerate_dumps.sh
./filter_linear.py

if [ "$PARALLEL" = "1" ]; then
  ./run_roundtrip.sh &
  ./run_cvc5.py --jobs 8 &
  ./run_sls.py --jobs 8 &
  wait
else
  ./run_roundtrip.sh
  ./run_cvc5.py
  ./run_sls.py
fi

./starfree_check.py
./make_comparison.py

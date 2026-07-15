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
# Requires: ./gradlew fatJar done, ~/cvc5/liastar built (binary + python
# bindings in build-python), ~/sls-reachability checked out.
set -euo pipefail
cd "$(dirname "$0")"
./regenerate_dumps.sh
./filter_linear.py
./run_roundtrip.sh &
./run_cvc5.py &
./run_sls.py &
wait
./starfree_check.py
./make_comparison.py

#!/bin/bash
# Full refresh of the three-solver comparison, end to end:
#   1. regenerate_dumps.sh   SQLSolver pipeline -> fresh smt2 corpus + per-call CSVs
#   2. run_roundtrip.sh      SQLSolver LIA* solver on the dumps -> sql_solver.csv
#   3. run_cvc5.py           liastar cvc5 on the dumps          -> cvc5_all.csv + .txt
#   4. run_sls.sh            sls-reachability on the dumps      -> sls_results.csv
#   5. starfree_check.py     exact-reduction soundness cross-check
#   6. make_comparison.py    comparison.csv / summary.csv / cactus plots
#
# Steps 2-4 are independent and could run concurrently; they are sequential here
# for simplicity. Requires: ./gradlew fatJar done, ~/cvc5/liastar built (binary +
# python bindings), ~/sls-reachability checked out.
set -euo pipefail
cd "$(dirname "$0")"
./regenerate_dumps.sh
./run_roundtrip.sh &
./run_cvc5.py &
./run_sls.py &
wait
./starfree_check.py
./make_comparison.py

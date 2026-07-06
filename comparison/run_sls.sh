#!/bin/bash
# Run sls-reachability on every dump in the corpus (parallel driver).
# See run_sls.py for details; this wrapper exists for symmetry with the other
# run_* scripts and for the original serial harness reference
# (~/sls-reachability/run_sql.py, same per-file semantics).
set -uo pipefail
cd "$(dirname "$0")"
./run_sls.py "$@"

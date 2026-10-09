#!/usr/bin/env bash
# Führt die Tests aus (nutzt die Werkzeuge aus .tools/). Weitere Argumente gehen an Maven.
# Letzte Ausgabezeile: Testzusammenfassung (für das Ledger).
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
mkdir -p target
log=target/last-test.log
mvn -B -ntp test "$@" > "$log" 2>&1
rc=$?
grep -E "ERROR|FAIL" "$log" | head -40
summary=$(grep -E "^\[(INFO|WARNING|ERROR)\] Tests run:" "$log" | tail -1)
echo "${summary:-keine Testzusammenfassung}"
exit $rc

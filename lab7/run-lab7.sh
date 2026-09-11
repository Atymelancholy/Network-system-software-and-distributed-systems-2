#!/bin/sh
ROOT="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
export MPJ_HOME="${MPJ_HOME:-$ROOT/lib/mpj-v0_44}"
export PATH="$MPJ_HOME/bin:$PATH"

MODE="${1:-blocking}"
N="${2:-3072}"
NP="${3:-4}"
STRIPS="${4:-8}"

echo "MPJ_HOME=$MPJ_HOME"
echo "mode=$MODE n=$N np=$NP strips=$STRIPS"

mvn -q -f "$ROOT/pom.xml" compile || exit 1
java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -jar "$MPJ_HOME/lib/starter.jar" -np "$NP" -cp "$ROOT/target/classes" org.example.lab7.Lab7App "$MODE" "$N" "$STRIPS"

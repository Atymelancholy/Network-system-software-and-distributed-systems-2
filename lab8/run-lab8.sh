#!/bin/sh
ROOT="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
export MPJ_HOME="${MPJ_HOME:-$ROOT/lib/mpj-v0_44}"
export PATH="$MPJ_HOME/bin:$PATH"

GROUPS="${1:-2}"
N="${2:-1024}"
MODE="${3:-nonblocking}"
NP="${4:-4}"
STRIPS="${5:-8}"
DATADIR="${6:-data}"

echo "MPJ_HOME=$MPJ_HOME"
echo "groups=$GROUPS n=$N mode=$MODE np=$NP strips=$STRIPS datadir=$DATADIR"

mvn -q -f "$ROOT/pom.xml" compile || exit 1
java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -jar "$MPJ_HOME/lib/starter.jar" -np "$NP" -cp "$ROOT/target/classes" org.example.lab8.Lab8App "$GROUPS" "$N" "$MODE" "$STRIPS" "$DATADIR"

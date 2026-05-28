#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
while [[ ! -x "$ROOT_DIR/gradlew" && "$ROOT_DIR" != "/" ]]; do
  ROOT_DIR="$(dirname "$ROOT_DIR")"
done

if [[ ! -x "$ROOT_DIR/gradlew" ]]; then
  echo "Cannot find project root with gradlew" >&2
  exit 1
fi

JAVA11_HOME="${JAVA11_HOME:-${JDK11_HOME:-}}"
JAVA21_HOME="${JAVA21_HOME:-${JDK21_HOME:-}}"

if [[ -z "$JAVA11_HOME" || ! -x "$JAVA11_HOME/bin/java" ]]; then
  echo "Set JAVA11_HOME or JDK11_HOME to a Java 11 installation" >&2
  exit 1
fi

if [[ -z "$JAVA21_HOME" || ! -x "$JAVA21_HOME/bin/java" ]]; then
  echo "Set JAVA21_HOME or JDK21_HOME to a Java 21 installation" >&2
  exit 1
fi

OUT_DIR="${DAO_HASH_COMPARE_OUT_DIR:-$ROOT_DIR/temp/dao-hash-jdk-compare-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT_DIR"

cd "$ROOT_DIR"

"$ROOT_DIR/gradlew" -q :proto:generateProto >/dev/null
CLASSPATH="$("$ROOT_DIR/gradlew" -q :core:daoHashToolClasspath)"
TOOL_CLASSES="$OUT_DIR/classes"
TOOL_SOURCES="$OUT_DIR/sources.txt"
mkdir -p "$TOOL_CLASSES"

find "$ROOT_DIR/proto/build/generated/sources/proto/main/java/protobuf" -name '*.java' > "$TOOL_SOURCES"
find "$ROOT_DIR/core/src/test/java/bisq/core/dao/tools" -name '*.java' >> "$TOOL_SOURCES"

"$JAVA11_HOME/bin/javac" --release 11 -cp "$CLASSPATH" -d "$TOOL_CLASSES" @"$TOOL_SOURCES"

JAVA_ARGS=(--add-opens java.base/java.util=ALL-UNNAMED -cp "$TOOL_CLASSES:$CLASSPATH")
TOOL_CLASS="bisq.core.dao.tools.DaoHashJdkComparisonTool"
PROBE_CLASS="bisq.core.dao.tools.HashMapOrderPathProbe"

"$JAVA11_HOME/bin/java" "${JAVA_ARGS[@]}" "$TOOL_CLASS" --comparable-only "$@" > "$OUT_DIR/dao-java11.comparable.txt"
"$JAVA21_HOME/bin/java" "${JAVA_ARGS[@]}" "$TOOL_CLASS" --comparable-only "$@" > "$OUT_DIR/dao-java21.comparable.txt"

"$JAVA11_HOME/bin/java" "${JAVA_ARGS[@]}" "$TOOL_CLASS" "$@" > "$OUT_DIR/dao-java11.full.txt"
"$JAVA21_HOME/bin/java" "${JAVA_ARGS[@]}" "$TOOL_CLASS" "$@" > "$OUT_DIR/dao-java21.full.txt"

"$JAVA11_HOME/bin/java" "${JAVA_ARGS[@]}" "$PROBE_CLASS" --comparable-only > "$OUT_DIR/hashmap-path-java11.comparable.txt"
"$JAVA21_HOME/bin/java" "${JAVA_ARGS[@]}" "$PROBE_CLASS" --comparable-only > "$OUT_DIR/hashmap-path-java21.comparable.txt"

grep '^collector\.' "$OUT_DIR/hashmap-path-java11.comparable.txt" > "$OUT_DIR/hashmap-path-java11.collector.txt"
grep '^collector\.' "$OUT_DIR/hashmap-path-java21.comparable.txt" > "$OUT_DIR/hashmap-path-java21.collector.txt"

DAO_DIFF="$OUT_DIR/dao-java11-vs-java21.diff"
COLLECTOR_DIFF="$OUT_DIR/hashmap-collector-java11-vs-java21.diff"
PATH_DIFF="$OUT_DIR/hashmap-path-java11-vs-java21.diff"

set +e
diff -u "$OUT_DIR/dao-java11.comparable.txt" "$OUT_DIR/dao-java21.comparable.txt" > "$DAO_DIFF"
DAO_STATUS=$?
diff -u "$OUT_DIR/hashmap-path-java11.collector.txt" "$OUT_DIR/hashmap-path-java21.collector.txt" > "$COLLECTOR_DIFF"
COLLECTOR_STATUS=$?
diff -u "$OUT_DIR/hashmap-path-java11.comparable.txt" "$OUT_DIR/hashmap-path-java21.comparable.txt" > "$PATH_DIFF"
PATH_STATUS=$?
set -e

echo "Output directory: $OUT_DIR"

if [[ $PATH_STATUS -ne 0 ]]; then
  echo "HashMap bulk-path probe differs across JDKs; this is expected for constructor/putAll and documented in $PATH_DIFF"
else
  echo "HashMap path probe did not differ across JDKs."
fi

if [[ $DAO_STATUS -ne 0 ]]; then
  echo "DAO comparable output differs across Java 11 and Java 21. See $DAO_DIFF" >&2
  exit 2
fi

if [[ $COLLECTOR_STATUS -ne 0 ]]; then
  echo "Collectors.toMap HashMap path differs across Java 11 and Java 21. See $COLLECTOR_DIFF" >&2
  exit 2
fi

echo "DAO comparable output matches across Java 11 and Java 21."
echo "Collectors.toMap HashMap path matches across Java 11 and Java 21."

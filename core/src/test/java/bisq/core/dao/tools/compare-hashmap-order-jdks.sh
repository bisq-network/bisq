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

if [[ -z "$JAVA11_HOME" && -x /usr/libexec/java_home ]]; then
  JAVA11_HOME="$(/usr/libexec/java_home -v 11 2>/dev/null || true)"
fi

if [[ -z "$JAVA21_HOME" && -x /usr/libexec/java_home ]]; then
  JAVA21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
fi

if [[ -z "$JAVA11_HOME" || ! -x "$JAVA11_HOME/bin/java" || ! -x "$JAVA11_HOME/bin/javac" ]]; then
  echo "Set JAVA11_HOME or JDK11_HOME to a Java 11 JDK installation" >&2
  exit 1
fi

if [[ -z "$JAVA21_HOME" || ! -x "$JAVA21_HOME/bin/java" || ! -x "$JAVA21_HOME/bin/javac" ]]; then
  echo "Set JAVA21_HOME or JDK21_HOME to a Java 21 JDK installation" >&2
  exit 1
fi

OUT_DIR="${HASHMAP_ORDER_COMPARE_OUT_DIR:-$ROOT_DIR/temp/hashmap-order-jdk-compare-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT_DIR"

cd "$ROOT_DIR"

CLASSPATH="$("$ROOT_DIR/gradlew" -q :core:hashMapOrderToolClasspath)"
TOOL_CLASSES="$OUT_DIR/classes"
TOOL_SOURCES="$OUT_DIR/sources.txt"
mkdir -p "$TOOL_CLASSES"

{
  echo "$ROOT_DIR/core/src/main/java/bisq/core/encoding/canonical/CanonicalMapEntryIterator.java"
  echo "$ROOT_DIR/core/src/main/java/bisq/core/encoding/canonical/LegacyCollectorsToMapIterator.java"
  echo "$ROOT_DIR/core/src/test/java/bisq/core/dao/tools/HashMapIntrospection.java"
  echo "$ROOT_DIR/core/src/test/java/bisq/core/dao/tools/HashMapOrderPathProbe.java"
} > "$TOOL_SOURCES"

"$JAVA11_HOME/bin/javac" --release 11 -cp "$CLASSPATH" -d "$TOOL_CLASSES" @"$TOOL_SOURCES"

JAVA_ARGS=(--add-opens java.base/java.util=ALL-UNNAMED -cp "$TOOL_CLASSES:$CLASSPATH")
PROBE_CLASS="bisq.core.dao.tools.HashMapOrderPathProbe"

"$JAVA11_HOME/bin/java" "${JAVA_ARGS[@]}" "$PROBE_CLASS" --comparable-only > "$OUT_DIR/hashmap-order-java11.comparable.txt"
"$JAVA21_HOME/bin/java" "${JAVA_ARGS[@]}" "$PROBE_CLASS" --comparable-only > "$OUT_DIR/hashmap-order-java21.comparable.txt"

"$JAVA11_HOME/bin/java" "${JAVA_ARGS[@]}" "$PROBE_CLASS" > "$OUT_DIR/hashmap-order-java11.full.txt"
"$JAVA21_HOME/bin/java" "${JAVA_ARGS[@]}" "$PROBE_CLASS" > "$OUT_DIR/hashmap-order-java21.full.txt"

grep '\.collector\.' "$OUT_DIR/hashmap-order-java11.comparable.txt" > "$OUT_DIR/hashmap-order-java11.collector.txt"
grep '\.collector\.' "$OUT_DIR/hashmap-order-java21.comparable.txt" > "$OUT_DIR/hashmap-order-java21.collector.txt"
grep '\.legacyIterator\.' "$OUT_DIR/hashmap-order-java11.comparable.txt" > "$OUT_DIR/hashmap-order-java11.legacy-iterator.txt"
grep '\.legacyIterator\.' "$OUT_DIR/hashmap-order-java21.comparable.txt" > "$OUT_DIR/hashmap-order-java21.legacy-iterator.txt"

COLLECTOR_DIFF="$OUT_DIR/hashmap-collector-java11-vs-java21.diff"
ITERATOR_DIFF="$OUT_DIR/hashmap-legacy-iterator-java11-vs-java21.diff"
FULL_DIFF="$OUT_DIR/hashmap-path-java11-vs-java21.diff"

set +e
diff -u "$OUT_DIR/hashmap-order-java11.collector.txt" "$OUT_DIR/hashmap-order-java21.collector.txt" > "$COLLECTOR_DIFF"
COLLECTOR_STATUS=$?
diff -u "$OUT_DIR/hashmap-order-java11.legacy-iterator.txt" "$OUT_DIR/hashmap-order-java21.legacy-iterator.txt" > "$ITERATOR_DIFF"
ITERATOR_STATUS=$?
diff -u "$OUT_DIR/hashmap-order-java11.comparable.txt" "$OUT_DIR/hashmap-order-java21.comparable.txt" > "$FULL_DIFF"
FULL_STATUS=$?
set -e

echo "Output directory: $OUT_DIR"

if grep -q '\.collector\.matchesLegacyIterator=false' "$OUT_DIR/hashmap-order-java11.comparable.txt"; then
  echo "Java 11 Collectors.toMap HashMap order does not match LegacyCollectorsToMapIterator." >&2
  exit 2
fi

if grep -q '\.collector\.matchesLegacyIterator=false' "$OUT_DIR/hashmap-order-java21.comparable.txt"; then
  echo "Java 21 Collectors.toMap HashMap order does not match LegacyCollectorsToMapIterator." >&2
  exit 2
fi

if [[ $COLLECTOR_STATUS -ne 0 ]]; then
  echo "Collectors.toMap HashMap order differs across Java 11 and Java 21. See $COLLECTOR_DIFF" >&2
  exit 2
fi

if [[ $ITERATOR_STATUS -ne 0 ]]; then
  echo "LegacyCollectorsToMapIterator output differs across Java 11 and Java 21. See $ITERATOR_DIFF" >&2
  exit 2
fi

if [[ $FULL_STATUS -ne 0 ]]; then
  echo "HashMap construction paths differ across JDKs; constructor/putAll differences are diagnostic and documented in $FULL_DIFF"
else
  echo "All HashMap construction path diagnostics matched across JDKs."
fi

echo "Collectors.toMap HashMap order matches LegacyCollectorsToMapIterator on Java 11 and Java 21."

# DAO HashMap Order Tools

These tools validate the only remaining useful JDK-sensitive part of the DAO
hash migration: the legacy Java 11 `Collectors.toMap(...)` default `HashMap`
iteration order that the canonical encoder must mimic for DAO state maps.

## Files

- `HashMapOrderPathProbe.java` compares selected small key vectors across
  `HashMap(Map)`, `putAll`, `Collectors.toMap`, and
  `LegacyCollectorsToMapIterator`.
- `HashMapIntrospection.java` contains the reflection code that reads `HashMap.table`, `threshold`, `loadFactor`, bucket chain lengths, and tree-bin counts.
- `compare-hashmap-order-jdks.sh` runs the probe under Java 11 and Java 21. It
  fails if the `Collectors.toMap` HashMap order differs from
  `LegacyCollectorsToMapIterator`, or if either output
  differs across JDKs.

## Run

Set both JDK homes:

```bash
export JAVA11_HOME=/path/to/jdk-11
export JAVA21_HOME=/path/to/jdk-21
```

Run the comparison:

```bash
core/src/test/java/bisq/core/dao/tools/compare-hashmap-order-jdks.sh
```

The script writes full outputs and diffs below
`temp/hashmap-order-jdk-compare-*`. Exit code `0` means the comparable
collector and iterator outputs matched. Exit code `2` means the legacy-order
iterator no longer matches the JDK HashMap collector path, or the collector or
iterator output differed across JDKs.

## HashMap Internals Reported

`treeBins` and `maxBinLength` are read reflectively from `java.util.HashMap`:

```java
Field tableField = HashMap.class.getDeclaredField("table");
Object[] table = (Object[]) tableField.get(map);
```

For each non-null bucket, the tool walks the private `next` field on each node.
It counts the bucket length, tracks the maximum as `maxBinLength`, and counts a
bucket as a tree bin when the first node class is `java.util.HashMap$TreeNode`.

Because these are JDK internals, the Java command must include:

```bash
--add-opens java.base/java.util=ALL-UNNAMED
```

The script adds this automatically.

## What Would Cause Different Order

The serialized DAO bytes can differ if the intermediate `HashMap` iteration
order differs. Concrete causes are:

- different table length/capacity, because bucket index is `(tableLength - 1) & hash`;
- changed `HashMap.hash(Object)` spreading;
- changed `putVal` or `resize` behavior;
- different source encounter order for keys that collide into the same bucket;
- tree-bin behavior for heavily collided buckets;
- using `new HashMap<>(source)`, `putAll(source)`, or `clone()` instead of the current collector path, because those call `putMapEntries(...)`;
- protobuf changing away from insertion-order-preserving map storage, or enabling deterministic serialization on only some nodes.

The important JDK 11 -> 21 difference is the bulk `putMapEntries(...)` sizing
path. `Collectors.toMap(...)` uses `new HashMap<>()` and per-entry insertion,
so it avoids that changed path.

## Default Bucket Size and Rearrangement Thresholds

For `new HashMap<>()`:

- default table length after the first insertion: `16` buckets;
- default load factor: `0.75`;
- resize threshold at table length `16`: `12` entries;
- the 13th insertion resizes/rearranges the table to `32` buckets;
- later thresholds are `24`, `48`, `96`, and so on as capacity doubles.

For treeification:

- `TREEIFY_THRESHOLD = 8`;
- `MIN_TREEIFY_CAPACITY = 64`;
- when a bucket gets too deep before table length reaches `64`, `HashMap`
  resizes instead of treeifying;
- at table length `64` or greater, adding another entry to a bucket that has
  reached the treeification threshold can convert that bucket to a tree bin.

For the bulk constructor path, the JDK 11 -> 21 sizing fix is enough to change
order. With a 12-entry source map, JDK 11 pre-sizes to `32` buckets while JDK 21
pre-sizes to `16` buckets. That changes bucket scan order even though the key
set is identical.

# DAO Hash JDK Comparison Tools

These tools compare the DAO-state protobuf serialization path under two JDKs.
They are diagnostic tools, not production code.

## Files

- `DaoHashJdkComparisonTool.java` loads a `DaoStateStore` and BSQ block bucket files, rebuilds the hash-relevant protobuf bytes, prints byte hashes, and prints the intermediate `HashMap` layout used by `getBsqStateBuilderExcludingBlocks()`. It deliberately uses only protobuf classes and JDK APIs so the script can compile it as Java 11 bytecode and run the same classes under both JDKs.
- `HashMapOrderPathProbe.java` demonstrates the JDK 11 -> 21 `HashMap` path difference: `HashMap(Map)` / `putAll` can differ, while the collector path used by the DAO should remain stable.
- `HashMapIntrospection.java` contains the reflection code that reads `HashMap.table`, `threshold`, `loadFactor`, bucket chain lengths, and tree-bin counts.
- `compare-dao-hash-jdks.sh` runs the tools under Java 11 and Java 21 and fails if the comparable DAO output differs.

## Run

Set both JDK homes:

```bash
export JAVA11_HOME=/path/to/jdk-11
export JAVA21_HOME=/path/to/jdk-21
```

Run the default mainnet resource snapshot:

```bash
core/src/test/java/bisq/core/dao/tools/compare-dao-hash-jdks.sh
```

Run a specific block height:

```bash
core/src/test/java/bisq/core/dao/tools/compare-dao-hash-jdks.sh \
  --from-height 949481 \
  --to-height 949481
```

Run a range of block heights:

```bash
core/src/test/java/bisq/core/dao/tools/compare-dao-hash-jdks.sh \
  --from-height 949001 \
  --to-height 949481
```

Use custom files:

```bash
core/src/test/java/bisq/core/dao/tools/compare-dao-hash-jdks.sh \
  --dao-state-store /path/to/DaoStateStore_BTC_MAINNET \
  --blocks-dir /path/to/BsqBlocks_BTC_MAINNET \
  --from-height 949481
```

The script writes full outputs and diffs below `temp/dao-hash-jdk-compare-*`.
Exit code `0` means the comparable DAO outputs matched. Exit code `2` means
Java 11 and Java 21 produced different comparable DAO output.

The `hash.<height>.stateBytesSha256` lines are SHA-256 fingerprints of the
serialized bytes used for JDK-to-JDK byte identity checks. They are not the DAO
network hash-chain link, which is `RIPEMD160(SHA256(previousHash || stateBytes))`.

The script compiles the tool classes and generated protobuf sources into a
temporary Java-11-compatible class directory under the output directory. This is
necessary because the normal project test classes may be Java 21 bytecode and
cannot be loaded by a Java 11 runtime.

## What the Height Range Means

The tool loads one DAO state snapshot from `DaoStateStore`, sorts map entries by
string key to mirror the DAO `TreeMap` order, collects them through
`Collectors.toMap(...)`, and then serializes that same snapshot with each
selected block as the single `blocks` entry. This matches the map-order behavior
and output shape of `DaoState.getSerializedStateForHashChain()` without loading
the Java-21-compiled production classes into the Java 11 runtime.

For the default resource files this is the correct current snapshot check at the
snapshot chain height. A wider range is useful for detecting JDK-dependent map
ordering while varying the last-block bytes, but it is not a historical DAO
replay. To validate historical state at multiple heights, provide snapshots for
those heights or replay the parser separately.

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

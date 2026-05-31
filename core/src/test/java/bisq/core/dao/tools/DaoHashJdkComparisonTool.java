/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.tools;

import protobuf.BaseTxOutput;
import protobuf.PersistableEnvelope;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DaoHashJdkComparisonTool {
    private static final Pattern BLOCK_BUCKET_FILE = Pattern.compile("BsqBlocks_(\\d+)-(\\d+)");
    private static final int BSQ_BLOCK_BUCKET_SIZE = 1000;

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        if (options.help) {
            printUsage();
            return;
        }

        protobuf.DaoState daoStateProto = readDaoState(options.daoStateStore);
        int snapshotHeight = daoStateProto.getChainHeight();
        int fromHeight = options.fromHeight == null ? snapshotHeight : options.fromHeight;
        int toHeight = options.toHeight == null ? snapshotHeight : options.toHeight;
        if (fromHeight > toHeight) {
            throw new IllegalArgumentException("--from-height must be <= --to-height");
        }

        List<protobuf.BaseBlock> selectedBlocks = readBlocks(options.blocksDir, fromHeight, toHeight);
        if (selectedBlocks.isEmpty()) {
            throw new IllegalArgumentException("No BSQ blocks found for range " + fromHeight + "-" + toHeight +
                    " in " + options.blocksDir);
        }

        HashMap<String, BaseTxOutput> unspentTxOutputMap = collectLikeDaoBuilder(daoStateProto.getUnspentTxOutputMapMap());
        HashMap<String, protobuf.SpentInfo> spentInfoMap = collectLikeDaoBuilder(daoStateProto.getSpentInfoMapMap());
        HashMap<String, protobuf.Issuance> issuanceMap = collectLikeDaoBuilder(daoStateProto.getIssuanceMapMap());
        protobuf.DaoState baseStateExcludingBlocks = buildStateExcludingBlocks(daoStateProto,
                unspentTxOutputMap,
                spentInfoMap,
                issuanceMap);

        if (!options.comparableOnly) {
            System.out.println("runtime.java.version=" + System.getProperty("java.version"));
            System.out.println("runtime.java.home=" + System.getProperty("java.home"));
            System.out.println("input.daoStateStore=" + options.daoStateStore.toAbsolutePath());
            System.out.println("input.blocksDir=" + options.blocksDir.toAbsolutePath());
        }

        printConstants();
        System.out.println("snapshot.chainHeight=" + snapshotHeight);
        System.out.println("range.fromHeight=" + fromHeight);
        System.out.println("range.toHeight=" + toHeight);
        System.out.println("range.selectedBlockCount=" + selectedBlocks.size());

        printMapStats("unspentTxOutputMap", unspentTxOutputMap, options.sampleKeys);
        printMapStats("spentInfoMap", spentInfoMap, options.sampleKeys);
        printMapStats("issuanceMap", issuanceMap, options.sampleKeys);

        for (protobuf.BaseBlock block : selectedBlocks) {
            protobuf.DaoState stateForHash = baseStateExcludingBlocks.toBuilder()
                    .setChainHeight(block.getHeight())
                    .clearBlocks()
                    .addBlocks(block)
                    .build();
            byte[] stateBytes = stateForHash.toByteArray();
            System.out.println("hash." + block.getHeight() + ".serializedBytes=" + stateBytes.length);
            System.out.println("hash." + block.getHeight() + ".stateBytesSha256=" +
                    HashMapIntrospection.toHex(HashMapIntrospection.sha256(stateBytes)));
        }
    }

    private static void printConstants() {
        System.out.println("hashMap.defaultInitialCapacity=" + HashMapIntrospection.DEFAULT_INITIAL_CAPACITY);
        System.out.println("hashMap.defaultLoadFactor=" + HashMapIntrospection.DEFAULT_LOAD_FACTOR);
        System.out.println("hashMap.defaultResizeThreshold=" + HashMapIntrospection.DEFAULT_RESIZE_THRESHOLD);
        System.out.println("hashMap.firstDefaultResizeInsertion=" + HashMapIntrospection.FIRST_DEFAULT_RESIZE_INSERTION);
        System.out.println("hashMap.treeifyThreshold=" + HashMapIntrospection.TREEIFY_THRESHOLD);
        System.out.println("hashMap.untreeifyThreshold=" + HashMapIntrospection.UNTREEIFY_THRESHOLD);
        System.out.println("hashMap.minTreeifyCapacity=" + HashMapIntrospection.MIN_TREEIFY_CAPACITY);
    }

    private static protobuf.DaoState readDaoState(Path daoStateStore) throws IOException {
        try (FileInputStream in = new FileInputStream(daoStateStore.toFile())) {
            PersistableEnvelope envelope = PersistableEnvelope.parseDelimitedFrom(in);
            if (envelope == null || !envelope.hasDaoStateStore()) {
                throw new IllegalArgumentException("File is not a DaoStateStore PersistableEnvelope: " + daoStateStore);
            }
            return envelope.getDaoStateStore().getDaoState();
        }
    }

    private static List<protobuf.BaseBlock> readBlocks(Path blocksDir, int fromHeight, int toHeight) throws IOException {
        List<Path> bucketFiles = findBucketFiles(blocksDir, fromHeight, toHeight);
        List<protobuf.BaseBlock> blocks = new ArrayList<>();
        for (Path bucketFile : bucketFiles) {
            try (FileInputStream in = new FileInputStream(bucketFile.toFile())) {
                PersistableEnvelope envelope = PersistableEnvelope.parseDelimitedFrom(in);
                if (envelope == null || !envelope.hasBsqBlockStore()) {
                    throw new IllegalArgumentException("File is not a BsqBlockStore PersistableEnvelope: " + bucketFile);
                }
                envelope.getBsqBlockStore().getBlocksList().stream()
                        .filter(block -> block.getHeight() >= fromHeight && block.getHeight() <= toHeight)
                        .forEach(blocks::add);
            }
        }
        Collections.sort(blocks, Comparator.comparingInt(protobuf.BaseBlock::getHeight));
        return blocks;
    }

    private static List<Path> findBucketFiles(Path blocksDir, int fromHeight, int toHeight) throws IOException {
        int fromBucket = fromHeight <= 0 ? 0 : (fromHeight - 1) / BSQ_BLOCK_BUCKET_SIZE + 1;
        int toBucket = toHeight <= 0 ? 0 : (toHeight - 1) / BSQ_BLOCK_BUCKET_SIZE + 1;
        List<Path> bucketFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(blocksDir)) {
            for (Path path : stream) {
                Matcher matcher = BLOCK_BUCKET_FILE.matcher(path.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                int first = Integer.parseInt(matcher.group(1));
                int bucket = first / BSQ_BLOCK_BUCKET_SIZE + 1;
                if (bucket >= fromBucket && bucket <= toBucket) {
                    bucketFiles.add(path);
                }
            }
        }
        Collections.sort(bucketFiles);
        return bucketFiles;
    }

    private static protobuf.DaoState buildStateExcludingBlocks(protobuf.DaoState source,
                                                               Map<String, BaseTxOutput> unspentTxOutputMap,
                                                               Map<String, protobuf.SpentInfo> spentInfoMap,
                                                               Map<String, protobuf.Issuance> issuanceMap) {
        return protobuf.DaoState.newBuilder()
                .setChainHeight(source.getChainHeight())
                .addAllCycles(source.getCyclesList())
                .putAllUnspentTxOutputMap(unspentTxOutputMap)
                .putAllSpentInfoMap(spentInfoMap)
                .addAllConfiscatedLockupTxList(source.getConfiscatedLockupTxListList())
                .putAllIssuanceMap(issuanceMap)
                .addAllParamChangeList(source.getParamChangeListList())
                .addAllEvaluatedProposalList(source.getEvaluatedProposalListList())
                .addAllDecryptedBallotsWithMeritsList(source.getDecryptedBallotsWithMeritsListList())
                .build();
    }

    private static <V> HashMap<String, V> collectLikeDaoBuilder(Map<String, V> source) {
        TreeMap<String, V> sortedLikeDaoStateTreeMap = new TreeMap<>(source);
        Map<String, V> map = sortedLikeDaoStateTreeMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return requireHashMap(map);
    }

    @SuppressWarnings("unchecked")
    private static <V> HashMap<String, V> requireHashMap(Map<String, V> map) {
        if (!(map instanceof HashMap)) {
            throw new IllegalStateException("Collectors.toMap produced " + map.getClass().getName() +
                    ", not java.util.HashMap");
        }
        return (HashMap<String, V>) map;
    }

    private static void printMapStats(String name, HashMap<String, ?> map, int sampleKeys) {
        HashMapIntrospection.Stats stats = HashMapIntrospection.inspect(map);
        System.out.println("map." + name + ".size=" + stats.size);
        System.out.println("map." + name + ".tableLength=" + stats.tableLength);
        System.out.println("map." + name + ".threshold=" + stats.threshold);
        System.out.println("map." + name + ".loadFactor=" + stats.loadFactor);
        System.out.println("map." + name + ".nonEmptyBins=" + stats.nonEmptyBins);
        System.out.println("map." + name + ".maxBinLength=" + stats.maxBinLength);
        System.out.println("map." + name + ".treeBins=" + stats.treeBins);
        System.out.println("map." + name + ".iterationSha256=" + HashMapIntrospection.keyIterationFingerprint(map));
        if (sampleKeys > 0) {
            System.out.println("map." + name + ".firstKeys=" + HashMapIntrospection.keySample(map, sampleKeys));
        }
    }

    private static void printUsage() {
        System.out.println("Usage: DaoHashJdkComparisonTool [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --dao-state-store <file>   DaoStateStore file. Default: p2p/src/main/resources/DaoStateStore_BTC_MAINNET");
        System.out.println("  --blocks-dir <dir>         BsqBlocks directory. Default: p2p/src/main/resources/BsqBlocks_BTC_MAINNET");
        System.out.println("  --from-height <height>     First block height to use as the last block in diagnostic serialization.");
        System.out.println("  --to-height <height>       Last block height. Default: same as --from-height, or snapshot chain height.");
        System.out.println("  --sample-keys <count>      Number of first HashMap iteration keys to print. Default: 3");
        System.out.println("  --comparable-only          Omit runtime and absolute path lines for JDK-to-JDK diffing.");
        System.out.println("  --help                     Show this text.");
    }

    private static final class Options {
        Path daoStateStore = Paths.get("p2p/src/main/resources/DaoStateStore_BTC_MAINNET");
        Path blocksDir = Paths.get("p2p/src/main/resources/BsqBlocks_BTC_MAINNET");
        Integer fromHeight;
        Integer toHeight;
        int sampleKeys = 3;
        boolean comparableOnly;
        boolean help;

        static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--dao-state-store".equals(arg)) {
                    options.daoStateStore = Paths.get(nextValue(args, ++i, arg));
                } else if ("--blocks-dir".equals(arg)) {
                    options.blocksDir = Paths.get(nextValue(args, ++i, arg));
                } else if ("--from-height".equals(arg)) {
                    options.fromHeight = Integer.parseInt(nextValue(args, ++i, arg));
                } else if ("--to-height".equals(arg)) {
                    options.toHeight = Integer.parseInt(nextValue(args, ++i, arg));
                } else if ("--sample-keys".equals(arg)) {
                    options.sampleKeys = Integer.parseInt(nextValue(args, ++i, arg));
                } else if ("--comparable-only".equals(arg)) {
                    options.comparableOnly = true;
                } else if ("--help".equals(arg) || "-h".equals(arg)) {
                    options.help = true;
                } else {
                    throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }

            if (options.fromHeight == null && options.toHeight != null) {
                options.fromHeight = options.toHeight;
            }
            if (options.fromHeight != null && options.toHeight == null) {
                options.toHeight = options.fromHeight;
            }
            return options;
        }

        private static String nextValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }
}

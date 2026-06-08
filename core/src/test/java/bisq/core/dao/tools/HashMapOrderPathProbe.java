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

import bisq.core.encoding.canonical.LegacyCollectorsToMapIterator;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HashMapOrderPathProbe {
    private static final List<Case> CASES = Arrays.asList(
            new Case("bulkPathRegression", Arrays.asList(
                    "31zf35rkuu6yf",
                    "1dbjp2a6kyqox",
                    "3b4553177plvg",
                    "bnvj3t0lpa79",
                    "3hr4261uk5i8i",
                    "2r912h4yyjucd",
                    "3m5w96199x87i",
                    "2uent5s0widco",
                    "gx5ryb218zq7",
                    "1ctcw65e5psnx",
                    "2hx74lb7768rt",
                    "2wzuozo92awxl")),
            new Case("daoMapVector", Arrays.asList(
                    "k00", "k01", "k02", "k03", "k04", "k05", "k06", "k07", "k08", "k09",
                    "k10", "k11", "k12", "k13", "k14", "k15", "k16", "k17", "k18", "k19",
                    "k20", "k21", "k22", "k23", "k24", "k25", "k26", "k27", "k28", "k29",
                    "k30", "k31", "k32", "k33", "k34", "k35", "k36", "k37", "k38", "k39")),
            new Case("treeifiedCollisions", getSameHashStrings(4))
    );

    public static void main(String[] args) {
        boolean comparableOnly = args.length == 1 && "--comparable-only".equals(args[0]);
        if (!comparableOnly) {
            System.out.println("runtime.java.version=" + System.getProperty("java.version"));
            System.out.println("runtime.java.home=" + System.getProperty("java.home"));
        }

        printConstants();

        for (Case testCase : CASES) {
            printCase(testCase);
        }
    }

    private static void printCase(Case testCase) {
        LinkedHashMap<String, Integer> source = new LinkedHashMap<>();
        for (int i = 0; i < testCase.keys.size(); i++) {
            source.put(testCase.keys.get(i), i);
        }
        HashMap<String, Integer> viaConstructor = new HashMap<>(source);
        HashMap<String, Integer> viaPutAll = new HashMap<>();
        viaPutAll.putAll(source);
        Map<String, Integer> collectorMap = source.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!(collectorMap instanceof HashMap)) {
            throw new IllegalStateException("Collectors.toMap produced " + collectorMap.getClass().getName());
        }
        @SuppressWarnings("unchecked")
        HashMap<String, Integer> viaCollector = (HashMap<String, Integer>) collectorMap;

        List<String> legacyIteratorOrder =
                LegacyCollectorsToMapIterator.getJava11HashMapIterationOrder(testCase.keys);

        System.out.println("case." + testCase.name + ".source.size=" + source.size());
        printHashMapPath(testCase.name, "constructor", viaConstructor, legacyIteratorOrder);
        printHashMapPath(testCase.name, "putAll", viaPutAll, legacyIteratorOrder);
        printHashMapPath(testCase.name, "collector", viaCollector, legacyIteratorOrder);
        printLegacyIterator(testCase.name, legacyIteratorOrder);
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

    private static void printHashMapPath(String caseName,
                                         String label,
                                         HashMap<String, Integer> map,
                                         List<String> legacyIteratorOrder) {
        HashMapIntrospection.Stats stats = HashMapIntrospection.inspect(map);
        List<String> keys = new ArrayList<>(map.keySet());
        String prefix = "case." + caseName + "." + label;
        System.out.println(prefix + ".size=" + stats.size);
        System.out.println(prefix + ".tableLength=" + stats.tableLength);
        System.out.println(prefix + ".threshold=" + stats.threshold);
        System.out.println(prefix + ".maxBinLength=" + stats.maxBinLength);
        System.out.println(prefix + ".treeBins=" + stats.treeBins);
        System.out.println(prefix + ".iterationSha256=" + listFingerprint(keys));
        System.out.println(prefix + ".matchesLegacyIterator=" + keys.equals(legacyIteratorOrder));
        System.out.println(prefix + ".keys=" + keys);
    }

    private static void printLegacyIterator(String caseName, List<String> legacyIteratorOrder) {
        String prefix = "case." + caseName + ".legacyIterator";
        System.out.println(prefix + ".iterationSha256=" + listFingerprint(legacyIteratorOrder));
        System.out.println(prefix + ".keys=" + legacyIteratorOrder);
    }

    private static List<String> getSameHashStrings(int parts) {
        List<String> result = new ArrayList<>();
        int count = 1 << parts;
        for (int i = 0; i < count; i++) {
            StringBuilder stringBuilder = new StringBuilder();
            for (int bit = parts - 1; bit >= 0; bit--) {
                stringBuilder.append(((i >>> bit) & 1) == 0 ? "Aa" : "BB");
            }
            result.add(stringBuilder.toString());
        }
        result.sort(String::compareTo);
        return result;
    }

    private static String listFingerprint(List<String> keys) {
        MessageDigest digest = sha256();
        for (String key : keys) {
            byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return HashMapIntrospection.toHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Case {
        private final String name;
        private final List<String> keys;

        private Case(String name, List<String> keys) {
            this.name = name;
            this.keys = new ArrayList<>(keys);
        }
    }
}

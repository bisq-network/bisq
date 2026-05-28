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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class HashMapOrderPathProbe {
    private static final String[] KEYS = {
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
            "2wzuozo92awxl"
    };

    public static void main(String[] args) {
        boolean comparableOnly = args.length == 1 && "--comparable-only".equals(args[0]);
        if (!comparableOnly) {
            System.out.println("runtime.java.version=" + System.getProperty("java.version"));
            System.out.println("runtime.java.home=" + System.getProperty("java.home"));
        }

        printConstants();

        LinkedHashMap<String, Integer> source = new LinkedHashMap<>();
        for (int i = 0; i < KEYS.length; i++) {
            source.put(KEYS[i], i);
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

        System.out.println("source.size=" + source.size());
        print("constructor", viaConstructor);
        print("putAll", viaPutAll);
        print("collector", viaCollector);
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

    private static void print(String label, HashMap<String, Integer> map) {
        HashMapIntrospection.Stats stats = HashMapIntrospection.inspect(map);
        System.out.println(label + ".size=" + stats.size);
        System.out.println(label + ".tableLength=" + stats.tableLength);
        System.out.println(label + ".threshold=" + stats.threshold);
        System.out.println(label + ".maxBinLength=" + stats.maxBinLength);
        System.out.println(label + ".treeBins=" + stats.treeBins);
        System.out.println(label + ".iterationSha256=" + HashMapIntrospection.keyIterationFingerprint(map));
        System.out.println(label + ".keys=" + map.keySet());
    }
}

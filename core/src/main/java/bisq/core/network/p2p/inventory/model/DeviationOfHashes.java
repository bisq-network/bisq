/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.network.p2p.inventory.model;

import bisq.common.util.Tuple2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public class DeviationOfHashes implements DeviationType {
    public DeviationSeverity getDeviationSeverity(Collection<List<RequestInfo>> collection,
                                                  @Nullable String value,
                                                  InventoryItem inventoryItem,
                                                  String currentBlockHeight) {
        DeviationSeverity deviationSeverity = DeviationSeverity.OK;
        if (value == null) {
            return deviationSeverity;
        }

        Map<String, Integer> sameHashesPerHashListByHash = new HashMap<>();
        collection.stream()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(list.size() - 1)) // We use last item only
                .map(RequestInfo::getDataMap)
                .filter(map -> currentBlockHeight.equals(map.get(InventoryItem.daoStateChainHeight).getValue()))
                .map(map -> map.get(inventoryItem).getValue())
                .filter(Objects::nonNull)
                .forEach(v -> {
                    sameHashesPerHashListByHash.putIfAbsent(v, 0);
                    int prev = sameHashesPerHashListByHash.get(v);
                    sameHashesPerHashListByHash.put(v, prev + 1);
                });
        if (sameHashesPerHashListByHash.size() > 1) {
            List<Tuple2<String, Integer>> sameHashesPerHashList = new ArrayList<>();
            sameHashesPerHashListByHash.forEach((k, v) -> sameHashesPerHashList.add(new Tuple2<>(k, v)));
            sameHashesPerHashList.sort(Comparator.comparing(o -> o.second));
            Collections.reverse(sameHashesPerHashList);

            // It could be that first and any following list entry has same number of hashes, but we ignore that as
            // it is reason enough to alert the operators in case not all hashes are the same.
            if (sameHashesPerHashList.get(0).first.equals(value)) {
                // We are in the majority group.
                // We also set a warning to make sure the operators act quickly and to check if there are
                // more severe issues.
                deviationSeverity = DeviationSeverity.WARN;
            } else {
                deviationSeverity = DeviationSeverity.ALERT;
            }
        }
        return deviationSeverity;
    }
}

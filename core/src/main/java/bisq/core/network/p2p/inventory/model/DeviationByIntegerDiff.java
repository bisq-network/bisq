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

public class DeviationByIntegerDiff implements DeviationType {
    private final int warnTrigger;
    private final int alertTrigger;

    public DeviationByIntegerDiff(int warnTrigger, int alertTrigger) {
        this.warnTrigger = warnTrigger;
        this.alertTrigger = alertTrigger;
    }

    public DeviationSeverity getDeviationSeverity(Collection<List<RequestInfo>> collection,
                                                  @Nullable String value,
                                                  InventoryItem inventoryItem) {
        DeviationSeverity deviationSeverity = DeviationSeverity.OK;
        if (value == null) {
            return deviationSeverity;
        }

        Map<String, Integer> sameItemsByValue = new HashMap<>();
        collection.stream()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(list.size() - 1)) // We use last item only
                .map(RequestInfo::getDataMap)
                .map(e -> e.get(inventoryItem).getValue())
                .filter(Objects::nonNull)
                .forEach(e -> {
                    sameItemsByValue.putIfAbsent(e, 0);
                    int prev = sameItemsByValue.get(e);
                    sameItemsByValue.put(e, prev + 1);
                });
        if (sameItemsByValue.size() > 1) {
            List<Tuple2<String, Integer>> sameItems = new ArrayList<>();
            sameItemsByValue.forEach((k, v) -> sameItems.add(new Tuple2<>(k, v)));
            sameItems.sort(Comparator.comparing(o -> o.second));
            Collections.reverse(sameItems);
            String majority = sameItems.get(0).first;
            if (!majority.equals(value)) {
                int majorityAsInt = Integer.parseInt(majority);
                int valueAsInt = Integer.parseInt(value);
                int diff = Math.abs(majorityAsInt - valueAsInt);
                if (diff >= alertTrigger) {
                    deviationSeverity = DeviationSeverity.ALERT;
                } else if (diff >= warnTrigger) {
                    deviationSeverity = DeviationSeverity.WARN;
                } else {
                    deviationSeverity = DeviationSeverity.OK;
                }
            }
        }
        return deviationSeverity;
    }
}

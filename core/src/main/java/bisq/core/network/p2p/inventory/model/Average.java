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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Average {
    public static Map<InventoryItem, Double> of(Set<RequestInfo> requestInfoSet) {
        Map<InventoryItem, Double> averageValuesPerItem = new HashMap<>();
        Arrays.asList(InventoryItem.values()).forEach(inventoryItem -> {
            if (inventoryItem.isNumberValue()) {
                averageValuesPerItem.put(inventoryItem, getAverage(requestInfoSet, inventoryItem));
            }
        });
        return averageValuesPerItem;
    }

    public static double getAverage(Set<RequestInfo> requestInfoSet, InventoryItem inventoryItem) {
        return requestInfoSet.stream()
                .map(RequestInfo::getDataMap)
                .filter(map -> map.containsKey(inventoryItem))
                .map(map -> map.get(inventoryItem).getValue())
                .filter(Objects::nonNull)
                .mapToDouble(Double::parseDouble)
                .average()
                .orElse(0d);
    }
}

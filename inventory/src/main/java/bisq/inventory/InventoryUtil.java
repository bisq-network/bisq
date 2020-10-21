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

package bisq.inventory;

import bisq.core.network.p2p.inventory.DeviationSeverity;
import bisq.core.network.p2p.inventory.InventoryItem;

import bisq.network.p2p.NodeAddress;

import bisq.common.util.Tuple2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class InventoryUtil {

    public static Map<InventoryItem, Double> getAverageValues(Set<RequestInfo> requestInfoSetOfOtherNodes) {
        Map<InventoryItem, Double> averageValuesPerItem = new HashMap<>();
        Arrays.asList(InventoryItem.values()).forEach(inventoryItem -> {
            if (inventoryItem.getType().equals(Integer.class)) {
                averageValuesPerItem.put(inventoryItem, getAverageFromIntegerValues(requestInfoSetOfOtherNodes, inventoryItem));
            } else if (inventoryItem.getType().equals(Long.class)) {
                averageValuesPerItem.put(inventoryItem, getAverageFromLongValues(requestInfoSetOfOtherNodes, inventoryItem));
            } else if (inventoryItem.getType().equals(Double.class)) {
                averageValuesPerItem.put(inventoryItem, getAverageFromDoubleValues(requestInfoSetOfOtherNodes, inventoryItem));
            }
            // If type of value is String we ignore it
        });
        return averageValuesPerItem;
    }

    public static double getAverageFromIntegerValues(Set<RequestInfo> requestInfoSetOfOtherNodes,
                                                     InventoryItem inventoryItem) {
        checkArgument(inventoryItem.getType().equals(Integer.class));
        return requestInfoSetOfOtherNodes.stream()
                .map(RequestInfo::getInventory)
                .filter(inventory -> inventory.containsKey(inventoryItem))
                .mapToInt(inventory -> Integer.parseInt(inventory.get(inventoryItem)))
                .average()
                .orElse(0d);
    }

    public static double getAverageFromLongValues(Set<RequestInfo> requestInfoSetOfOtherNodes,
                                                  InventoryItem inventoryItem) {
        checkArgument(inventoryItem.getType().equals(Long.class));
        return requestInfoSetOfOtherNodes.stream()
                .map(RequestInfo::getInventory)
                .filter(inventory -> inventory.containsKey(inventoryItem))
                .mapToLong(inventory -> Long.parseLong(inventory.get(inventoryItem)))
                .average()
                .orElse(0d);
    }

    public static double getAverageFromDoubleValues(Set<RequestInfo> requestInfoSetOfOtherNodes,
                                                    InventoryItem inventoryItem) {
        checkArgument(inventoryItem.getType().equals(Double.class));
        return requestInfoSetOfOtherNodes.stream()
                .map(RequestInfo::getInventory)
                .filter(inventory -> inventory.containsKey(inventoryItem))
                .mapToDouble(inventory -> Double.parseDouble((inventory.get(inventoryItem))))
                .average()
                .orElse(0d);
    }


    public static DeviationSeverity getDeviationSeverityByIntegerDistance(Map<NodeAddress, List<RequestInfo>> map,
                                                                          RequestInfo requestInfo,
                                                                          InventoryItem inventoryItem,
                                                                          int warnTrigger,
                                                                          int alertTrigger) {
        DeviationSeverity deviationSeverity = DeviationSeverity.OK;
        Map<String, Integer> sameItemsByValue = new HashMap<>();
        map.values().stream()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(list.size() - 1)) // We use last item only
                .map(RequestInfo::getInventory)
                .map(e -> e.get(inventoryItem))
                .forEach(e -> {
                    sameItemsByValue.putIfAbsent(e, 0);
                    int prev = sameItemsByValue.get(e);
                    sameItemsByValue.put(e, prev + 1);
                });
        if (sameItemsByValue.size() > 1) {
            List<Tuple2<String, Integer>> sameItems = new ArrayList<>();
            sameItemsByValue.forEach((key, value) -> sameItems.add(new Tuple2<>(key, value)));
            sameItems.sort(Comparator.comparing(o -> o.second));
            Collections.reverse(sameItems);
            String majority = sameItems.get(0).first;
            String candidate = requestInfo.getInventory().get(inventoryItem);
            if (!majority.equals(candidate)) {
                int majorityAsInt = Integer.parseInt(majority);
                int candidateAsInt = Integer.parseInt(candidate);
                int diff = Math.abs(majorityAsInt - candidateAsInt);
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

    public static DeviationSeverity getDeviationSeverityForHash(Map<NodeAddress, List<RequestInfo>> map,
                                                                String daoStateChainHeightAsString,
                                                                RequestInfo requestInfo,
                                                                InventoryItem inventoryItem) {
        DeviationSeverity deviationSeverity = DeviationSeverity.OK;
        Map<String, Integer> sameHashesPerHashListByHash = new HashMap<>();
        map.values().stream()
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(list.size() - 1)) // We use last item only
                .map(RequestInfo::getInventory)
                .filter(inventoryMap -> inventoryMap.get(InventoryItem.daoStateChainHeight).equals(daoStateChainHeightAsString))
                .map(inventoryMap -> inventoryMap.get(inventoryItem))
                .forEach(value -> {
                    sameHashesPerHashListByHash.putIfAbsent(value, 0);
                    int prev = sameHashesPerHashListByHash.get(value);
                    sameHashesPerHashListByHash.put(value, prev + 1);
                });
        if (sameHashesPerHashListByHash.size() > 1) {
            List<Tuple2<String, Integer>> sameHashesPerHashList = new ArrayList<>();
            sameHashesPerHashListByHash.forEach((key, value) -> sameHashesPerHashList.add(new Tuple2<>(key, value)));
            sameHashesPerHashList.sort(Comparator.comparing(o -> o.second));
            Collections.reverse(sameHashesPerHashList);

            // It could be that first and any following list entry has same number of hashes, but we ignore that as
            // it is reason enough to alert the operators in case not all hashes are the same.
            if (sameHashesPerHashList.get(0).first.equals(requestInfo.getInventory().get(inventoryItem))) {
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

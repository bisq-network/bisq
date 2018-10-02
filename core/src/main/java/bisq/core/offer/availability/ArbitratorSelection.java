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

package bisq.core.offer.availability;

import bisq.core.arbitration.ArbitratorManager;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.common.util.Tuple2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArbitratorSelection {
    public static String getLeastUsedArbitrator(TradeStatisticsManager tradeStatisticsManager,
                                                ArbitratorManager arbitratorManager) {
        List<TradeStatistics2> list = new ArrayList<>(tradeStatisticsManager.getObservableTradeStatisticsSet());
        list.sort(Comparator.comparing(TradeStatistics2::getTradeDate));
        Collections.reverse(list);
        log.error("first  " + list.get(0).getTradeDate());
        log.error("last   " + list.get(list.size() - 1).getTradeDate());
        list = list.subList(0, 100);
        log.error("list post " + list);

        List<String> lastAddressesUsedInTrades = list.stream()
                .filter(tradeStatistics2 -> tradeStatistics2.getExtraDataMap() != null)
                .map(tradeStatistics2 -> tradeStatistics2.getExtraDataMap().get(TradeStatistics2.ARBITRATOR_ADDRESS))
                .collect(Collectors.toList());

        List<String> arbitrators = arbitratorManager.getArbitratorsObservableMap().values().stream()
                .map(arbitrator -> arbitrator.getNodeAddress().getHostNameWithoutPostFix())
                .collect(Collectors.toList());

        String result = getLeastUsedArbitrator(lastAddressesUsedInTrades, arbitrators);
        log.error("result: " + result);
        return result;
    }

    static String getLeastUsedArbitrator(List<String> lastAddressesUsedInTrades, List<String> arbitrators) {
        List<Tuple2<String, AtomicInteger>> arbitratorTuples = arbitrators.stream()
                .map(e -> new Tuple2<>(e, new AtomicInteger(0)))
                .collect(Collectors.toList());
        arbitratorTuples.forEach(tuple -> {
            int count = (int) lastAddressesUsedInTrades.stream()
                    .filter(tuple.first::equals)
                    .mapToInt(e -> 1)
                    .count();
            tuple.second.set(count);
        });

        arbitratorTuples.sort(Comparator.comparing(e -> e.first));
        arbitratorTuples.sort(Comparator.comparingInt(e -> e.second.get()));

        return arbitratorTuples.get(0).first;
    }
}

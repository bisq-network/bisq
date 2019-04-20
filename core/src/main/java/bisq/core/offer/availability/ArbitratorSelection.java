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

import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.common.util.Tuple2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class ArbitratorSelection {

    public static Arbitrator getLeastUsedArbitrator(TradeStatisticsManager tradeStatisticsManager,
                                                    ArbitratorManager arbitratorManager) {
        // We take last 100 entries from trade statistics
        List<TradeStatistics2> list = new ArrayList<>(tradeStatisticsManager.getObservableTradeStatisticsSet());
        list.sort(Comparator.comparing(TradeStatistics2::getTradeDate));
        Collections.reverse(list);
        if (!list.isEmpty()) {
            int max = Math.min(list.size(), 100);
            list = list.subList(0, max);
        }

        // We stored only first 4 chars of arbitrators onion address
        List<String> lastAddressesUsedInTrades = list.stream()
                .filter(tradeStatistics2 -> tradeStatistics2.getExtraDataMap() != null)
                .map(tradeStatistics2 -> tradeStatistics2.getExtraDataMap().get(TradeStatistics2.ARBITRATOR_ADDRESS))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<String> arbitrators = arbitratorManager.getArbitratorsObservableMap().values().stream()
                .map(arbitrator -> arbitrator.getNodeAddress().getFullAddress())
                .collect(Collectors.toSet());

        String result = getLeastUsedArbitrator(lastAddressesUsedInTrades, arbitrators);

        Optional<Arbitrator> optionalArbitrator = arbitratorManager.getArbitratorsObservableMap().values().stream()
                .filter(e -> e.getNodeAddress().getFullAddress().equals(result))
                .findAny();
        checkArgument(optionalArbitrator.isPresent(), "optionalArbitrator has to be present");
        return optionalArbitrator.get();
    }

    static String getLeastUsedArbitrator(List<String> lastAddressesUsedInTrades, Set<String> arbitrators) {
        checkArgument(!arbitrators.isEmpty(), "arbitrators must not be empty");
        List<Tuple2<String, AtomicInteger>> arbitratorTuples = arbitrators.stream()
                .map(e -> new Tuple2<>(e, new AtomicInteger(0)))
                .collect(Collectors.toList());
        arbitratorTuples.forEach(tuple -> {
            int count = (int) lastAddressesUsedInTrades.stream()
                    .filter(tuple.first::startsWith) // we use only first 4 chars for comparing
                    .mapToInt(e -> 1)
                    .count();
            tuple.second.set(count);
        });

        arbitratorTuples.sort(Comparator.comparing(e -> e.first));
        arbitratorTuples.sort(Comparator.comparingInt(e -> e.second.get()));
        return arbitratorTuples.get(0).first;
    }
}

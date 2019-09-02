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

import bisq.core.support.DisputeResolver;
import bisq.core.support.DisputeResolverManager;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.common.util.Tuple2;

import com.google.common.annotations.VisibleForTesting;

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
public class DisputeResolverSelection {

    public static <T extends DisputeResolver> T getLeastUsedArbitrator(TradeStatisticsManager tradeStatisticsManager,
                                                                       DisputeResolverManager<T> disputeResolverManager) {
        return getLeastUsedDisputeResolver(tradeStatisticsManager,
                disputeResolverManager,
                TradeStatistics2.ARBITRATOR_ADDRESS);
    }

    public static <T extends DisputeResolver> T getLeastUsedMediator(TradeStatisticsManager tradeStatisticsManager,
                                                                     DisputeResolverManager<T> disputeResolverManager) {
        return getLeastUsedDisputeResolver(tradeStatisticsManager,
                disputeResolverManager,
                TradeStatistics2.MEDIATOR_ADDRESS);
    }

    private static <T extends DisputeResolver> T getLeastUsedDisputeResolver(TradeStatisticsManager tradeStatisticsManager,
                                                                             DisputeResolverManager<T> disputeResolverManager,
                                                                             String extraMapKey) {
        // We take last 100 entries from trade statistics
        List<TradeStatistics2> list = new ArrayList<>(tradeStatisticsManager.getObservableTradeStatisticsSet());
        list.sort(Comparator.comparing(TradeStatistics2::getTradeDate));
        Collections.reverse(list);
        if (!list.isEmpty()) {
            int max = Math.min(list.size(), 100);
            list = list.subList(0, max);
        }

        // We stored only first 4 chars of disputeResolvers onion address
        List<String> lastAddressesUsedInTrades = list.stream()
                .filter(tradeStatistics2 -> tradeStatistics2.getExtraDataMap() != null)
                .map(tradeStatistics2 -> tradeStatistics2.getExtraDataMap().get(extraMapKey))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<String> disputeResolvers = disputeResolverManager.getObservableMap().values().stream()
                .map(disputeResolver -> disputeResolver.getNodeAddress().getFullAddress())
                .collect(Collectors.toSet());

        String result = getLeastUsedDisputeResolver(lastAddressesUsedInTrades, disputeResolvers);

        Optional<T> optionalDisputeResolver = disputeResolverManager.getObservableMap().values().stream()
                .filter(e -> e.getNodeAddress().getFullAddress().equals(result))
                .findAny();
        checkArgument(optionalDisputeResolver.isPresent(), "optionalDisputeResolver has to be present");
        return optionalDisputeResolver.get();
    }

    @VisibleForTesting
    static String getLeastUsedDisputeResolver(List<String> lastAddressesUsedInTrades, Set<String> disputeResolvers) {
        checkArgument(!disputeResolvers.isEmpty(), "disputeResolvers must not be empty");
        List<Tuple2<String, AtomicInteger>> disputeResolverTuples = disputeResolvers.stream()
                .map(e -> new Tuple2<>(e, new AtomicInteger(0)))
                .collect(Collectors.toList());
        disputeResolverTuples.forEach(tuple -> {
            int count = (int) lastAddressesUsedInTrades.stream()
                    .filter(tuple.first::startsWith) // we use only first 4 chars for comparing
                    .mapToInt(e -> 1)
                    .count();
            tuple.second.set(count);
        });

        disputeResolverTuples.sort(Comparator.comparing(e -> e.first));
        disputeResolverTuples.sort(Comparator.comparingInt(e -> e.second.get()));
        return disputeResolverTuples.get(0).first;
    }
}

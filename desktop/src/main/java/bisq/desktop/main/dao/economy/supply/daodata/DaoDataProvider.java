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

package bisq.desktop.main.dao.economy.supply.daodata;

import bisq.desktop.components.chart.TemporalAdjusterUtil;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.time.Instant;
import java.time.temporal.TemporalAdjuster;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DaoDataProvider {
    private final DaoStateService daoStateService;
    private final Function<Issuance, Long> blockTimeOfIssuanceFunction;

    private TemporalAdjuster temporalAdjuster = TemporalAdjusterUtil.Interval.MONTH.getAdjuster();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoDataProvider(DaoStateService daoStateService) {
        super();
        this.daoStateService = daoStateService;

        blockTimeOfIssuanceFunction = memoize(issuance -> {
            int height = daoStateService.getStartHeightOfCurrentCycle(issuance.getChainHeight()).orElse(0);
            return daoStateService.getBlockTime(height);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setTemporalAdjuster(TemporalAdjuster temporalAdjuster) {
        this.temporalAdjuster = temporalAdjuster;
    }

    public TemporalAdjuster getTemporalAdjuster() {
        return temporalAdjuster;
    }

    /**
     * @param fromDate      Epoch in millis
     * @param toDate        Epoch in millis
     */
    public long getCompensationAmount(long fromDate, long toDate) {
        return getMergedCompensationMap(getPredicate(fromDate, toDate)).values().stream()
                .mapToLong(e -> e)
                .sum();
    }

    /**
     * @param fromDate      Epoch in millis
     * @param toDate        Epoch in millis
     */
    public long getReimbursementAmount(long fromDate, long toDate) {
        return getMergedReimbursementMap(getPredicate(fromDate, toDate)).values().stream()
                .mapToLong(e -> e)
                .sum();
    }

    /**
     * @param fromDate      Epoch in millis
     * @param toDate        Epoch in millis
     */
    public long getBsqTradeFeeAmount(long fromDate, long toDate) {
        return getBurnedBsqByMonth(daoStateService.getTradeFeeTxs(), getPredicate(fromDate, toDate)).values()
                .stream()
                .mapToLong(e -> e)
                .sum();
    }

    /**
     * @param fromDate      Epoch in millis
     * @param toDate        Epoch in millis
     */
    public long getProofOfBurnAmount(long fromDate, long toDate) {
        return getBurnedBsqByMonth(daoStateService.getProofOfBurnTxs(), getPredicate(fromDate, toDate)).values().stream()
                .mapToLong(e -> e)
                .sum();
    }

    public Map<Long, Long> getBurnedBsqByMonth(Collection<Tx> txs, Predicate<Long> predicate) {
        return txs.stream()
                .collect(Collectors.groupingBy(tx -> toTimeInterval(Instant.ofEpochMilli(tx.getTime()))))
                .entrySet()
                .stream()
                .filter(entry -> predicate.test(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .mapToLong(Tx::getBurntBsq)
                                .sum()));
    }

    // We map all issuance entries to a map with the beginning of the month as key and the list of issuance as value.
    // Then we apply the date filter and and sum up all issuance amounts if the items in the list to return the
    // issuance by month. We use calendar month because we want to combine the data with other data and using the cycle
    // as adjuster would be more complicate (though could be done in future).
    public Map<Long, Long> getIssuedBsqByMonth(Set<Issuance> issuanceSet, Predicate<Long> predicate) {
        return issuanceSet.stream()
                .collect(Collectors.groupingBy(issuance ->
                        toTimeInterval(Instant.ofEpochMilli(blockTimeOfIssuanceFunction.apply(issuance)))))
                .entrySet()
                .stream()
                .filter(entry -> predicate.test(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .mapToLong(Issuance::getAmount)
                                .sum()));
    }

    public Map<Long, Long> getMergedCompensationMap(Predicate<Long> predicate) {
        Map<Long, Long> issuedBsqByMonth = getIssuedBsqByMonth(daoStateService.getIssuanceSetForType(IssuanceType.COMPENSATION), predicate);
        Map<Long, Long> historicalIssuanceByMonth = getHistoricalIssuanceByMonth(DaoEconomyHistoricalData.COMPENSATIONS_BY_CYCLE_DATE, predicate);
        return getMergedMap(issuedBsqByMonth, historicalIssuanceByMonth, (daoDataValue, staticDataValue) -> staticDataValue);
    }

    public Map<Long, Long> getMergedReimbursementMap(Predicate<Long> predicate) {
        Map<Long, Long> issuedBsqByMonth = getIssuedBsqByMonth(daoStateService.getIssuanceSetForType(IssuanceType.REIMBURSEMENT), predicate);
        Map<Long, Long> historicalIssuanceByMonth = getHistoricalIssuanceByMonth(DaoEconomyHistoricalData.REIMBURSEMENTS_BY_CYCLE_DATE, predicate);
        return getMergedMap(issuedBsqByMonth, historicalIssuanceByMonth, (daoDataValue, staticDataValue) -> staticDataValue);
    }

    public Map<Long, Long> getMergedMap(Map<Long, Long> map1,
                                        Map<Long, Long> map2,
                                        BinaryOperator<Long> mergeFunction) {
        return Stream.concat(map1.entrySet().stream(),
                map2.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue,
                        mergeFunction));
    }

    private Map<Long, Long> getHistoricalIssuanceByMonth(Map<Long, Long> historicalData, Predicate<Long> predicate) {
        // We did not use the reimbursement requests initially (but the compensation requests) because the limits
        // have been too low. Over time it got mixed in compensation requests and reimbursement requests.
        // To reflect that we use static data derived from the Github data. For new data we do not need that anymore
        // as we have clearly separated that now. In case we have duplicate data for a months we use the static data.
        return historicalData.entrySet().stream()
                .filter(e -> predicate.test(e.getKey()))
                .collect(Collectors.toMap(e -> toTimeInterval(Instant.ofEpochSecond(e.getKey())),
                        Map.Entry::getValue,
                        (a, b) -> a + b));
    }

    public long toTimeInterval(Instant instant) {
        return TemporalAdjusterUtil.toTimeInterval(instant, temporalAdjuster);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static Predicate<Long> getPredicate(long fromDate, long toDate) {
        return value -> value >= fromDate / 1000 && value <= toDate / 1000;
    }

    private static <T, R> Function<T, R> memoize(Function<T, R> fn) {
        Map<T, R> map = new ConcurrentHashMap<>();
        return x -> map.computeIfAbsent(x, fn);
    }

    private static class DaoEconomyHistoricalData {
        // Key is start date of the cycle in epoch seconds, value is reimbursement amount
        public final static Map<Long, Long> REIMBURSEMENTS_BY_CYCLE_DATE = new HashMap<>();
        public final static Map<Long, Long> COMPENSATIONS_BY_CYCLE_DATE = new HashMap<>();

        static {
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1571349571L, 60760L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1574180991L, 2621000L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1576966522L, 4769100L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1579613568L, 0L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1582399054L, 9186600L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1585342220L, 12089400L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1588025030L, 5420700L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1591004931L, 9138760L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1593654027L, 10821807L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1596407074L, 2160157L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1599175867L, 8769408L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1601861442L, 4956585L);
            REIMBURSEMENTS_BY_CYCLE_DATE.put(1604845863L, 2121664L);

            COMPENSATIONS_BY_CYCLE_DATE.put(1555340856L, 6931863L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1558083590L, 2287000L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1560771266L, 2273000L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1563347672L, 2943772L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1566009595L, 10040170L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1568643566L, 8685115L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1571349571L, 7315879L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1574180991L, 12508300L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1576966522L, 5884500L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1579613568L, 8206000L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1582399054L, 3518364L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1585342220L, 6231700L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1588025030L, 4391400L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1591004931L, 3636463L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1593654027L, 6156631L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1596407074L, 5838368L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1599175867L, 6086442L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1601861442L, 5615973L);
            COMPENSATIONS_BY_CYCLE_DATE.put(1604845863L, 7782667L);
        }
    }
}

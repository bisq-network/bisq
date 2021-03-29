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

package bisq.desktop.main.dao.economy.supply.dao;

import bisq.desktop.components.chart.ChartDataModel;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.time.Instant;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DaoChartDataModel extends ChartDataModel {
    private final DaoStateService daoStateService;
    private final Function<Issuance, Long> blockTimeOfIssuanceFunction;
    private Map<Long, Long> totalIssuedByInterval, compensationByInterval, reimbursementByInterval,
            totalBurnedByInterval, bsqTradeFeeByInterval, proofOfBurnByInterval;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoChartDataModel(DaoStateService daoStateService) {
        super();

        this.daoStateService = daoStateService;

        // TODO getBlockTime is the bottleneck. Add a lookup map to daoState to fix that in a dedicated PR.
        blockTimeOfIssuanceFunction = memoize(issuance -> {
            int height = daoStateService.getStartHeightOfCurrentCycle(issuance.getChainHeight()).orElse(0);
            return daoStateService.getBlockTime(height);
        });
    }

    @Override
    protected void invalidateCache() {
        totalIssuedByInterval = null;
        compensationByInterval = null;
        reimbursementByInterval = null;
        totalBurnedByInterval = null;
        bsqTradeFeeByInterval = null;
        proofOfBurnByInterval = null;

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Total amounts
    ///////////////////////////////////////////////////////////////////////////////////////////

    long getCompensationAmount() {
        return getCompensationByInterval().values().stream()
                .mapToLong(e -> e)
                .sum();
    }

    long getReimbursementAmount() {
        return getReimbursementByInterval().values().stream()
                .mapToLong(e -> e)
                .sum();
    }

    long getBsqTradeFeeAmount() {
        return getBsqTradeFeeByInterval().values().stream()
                .mapToLong(e -> e)
                .sum();
    }

    long getProofOfBurnAmount() {
        return getProofOfBurnByInterval().values().stream()
                .mapToLong(e -> e)
                .sum();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data for chart
    ///////////////////////////////////////////////////////////////////////////////////////////

    Map<Long, Long> getTotalIssuedByInterval() {
        if (totalIssuedByInterval != null) {
            return totalIssuedByInterval;
        }

        Map<Long, Long> compensationMap = getCompensationByInterval();
        Map<Long, Long> reimbursementMap = getReimbursementByInterval();
        totalIssuedByInterval = getMergedMap(compensationMap, reimbursementMap, Long::sum);
        return totalIssuedByInterval;
    }

    Map<Long, Long> getCompensationByInterval() {
        if (compensationByInterval != null) {
            return compensationByInterval;
        }

        Set<Issuance> issuanceSetForType = daoStateService.getIssuanceSetForType(IssuanceType.COMPENSATION);
        Map<Long, Long> issuedBsqByInterval = getIssuedBsqByInterval(issuanceSetForType, getDateFilter());
        Map<Long, Long> historicalIssuanceByInterval = getHistoricalIssuedBsqByInterval(DaoEconomyHistoricalData.COMPENSATIONS_BY_CYCLE_DATE, getDateFilter());
        compensationByInterval = getMergedMap(issuedBsqByInterval, historicalIssuanceByInterval, (daoDataValue, staticDataValue) -> staticDataValue);
        return compensationByInterval;
    }

    Map<Long, Long> getReimbursementByInterval() {
        if (reimbursementByInterval != null) {
            return reimbursementByInterval;
        }

        Map<Long, Long> issuedBsqByInterval = getIssuedBsqByInterval(daoStateService.getIssuanceSetForType(IssuanceType.REIMBURSEMENT), getDateFilter());
        Map<Long, Long> historicalIssuanceByInterval = getHistoricalIssuedBsqByInterval(DaoEconomyHistoricalData.REIMBURSEMENTS_BY_CYCLE_DATE, getDateFilter());
        reimbursementByInterval = getMergedMap(issuedBsqByInterval, historicalIssuanceByInterval, (daoDataValue, staticDataValue) -> staticDataValue);
        return reimbursementByInterval;
    }

    Map<Long, Long> getTotalBurnedByInterval() {
        if (totalBurnedByInterval != null) {
            return totalBurnedByInterval;
        }

        Map<Long, Long> tradeFee = getBsqTradeFeeByInterval();
        Map<Long, Long> proofOfBurn = getProofOfBurnByInterval();
        totalBurnedByInterval = getMergedMap(tradeFee, proofOfBurn, Long::sum);
        return totalBurnedByInterval;
    }

    Map<Long, Long> getBsqTradeFeeByInterval() {
        if (bsqTradeFeeByInterval != null) {
            return bsqTradeFeeByInterval;
        }

        bsqTradeFeeByInterval = getBurntBsqByInterval(daoStateService.getTradeFeeTxs(), getDateFilter());
        return bsqTradeFeeByInterval;
    }

    Map<Long, Long> getProofOfBurnByInterval() {
        if (proofOfBurnByInterval != null) {
            return proofOfBurnByInterval;
        }

        proofOfBurnByInterval = getBurntBsqByInterval(daoStateService.getProofOfBurnTxs(), getDateFilter());
        return proofOfBurnByInterval;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Aggregated collection data by interval
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Map<Long, Long> getIssuedBsqByInterval(Set<Issuance> issuanceSet, Predicate<Long> dateFilter) {
        return issuanceSet.stream()
                .collect(Collectors.groupingBy(issuance ->
                        toTimeInterval(Instant.ofEpochMilli(blockTimeOfIssuanceFunction.apply(issuance)))))
                .entrySet()
                .stream()
                .filter(entry -> dateFilter.test(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .mapToLong(Issuance::getAmount)
                                .sum()));
    }

    private Map<Long, Long> getHistoricalIssuedBsqByInterval(Map<Long, Long> historicalData,
                                                             Predicate<Long> dateFilter) {

        return historicalData.entrySet().stream()
                .filter(e -> dateFilter.test(e.getKey()))
                .collect(Collectors.toMap(e -> toTimeInterval(Instant.ofEpochSecond(e.getKey())),
                        Map.Entry::getValue,
                        (a, b) -> a + b));
    }

    private Map<Long, Long> getBurntBsqByInterval(Collection<Tx> txs, Predicate<Long> dateFilter) {
        return txs.stream()
                .collect(Collectors.groupingBy(tx -> toTimeInterval(Instant.ofEpochMilli(tx.getTime()))))
                .entrySet()
                .stream()
                .filter(entry -> dateFilter.test(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .mapToLong(Tx::getBurntBsq)
                                .sum()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static <T, R> Function<T, R> memoize(Function<T, R> fn) {
        Map<T, R> map = new ConcurrentHashMap<>();
        return x -> map.computeIfAbsent(x, fn);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Historical data
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We did not use the reimbursement requests initially (but the compensation requests) because the limits
    // have been too low. Over time it got mixed in compensation requests and reimbursement requests.
    // To reflect that we use static data derived from the Github data. For new data we do not need that anymore
    // as we have clearly separated that now. In case we have duplicate data for a months we use the static data.
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

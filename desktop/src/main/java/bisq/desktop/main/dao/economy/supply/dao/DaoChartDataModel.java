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
import bisq.core.dao.state.model.governance.BsqSupplyChange;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;

import bisq.common.util.Hex;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.time.Instant;

import java.util.Collection;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DaoChartDataModel extends ChartDataModel {
    // Date when we started to use tags for separating proof of burn txs
    private static final GregorianCalendar TAG_DATE = new GregorianCalendar(2021, GregorianCalendar.NOVEMBER, 3, 13, 0);

    private final DaoStateService daoStateService;
    private final Function<Issuance, Long> blockTimeOfIssuanceFunction;
    private Map<Long, Long> totalSupplyByInterval, totalIssuedByInterval, compensationByInterval,
            reimbursementByInterval, reimbursementByIntervalAfterTagging,
            totalBurnedByInterval, bsqTradeFeeByInterval, bsqTradeFeeByIntervalAfterTagging,
            proofOfBurnByInterval, proofOfBurnFromBtcFeesByInterval, proofOfBurnFromArbitrationByInterval,
            arbitrationDiffByInterval, totalTradeFeesByInterval;

    static {
        TAG_DATE.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

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
        totalSupplyByInterval = null;
        totalIssuedByInterval = null;
        compensationByInterval = null;
        reimbursementByInterval = null;
        reimbursementByIntervalAfterTagging = null;
        totalBurnedByInterval = null;
        bsqTradeFeeByInterval = null;
        bsqTradeFeeByIntervalAfterTagging = null;
        proofOfBurnByInterval = null;
        proofOfBurnFromBtcFeesByInterval = null;
        proofOfBurnFromArbitrationByInterval = null;
        arbitrationDiffByInterval = null;
        totalTradeFeesByInterval = null;
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

    Map<Long, Long> getTotalSupplyByInterval() {
        if (totalSupplyByInterval != null) {
            return totalSupplyByInterval;
        }

        totalSupplyByInterval = getTotalSupplyByInterval(
                daoStateService.getBsqSupplyChanges(),
                getDateFilter()
        );
        return totalSupplyByInterval;
    }

    Map<Long, Long> getArbitrationDiffByInterval() {
        if (arbitrationDiffByInterval != null) {
            return arbitrationDiffByInterval;
        }

        // The subtraction of the burned BSQ from arbitration cases from reimbursement amounts should tend to zero.
        // We only support data separation for burned BSQ from arbitration since Nov 2021.
        Map<Long, Long> reimbursementMap = getReimbursementByInterval();
        Map<Long, Long> burnFromArbitrationMap = getProofOfBurnFromArbitrationByInterval();
        Map<Long, Long> mergedMap = getMergedMap(reimbursementMap, burnFromArbitrationMap, (a, b) -> a - b);
        arbitrationDiffByInterval = mergedMap.entrySet().stream()
                .filter(e -> getPostTagDateFilter().test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return arbitrationDiffByInterval;
    }

    Map<Long, Long> getTotalTradeFeesByInterval() {
        if (totalTradeFeesByInterval != null) {
            return totalTradeFeesByInterval;
        }

        // From Nov 2021 we use tags for the burned BSQ from BTC fees and for those from delayed payout txs.
        // By that we can filter out the burned BSQ from BTC fees.
        Map<Long, Long> tradeFee = getBsqTradeFeeByInterval();
        Map<Long, Long> proofOfBurn = getProofOfBurnFromBtcFeesByInterval();
        Map<Long, Long> merged = getMergedMap(tradeFee, proofOfBurn, Long::sum);
        totalTradeFeesByInterval = merged.entrySet().stream()
                .filter(entry -> entry.getKey() * 1000 >= TAG_DATE.getTimeInMillis())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return totalTradeFeesByInterval;
    }

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

    Map<Long, Long> getReimbursementAfterTaggingByInterval() {
        if (reimbursementByIntervalAfterTagging != null) {
            return reimbursementByIntervalAfterTagging;
        }

        reimbursementByIntervalAfterTagging = getReimbursementByInterval().entrySet().stream()
                .filter(e -> getPostTagDateFilter().test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return reimbursementByIntervalAfterTagging;
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

    Map<Long, Long> getBsqTradeFeeByIntervalAfterTagging() {
        if (bsqTradeFeeByIntervalAfterTagging != null) {
            return bsqTradeFeeByIntervalAfterTagging;
        }

        bsqTradeFeeByIntervalAfterTagging = getBsqTradeFeeByInterval().entrySet().stream()
                .filter(e -> getPostTagDateFilter().test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return bsqTradeFeeByIntervalAfterTagging;
    }

    Map<Long, Long> getProofOfBurnByInterval() {
        if (proofOfBurnByInterval != null) {
            return proofOfBurnByInterval;
        }

        proofOfBurnByInterval = getBurntBsqByInterval(daoStateService.getProofOfBurnTxs(), getDateFilter());
        return proofOfBurnByInterval;
    }

    Map<Long, Long> getProofOfBurnFromBtcFeesByInterval() {
        if (proofOfBurnFromBtcFeesByInterval != null) {
            return proofOfBurnFromBtcFeesByInterval;
        }

        // Tagging started Nov 2021
        // opReturn data from BTC fees: 1701721206fe6b40777763de1c741f4fd2706d94775d
        Set<Tx> proofOfBurnTxs = daoStateService.getProofOfBurnTxs();
        Set<Tx> feeTxs = proofOfBurnTxs.stream()
                .filter(tx -> "1701721206fe6b40777763de1c741f4fd2706d94775d".equals(Hex.encode(tx.getLastTxOutput().getOpReturnData())))
                .collect(Collectors.toSet());
        proofOfBurnFromBtcFeesByInterval = getBurntBsqByInterval(feeTxs, getDateFilter());
        return proofOfBurnFromBtcFeesByInterval;
    }

    Map<Long, Long> getProofOfBurnFromArbitrationByInterval() {
        if (proofOfBurnFromArbitrationByInterval != null) {
            return proofOfBurnFromArbitrationByInterval;
        }

        // Tagging started Nov 2021
        // opReturn data from delayed payout txs: 1701e47e5d8030f444c182b5e243871ebbaeadb5e82f
        // opReturn data from BM trades with a trade who got reimbursed by the DAO : 1701293c488822f98e70e047012f46f5f1647f37deb7
        Set<Tx> feeTxs = daoStateService.getProofOfBurnTxs().stream()
                .filter(e -> "1701e47e5d8030f444c182b5e243871ebbaeadb5e82f".equals(Hex.encode(e.getLastTxOutput().getOpReturnData())) ||
                        "1701293c488822f98e70e047012f46f5f1647f37deb7".equals(Hex.encode(e.getLastTxOutput().getOpReturnData())))
                .collect(Collectors.toSet());
        proofOfBurnFromArbitrationByInterval = getBurntBsqByInterval(feeTxs, getDateFilter());
        return proofOfBurnFromArbitrationByInterval;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Aggregated collection data by interval
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Map<Long, Long> getTotalSupplyByInterval(Stream<BsqSupplyChange> bsqSupplyChanges,
                                                     Predicate<Long> dateFilter) {
        AtomicLong supply = new AtomicLong(DaoEconomyHistoricalData.TOTAL_SUPPLY_BY_CYCLE_DATE.get(1555340856L));

        return bsqSupplyChanges
                .collect(Collectors.groupingBy(tx -> toTimeInterval(Instant.ofEpochMilli(tx.getTime()))))
                .entrySet()
                .stream()
                .sorted(Comparator.comparingLong(Map.Entry::getKey))
                .map(e -> new BsqSupplyChange(
                        e.getKey(),
                        supply.addAndGet(e
                                .getValue()
                                .stream()
                                .mapToLong(BsqSupplyChange::getValue)
                                .sum()
                        ))
                )
                .filter(t -> dateFilter.test(t.getTime()))
                .collect(Collectors.toMap(BsqSupplyChange::getTime, BsqSupplyChange::getValue));
    }

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
                        Long::sum));
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

    private Predicate<Long> getPostTagDateFilter() {
        // We filter out old dates as it only makes sense since Nov 2021
        return date -> date >= TAG_DATE.getTimeInMillis() / 1000;  // we use seconds
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
        public final static Map<Long, Long> TOTAL_SUPPLY_BY_CYCLE_DATE = new HashMap<>();

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

            TOTAL_SUPPLY_BY_CYCLE_DATE.put(1555340856L, 372540100L);
        }
    }
}

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

package bisq.core.dao.governance.merit;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.Merit;
import bisq.core.dao.state.model.governance.MeritList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Adds cycle wide uniqueness of merit issuances to the rules of {@link MeritConsensusV2}.
 * <p>
 * V2 rejects a merit list which claims the same issuance twice, but it evaluates each blind vote on its own, so the
 * same issuance still backed merit once per blind vote. A contributor could therefore publish several blind votes in
 * one cycle, each with a merit list re-signed for its own blind vote transaction id, and have their merit counted once
 * per blind vote.
 * <p>
 * An issuance claimed by more than one blind vote of the cycle is counted for none of them. Awarding it to one blind
 * vote instead would let whoever holds a stolen issuance key take the merit, and any tie break on transaction ids
 * would be grindable. Dropping every copy leaves no way to gain from duplicating.
 * <p>
 * Only claims which passed the full validation take part in the duplicate detection. Collecting the claimed issuance
 * ids before validating them would let anyone publish a blind vote naming other voters' issuances with invalid
 * signatures and thereby suppress those voters' merit, which would be a cheaper attack than the one this rule closes.
 */
@Slf4j
public class MeritConsensusV3 {

    /**
     * Returns the merit stake of every given blind vote, keyed by blind vote transaction id.
     * <p>
     * The merit of a blind vote cannot be derived on its own under these rules, because whether a claim counts depends
     * on the other blind votes of the same cycle. Callers therefore pass the merit lists of the whole cycle.
     */
    public static Map<String, Long> getMeritStakeByBlindVoteTxId(Map<String, MeritList> untrustedMeritListByBlindVoteTxId,
                                                                 DaoStateService daoStateService) {
        // Sorted, so that a fault which aborts the vote result calculation is raised at the same claim on every node.
        Map<String, Integer> blindVoteTxHeightByBlindVoteTxId = new TreeMap<>();
        Map<String, List<Issuance>> validatedIssuancesByBlindVoteTxId = new TreeMap<>();
        new TreeMap<>(untrustedMeritListByBlindVoteTxId).forEach((blindVoteTxId, untrustedMeritList) -> {
            int blindVoteTxHeight = MeritConsensusV2.getBlindVoteTxHeight(blindVoteTxId, daoStateService);
            blindVoteTxHeightByBlindVoteTxId.put(blindVoteTxId, blindVoteTxHeight);
            validatedIssuancesByBlindVoteTxId.put(blindVoteTxId,
                    getValidatedIssuances(blindVoteTxId, untrustedMeritList, daoStateService, blindVoteTxHeight));
        });

        Map<String, Long> claimCountByIssuanceTxId = validatedIssuancesByBlindVoteTxId.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(Issuance::getTxId, Collectors.counting()));
        logDuplicates(claimCountByIssuanceTxId);

        Map<String, Long> meritStakeByBlindVoteTxId = new HashMap<>();
        validatedIssuancesByBlindVoteTxId.forEach((blindVoteTxId, validatedIssuances) ->
                meritStakeByBlindVoteTxId.put(blindVoteTxId,
                        sumUniqueMeritAmounts(blindVoteTxId,
                                validatedIssuances,
                                claimCountByIssuanceTxId,
                                blindVoteTxHeightByBlindVoteTxId.get(blindVoteTxId))));
        return meritStakeByBlindVoteTxId;
    }

    /**
     * Returns the DAO state issuances backing the valid claims of one merit list, in the order of the merit list.
     * A blind vote whose transaction is not in the DAO state, or whose merit list cannot be evaluated, contributes no
     * claims. It must not remove the claims of the other blind votes of the cycle.
     */
    private static List<Issuance> getValidatedIssuances(String blindVoteTxId,
                                                        MeritList untrustedMeritList,
                                                        DaoStateService daoStateService,
                                                        int blindVoteTxHeight) {
        if (blindVoteTxHeight <= 0) {
            return List.of();
        }
        try {
            Set<String> alreadyUsedIssuanceTxIds = new HashSet<>();
            List<Issuance> validatedIssuances = new ArrayList<>();
            for (Merit untrustedMerit : untrustedMeritList.getList()) {
                MeritConsensusV2.validateMeritClaim(untrustedMerit,
                                blindVoteTxId,
                                daoStateService,
                                blindVoteTxHeight,
                                alreadyUsedIssuanceTxIds)
                        .ifPresent(validatedIssuances::add);
            }
            return validatedIssuances;
        } catch (ArithmeticException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Error at validating the merit list. blindVoteTxId={}, meritList={}",
                    blindVoteTxId, untrustedMeritList, e);
            return List.of();
        }
    }

    private static long sumUniqueMeritAmounts(String blindVoteTxId,
                                              List<Issuance> validatedIssuances,
                                              Map<String, Long> claimCountByIssuanceTxId,
                                              int blindVoteTxHeight) {
        try {
            long totalMeritAmount = 0;
            for (Issuance issuance : validatedIssuances) {
                if (claimCountByIssuanceTxId.getOrDefault(issuance.getTxId(), 0L) > 1) {
                    continue;
                }
                totalMeritAmount = Math.addExact(totalMeritAmount,
                        MeritConsensusV2.getWeightedMeritAmount(issuance, blindVoteTxHeight));
            }
            return totalMeritAmount;
        } catch (ArithmeticException e) {
            // Overflow in consensus merit arithmetic must fail the vote result instead of being treated as zero merit.
            throw e;
        } catch (RuntimeException e) {
            log.error("Error at summing the merit. blindVoteTxId={}", blindVoteTxId, e);
            return 0;
        }
    }

    private static void logDuplicates(Map<String, Long> claimCountByIssuanceTxId) {
        // Reported once per issuance rather than once per claiming blind vote, so that a large merit list cannot
        // amplify itself into a quadratic amount of log output.
        new TreeMap<>(claimCountByIssuanceTxId).forEach((issuanceTxId, claimCount) -> {
            if (claimCount > 1) {
                log.warn("Merit issuance was claimed by {} blind votes of the cycle and is therefore not counted for " +
                        "any of them. issuanceTxId={}", claimCount, issuanceTxId);
            }
        });
    }
}

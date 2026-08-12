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
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.Merit;

import com.google.protobuf.CodedInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Audits the shipped mainnet DAO state for merit issuances which are claimed by more than one blind vote of the same
 * cycle, which is what {@link MeritConsensusV3} rejects from the hard fork 3 activation height on.
 * <p>
 * The audit establishes whether the weakness was ever exploited. Replay compatibility follows from selecting the rule
 * by the evaluation height of each cycle, not from this audit. It runs over the bundled resource rather than over a
 * synced node, so it covers history up to the height of that resource; the already evaluated cycles after that height
 * still have to be checked against a synced node before release, and new cycles have to be monitored until activation.
 * <p>
 * The claims are validated with the production rules, so the audit cannot disagree with the implementation about which
 * claim counts. Two relaxations make the result an upper bound on the real set of counted claims, which is what an
 * audit for the absence of duplicates needs:
 * <ul>
 *     <li>the blind vote height is taken from the vote reveal transaction which spent the blind vote stake output,
 *     which lies in the same cycle but slightly later, so the rule that an issuance must not be younger than the
 *     blind vote is applied slightly permissively;</li>
 *     <li>merit consensus V2 applies only from its activation height, so claims of earlier cycles were not validated
 *     at all when their result was derived. Validating them here can only remove claims from the comparison, so the
 *     audit additionally reports the duplicates found without any validation.</li>
 * </ul>
 */
class BundledMeritDuplicationAuditTest {
    private static final String DAO_STATE_RESOURCE = "/DaoStateStore_BTC_MAINNET";

    @Test
    void bundledDaoStateHasNoIssuanceClaimedBySeveralBlindVotesOfOneCycle() throws IOException {
        protobuf.DaoState daoState = readBundledDaoState();

        Map<String, protobuf.Issuance> issuanceByTxId = daoState.getIssuanceMapMap();
        List<Cycle> cycles = daoState.getCyclesList().stream().map(Cycle::fromProto).toList();
        DaoStateService daoStateService = daoStateServiceOf(issuanceByTxId);

        // Cycle of a blind vote, resolved through the vote reveal transaction which spent its stake output. The stake
        // output of a blind vote transaction is always its first output.
        Map<String, Long> voteRevealHeightByBlindVoteTxId = new HashMap<>();
        daoState.getSpentInfoMapMap().forEach((txOutputKey, spentInfo) -> {
            int separator = txOutputKey.lastIndexOf(':');
            if (separator > 0 && "0".equals(txOutputKey.substring(separator + 1))) {
                voteRevealHeightByBlindVoteTxId.put(txOutputKey.substring(0, separator), spentInfo.getBlockHeight());
            }
        });

        Map<Integer, Map<String, Set<String>>> blindVoteTxIdsByIssuanceTxIdByCycle = new TreeMap<>();
        Map<Integer, Map<String, Set<String>>> unvalidatedBlindVoteTxIdsByIssuanceTxIdByCycle = new TreeMap<>();
        List<String> unresolvedBlindVoteTxIds = new ArrayList<>();
        int meritClaims = 0;
        int validatedMeritClaims = 0;

        for (protobuf.DecryptedBallotsWithMerits decryptedBallotsWithMerits :
                daoState.getDecryptedBallotsWithMeritsListList()) {
            String blindVoteTxId = decryptedBallotsWithMerits.getBlindVoteTxId();
            Long voteRevealHeight = voteRevealHeightByBlindVoteTxId.get(blindVoteTxId);
            if (voteRevealHeight == null) {
                unresolvedBlindVoteTxIds.add(blindVoteTxId);
                continue;
            }
            int cycleIndex = cycleIndexOf(cycles, voteRevealHeight);
            Map<String, Set<String>> blindVoteTxIdsByIssuanceTxId = blindVoteTxIdsByIssuanceTxIdByCycle
                    .computeIfAbsent(cycleIndex, index -> new LinkedHashMap<>());
            Map<String, Set<String>> unvalidatedBlindVoteTxIdsByIssuanceTxId = unvalidatedBlindVoteTxIdsByIssuanceTxIdByCycle
                    .computeIfAbsent(cycleIndex, index -> new LinkedHashMap<>());

            Set<String> alreadyUsedIssuanceTxIds = new HashSet<>();
            for (protobuf.Merit meritProto : decryptedBallotsWithMerits.getMeritList().getMeritList()) {
                Merit merit = Merit.fromProto(meritProto);
                meritClaims++;
                unvalidatedBlindVoteTxIdsByIssuanceTxId
                        .computeIfAbsent(merit.getIssuanceTxId(), issuanceTxId -> new HashSet<>())
                        .add(blindVoteTxId);

                Optional<Issuance> validated = MeritConsensusV2.validateMeritClaim(merit,
                        blindVoteTxId,
                        daoStateService,
                        Math.toIntExact(voteRevealHeight),
                        alreadyUsedIssuanceTxIds);
                if (validated.isPresent()) {
                    validatedMeritClaims++;
                    blindVoteTxIdsByIssuanceTxId
                            .computeIfAbsent(validated.get().getTxId(), issuanceTxId -> new HashSet<>())
                            .add(blindVoteTxId);
                }
            }
        }

        List<String> duplicates = duplicates(blindVoteTxIdsByIssuanceTxIdByCycle);
        List<String> unvalidatedDuplicates = duplicates(unvalidatedBlindVoteTxIdsByIssuanceTxIdByCycle);

        // A grouping which put every blind vote into its own group would report no duplicates whatever the data
        // contains. These two numbers show that the grouping aggregates, and that issuances really are claimed again
        // in later cycles, which is legitimate and is exactly what the per cycle grouping has to tolerate.
        int mostBlindVotesInOneCycle = blindVoteTxIdsByIssuanceTxIdByCycle.values().stream()
                .mapToInt(blindVoteTxIdsByIssuanceTxId -> blindVoteTxIdsByIssuanceTxId.values().stream()
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet())
                        .size())
                .max()
                .orElse(0);
        Map<String, Integer> cycleCountByIssuanceTxId = new HashMap<>();
        blindVoteTxIdsByIssuanceTxIdByCycle.values().forEach(blindVoteTxIdsByIssuanceTxId ->
                blindVoteTxIdsByIssuanceTxId.keySet().forEach(issuanceTxId ->
                        cycleCountByIssuanceTxId.merge(issuanceTxId, 1, Integer::sum)));
        long issuancesClaimedInSeveralCycles = cycleCountByIssuanceTxId.values().stream()
                .filter(cycleCount -> cycleCount > 1)
                .count();

        System.out.println("Merit duplication audit of the bundled mainnet DAO state"
                + "\n  chainHeight=" + daoState.getChainHeight()
                + "\n  cycles=" + cycles.size()
                + "\n  compensation issuances=" + issuanceByTxId.values().stream()
                .filter(issuance -> IssuanceType.COMPENSATION.name().equals(issuance.getIssuanceType())).count()
                + "\n  decrypted blind votes=" + daoState.getDecryptedBallotsWithMeritsListCount()
                + "\n  cycles with votes=" + blindVoteTxIdsByIssuanceTxIdByCycle.size()
                + "\n  blind votes without a resolvable vote reveal=" + unresolvedBlindVoteTxIds.size()
                + "\n  merit claims=" + meritClaims
                + "\n  merit claims passing the V2 validation=" + validatedMeritClaims
                + "\n  most blind votes in one cycle=" + mostBlindVotesInOneCycle
                + "\n  issuances claimed in several cycles=" + issuancesClaimedInSeveralCycles
                + "\n  issuances claimed by several blind votes of one cycle, validated=" + duplicates.size()
                + "\n  issuances claimed by several blind votes of one cycle, unvalidated=" + unvalidatedDuplicates.size()
                + (unvalidatedDuplicates.isEmpty() ? "" : "\n  " + String.join("\n  ", unvalidatedDuplicates)));

        assertTrue(mostBlindVotesInOneCycle > 1,
                "the cycle grouping does not aggregate, so it cannot show the absence of duplicates");
        assertTrue(issuancesClaimedInSeveralCycles > 0,
                "no issuance is claimed again in a later cycle, so the data cannot show that the grouping " +
                        "distinguishes cycles");

        assertTrue(daoState.getDecryptedBallotsWithMeritsListCount() > 0, "no decrypted blind votes in the resource");
        assertTrue(unresolvedBlindVoteTxIds.isEmpty(),
                "blind votes whose cycle could not be resolved: " + unresolvedBlindVoteTxIds);
        assertEquals(List.of(), duplicates,
                "an issuance is claimed by several blind votes of one cycle, so activating the cycle wide uniqueness " +
                        "rule would change an already derived vote result");
        assertEquals(List.of(), unvalidatedDuplicates,
                "an issuance is claimed by several blind votes of one cycle even without validating the claims");
        assertFalse(validatedMeritClaims == 0, "no merit claim passed the validation, the audit proves nothing");
    }

    private static List<String> duplicates(Map<Integer, Map<String, Set<String>>> blindVoteTxIdsByIssuanceTxIdByCycle) {
        List<String> duplicates = new ArrayList<>();
        blindVoteTxIdsByIssuanceTxIdByCycle.forEach((cycleIndex, blindVoteTxIdsByIssuanceTxId) ->
                blindVoteTxIdsByIssuanceTxId.forEach((issuanceTxId, blindVoteTxIds) -> {
                    if (blindVoteTxIds.size() > 1) {
                        duplicates.add("cycleIndex=" + cycleIndex + ", issuanceTxId=" + issuanceTxId
                                + ", blindVoteTxIds=" + blindVoteTxIds);
                    }
                }));
        return duplicates;
    }

    private static int cycleIndexOf(List<Cycle> cycles, long height) {
        for (int i = cycles.size() - 1; i >= 0; i--) {
            if (height >= cycles.get(i).getHeightOfFirstBlock()) {
                return i;
            }
        }
        return -1;
    }

    private static DaoStateService daoStateServiceOf(Map<String, protobuf.Issuance> issuanceByTxId) {
        Map<String, Issuance> compensationIssuanceByTxId = new HashMap<>();
        issuanceByTxId.forEach((txId, issuanceProto) -> {
            Issuance issuance = Issuance.fromProto(issuanceProto);
            if (issuance.getIssuanceType() == IssuanceType.COMPENSATION) {
                compensationIssuanceByTxId.put(txId, issuance);
            }
        });

        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getIssuance(anyString(), any())).thenAnswer(invocation -> {
            if (invocation.getArgument(1) != IssuanceType.COMPENSATION) {
                return Optional.empty();
            }
            return Optional.ofNullable(compensationIssuanceByTxId.get((String) invocation.getArgument(0)));
        });
        return daoStateService;
    }

    private static protobuf.DaoState readBundledDaoState() throws IOException {
        try (InputStream inputStream = BundledMeritDuplicationAuditTest.class.getResourceAsStream(DAO_STATE_RESOURCE)) {
            assertTrue(inputStream != null, "resource not found: " + DAO_STATE_RESOURCE);
            // The store is written with writeDelimitedTo and exceeds the default size limit for stream input.
            CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
            codedInputStream.setSizeLimit(Integer.MAX_VALUE);
            int size = codedInputStream.readRawVarint32();
            int oldLimit = codedInputStream.pushLimit(size);
            protobuf.PersistableEnvelope envelope = protobuf.PersistableEnvelope.parser()
                    .parseFrom(codedInputStream);
            codedInputStream.popLimit(oldLimit);
            return envelope.getDaoStateStore().getDaoState();
        }
    }
}

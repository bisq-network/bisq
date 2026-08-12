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
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.governance.Issuance;
import bisq.core.dao.state.model.governance.IssuanceType;
import bisq.core.dao.state.model.governance.Merit;
import bisq.core.dao.state.model.governance.MeritList;

import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeritConsensusV3Test {
    private static final int BLIND_VOTE_HEIGHT = 954_210;
    private static final long ISSUANCE_AMOUNT = 100_000;
    private static final long OTHER_ISSUANCE_AMOUNT = 70_000;
    private static final String FIRST_BLIND_VOTE_TX_ID = txId("first-blind-vote-tx");
    private static final String SECOND_BLIND_VOTE_TX_ID = txId("second-blind-vote-tx");

    @Test
    void issuanceClaimedByTwoBlindVotesIsCountedForNeither() {
        ECKey key = new ECKey();
        Issuance issuance = compensationIssuance("compensation-tx", ISSUANCE_AMOUNT, key);
        DaoStateService daoStateService = daoStateService(issuance);

        Map<String, Long> meritStakeByBlindVoteTxId = MeritConsensusV3.getMeritStakeByBlindVoteTxId(Map.of(
                FIRST_BLIND_VOTE_TX_ID, meritList(merit(issuance, key, FIRST_BLIND_VOTE_TX_ID)),
                SECOND_BLIND_VOTE_TX_ID, meritList(merit(issuance, key, SECOND_BLIND_VOTE_TX_ID))),
                daoStateService);

        assertEquals(0, meritStakeByBlindVoteTxId.get(FIRST_BLIND_VOTE_TX_ID));
        assertEquals(0, meritStakeByBlindVoteTxId.get(SECOND_BLIND_VOTE_TX_ID));
    }

    @Test
    void onlyTheDuplicatedIssuanceIsDroppedFromABlindVote() {
        ECKey key = new ECKey();
        Issuance duplicatedIssuance = compensationIssuance("compensation-tx", ISSUANCE_AMOUNT, key);
        Issuance uniqueIssuance = compensationIssuance("other-compensation-tx", OTHER_ISSUANCE_AMOUNT, key);
        DaoStateService daoStateService = daoStateService(duplicatedIssuance, uniqueIssuance);

        Map<String, Long> meritStakeByBlindVoteTxId = MeritConsensusV3.getMeritStakeByBlindVoteTxId(Map.of(
                FIRST_BLIND_VOTE_TX_ID, meritList(merit(duplicatedIssuance, key, FIRST_BLIND_VOTE_TX_ID),
                        merit(uniqueIssuance, key, FIRST_BLIND_VOTE_TX_ID)),
                SECOND_BLIND_VOTE_TX_ID, meritList(merit(duplicatedIssuance, key, SECOND_BLIND_VOTE_TX_ID))),
                daoStateService);

        assertEquals(OTHER_ISSUANCE_AMOUNT, meritStakeByBlindVoteTxId.get(FIRST_BLIND_VOTE_TX_ID));
        assertEquals(0, meritStakeByBlindVoteTxId.get(SECOND_BLIND_VOTE_TX_ID));
    }

    @Test
    void distinctIssuancesKeepTheirFullMerit() {
        ECKey firstKey = new ECKey();
        ECKey secondKey = new ECKey();
        Issuance firstIssuance = compensationIssuance("first-compensation-tx", ISSUANCE_AMOUNT, firstKey);
        Issuance secondIssuance = compensationIssuance("second-compensation-tx", OTHER_ISSUANCE_AMOUNT, secondKey);
        DaoStateService daoStateService = daoStateService(firstIssuance, secondIssuance);

        Map<String, Long> meritStakeByBlindVoteTxId = MeritConsensusV3.getMeritStakeByBlindVoteTxId(Map.of(
                FIRST_BLIND_VOTE_TX_ID, meritList(merit(firstIssuance, firstKey, FIRST_BLIND_VOTE_TX_ID)),
                SECOND_BLIND_VOTE_TX_ID, meritList(merit(secondIssuance, secondKey, SECOND_BLIND_VOTE_TX_ID))),
                daoStateService);

        assertEquals(ISSUANCE_AMOUNT, meritStakeByBlindVoteTxId.get(FIRST_BLIND_VOTE_TX_ID));
        assertEquals(OTHER_ISSUANCE_AMOUNT, meritStakeByBlindVoteTxId.get(SECOND_BLIND_VOTE_TX_ID));
    }

    @Test
    void claimSignedWithAForeignKeyDoesNotSuppressTheGenuineClaim() {
        ECKey key = new ECKey();
        ECKey attackerKey = new ECKey();
        Issuance issuance = compensationIssuance("compensation-tx", ISSUANCE_AMOUNT, key);
        DaoStateService daoStateService = daoStateService(issuance);

        // The attacker names the victim's issuance but cannot sign for it.
        Map<String, Long> meritStakeByBlindVoteTxId = MeritConsensusV3.getMeritStakeByBlindVoteTxId(Map.of(
                FIRST_BLIND_VOTE_TX_ID, meritList(merit(issuance, key, FIRST_BLIND_VOTE_TX_ID)),
                SECOND_BLIND_VOTE_TX_ID, meritList(merit(issuance, attackerKey, SECOND_BLIND_VOTE_TX_ID))),
                daoStateService);

        assertEquals(ISSUANCE_AMOUNT, meritStakeByBlindVoteTxId.get(FIRST_BLIND_VOTE_TX_ID));
        assertEquals(0, meritStakeByBlindVoteTxId.get(SECOND_BLIND_VOTE_TX_ID));
    }

    @Test
    void claimWithForgedIssuanceValuesDoesNotSuppressTheGenuineClaim() {
        ECKey key = new ECKey();
        Issuance issuance = compensationIssuance("compensation-tx", ISSUANCE_AMOUNT, key);
        Issuance forgedIssuance = new Issuance(issuance.getTxId(),
                issuance.getChainHeight(),
                issuance.getAmount() + 1,
                issuance.getPubKey(),
                issuance.getIssuanceType());
        DaoStateService daoStateService = daoStateService(issuance);

        Map<String, Long> meritStakeByBlindVoteTxId = MeritConsensusV3.getMeritStakeByBlindVoteTxId(Map.of(
                FIRST_BLIND_VOTE_TX_ID, meritList(merit(issuance, key, FIRST_BLIND_VOTE_TX_ID)),
                SECOND_BLIND_VOTE_TX_ID, meritList(merit(forgedIssuance, key, SECOND_BLIND_VOTE_TX_ID))),
                daoStateService);

        assertEquals(ISSUANCE_AMOUNT, meritStakeByBlindVoteTxId.get(FIRST_BLIND_VOTE_TX_ID));
        assertEquals(0, meritStakeByBlindVoteTxId.get(SECOND_BLIND_VOTE_TX_ID));
    }

    @Test
    void claimToAnIssuanceMissingFromDaoStateDoesNotSuppressTheGenuineClaim() {
        ECKey key = new ECKey();
        ECKey unknownKey = new ECKey();
        Issuance issuance = compensationIssuance("compensation-tx", ISSUANCE_AMOUNT, key);
        Issuance unknownIssuance = compensationIssuance("unknown-compensation-tx", ISSUANCE_AMOUNT, unknownKey);
        DaoStateService daoStateService = daoStateService(issuance);

        Map<String, Long> meritStakeByBlindVoteTxId = MeritConsensusV3.getMeritStakeByBlindVoteTxId(Map.of(
                FIRST_BLIND_VOTE_TX_ID, meritList(merit(issuance, key, FIRST_BLIND_VOTE_TX_ID)),
                SECOND_BLIND_VOTE_TX_ID, meritList(merit(unknownIssuance, unknownKey, SECOND_BLIND_VOTE_TX_ID),
                        merit(unknownIssuance, unknownKey, SECOND_BLIND_VOTE_TX_ID))),
                daoStateService);

        assertEquals(ISSUANCE_AMOUNT, meritStakeByBlindVoteTxId.get(FIRST_BLIND_VOTE_TX_ID));
        assertEquals(0, meritStakeByBlindVoteTxId.get(SECOND_BLIND_VOTE_TX_ID));
    }

    @Test
    void issuanceClaimedTwiceByOneBlindVoteIsStillCountedOnce() {
        ECKey key = new ECKey();
        Issuance issuance = compensationIssuance("compensation-tx", ISSUANCE_AMOUNT, key);
        DaoStateService daoStateService = daoStateService(issuance);

        // The repeated claim is already removed within the merit list, so it must not look like a duplicate across
        // blind votes and remove the merit altogether.
        Map<String, Long> meritStakeByBlindVoteTxId = MeritConsensusV3.getMeritStakeByBlindVoteTxId(Map.of(
                FIRST_BLIND_VOTE_TX_ID, meritList(merit(issuance, key, FIRST_BLIND_VOTE_TX_ID),
                        merit(issuance, key, FIRST_BLIND_VOTE_TX_ID))),
                daoStateService);

        assertEquals(ISSUANCE_AMOUNT, meritStakeByBlindVoteTxId.get(FIRST_BLIND_VOTE_TX_ID));
    }

    @Test
    void blindVoteMissingFromDaoStateDoesNotSuppressTheGenuineClaim() {
        ECKey key = new ECKey();
        Issuance issuance = compensationIssuance("compensation-tx", ISSUANCE_AMOUNT, key);
        DaoStateService daoStateService = daoStateService(issuance);
        when(daoStateService.getTx(SECOND_BLIND_VOTE_TX_ID)).thenReturn(Optional.empty());

        Map<String, Long> meritStakeByBlindVoteTxId = MeritConsensusV3.getMeritStakeByBlindVoteTxId(Map.of(
                FIRST_BLIND_VOTE_TX_ID, meritList(merit(issuance, key, FIRST_BLIND_VOTE_TX_ID)),
                SECOND_BLIND_VOTE_TX_ID, meritList(merit(issuance, key, SECOND_BLIND_VOTE_TX_ID))),
                daoStateService);

        assertEquals(ISSUANCE_AMOUNT, meritStakeByBlindVoteTxId.get(FIRST_BLIND_VOTE_TX_ID));
        assertEquals(0, meritStakeByBlindVoteTxId.get(SECOND_BLIND_VOTE_TX_ID));
    }

    private static DaoStateService daoStateService(Issuance... issuances) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        Tx blindVoteTx = mock(Tx.class);
        when(blindVoteTx.getBlockHeight()).thenReturn(BLIND_VOTE_HEIGHT);
        when(daoStateService.getTx(FIRST_BLIND_VOTE_TX_ID)).thenReturn(Optional.of(blindVoteTx));
        when(daoStateService.getTx(SECOND_BLIND_VOTE_TX_ID)).thenReturn(Optional.of(blindVoteTx));
        for (Issuance issuance : issuances) {
            when(daoStateService.getIssuance(issuance.getTxId(), IssuanceType.COMPENSATION))
                    .thenReturn(Optional.of(issuance));
        }
        return daoStateService;
    }

    private static Issuance compensationIssuance(String txIdSeed, long amount, ECKey key) {
        return new Issuance(txId(txIdSeed),
                BLIND_VOTE_HEIGHT,
                amount,
                Utilities.encodeToHex(key.getPubKey()),
                IssuanceType.COMPENSATION);
    }

    private static MeritList meritList(Merit... merits) {
        return new MeritList(List.of(merits));
    }

    private static Merit merit(Issuance issuance, ECKey key, String blindVoteTxId) {
        return new Merit(issuance, key.sign(Sha256Hash.wrap(blindVoteTxId)).encodeToDER());
    }

    private static String txId(String seed) {
        return Sha256Hash.of(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }
}

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeritConsensusV2Test {
    private static final int BLIND_VOTE_HEIGHT = 954_210;
    private static final long ISSUANCE_AMOUNT = 100_000;
    private static final String BLIND_VOTE_TX_ID = txId("blind-vote-tx");

    @Test
    void getMeritStakeAcceptsDistinctIssuanceObjectWithSameValuesAsDaoState() {
        ECKey key = new ECKey();
        Issuance daoStateIssuance = compensationIssuance("compensation-tx", BLIND_VOTE_HEIGHT, ISSUANCE_AMOUNT, key);
        Issuance embeddedIssuance = copy(daoStateIssuance);

        assertNotSame(daoStateIssuance, embeddedIssuance);
        assertTrue(daoStateIssuance.isValueEqual(embeddedIssuance));

        MeritList meritList = new MeritList(List.of(merit(embeddedIssuance, key)));
        DaoStateService daoStateService = daoStateService(daoStateIssuance);

        assertEquals(ISSUANCE_AMOUNT, MeritConsensusV2.getMeritStake(BLIND_VOTE_TX_ID, meritList, daoStateService));
    }

    @Test
    void getMeritStakeRejectsEmbeddedIssuanceWhenValuesDifferFromDaoState() {
        ECKey key = new ECKey();
        Issuance daoStateIssuance = compensationIssuance("compensation-tx", BLIND_VOTE_HEIGHT, ISSUANCE_AMOUNT, key);
        Issuance forgedEmbeddedIssuance = new Issuance(daoStateIssuance.getTxId(),
                daoStateIssuance.getChainHeight(),
                daoStateIssuance.getAmount() + 1,
                daoStateIssuance.getPubKey(),
                daoStateIssuance.getIssuanceType());

        MeritList meritList = new MeritList(List.of(merit(forgedEmbeddedIssuance, key)));
        DaoStateService daoStateService = daoStateService(daoStateIssuance);

        assertEquals(0, MeritConsensusV2.getMeritStake(BLIND_VOTE_TX_ID, meritList, daoStateService));
    }

    @Test
    void getMeritStakeCountsDuplicateIssuanceTxIdOnlyOnce() {
        ECKey key = new ECKey();
        Issuance daoStateIssuance = compensationIssuance("compensation-tx", BLIND_VOTE_HEIGHT, ISSUANCE_AMOUNT, key);

        MeritList meritList = new MeritList(List.of(
                merit(copy(daoStateIssuance), key),
                merit(copy(daoStateIssuance), key)));
        DaoStateService daoStateService = daoStateService(daoStateIssuance);

        assertEquals(ISSUANCE_AMOUNT, MeritConsensusV2.getMeritStake(BLIND_VOTE_TX_ID, meritList, daoStateService));
    }

    @Test
    void getMeritStakeThrowsOnTotalMeritOverflow() {
        ECKey firstKey = new ECKey();
        ECKey secondKey = new ECKey();
        Issuance firstIssuance = compensationIssuance("first-compensation-tx",
                BLIND_VOTE_HEIGHT,
                Long.MAX_VALUE,
                firstKey);
        Issuance secondIssuance = compensationIssuance("second-compensation-tx",
                BLIND_VOTE_HEIGHT,
                1,
                secondKey);
        MeritList meritList = new MeritList(List.of(
                merit(firstIssuance, firstKey),
                merit(secondIssuance, secondKey)));
        DaoStateService daoStateService = daoStateService(firstIssuance, secondIssuance);

        assertThrows(ArithmeticException.class,
                () -> MeritConsensusV2.getMeritStake(BLIND_VOTE_TX_ID, meritList, daoStateService));
    }

    @Test
    void getMeritStakeIgnoresInvalidAndExpiredMeritsWithoutAbortingValidMerits() {
        ECKey validKey = new ECKey();
        Issuance validIssuance = compensationIssuance("valid-compensation-tx",
                BLIND_VOTE_HEIGHT,
                ISSUANCE_AMOUNT,
                validKey);

        ECKey expiredKey = new ECKey();
        Issuance expiredIssuance = compensationIssuance("expired-compensation-tx",
                BLIND_VOTE_HEIGHT - 100_000,
                ISSUANCE_AMOUNT,
                expiredKey);

        ECKey invalidTypeKey = new ECKey();
        Issuance invalidTypeIssuance = new Issuance(txId("reimbursement-tx"),
                BLIND_VOTE_HEIGHT,
                ISSUANCE_AMOUNT,
                Utilities.encodeToHex(invalidTypeKey.getPubKey()),
                IssuanceType.REIMBURSEMENT);

        MeritList meritList = new MeritList(List.of(
                merit(invalidTypeIssuance, invalidTypeKey),
                merit(copy(expiredIssuance), expiredKey),
                merit(copy(validIssuance), validKey)));
        DaoStateService daoStateService = daoStateService(validIssuance, expiredIssuance);

        assertEquals(ISSUANCE_AMOUNT, MeritConsensusV2.getMeritStake(BLIND_VOTE_TX_ID, meritList, daoStateService));
    }

    private static DaoStateService daoStateService(Issuance... issuances) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        Tx blindVoteTx = mock(Tx.class);
        when(blindVoteTx.getBlockHeight()).thenReturn(BLIND_VOTE_HEIGHT);
        when(daoStateService.getTx(BLIND_VOTE_TX_ID)).thenReturn(Optional.of(blindVoteTx));
        for (Issuance issuance : issuances) {
            when(daoStateService.getIssuance(issuance.getTxId(), IssuanceType.COMPENSATION))
                    .thenReturn(Optional.of(issuance));
        }
        return daoStateService;
    }

    private static Issuance compensationIssuance(String txIdSeed, int chainHeight, long amount, ECKey key) {
        return new Issuance(txId(txIdSeed),
                chainHeight,
                amount,
                Utilities.encodeToHex(key.getPubKey()),
                IssuanceType.COMPENSATION);
    }

    private static Issuance copy(Issuance issuance) {
        return new Issuance(issuance.getTxId(),
                issuance.getChainHeight(),
                issuance.getAmount(),
                issuance.getPubKey(),
                issuance.getIssuanceType());
    }

    private static Merit merit(Issuance issuance, ECKey key) {
        byte[] signature = key.sign(Sha256Hash.wrap(BLIND_VOTE_TX_ID)).encodeToDER();
        return new Merit(issuance, signature);
    }

    private static String txId(String seed) {
        return Sha256Hash.of(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }
}

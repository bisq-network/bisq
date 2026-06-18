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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeritConsensusTest {
    private static final int PRE_ACTIVATION_HEIGHT = MeritConsensus.MERIT_CONSENSUS_V2_ACTIVATION_HEIGHT - 1;
    private static final int ACTIVATION_HEIGHT = MeritConsensus.MERIT_CONSENSUS_V2_ACTIVATION_HEIGHT;
    private static final int BLIND_VOTE_HEIGHT = PRE_ACTIVATION_HEIGHT - 10;
    private static final long ISSUANCE_AMOUNT = 100_000;
    private static final long FORGED_ISSUANCE_AMOUNT = ISSUANCE_AMOUNT + 1;
    private static final String BLIND_VOTE_TX_ID = txId("blind-vote-tx");

    @Test
    void getMeritStakeUsesEvaluationHeightForActivationBoundary() {
        ECKey key = new ECKey();
        Issuance daoStateIssuance = compensationIssuance("compensation-tx", BLIND_VOTE_HEIGHT, ISSUANCE_AMOUNT, key);
        Issuance forgedEmbeddedIssuance = new Issuance(daoStateIssuance.getTxId(),
                daoStateIssuance.getChainHeight(),
                FORGED_ISSUANCE_AMOUNT,
                daoStateIssuance.getPubKey(),
                daoStateIssuance.getIssuanceType());
        MeritList meritList = new MeritList(List.of(merit(forgedEmbeddedIssuance, key)));
        DaoStateService daoStateService = daoStateService(ACTIVATION_HEIGHT, daoStateIssuance);

        assertEquals(FORGED_ISSUANCE_AMOUNT, MeritConsensus.getMeritStake(BLIND_VOTE_TX_ID,
                meritList,
                daoStateService,
                PRE_ACTIVATION_HEIGHT));
        assertEquals(0, MeritConsensus.getMeritStake(BLIND_VOTE_TX_ID,
                meritList,
                daoStateService,
                ACTIVATION_HEIGHT));
    }

    @Test
    void getMeritStakeDefaultUsesDaoStateChainHeight() {
        ECKey key = new ECKey();
        Issuance issuance = compensationIssuance("compensation-tx", BLIND_VOTE_HEIGHT, ISSUANCE_AMOUNT, key);
        MeritList meritList = new MeritList(List.of(merit(issuance, key)));
        DaoStateService daoStateService = daoStateService(ACTIVATION_HEIGHT, issuance);

        assertEquals(ISSUANCE_AMOUNT, MeritConsensus.getMeritStake(BLIND_VOTE_TX_ID,
                meritList,
                daoStateService));
    }

    private static DaoStateService daoStateService(int chainHeight, Issuance issuance) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        Tx blindVoteTx = mock(Tx.class);
        when(blindVoteTx.getBlockHeight()).thenReturn(BLIND_VOTE_HEIGHT);
        when(daoStateService.getChainHeight()).thenReturn(chainHeight);
        when(daoStateService.getTx(BLIND_VOTE_TX_ID)).thenReturn(Optional.of(blindVoteTx));
        when(daoStateService.getIssuance(issuance.getTxId(), IssuanceType.COMPENSATION))
                .thenReturn(Optional.of(issuance));
        return daoStateService;
    }

    private static Issuance compensationIssuance(String txIdSeed, int chainHeight, long amount, ECKey key) {
        return new Issuance(txId(txIdSeed),
                chainHeight,
                amount,
                Utilities.encodeToHex(key.getPubKey()),
                IssuanceType.COMPENSATION);
    }

    private static Merit merit(Issuance issuance, ECKey key) {
        byte[] signature = key.sign(Sha256Hash.wrap(BLIND_VOTE_TX_ID)).encodeToDER();
        return new Merit(issuance, signature);
    }

    private static String txId(String seed) {
        return Sha256Hash.of(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }
}

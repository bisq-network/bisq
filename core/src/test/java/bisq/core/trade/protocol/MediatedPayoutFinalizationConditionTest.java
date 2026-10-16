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

package bisq.core.trade.protocol;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferMaker;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.DisputeProtocol;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import org.junit.jupiter.api.Test;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins down when a trader publishes the mediated payout without a further click
 * (DisputeProtocol#isMediatedPayoutReadyToFinalize): while the mediation result is
 * proposed, once it has signed the payout itself and has the peer's signature, as long
 * as the payout is not published.
 */
public class MediatedPayoutFinalizationConditionTest {
    private static final byte[] OWN_SIGNATURE = new byte[]{1};
    private static final byte[] PEERS_SIGNATURE = new byte[]{2};

    private static Trade trade(Trade.DisputeState disputeState,
                               byte[] ownSignature,
                               byte[] peersSignature,
                               boolean payoutPublished) {
        Offer offer = make(a(OfferMaker.Offer, with(OfferMaker.direction, OfferDirection.SELL)));
        ProcessModel processModel = new ProcessModel(offer.getId(), "account-id",
                new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic()));
        processModel.applyTransient(mock(Provider.class), mock(TradeManager.class), offer);
        processModel.setMediatedPayoutTxSignature(ownSignature);
        processModel.getTradePeer().setMediatedPayoutTxSignature(peersSignature);
        Trade trade = new SellerAsMakerTrade(offer,
                Coin.valueOf(1_000) /* txFee */,
                Coin.valueOf(100) /* takerFee */,
                true,
                null /* arbitratorNodeAddress */,
                null /* mediatorNodeAddress */,
                null /* refundAgentNodeAddress */,
                null /* btcWalletService */,
                processModel,
                "uid");
        trade.setDisputeState(disputeState);
        if (payoutPublished) {
            Transaction payoutTx = mock(Transaction.class);
            when(payoutTx.getTxId()).thenReturn(Sha256Hash.ZERO_HASH);
            trade.setPayoutTx(payoutTx);
        }
        return trade;
    }

    @Test
    public void readyWhenBothHaveSigned() {
        // the fixed case: the peer's signature arrived after our own acceptance
        assertTrue(DisputeProtocol.isMediatedPayoutReadyToFinalize(
                trade(Trade.DisputeState.MEDIATION_CLOSED, OWN_SIGNATURE, PEERS_SIGNATURE, false)));
    }

    @Test
    public void notReadyBeforeOwnAcceptance() {
        // the peer has accepted, but we have not: the trader decides with the accept button
        assertFalse(DisputeProtocol.isMediatedPayoutReadyToFinalize(
                trade(Trade.DisputeState.MEDIATION_CLOSED, null, PEERS_SIGNATURE, false)));
    }

    @Test
    public void notReadyWithoutPeersSignature() {
        assertFalse(DisputeProtocol.isMediatedPayoutReadyToFinalize(
                trade(Trade.DisputeState.MEDIATION_CLOSED, OWN_SIGNATURE, null, false)));
    }

    @Test
    public void notReadyOncePayoutIsPublished() {
        assertFalse(DisputeProtocol.isMediatedPayoutReadyToFinalize(
                trade(Trade.DisputeState.MEDIATION_CLOSED, OWN_SIGNATURE, PEERS_SIGNATURE, true)));
    }

    @Test
    public void notReadyWithoutProposedMediationResult() {
        assertFalse(DisputeProtocol.isMediatedPayoutReadyToFinalize(
                trade(Trade.DisputeState.MEDIATION_REQUESTED, OWN_SIGNATURE, PEERS_SIGNATURE, false)));
        assertFalse(DisputeProtocol.isMediatedPayoutReadyToFinalize(
                trade(Trade.DisputeState.REFUND_REQUESTED, OWN_SIGNATURE, PEERS_SIGNATURE, false)));
        assertFalse(DisputeProtocol.isMediatedPayoutReadyToFinalize(
                trade(Trade.DisputeState.NO_DISPUTE, OWN_SIGNATURE, PEERS_SIGNATURE, false)));
    }
}

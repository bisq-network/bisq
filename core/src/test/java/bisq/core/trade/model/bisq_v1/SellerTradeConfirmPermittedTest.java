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

package bisq.core.trade.model.bisq_v1;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferMaker;
import bisq.core.support.dispute.mediation.MediationResultState;
import bisq.core.trade.TradeManager;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bisq_v1.SellerAsMakerProtocol;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

// After the mediator proposed a result that does not penalize the seller, the seller can still confirm the payment
// receipt and complete the trade, but not after the mediated payout was published.
class SellerTradeConfirmPermittedTest {
    private static final long SELLER_SECURITY_DEPOSIT = 150_000;

    private SellerAsMakerTrade trade;

    @BeforeEach
    void setUp() {
        Offer offer = make(a(OfferMaker.Offer,
                with(OfferMaker.direction, OfferDirection.SELL),
                with(OfferMaker.sellerSecurityDeposit, SELLER_SECURITY_DEPOSIT)));
        ProcessModel processModel = new ProcessModel(offer.getId(), "account-id",
                new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic()));
        processModel.applyTransient(mock(Provider.class), mock(TradeManager.class), offer);
        trade = new SellerAsMakerTrade(offer,
                Coin.valueOf(1_000) /* txFee */,
                Coin.valueOf(100) /* takerFee */,
                true,
                null /* arbitratorNodeAddress */,
                null /* mediatorNodeAddress */,
                null /* refundAgentNodeAddress */,
                null /* btcWalletService */,
                processModel,
                "uid");
    }

    private void closeMediation(long sellerPayoutAmount, MediationResultState mediationResultState) {
        trade.setDisputeState(Trade.DisputeState.MEDIATION_CLOSED);
        trade.getProcessModel().setSellerPayoutAmountFromMediation(sellerPayoutAmount);
        trade.setMediationResultState(mediationResultState);
    }

    @Test
    void permittedWithoutDispute() {
        assertTrue(trade.confirmPermitted());
    }

    // Also after the seller accepted the proposal, as long as no mediated payout exists
    @ParameterizedTest
    @EnumSource(value = MediationResultState.class, mode = EnumSource.Mode.EXCLUDE, names = {
            "PAYOUT_TX_PUBLISHED",
            "PAYOUT_TX_PUBLISHED_MSG_SENT",
            "PAYOUT_TX_PUBLISHED_MSG_ARRIVED",
            "PAYOUT_TX_PUBLISHED_MSG_IN_MAILBOX",
            "PAYOUT_TX_PUBLISHED_MSG_SEND_FAILED",
            "RECEIVED_PAYOUT_TX_PUBLISHED_MSG",
            "PAYOUT_TX_SEEN_IN_NETWORK"})
    void permittedAfterProposalUntilMediatedPayoutIsPublished(MediationResultState mediationResultState) {
        closeMediation(SELLER_SECURITY_DEPOSIT, mediationResultState);

        assertTrue(trade.confirmPermitted());
    }

    // Published by us, received from the peer, or seen in the network
    @ParameterizedTest
    @EnumSource(value = MediationResultState.class, names = {
            "PAYOUT_TX_PUBLISHED",
            "PAYOUT_TX_PUBLISHED_MSG_SENT",
            "PAYOUT_TX_PUBLISHED_MSG_ARRIVED",
            "PAYOUT_TX_PUBLISHED_MSG_IN_MAILBOX",
            "PAYOUT_TX_PUBLISHED_MSG_SEND_FAILED",
            "RECEIVED_PAYOUT_TX_PUBLISHED_MSG",
            "PAYOUT_TX_SEEN_IN_NETWORK"})
    void refusedAfterMediatedPayoutIsPublished(MediationResultState mediationResultState) {
        closeMediation(SELLER_SECURITY_DEPOSIT, mediationResultState);

        assertFalse(trade.confirmPermitted());
    }

    @Test
    void refusedAfterProposalThatPenalizesTheSeller() {
        closeMediation(SELLER_SECURITY_DEPOSIT - 1, MediationResultState.UNDEFINED_MEDIATION_RESULT);

        assertFalse(trade.confirmPermitted());
    }

    @Test
    void confirmationAfterMediatedPayoutDoesNotStartThePayout() {
        // Publishing the mediated payout moves the seller's trade to step 4
        trade.setState(Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG);
        closeMediation(SELLER_SECURITY_DEPOSIT, MediationResultState.PAYOUT_TX_SEEN_IN_NETWORK);

        new SellerAsMakerProtocol(trade).onPaymentReceived(() -> fail("result handler called"),
                errorMessage -> fail(errorMessage));

        assertEquals(Trade.State.SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG, trade.getTradeState());
    }

    @Test
    void confirmationAfterAcceptedProposalStartsThePayout() {
        trade.setState(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG);
        closeMediation(SELLER_SECURITY_DEPOSIT, MediationResultState.SIG_MSG_ARRIVED);

        // The first payout task fails on the incomplete test trade and logs the error
        new SellerAsMakerProtocol(trade).onPaymentReceived(() -> {
        }, errorMessage -> {
        });

        assertEquals(Trade.State.SELLER_CONFIRMED_IN_UI_FIAT_PAYMENT_RECEIPT, trade.getTradeState());
    }
}

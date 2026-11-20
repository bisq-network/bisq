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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferMaker;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import org.bitcoinj.core.Coin;

import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.mockito.Mockito.mock;

/**
 * Real trades for the tests of the mediation handling in DisputeProtocol.
 */
final class MediationTestTrades {
    private MediationTestTrades() {
    }

    static Trade sellerAsMakerTrade(Provider provider, Trade.State state, Trade.DisputeState disputeState) {
        Offer offer = make(a(OfferMaker.Offer, with(OfferMaker.direction, OfferDirection.SELL)));
        ProcessModel processModel = new ProcessModel(offer.getId(), "account-id",
                new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic()));
        processModel.applyTransient(provider, mock(TradeManager.class), offer);
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
        trade.setState(state);
        trade.setDisputeState(disputeState);
        return trade;
    }
}

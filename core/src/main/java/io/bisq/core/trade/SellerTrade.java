/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade;

import io.bisq.common.app.Version;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.storage.Storage;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.protocol.SellerProtocol;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class SellerTrade extends Trade {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(BuyerAsTakerTrade.class);

    SellerTrade(Offer offer, Coin tradeAmount, Coin txFee, Coin takeOfferFee, long tradePrice, NodeAddress tradingPeerNodeAddress, Storage<? extends TradableList> storage) {
        super(offer, tradeAmount, txFee, takeOfferFee, tradePrice, tradingPeerNodeAddress, storage);
    }

    SellerTrade(Offer offer, Coin txFee, Coin takeOfferFee, Storage<? extends TradableList> storage) {
        super(offer, txFee, takeOfferFee, storage);
    }

    @Override
    protected void initStates() {
        if (state == null)
            state = State.PREPARATION;
    }

    public void onFiatPaymentReceived(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        checkArgument(tradeProtocol instanceof SellerProtocol, "tradeProtocol NOT instanceof SellerProtocol");
        ((SellerProtocol) tradeProtocol).onFiatPaymentReceived(resultHandler, errorMessageHandler);
    }

    @Override
    public Coin getPayoutAmount() {
        return getOffer().getSellerSecurityDeposit();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setState(State state) {
        super.setState(state);

        if (state == State.WITHDRAW_COMPLETED && tradeProtocol != null)
            tradeProtocol.completed();
    }
}

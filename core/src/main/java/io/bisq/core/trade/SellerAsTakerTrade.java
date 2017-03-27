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
import io.bisq.common.storage.Storage;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.protocol.SellerAsTakerProtocol;
import io.bisq.core.trade.protocol.TakerProtocol;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;

public final class SellerAsTakerTrade extends SellerTrade implements TakerTrade {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(SellerAsTakerTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerTrade(Offer offer, Coin tradeAmount, Coin txFee, Coin takeOfferFee, long tradePrice, NodeAddress tradingPeerNodeAddress, Storage<? extends TradableList> storage) {
        super(offer, tradeAmount, txFee, takeOfferFee, tradePrice, tradingPeerNodeAddress, storage);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            initStateProperties();
            initAmountProperty();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    @Override
    protected void createProtocol() {
        tradeProtocol = new SellerAsTakerProtocol(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        checkArgument(tradeProtocol instanceof TakerProtocol, "tradeProtocol NOT instanceof TakerProtocol");
        ((TakerProtocol) tradeProtocol).takeAvailableOffer();
    }
}

/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade;

import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.trade.SellerAsTakerProtocol;
import io.bitsquare.trade.protocol.trade.SellerProtocol;
import io.bitsquare.trade.states.TakerTradeState;

import org.bitcoinj.core.Coin;

import java.io.IOException;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SellerAsTakerTrade extends TakerTrade implements SellerTrade, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(TakerTradeState.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsTakerTrade(Offer offer, Coin tradeAmount, Peer tradingPeer, Storage<? extends TradeList> storage) {
        super(offer, tradeAmount, tradingPeer, storage);
        log.trace("Created by constructor");
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");

        initStateProperties();
        initAmountProperty();
    }

    @Override
    protected void createProtocol() {
        tradeProtocol = new SellerAsTakerProtocol(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onFiatPaymentReceived() {
        assert tradeProtocol instanceof SellerProtocol;
        ((SellerProtocol) tradeProtocol).onFiatPaymentReceived();
    }
}

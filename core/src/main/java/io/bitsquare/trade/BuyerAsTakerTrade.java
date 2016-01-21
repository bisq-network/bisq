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

import io.bitsquare.app.Version;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.trade.BuyerAsTakerProtocol;
import io.bitsquare.trade.protocol.trade.TakerProtocol;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class BuyerAsTakerTrade extends BuyerTrade implements TakerTrade, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(BuyerAsTakerTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsTakerTrade(Offer offer, Coin tradeAmount, NodeAddress tradingPeerNodeAddress, Storage<? extends TradableList> storage) {
        super(offer, tradeAmount, tradingPeerNodeAddress, storage);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            initStateProperties();
            initAmountProperty();
        } catch (Throwable t) {
            log.trace("Cannot be deserialized." + t.getMessage());
        }
    }

    @Override
    protected void createProtocol() {
        tradeProtocol = new BuyerAsTakerProtocol(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        checkArgument(tradeProtocol instanceof TakerProtocol, "tradeProtocol NOT instanceof TakerProtocol");
        ((TakerProtocol) tradeProtocol).takeAvailableOffer();
    }

    @Override
    public Coin getPayoutAmount() {
        checkNotNull(getTradeAmount(), "Invalid state: getTradeAmount() = null");

        return FeePolicy.getSecurityDeposit().add(getTradeAmount());
    }
}

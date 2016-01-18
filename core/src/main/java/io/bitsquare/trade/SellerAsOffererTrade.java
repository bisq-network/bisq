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
import io.bitsquare.p2p.Address;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.trade.OffererProtocol;
import io.bitsquare.trade.protocol.trade.SellerAsOffererProtocol;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

public class SellerAsOffererTrade extends SellerTrade implements OffererTrade, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(SellerAsOffererTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerAsOffererTrade(Offer offer, Storage<? extends TradableList> storage) {
        super(offer, storage);
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
        tradeProtocol = new SellerAsOffererProtocol(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleTakeOfferRequest(TradeMessage message, Address taker) {
        ((OffererProtocol) tradeProtocol).handleTakeOfferRequest(message, taker);
    }
}
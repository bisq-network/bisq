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

package io.bitsquare.gui.main.portfolio.pending;

import io.bitsquare.trade.Trade;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.util.Date;

import javafx.beans.property.ObjectProperty;

/**
 * We could remove that wrapper if it is not needed for additional UI only fields.
 */
public class PendingTradesListItem {

    private final Trade trade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PendingTradesListItem(Trade trade) {
        this.trade = trade;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Trade getTrade() {
        return trade;
    }

    public ObjectProperty<Coin> tradeAmountProperty() {
        return trade.tradeAmountProperty();
    }

    public ObjectProperty<Fiat> tradeVolumeProperty() {
        return trade.tradeVolumeProperty();
    }

    public Date getDate() {
        return trade.getDate();
    }

    public String getId() {
        return trade.getId();
    }

    public Fiat getPrice() {
        return trade.getOffer().getPrice();
    }
}

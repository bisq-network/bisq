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

package io.bitsquare.gui.main.orders.pending;

import io.bitsquare.locale.Country;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO don't use inheritance
public class PendingTradesListItem {
    private static final Logger log = LoggerFactory.getLogger(PendingTradesListItem.class);

    private final Offer offer;
    private final ObjectProperty<Country> bankAccountCountry = new SimpleObjectProperty<>();
    private final Trade trade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PendingTradesListItem(Trade trade) {
        this.trade = trade;

        this.offer = trade.getOffer();
        setBankAccountCountry(offer.getBankAccountCountry());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setBankAccountCountry(Country bankAccountCountry) {
        this.bankAccountCountry.set(bankAccountCountry);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Trade getTrade() {
        return trade;
    }

    Offer getOffer() {
        return offer;
    }

    Country getBankAccountCountry() {
        return bankAccountCountry.get();
    }

    ObjectProperty<Country> bankAccountCountryProperty() {
        return bankAccountCountry;
    }

}

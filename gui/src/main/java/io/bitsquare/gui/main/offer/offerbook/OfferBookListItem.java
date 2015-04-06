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

package io.bitsquare.gui.main.offer.offerbook;

import io.bitsquare.locale.Country;
import io.bitsquare.offer.Offer;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class OfferBookListItem {
    private final Offer offer;
    private final ObjectProperty<Country> bankAccountCountry = new SimpleObjectProperty<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferBookListItem(Offer offer, Country bankAccountCountry) {
        this.offer = offer;
        setBankAccountCountry(bankAccountCountry);
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

    public Offer getOffer() {
        return offer;
    }

    Country getBankAccountCountry() {
        return bankAccountCountry.get();
    }

    ObjectProperty<Country> bankAccountCountryProperty() {
        return bankAccountCountry;
    }


}


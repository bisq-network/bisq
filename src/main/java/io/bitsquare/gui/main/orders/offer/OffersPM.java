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

package io.bitsquare.gui.main.orders.offer;

import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.BSFormatter;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OffersPM extends PresentationModel<OffersModel> {
    private static final Logger log = LoggerFactory.getLogger(OffersPM.class);

    private final BSFormatter formatter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OffersPM(OffersModel model, BSFormatter formatter) {
        super(model);

        this.formatter = formatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize() {
        super.initialize();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void removeOffer(OfferListItem item) {
        model.removeOffer(item);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<OfferListItem> getList() {
        return model.getList();
    }

    String getTradeId(OfferListItem item) {
        return item.getOffer().getId();
    }

    String getAmount(OfferListItem item) {
        return (item != null) ? formatter.formatAmountWithMinAmount(item.getOffer()) : "";
    }

    String getPrice(OfferListItem item) {
        return (item != null) ? formatter.formatFiat(item.getOffer().getPrice()) : "";
    }

    String getVolume(OfferListItem item) {
        return (item != null) ? formatter.formatVolumeWithMinVolume(item.getOffer()) : "";
    }

    String getDirectionLabel(OfferListItem item) {
        return (item != null) ? formatter.formatDirection(model.getDirection(item.getOffer())) : "";
    }

    String getDate(OfferListItem item) {
        return formatter.formatDateTime(item.getOffer().getCreationDate());
    }

}

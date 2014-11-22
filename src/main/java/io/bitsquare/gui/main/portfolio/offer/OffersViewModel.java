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

package io.bitsquare.gui.main.portfolio.offer;

import io.bitsquare.gui.ActivatableWithDelegate;
import io.bitsquare.gui.ViewModel;
import io.bitsquare.gui.util.BSFormatter;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

class OffersViewModel extends ActivatableWithDelegate<OffersDataModel> implements ViewModel {

    private final BSFormatter formatter;


    @Inject
    public OffersViewModel(OffersDataModel model, BSFormatter formatter) {
        super(model);

        this.formatter = formatter;
    }


    void removeOffer(OfferListItem item) {
        delegate.removeOffer(item);
    }


    public ObservableList<OfferListItem> getList() {
        return delegate.getList();
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
        return (item != null) ? formatter.formatDirection(delegate.getDirection(item.getOffer())) : "";
    }

    String getDate(OfferListItem item) {
        return formatter.formatDateTime(item.getOffer().getCreationDate());
    }

}

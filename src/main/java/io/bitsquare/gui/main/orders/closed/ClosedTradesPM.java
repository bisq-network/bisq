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

package io.bitsquare.gui.main.orders.closed;

import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.BSFormatter;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ClosedTradesPM extends PresentationModel<ClosedTradesModel> {
    private static final Logger log = LoggerFactory.getLogger(ClosedTradesPM.class);

    private final BSFormatter formatter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ClosedTradesPM(ClosedTradesModel model, BSFormatter formatter) {
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
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<ClosedTradesListItem> getList() {
        return model.getList();
    }

    String getTradeId(ClosedTradesListItem item) {
        return item.getTrade().getId();
    }

    String getAmount(ClosedTradesListItem item) {
        return (item != null) ? formatter.formatCoinWithCode(item.getTrade().getTradeAmount()) : "";
    }

    String getPrice(ClosedTradesListItem item) {
        return (item != null) ? formatter.formatFiat(item.getTrade().getOffer().getPrice()) : "";
    }

    String getVolume(ClosedTradesListItem item) {
        return (item != null) ? formatter.formatFiatWithCode(item.getTrade().getTradeVolume()) : "";
    }

    String getDirectionLabel(ClosedTradesListItem item) {
        return (item != null) ? formatter.formatDirection(model.getDirection(item.getTrade().getOffer())) : "";
    }

    String getDate(ClosedTradesListItem item) {
        return formatter.formatDateTime(item.getTrade().getDate());
    }

}

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

package io.bitsquare.gui.main.portfolio.closedtrades;

import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.model.DataModel;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.closed.ClosedTradableManager;
import io.bitsquare.trade.offer.Offer;

import com.google.inject.Inject;

import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

class ClosedTradesDataModel implements Activatable, DataModel {

    private final ClosedTradableManager closedTradableManager;

    private final ObservableList<ClosedTradesListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<Tradable> tradesListChangeListener;

    @Inject
    public ClosedTradesDataModel(ClosedTradableManager closedTradableManager) {
        this.closedTradableManager = closedTradableManager;

        tradesListChangeListener = change -> applyList();
    }

    @Override
    public void activate() {
        applyList();
        closedTradableManager.getClosedTrades().addListener(tradesListChangeListener);
    }

    @Override
    public void deactivate() {
        closedTradableManager.getClosedTrades().removeListener(tradesListChangeListener);
    }

    public ObservableList<ClosedTradesListItem> getList() {
        return list;
    }

    public Offer.Direction getDirection(Offer offer) {
        return closedTradableManager.wasMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(closedTradableManager.getClosedTrades().stream().map(ClosedTradesListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTradable().getDate().compareTo(o1.getTradable().getDate()));
    }

}

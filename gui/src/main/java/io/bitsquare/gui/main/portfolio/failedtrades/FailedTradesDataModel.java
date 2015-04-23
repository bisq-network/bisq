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

package io.bitsquare.gui.main.portfolio.failedtrades;

import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.model.DataModel;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.failed.FailedTradesManager;
import io.bitsquare.trade.offer.Offer;

import com.google.inject.Inject;

import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

class FailedTradesDataModel implements Activatable, DataModel {

    private final FailedTradesManager failedTradesManager;

    private final ObservableList<FailedTradesListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<Trade> tradesListChangeListener;

    @Inject
    public FailedTradesDataModel(FailedTradesManager failedTradesManager) {
        this.failedTradesManager = failedTradesManager;

        tradesListChangeListener = change -> applyList();
    }

    @Override
    public void activate() {
        applyList();
        failedTradesManager.getFailedTrades().addListener(tradesListChangeListener);
    }

    @Override
    public void deactivate() {
        failedTradesManager.getFailedTrades().removeListener(tradesListChangeListener);
    }

    public ObservableList<FailedTradesListItem> getList() {
        return list;
    }

    public Offer.Direction getDirection(Offer offer) {
        return failedTradesManager.wasMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(failedTradesManager.getFailedTrades().stream().map(FailedTradesListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));
    }

}

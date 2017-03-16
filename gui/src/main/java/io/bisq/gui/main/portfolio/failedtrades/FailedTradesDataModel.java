/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.portfolio.failedtrades;

import com.google.inject.Inject;
import io.bisq.gui.common.model.ActivatableDataModel;
import io.bisq.messages.trade.offer.payload.OfferPayload;
import io.bisq.p2p.protocol.availability.Offer;
import io.bisq.trade.Trade;
import io.bisq.trade.failed.FailedTradesManager;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.stream.Collectors;

class FailedTradesDataModel extends ActivatableDataModel {

    private final FailedTradesManager failedTradesManager;

    private final ObservableList<FailedTradesListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<Trade> tradesListChangeListener;

    @Inject
    public FailedTradesDataModel(FailedTradesManager failedTradesManager) {
        this.failedTradesManager = failedTradesManager;

        tradesListChangeListener = change -> applyList();
    }

    @Override
    protected void activate() {
        applyList();
        failedTradesManager.getFailedTrades().addListener(tradesListChangeListener);
    }

    @Override
    protected void deactivate() {
        failedTradesManager.getFailedTrades().removeListener(tradesListChangeListener);
    }

    public ObservableList<FailedTradesListItem> getList() {
        return list;
    }

    public OfferPayload.Direction getDirection(Offer offer) {
        return failedTradesManager.wasMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(failedTradesManager.getFailedTrades().stream().map(FailedTradesListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));
    }

}

/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.portfolio.closedtrades;

import bisq.desktop.common.model.ActivatableDataModel;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.trade.Tradable;
import bisq.core.trade.closed.ClosedTradableManager;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.stream.Collectors;

class ClosedTradesDataModel extends ActivatableDataModel {

    final ClosedTradableManager closedTradableManager;
    private final ObservableList<ClosedTradableListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<Tradable> tradesListChangeListener;

    @Inject
    public ClosedTradesDataModel(ClosedTradableManager closedTradableManager) {
        this.closedTradableManager = closedTradableManager;

        tradesListChangeListener = change -> applyList();
    }

    @Override
    protected void activate() {
        applyList();
        closedTradableManager.getClosedTradables().addListener(tradesListChangeListener);
    }

    @Override
    protected void deactivate() {
        closedTradableManager.getClosedTradables().removeListener(tradesListChangeListener);
    }

    public ObservableList<ClosedTradableListItem> getList() {
        return list;
    }

    public OfferPayload.Direction getDirection(Offer offer) {
        return closedTradableManager.wasMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(closedTradableManager.getClosedTradables().stream().map(ClosedTradableListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getTradable().getDate().compareTo(o1.getTradable().getDate()));
    }

}

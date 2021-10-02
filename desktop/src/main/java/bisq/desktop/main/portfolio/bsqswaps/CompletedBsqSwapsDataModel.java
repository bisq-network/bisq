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

package bisq.desktop.main.portfolio.bsqswaps;

import bisq.desktop.common.model.ActivatableDataModel;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.bsqswap.BsqSwapTradeManager;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.stream.Collectors;

class CompletedBsqSwapsDataModel extends ActivatableDataModel {

    //    final ClosedTradableManager closedTradableManager;
    final BsqSwapTradeManager bsqSwapTradeManager;
    private final ObservableList<CompletedBsqSwapsListItem> list = FXCollections.observableArrayList();
    private final ListChangeListener<Tradable> tradesListChangeListener;

    @Inject
    public CompletedBsqSwapsDataModel(BsqSwapTradeManager bsqSwapTradeManager) {
        this.bsqSwapTradeManager = bsqSwapTradeManager;

        tradesListChangeListener = change -> applyList();
    }

    @Override
    protected void activate() {
        applyList();
        bsqSwapTradeManager.getObservableList().addListener(tradesListChangeListener);
    }

    @Override
    protected void deactivate() {
        bsqSwapTradeManager.getObservableList().removeListener(tradesListChangeListener);
    }

    public ObservableList<CompletedBsqSwapsListItem> getList() {
        return list;
    }

    public OfferDirection getDirection(Offer offer) {
        return bsqSwapTradeManager.wasMyOffer(offer) ? offer.getDirection() : offer.getMirroredDirection();
    }

    private void applyList() {
        list.clear();

        list.addAll(bsqSwapTradeManager.getObservableList().stream().map(CompletedBsqSwapsListItem::new).collect(Collectors.toList()));

        // we sort by date, earliest first
        list.sort((o1, o2) -> o2.getBsqSwapTrade().getDate().compareTo(o1.getBsqSwapTrade().getDate()));
    }

}

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

package io.bitsquare.gui.main.portfolio.closed;

import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;
import io.bitsquare.common.viewfx.model.Activatable;
import io.bitsquare.common.viewfx.model.DataModel;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

class ClosedTradesDataModel implements Activatable, DataModel {

    private final TradeManager tradeManager;
    private final User user;

    private final ObservableList<ClosedTradesListItem> list = FXCollections.observableArrayList();
    private final MapChangeListener<String, Trade> mapChangeListener;


    @Inject
    public ClosedTradesDataModel(TradeManager tradeManager, User user) {
        this.tradeManager = tradeManager;
        this.user = user;

        this.mapChangeListener = change -> {
            if (change.wasAdded())
                list.add(new ClosedTradesListItem(change.getValueAdded()));
            else if (change.wasRemoved())
                list.removeIf(e -> e.getTrade().getId().equals(change.getValueRemoved().getId()));
        };
    }

    @Override
    public void activate() {
        list.clear();
        tradeManager.getClosedTrades().values().stream()
                .forEach(e -> list.add(new ClosedTradesListItem(e)));
        tradeManager.getClosedTrades().addListener(mapChangeListener);

        // We sort by date, earliest first
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));
    }

    @Override
    public void deactivate() {
        tradeManager.getClosedTrades().removeListener(mapChangeListener);
    }

    public ObservableList<ClosedTradesListItem> getList() {
        return list;
    }

    public Direction getDirection(Offer offer) {
        return offer.getMessagePublicKey().equals(user.getMessagePubKey()) ?
                offer.getDirection() : offer.getMirroredDirection();
    }

}

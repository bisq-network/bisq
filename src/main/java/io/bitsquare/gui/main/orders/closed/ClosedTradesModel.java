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

import io.bitsquare.gui.UIModel;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ClosedTradesModel extends UIModel {
    private static final Logger log = LoggerFactory.getLogger(ClosedTradesModel.class);

    private final TradeManager tradeManager;
    private User user;

    private final ObservableList<ClosedTradesListItem> list = FXCollections.observableArrayList();
    private MapChangeListener<String, Trade> mapChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ClosedTradesModel(TradeManager tradeManager, User user) {
        this.tradeManager = tradeManager;
        this.user = user;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize() {
        mapChangeListener = change -> {
            if (change.wasAdded())
                list.add(new ClosedTradesListItem(change.getValueAdded()));
            else if (change.wasRemoved())
                list.removeIf(e -> e.getTrade().getId().equals(change.getValueRemoved().getId()));
        };

        super.initialize();
    }

    @Override
    public void activate() {
        super.activate();

        list.clear();
        tradeManager.getClosedTrades().values().stream()
                .forEach(e -> list.add(new ClosedTradesListItem(e)));
        tradeManager.getClosedTrades().addListener(mapChangeListener);

        // We sort by date, earliest first
        list.sort((o1, o2) -> o2.getTrade().getDate().compareTo(o1.getTrade().getDate()));
    }

    @Override
    public void deactivate() {
        super.deactivate();

        tradeManager.getClosedTrades().removeListener(mapChangeListener);
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
        return list;
    }

    public Direction getDirection(Offer offer) {
        return offer.getMessagePublicKey().equals(user.getMessagePublicKey()) ?
                offer.getDirection() : offer.getMirroredDirection();
    }

}

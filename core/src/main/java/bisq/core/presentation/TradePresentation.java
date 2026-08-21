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

package bisq.core.presentation;

import bisq.core.trade.TradeManager;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import lombok.Getter;

public class TradePresentation {
    @Getter
    private final StringProperty numPendingTrades = new SimpleStringProperty();
    @Getter
    private final BooleanProperty showPendingTradesNotification = new SimpleBooleanProperty();

    @Inject
    public TradePresentation(TradeManager tradeManager) {
        tradeManager.getNumPendingTrades().addListener((observable, oldValue, newValue) -> {
            long numPendingTrades = (long) newValue;
            if (numPendingTrades > 0)
                this.numPendingTrades.set(String.valueOf(numPendingTrades));

            showPendingTradesNotification.set(numPendingTrades > 0);
        });
    }
}

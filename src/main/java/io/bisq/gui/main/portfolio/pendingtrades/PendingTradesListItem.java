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

package io.bisq.gui.main.portfolio.pendingtrades;

import io.bisq.common.monetary.Price;
import io.bisq.common.monetary.Volume;
import io.bisq.core.trade.Trade;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.bitcoinj.core.Coin;

/**
 * We could remove that wrapper if it is not needed for additional UI only fields.
 */
public class PendingTradesListItem {

    private final Trade trade;

    public PendingTradesListItem(Trade trade) {
        this.trade = trade;
    }

    public Trade getTrade() {
        return trade;
    }

    public ReadOnlyObjectProperty<Coin> tradeAmountProperty() {
        return trade.tradeAmountProperty();
    }

    public ReadOnlyObjectProperty<Volume> tradeVolumeProperty() {
        return trade.tradeVolumeProperty();
    }

    public Price getPrice() {
        return trade.getTradePrice();
    }

}

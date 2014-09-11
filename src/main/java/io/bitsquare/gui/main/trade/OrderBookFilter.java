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

package io.bitsquare.gui.main.trade;

import io.bitsquare.trade.Direction;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.Fiat;

import javafx.beans.property.SimpleBooleanProperty;

//TODO move to OrderBookModel when converted to new UI structure
public class OrderBookFilter {
    // TODO use ObjectProperty<Direction> instead
    private final SimpleBooleanProperty directionChangedProperty = new SimpleBooleanProperty();

    private Fiat price;
    private Coin amount;
    private Direction direction;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getAmount() {
        return amount;
    }

    public void setAmount(Coin amount) {
        this.amount = amount;
    }

    public Direction getDirection() {
        return direction;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setDirection(Direction direction) {
        this.direction = direction;
        directionChangedProperty.set(!directionChangedProperty.get());
    }

    public Fiat getPrice() {
        return price;
    }

    public void setPrice(Fiat price) {
        this.price = price;
    }

    public SimpleBooleanProperty getDirectionChangedProperty() {
        return directionChangedProperty;
    }
}

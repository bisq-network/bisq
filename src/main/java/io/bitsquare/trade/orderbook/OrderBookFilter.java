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

package io.bitsquare.trade.orderbook;

import com.google.bitcoin.core.Coin;
import io.bitsquare.trade.Direction;
import javafx.beans.property.SimpleBooleanProperty;

public class OrderBookFilter {
    private final SimpleBooleanProperty directionChangedProperty = new SimpleBooleanProperty();

    private double price;
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }


    public SimpleBooleanProperty getDirectionChangedProperty() {
        return directionChangedProperty;
    }


}

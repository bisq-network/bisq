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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Holds shared data between the different trade UIs
 */
public class OrderBookInfo {

    private final ObjectProperty<Direction> direction = new SimpleObjectProperty<>();
    private Coin amount;
    private Fiat price;
    private Fiat volume;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OrderBookInfo() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setAmount(Coin amount) {
        this.amount = amount;
    }

    public void setPrice(Fiat price) {
        this.price = price;
    }

    public void setVolume(Fiat volume) {
        this.volume = volume;
    }

    public void setDirection(Direction direction) {
        this.direction.set(direction);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getAmount() {
        return amount;
    }

    public Fiat getPrice() {
        return price;
    }

    public Fiat getVolume() {
        return volume;
    }

    public Direction getDirection() {
        return direction.get();
    }

    public ObjectProperty<Direction> directionProperty() {
        return direction;
    }
}

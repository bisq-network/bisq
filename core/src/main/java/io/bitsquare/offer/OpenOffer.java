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

package io.bitsquare.offer;

import java.io.Serializable;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Offerer has his own offers wrapped in that OpenOffer object. Taker never has an OpenOffer object.
public class OpenOffer implements Serializable {
    private static final long serialVersionUID = -7523483764145982933L;

    private static final Logger log = LoggerFactory.getLogger(OpenOffer.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static enum State {
        OPEN,
        OFFER_ACCEPTED
    }

    private final Offer offer;
    private State state;

    transient private ObjectProperty<State> _state;

    public OpenOffer(Offer offer) {
        this.offer = offer;

        state = State.OPEN;
    }

    public Offer getOffer() {
        return offer;
    }

    public String getId() {
        return offer.getId();
    }

    public void setState(State state) {
        this.state = state;
        stateProperty().set(state);
    }

    public State getState() {
        return state;
    }

    public ObjectProperty<State> stateProperty() {
        if (_state == null)
            _state = new SimpleObjectProperty<>(state);

        return _state;
    }
}

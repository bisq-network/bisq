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

package io.bitsquare.trade.offer;

import io.bitsquare.app.Version;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.Tradable;
import io.bitsquare.trade.TradableList;

import org.bitcoinj.utils.Threading;

import java.io.Serializable;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenOffer implements Tradable, Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private static final Logger log = LoggerFactory.getLogger(OpenOffer.class);

    transient private static final long TIMEOUT = 5000;

    public enum State {
        AVAILABLE,
        RESERVED,
        CLOSED,
        CANCELED
    }

    private final Offer offer;
    private State state = State.AVAILABLE;

    transient private Timer timeoutTimer;
    transient private Storage<TradableList<OpenOffer>> storage;

    public OpenOffer(Offer offer, Storage<TradableList<OpenOffer>> storage) {
        this.offer = offer;
        this.storage = storage;
    }

    public Date getDate() {
        return offer.getCreationDate();
    }

    @Override
    public String getId() {
        return offer.getId();
    }

    public Offer getOffer() {
        return offer;
    }

    public void setStorage(Storage<TradableList<OpenOffer>> storage) {
        this.storage = storage;
    }

    public void setState(State state) {
        log.trace("setState" + state);
        this.state = state;
        storage.queueUpForSave();

        // We keep it reserved for a limited time, if trade preparation fails we revert to available state
        if (this.state == State.RESERVED)
            startTimeout();
        else
            stopTimeout();

    }

    public State getState() {
        return state;
    }


    private void startTimeout() {
        log.trace("startTimeout");
        stopTimeout();

        timeoutTimer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Threading.USER_THREAD.execute(() -> {
                    log.debug("Timeout reached");
                    if (state == State.RESERVED)
                        setState(State.AVAILABLE);
                });
            }
        };

        timeoutTimer.schedule(task, TIMEOUT);
    }

    protected void stopTimeout() {
        log.trace("stopTimeout");
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
}

   
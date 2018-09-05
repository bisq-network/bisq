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

package bisq.core.offer;

import bisq.core.trade.Tradable;
import bisq.core.trade.TradableList;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.proto.ProtoUtil;
import bisq.common.storage.Storage;

import io.bisq.generated.protobuffer.PB;

import java.util.Date;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@Slf4j
public final class OpenOffer implements Tradable {
    // Timeout for offer reservation during takeoffer process. If deposit tx is not completed in that time we reset the offer to AVAILABLE state.
    private static final long TIMEOUT = 60;
    transient private Timer timeoutTimer;

    public enum State {
        AVAILABLE,
        RESERVED,
        CLOSED,
        CANCELED,
        DEACTIVATED
    }

    @Getter
    private final Offer offer;
    @Getter
    private State state;

    transient private Storage<TradableList<OpenOffer>> storage;

    public OpenOffer(Offer offer, Storage<TradableList<OpenOffer>> storage) {
        this.offer = offer;
        this.storage = storage;
        state = State.AVAILABLE;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private OpenOffer(Offer offer, State state) {
        this.offer = offer;
        this.state = state;

        if (this.state == State.RESERVED)
            setState(State.AVAILABLE);
    }

    @Override
    public PB.Tradable toProtoMessage() {
        return PB.Tradable.newBuilder().setOpenOffer(PB.OpenOffer.newBuilder()
                .setOffer(offer.toProtoMessage())
                .setState(PB.OpenOffer.State.valueOf(state.name())))
                .build();
    }

    public static Tradable fromProto(PB.OpenOffer proto) {
        return new OpenOffer(Offer.fromProto(proto.getOffer()),
                ProtoUtil.enumFromProto(OpenOffer.State.class, proto.getState().name()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Date getDate() {
        return offer.getDate();
    }

    @Override
    public String getId() {
        return offer.getId();
    }

    @Override
    public String getShortId() {
        return offer.getShortId();
    }

    public void setStorage(Storage<TradableList<OpenOffer>> storage) {
        this.storage = storage;
    }

    public void setState(State state) {
        log.trace("setState" + state);
        boolean changed = this.state != state;
        this.state = state;
        if (changed)
            storage.queueUpForSave();

        // We keep it reserved for a limited time, if trade preparation fails we revert to available state
        if (this.state == State.RESERVED)
            startTimeout();
        else
            stopTimeout();
    }

    public boolean isDeactivated() {
        return state == State.DEACTIVATED;
    }

    private void startTimeout() {
        stopTimeout();

        timeoutTimer = UserThread.runAfter(() -> {
            log.debug("Timeout for resettin State.RESERVED reached");
            if (state == State.RESERVED)
                setState(State.AVAILABLE);
        }, TIMEOUT);
    }

    private void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    @Override
    public String toString() {
        return "OpenOffer{" +
                "\n\toffer=" + offer +
                "\n\tstate=" + state +
                '}';
    }
}


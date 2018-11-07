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

package bisq.core.dao.state.period;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import lombok.Value;

import javax.annotation.concurrent.Immutable;

/**
 * Encapsulated the phase enum with the duration.
 * As the duration can change by voting we don't want to put the duration property in the enum but use that wrapper.
 */
@Immutable
@Value
public class DaoPhase implements PersistablePayload {

    /**
     * Enum for phase of a cycle.
     *
     * We don't want to use a enum with the duration as field because the duration can change by voting and enums
     * should be considered immutable.
     */
    public enum Phase {
        UNDEFINED,
        PROPOSAL,
        BREAK1,
        BLIND_VOTE,
        BREAK2,
        VOTE_REVEAL,
        BREAK3,
        RESULT
    }


    private final Phase phase;
    private final int duration;

    public DaoPhase(Phase phase, int duration) {
        this.phase = phase;
        this.duration = duration;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.DaoPhase toProtoMessage() {
        return PB.DaoPhase.newBuilder()
                .setPhaseOrdinal(phase.ordinal())
                .setDuration(duration)
                .build();
    }

    public static DaoPhase fromProto(PB.DaoPhase proto) {
        return new DaoPhase(Phase.values()[proto.getPhaseOrdinal()], proto.getDuration());
    }


    @Override
    public String toString() {
        return "DaoPhase{" +
                "\n     phase=" + phase +
                ",\n     duration=" + duration +
                "\n}";
    }
}

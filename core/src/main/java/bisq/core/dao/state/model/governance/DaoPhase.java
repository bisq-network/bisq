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

package bisq.core.dao.state.model.governance;

import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.persistable.PersistablePayload;

import java.util.Objects;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * Encapsulated the phase enum with the duration.
 * As the duration can change by voting we don't want to put the duration property in the enum but use that wrapper.
 */
@Immutable
@Value
@Slf4j
public class DaoPhase implements PersistablePayload, ImmutableDaoStateModel {

    /**
     * Enum for phase of a cycle.
     *
     * We don't want to use an enum with the duration as field because the duration can change by voting and enums
     * should be considered immutable.
     */
    @Immutable
    public enum Phase implements ImmutableDaoStateModel {
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
    public protobuf.DaoPhase toProtoMessage() {
        return protobuf.DaoPhase.newBuilder()
                .setPhaseOrdinal(phase.ordinal())
                .setDuration(duration)
                .build();
    }

    public static DaoPhase fromProto(protobuf.DaoPhase proto) {
        int ordinal = proto.getPhaseOrdinal();
        if (ordinal >= Phase.values().length) {
            log.warn("We tried to access a ordinal outside of the DaoPhase.Phase enum bounds and set it to " +
                    "UNDEFINED. ordinal={}", ordinal);
            return new DaoPhase(Phase.UNDEFINED, 0);
        }

        return new DaoPhase(Phase.values()[ordinal], proto.getDuration());
    }


    @Override
    public String toString() {
        return "DaoPhase{" +
                "\n     phase=" + phase +
                ",\n     duration=" + duration +
                "\n}";
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DaoPhase)) return false;
        if (!super.equals(o)) return false;
        DaoPhase daoPhase = (DaoPhase) o;
        return duration == daoPhase.duration &&
                phase.name().equals(daoPhase.phase.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), phase.name(), duration);
    }
}

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

import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Value;

import javax.annotation.concurrent.Immutable;

/**
 * Cycle represents the monthly period for proposals and voting.
 * It consists of a ordered list of phases represented by the phaseWrappers.
 */
@Immutable
@Value
public class Cycle implements PersistablePayload, ImmutableDaoStateModel {
    // List is ordered according to the Phase enum.
    private final ImmutableList<DaoPhase> daoPhaseList;
    private final int heightOfFirstBlock;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Cycle(int heightOfFirstBlock, ImmutableList<DaoPhase> daoPhaseList) {
        this.heightOfFirstBlock = heightOfFirstBlock;
        this.daoPhaseList = daoPhaseList;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.Cycle toProtoMessage() {
        return protobuf.Cycle.newBuilder()
                .setHeightOfFirstLock(heightOfFirstBlock)
                .addAllDaoPhase(daoPhaseList.stream()
                        .map(DaoPhase::toProtoMessage)
                        .collect(Collectors.toList()))
                .build();
    }

    public static Cycle fromProto(protobuf.Cycle proto) {
        final ImmutableList<DaoPhase> daoPhaseList = ImmutableList.copyOf(proto.getDaoPhaseList().stream()
                .map(DaoPhase::fromProto)
                .collect(Collectors.toList()));
        return new Cycle(proto.getHeightOfFirstLock(), daoPhaseList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int getHeightOfLastBlock() {
        return heightOfFirstBlock + getDuration() - 1;
    }

    public boolean isInPhase(int height, DaoPhase.Phase phase) {
        return height >= getFirstBlockOfPhase(phase) && height <= getLastBlockOfPhase(phase);
    }

    public boolean isInCycle(int height) {
        return height >= getHeightOfFirstBlock() && height <= getHeightOfLastBlock();
    }

    public int getFirstBlockOfPhase(DaoPhase.Phase phase) {
        return heightOfFirstBlock + daoPhaseList.stream()
                .filter(item -> item.getPhase().ordinal() < phase.ordinal())
                .mapToInt(DaoPhase::getDuration).sum();
    }

    public int getLastBlockOfPhase(DaoPhase.Phase phase) {
        return getFirstBlockOfPhase(phase) + getDuration(phase) - 1;
    }

    public int getDurationOfPhase(DaoPhase.Phase phase) {
        return daoPhaseList.stream()
                .filter(item -> item.getPhase() == phase)
                .mapToInt(DaoPhase::getDuration)
                .sum();
    }

    public Optional<DaoPhase.Phase> getPhaseForHeight(int height) {
        return daoPhaseList.stream()
                .filter(item -> isInPhase(height, item.getPhase()))
                .map(DaoPhase::getPhase)
                .findAny();
    }

    private Optional<DaoPhase> getPhaseWrapper(DaoPhase.Phase phase) {
        return daoPhaseList.stream().filter(item -> item.getPhase() == phase).findAny();
    }

    private int getDuration(DaoPhase.Phase phase) {
        return getPhaseWrapper(phase).map(DaoPhase::getDuration).orElse(0);
    }

    public int getDuration() {
        return daoPhaseList.stream().mapToInt(DaoPhase::getDuration).sum();
    }

    @Override
    public String toString() {
        return "Cycle{" +
                "\n     daoPhaseList=" + daoPhaseList +
                ",\n     heightOfFirstBlock=" + heightOfFirstBlock +
                "\n}";
    }
}

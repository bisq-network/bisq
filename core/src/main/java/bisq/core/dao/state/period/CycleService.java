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

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.governance.Param;

import com.google.inject.Inject;

import javax.inject.Named;

import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CycleService implements BsqStateListener, DaoSetupService {
    private final BsqStateService bsqStateService;
    private final int genesisBlockHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CycleService(BsqStateService bsqStateService,
                        @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this.bsqStateService = bsqStateService;
        this.genesisBlockHeight = genesisBlockHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        bsqStateService.addBsqStateListener(this);
    }

    @Override
    public void start() {
        bsqStateService.getCycles().add(getFirstCycle());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
        if (blockHeight != genesisBlockHeight)
            maybeCreateNewCycle(blockHeight, bsqStateService.getCycles())
                    .ifPresent(bsqStateService.getCycles()::add);
    }

    @Override
    public void onParseTxsComplete(Block block) {
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Optional<Cycle> maybeCreateNewCycle(int blockHeight, LinkedList<Cycle> cycles) {
        // We want to set the correct phase and cycle before we start parsing a new block.
        // For Genesis block we did it already in the start method.
        // We copy over the phases from the current block as we get the phase only set in
        // applyParamToPhasesInCycle if there was a changeEvent.
        // The isFirstBlockInCycle methods returns from the previous cycle the first block as we have not
        // applied the new cycle yet. But the first block of the old cycle will always be the same as the
        // first block of the new cycle.
        Cycle cycle = null;
        if (blockHeight != genesisBlockHeight && isFirstBlockAfterPreviousCycle(blockHeight, cycles)) {
            // We have the not update bsqStateService.getCurrentCycle() so we grab here the previousCycle
            final Cycle previousCycle = cycles.getLast();
            // We create the new cycle as clone of the previous cycle and only if there have been change events we use
            // the new values from the change event.
            cycle = createNewCycle(blockHeight, previousCycle);
        }
        return Optional.ofNullable(cycle);
    }


    private Cycle getFirstCycle() {
        // We want to have the initial data set up before the genesis tx gets parsed so we do it here in the constructor
        // as onAllServicesInitialized might get called after the parser has started.
        // We add the default values from the Param enum to our StateChangeEvent list.
        List<DaoPhase> daoPhasesWithDefaultDuration = Arrays.stream(DaoPhase.Phase.values())
                .map(this::getPhaseWithDefaultDuration)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        return new Cycle(genesisBlockHeight, ImmutableList.copyOf(daoPhasesWithDefaultDuration));
    }

    public int getCycleIndex(Cycle cycle) {
        return (cycle.getHeightOfFirstBlock() - genesisBlockHeight) / cycle.getDuration();
    }

    public boolean isTxInCycle(Cycle cycle, String txId) {
        return bsqStateService.getTx(txId).filter(tx -> isBlockHeightInCycle(tx.getBlockHeight(), cycle)).isPresent();
    }

    private boolean isBlockHeightInCycle(int blockHeight, Cycle cycle) {
        return blockHeight >= cycle.getHeightOfFirstBlock() &&
                blockHeight <= cycle.getHeightOfLastBlock();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Cycle createNewCycle(int blockHeight, Cycle previousCycle) {
        List<DaoPhase> daoPhaseList = previousCycle.getDaoPhaseList().stream()
                .map(daoPhase -> {
                    DaoPhase.Phase phase = daoPhase.getPhase();
                    try {
                        Param param = Param.valueOf("PHASE_" + phase.name());
                        long value = bsqStateService.getParamValue(param, blockHeight);
                        return new DaoPhase(phase, (int) value);
                    } catch (Throwable ignore) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new Cycle(blockHeight, ImmutableList.copyOf(daoPhaseList));
    }

    private boolean isFirstBlockAfterPreviousCycle(int height, LinkedList<Cycle> cycles) {
        final int previousBlockHeight = height - 1;
        final Optional<Cycle> previousCycle = getCycle(previousBlockHeight, cycles);
        return previousCycle
                .filter(cycle -> cycle.getHeightOfLastBlock() + 1 == height)
                .isPresent();
    }

    private Optional<DaoPhase> getPhaseWithDefaultDuration(DaoPhase.Phase phase) {
        return Arrays.stream(Param.values())
                .filter(param -> isParamMatchingPhase(param, phase))
                .map(param -> new DaoPhase(phase, (int) param.getDefaultValue()))
                .findAny(); // We will always have a default value defined
    }

    private boolean isParamMatchingPhase(Param param, DaoPhase.Phase phase) {
        return param.name().contains("PHASE_") && param.name().replace("PHASE_", "").equals(phase.name());
    }

    private Optional<Cycle> getCycle(int height, LinkedList<Cycle> cycles) {
        return cycles.stream()
                .filter(cycle -> cycle.getHeightOfFirstBlock() <= height)
                .filter(cycle -> cycle.getHeightOfLastBlock() >= height)
                .findAny();
    }
}

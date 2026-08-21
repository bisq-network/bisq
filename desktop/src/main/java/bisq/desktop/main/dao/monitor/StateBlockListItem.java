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

package bisq.desktop.main.dao.monitor;

import bisq.core.dao.monitoring.model.StateBlock;
import bisq.core.dao.monitoring.model.StateHash;
import bisq.core.locale.Res;

import bisq.common.util.Utilities;

import com.google.common.base.Suppliers;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@EqualsAndHashCode
public abstract class StateBlockListItem<StH extends StateHash, StB extends StateBlock<StH>> {
    protected final StateBlock<StH> stateBlock;
    private final Supplier<String> height;
    private final String hash;
    private final String numNetworkMessages;
    private final String numMisMatches;
    private final boolean isInSync;

    public String getHeight() {
        return height.get();
    }

    protected StateBlockListItem(StB stateBlock, int cycleIndex) {
        this(stateBlock, () -> cycleIndex);
    }

    protected StateBlockListItem(StB stateBlock, IntSupplier cycleIndexSupplier) {
        this.stateBlock = stateBlock;
        height = Suppliers.memoize(() ->
                Res.get("dao.monitor.table.cycleBlockHeight", cycleIndexSupplier.getAsInt() + 1,
                        String.valueOf(stateBlock.getHeight())))::get;
        hash = Utilities.bytesAsHexString(stateBlock.getHash());
        numNetworkMessages = String.valueOf(stateBlock.getPeersMap().size());
        int size = stateBlock.getInConflictMap().size();
        numMisMatches = String.valueOf(size);
        isInSync = size == 0;
    }
}

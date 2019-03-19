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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@EqualsAndHashCode
public abstract class StateBlockListItem<StH extends StateHash, StB extends StateBlock<StH>> {
    protected final StateBlock<StH> stateBlock;
    protected final String height;
    protected final String hash;
    protected final String prevHash;
    protected final String numNetworkMessages;
    protected final String numMisMatches;
    protected final boolean isInSync;

    protected StateBlockListItem(StB stateBlock, int cycleIndex) {
        this.stateBlock = stateBlock;
        height = Res.get("dao.monitor.table.cycleBlockHeight", cycleIndex + 1, String.valueOf(stateBlock.getHeight()));
        hash = Utilities.bytesAsHexString(stateBlock.getHash());
        prevHash = stateBlock.getPrevHash().length > 0 ? Utilities.bytesAsHexString(stateBlock.getPrevHash()) : "-";
        numNetworkMessages = String.valueOf(stateBlock.getPeersMap().size());
        int size = stateBlock.getInConflictMap().size();
        numMisMatches = String.valueOf(size);
        isInSync = size == 0;
    }
}

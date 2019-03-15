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

import bisq.core.dao.monitoring.model.StateHash;
import bisq.core.locale.Res;

import bisq.common.util.Utilities;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@EqualsAndHashCode
public abstract class StateInConflictListItem<T extends StateHash> {
    private final String peerAddress;
    private final String height;
    private final String hash;
    private final String prevHash;
    private final T stateHash;

    protected StateInConflictListItem(String peerAddress, T stateHash, int cycleIndex) {
        this.stateHash = stateHash;
        this.peerAddress = peerAddress;
        height = Res.get("dao.monitor.table.cycleBlockHeight", cycleIndex + 1, String.valueOf(stateHash.getHeight()));
        hash = Utilities.bytesAsHexString(stateHash.getHash());
        prevHash = stateHash.getPrevHash().length > 0 ?
                Utilities.bytesAsHexString(stateHash.getPrevHash()) : "-";
    }
}

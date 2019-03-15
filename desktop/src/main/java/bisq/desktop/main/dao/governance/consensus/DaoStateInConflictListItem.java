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

package bisq.desktop.main.dao.governance.consensus;

import bisq.core.dao.monitoring.model.DaoStateHash;

import bisq.common.util.Utilities;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
class DaoStateInConflictListItem {
    private final String peerAddress;
    private final String height;
    private final String hash;
    private final String prevHash;
    private final DaoStateHash daoStateHash;

    DaoStateInConflictListItem(String peerAddress, DaoStateHash daoStateHash) {
        this.daoStateHash = daoStateHash;
        this.peerAddress = peerAddress;
        height = String.valueOf(daoStateHash.getHeight());
        hash = Utilities.bytesAsHexString(daoStateHash.getHash());
        prevHash = daoStateHash.getPrevHash().length > 0 ?
                Utilities.bytesAsHexString(daoStateHash.getPrevHash()) : "-";
    }
}

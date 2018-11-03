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

package bisq.core.dao.governance.bond.lockup;

import bisq.core.locale.Res;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;

public enum LockupType {
    BONDED_ROLE((byte) 0x01),
    REPUTATION((byte) 0x02);

    @Getter
    private byte type;

    LockupType(byte type) {
        this.type = type;
    }

    public String getDisplayString() {
        return Res.get("dao.bond.lockupType." + name());
    }

    public static Optional<LockupType> getLockupType(byte type) {
        return Arrays.stream(LockupType.values())
                .filter(lockupType -> lockupType.type == type)
                .map(Optional::of)
                .findAny()
                .orElse(Optional.empty());
    }
}

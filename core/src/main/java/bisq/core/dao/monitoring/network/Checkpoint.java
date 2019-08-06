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

package bisq.core.dao.monitoring.network;

import bisq.common.util.Utilities;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Checkpoint {
    final int height;
    final byte[] hash;
    @Setter
    boolean passed;

    public Checkpoint(int height, byte[] hash) {
        this.height = height;
        this.hash = hash;
    }

    @Override
    public String toString() {
        return "Checkpoint {" +
                "\n     height=" + height +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }

}

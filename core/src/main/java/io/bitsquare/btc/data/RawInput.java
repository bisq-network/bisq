/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc.data;

import io.bitsquare.app.Version;

import java.io.Serializable;
import java.util.Arrays;

public class RawInput implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final long index;
    public final byte[] parentTransaction;
    public final long value;

    public RawInput(long index, byte[] parentTransaction, long value) {
        this.index = index;
        this.parentTransaction = parentTransaction;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RawInput)) return false;

        RawInput rawInput = (RawInput) o;

        if (index != rawInput.index) return false;
        if (value != rawInput.value) return false;
        return Arrays.equals(parentTransaction, rawInput.parentTransaction);

    }

    @Override
    public int hashCode() {
        int result = (int) (index ^ (index >>> 32));
        result = 31 * result + (parentTransaction != null ? Arrays.hashCode(parentTransaction) : 0);
        result = 31 * result + (int) (value ^ (value >>> 32));
        return result;
    }
}

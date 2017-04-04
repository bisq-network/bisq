/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.blockchain;

import lombok.Value;
import org.bouncycastle.util.encoders.Hex;

import java.io.Serializable;
import java.util.List;

@Value
public class TxOutput implements Serializable {
    private final int index;
    private final long value;
    private final List<String> addresses;
    private final byte[] scriptProgramBytes;
    private final String txId;

    @Override
    public String toString() {
        return "TxOutput{" +
                "\n        txId=" + txId +
                ",\n        index=" + index +
                ",\n        value=" + value +
                ",\n        addresses=" + addresses +
                ",\n        scriptProgramBytes=" + Hex.toHexString(scriptProgramBytes) +
                "\n" +
                "     }";
    }
}

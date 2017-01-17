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

package io.bitsquare.dao.blockchain;

import org.bitcoinj.core.Coin;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SquTxOutput {
    private static final Logger log = LoggerFactory.getLogger(SquTxOutput.class);

    public final int index;
    public final Coin value;
    public final List<String> addresses;
    public final Script script;

    public SquTxOutput(int index, Coin value, List<String> addresses, Script script) {
        this.index = index;
        this.value = value;
        this.addresses = addresses;
        this.script = script;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SquTxOutput that = (SquTxOutput) o;

        if (index != that.index) return false;
        if (value != null ? !value.equals(that.value) : that.value != null) return false;
        if (addresses != null ? !addresses.equals(that.addresses) : that.addresses != null) return false;
        return !(script != null ? !script.equals(that.script) : that.script != null);

    }

    @Override
    public int hashCode() {
        int result = index;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (addresses != null ? addresses.hashCode() : 0);
        result = 31 * result + (script != null ? script.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SquTxOutput{" +
                "index=" + index +
                ", value=" + value +
                ", addresses=" + addresses +
                ", script=" + script +
                '}';
    }
}

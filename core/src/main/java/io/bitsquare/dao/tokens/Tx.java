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

package io.bitsquare.dao.tokens;

import java.util.ArrayList;
import java.util.List;

public class Tx {
    public Tx(String id) {
        this.id = id;
    }

    public Tx(String id, List<TxInput> inputs, List<TxOutput> outputs) {
        this.id = id;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public String id;
    public List<TxInput> inputs = new ArrayList<>();
    public List<TxOutput> outputs = new ArrayList<>();

    public void addOutput(TxOutput output) {
        output.tx = this;
        output.index = outputs.size();
        outputs.add(output);
    }

    public void addInput(TxInput input) {
        input.tx = this;
        input.index = inputs.size();

        // TODO our mocks have null values, might be not null in production
        if (input.output != null) {
            input.output.isSpent = true;
            input.output.inputOfSpendingTx = input;
            input.value = input.output.value;
        }
        inputs.add(input);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tx tx = (Tx) o;

        if (id != null ? !id.equals(tx.id) : tx.id != null) return false;
        if (outputs != null ? !outputs.equals(tx.outputs) : tx.outputs != null) return false;
        return !(inputs != null ? !inputs.equals(tx.inputs) : tx.inputs != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (outputs != null ? outputs.hashCode() : 0);
        result = 31 * result + (inputs != null ? inputs.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Tx{" +
                "id='" + id + '\'' +
                ", inputs=" + inputs +
                ", outputs=" + outputs +
                '}';
    }
}

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BsqTransaction {
    private static final Logger log = LoggerFactory.getLogger(BsqTransaction.class);

    public final String txId;
    public final List<BsqTxInput> inputs;
    public final List<BsqTxOutput> outputs;

    public BsqTransaction(String txId, List<BsqTxInput> inputs, List<BsqTxOutput> outputs) {
        this.txId = txId;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BsqTransaction that = (BsqTransaction) o;

        if (txId != null ? !txId.equals(that.txId) : that.txId != null) return false;
        if (inputs != null ? !inputs.equals(that.inputs) : that.inputs != null) return false;
        return !(outputs != null ? !outputs.equals(that.outputs) : that.outputs != null);

    }

    @Override
    public int hashCode() {
        int result = txId != null ? txId.hashCode() : 0;
        result = 31 * result + (inputs != null ? inputs.hashCode() : 0);
        result = 31 * result + (outputs != null ? outputs.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BsqTransaction{" +
                "txId='" + txId + '\'' +
                ", inputs=" + inputs +
                ", outputs=" + outputs +
                '}';
    }
}

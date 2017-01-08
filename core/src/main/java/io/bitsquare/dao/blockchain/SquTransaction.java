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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SquTransaction {
    private static final Logger log = LoggerFactory.getLogger(SquTransaction.class);

    public final String txId;
    public final List<SquTxInput> inputs;
    public final List<SquTxOutput> outputs;

    public SquTransaction(String txId, List<SquTxInput> inputs, List<SquTxOutput> outputs) {
        this.txId = txId;
        this.inputs = inputs;
        this.outputs = outputs;
    }
}

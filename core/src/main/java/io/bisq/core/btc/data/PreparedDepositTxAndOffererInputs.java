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

package io.bisq.core.btc.data;

import io.bisq.protobuffer.payload.btc.RawTransactionInput;

import java.util.ArrayList;

public class PreparedDepositTxAndOffererInputs {
    public final ArrayList<RawTransactionInput> rawOffererInputs;
    public final byte[] depositTransaction;

    public PreparedDepositTxAndOffererInputs(ArrayList<RawTransactionInput> rawOffererInputs, byte[] depositTransaction) {
        this.rawOffererInputs = rawOffererInputs;
        this.depositTransaction = depositTransaction;
    }
}

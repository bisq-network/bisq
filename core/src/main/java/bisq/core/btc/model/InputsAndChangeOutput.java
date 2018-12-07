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

package bisq.core.btc.model;

import java.util.List;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

public class InputsAndChangeOutput {
    public final List<RawTransactionInput> rawTransactionInputs;

    // Is set to 0L in case we don't have an output
    public final long changeOutputValue;
    @Nullable
    public final String changeOutputAddress;

    public InputsAndChangeOutput(List<RawTransactionInput> rawTransactionInputs, long changeOutputValue, @Nullable String changeOutputAddress) {
        checkArgument(!rawTransactionInputs.isEmpty(), "rawInputs.isEmpty()");

        this.rawTransactionInputs = rawTransactionInputs;
        this.changeOutputValue = changeOutputValue;
        this.changeOutputAddress = changeOutputAddress;
    }
}

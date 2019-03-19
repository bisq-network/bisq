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

package bisq.core.dao.state.model.blockchain;

import bisq.core.dao.state.model.ImmutableDaoStateModel;

import lombok.Value;

import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.Immutable;

/**
 * Convenience object for identifying a TxOutput.
 * Used as key in maps in the daoState.
 */
@Immutable
@Value
public final class TxOutputKey implements ImmutableDaoStateModel, Comparable {
    private final String txId;
    private final int index;

    public TxOutputKey(String txId, int index) {
        this.txId = txId;
        this.index = index;
    }

    @Override
    public String toString() {
        return txId + ":" + index;
    }

    public static TxOutputKey getKeyFromString(String keyAsString) {
        final String[] tokens = keyAsString.split(":");
        return new TxOutputKey(tokens[0], Integer.valueOf(tokens[1]));
    }

    @Override
    public int compareTo(@NotNull Object o) {
        return toString().compareTo(o.toString());
    }
}

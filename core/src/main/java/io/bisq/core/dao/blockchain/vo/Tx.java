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

package io.bisq.core.dao.blockchain.vo;

import com.google.common.collect.ImmutableList;
import lombok.Value;

import javax.annotation.concurrent.Immutable;
import java.io.Serializable;
import java.util.Optional;

@Value
@Immutable
public class Tx implements Serializable {
    private final String id;
    private final String blockHash;
    private final ImmutableList<TxInput> inputs;
    private final ImmutableList<TxOutput> outputs;
    private final boolean isIssuanceTx;

    public Optional<TxOutput> getTxOutput(int index) {
        return outputs.size() > index ? Optional.of(outputs.get(index)) : Optional.<TxOutput>empty();
    }

    @Override
    public String toString() {
        return "Tx{" +
                "\nid='" + id + '\'' +
                ",\nblockHash=" + blockHash +
                ",\ninputs=" + inputs +
                ",\noutputs=" + outputs +
                ",\nisIssuanceTx=" + isIssuanceTx +
                "}\n";
    }
}

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

import io.bisq.common.app.Version;
import io.bisq.common.persistence.Persistable;
import lombok.Value;

import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.Optional;

@Value
@Immutable
public class Tx implements Persistable {
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private final String id;
    private final int blockHeight;
    private final String blockHash;
    private final List<TxInput> inputs;
    private final List<TxOutput> outputs;
    private final boolean isGenesisTx;
    private final String txVersion = Version.BSQ_TX_VERSION;

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
                ",\nisGenesisTx=" + isGenesisTx +
                ",\ntxVersion=" + txVersion +
                "}\n";
    }
}

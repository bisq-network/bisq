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

package io.bisq.core.dao.blockchain.json;

import io.bisq.common.app.Version;
import lombok.Value;

import java.util.List;

@Value
public class JsonTx {
    private final String txVersion = Version.BSQ_TX_VERSION;
    private final String id;
    private final int blockHeight;
    private final String blockHash;
    private final List<JsonTxInput> inputs;
    private final List<JsonTxOutput> outputs;
    private final boolean isVerified;
    private final JsonTxType txType;
    private final String txTypeDisplayString;
    private final long burntFee;
}

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

package bisq.core.dao.node.json;

import bisq.common.app.Version;

import java.util.List;

import lombok.Value;

@Value
class JsonTx {
    private final String txVersion = Version.BSQ_TX_VERSION;
    private final String id;
    private final int blockHeight;
    private final String blockHash;
    private final long time;
    private final List<JsonTxInput> inputs;
    private final List<JsonTxOutput> outputs;
    private final JsonTxType txType;
    private final String txTypeDisplayString;
    private final long burntFee;
    // If not set it is -1. LockTime of 0 is a valid value.
    private final int unlockBlockHeight;
}

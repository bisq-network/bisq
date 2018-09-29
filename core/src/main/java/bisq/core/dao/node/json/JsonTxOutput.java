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

import lombok.Value;

import javax.annotation.Nullable;

@Value
class JsonTxOutput {
    private final String txVersion = Version.BSQ_TX_VERSION;
    private final String txId;
    private final int index;
    private final long bsqAmount;
    private final long btcAmount;
    private final int height;
    private final boolean isVerified; // isBsqTxOutputType
    private final long burntFee;
    private final String address;
    @Nullable
    private final JsonScriptPubKey scriptPubKey;
    @Nullable
    private final JsonSpentInfo spentInfo;
    private final long time;
    private final JsonTxType txType;
    private final String txTypeDisplayString;
    private final JsonTxOutputType txOutputType; // new
    private final String txOutputTypeDisplayString; // new
    @Nullable
    private final String opReturn;
    private final int lockTime; // new

    String getId() {
        return txId + ":" + index;
    }
}

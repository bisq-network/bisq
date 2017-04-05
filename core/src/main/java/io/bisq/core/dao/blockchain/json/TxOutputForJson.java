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

import lombok.Value;

@Value
public class TxOutputForJson {
    private final boolean coinBase;
    private final int height;
    private final int index;
    private final boolean invalid;
    private final int n;
    private final int output_index;

    private final ScriptPubKeyJson scriptPubKey;
    private final SpentInfoJson spent_info;

    private final long squ_amount;
    private final String status;
    private final String transaction_version;
    private final long tx_time;
    private final String tx_type_str;
    private final String txid;
    private final boolean validated;
    private final double value;
    private final long valueSat;
}

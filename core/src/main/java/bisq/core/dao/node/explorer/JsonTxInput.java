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

package bisq.core.dao.node.explorer;

import lombok.Value;

import javax.annotation.concurrent.Immutable;

@Value
@Immutable
class JsonTxInput {
    private final int spendingTxOutputIndex; // connectedTxOutputIndex
    private final String spendingTxId; // connectedTxOutputTxId
    private final long bsqAmount;
    private final boolean isVerified; // isBsqTxOutputType
    private final String address;
    private final long time;
}

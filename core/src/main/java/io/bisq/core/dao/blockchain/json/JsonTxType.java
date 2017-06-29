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

package io.bisq.core.dao.blockchain.json;

import lombok.Getter;

public enum JsonTxType {
    UNVERIFIED("Unverified"),
    INVALID("Invalid"),
    GENESIS("Genesis"),
    TRANSFER_BSQ("Transfer BSQ"),
    PAY_TRADE_FEE("Pay trade fee"),
    COMPENSATION_REQUEST("Compensation request"),
    VOTE("Vote"),
    ISSUANCE("Issuance"),
    LOCK_UP("Lockup"),
    UN_LOCK("Unlock");

    @Getter
    private String displayString;

    JsonTxType(String displayString) {
        this.displayString = displayString;
    }
}

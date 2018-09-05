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

import lombok.Getter;

//TODO sync up with data model
public enum JsonTxType {
    UNDEFINED_TX_TYPE("Undefined"),
    UNVERIFIED("Unverified"),
    INVALID("Invalid"),
    GENESIS("Genesis"),
    TRANSFER_BSQ("Transfer BSQ"),
    PAY_TRADE_FEE("Pay trade fee"),
    PROPOSAL("Ballot"),
    COMPENSATION_REQUEST("Compensation request"),
    VOTE("Vote"),
    BLIND_VOTE("Blind vote"),
    VOTE_REVEAL("Vote reveal"),
    LOCK_UP("Lockup"),
    UN_LOCK("Unlock");

    @Getter
    private String displayString;

    JsonTxType(String displayString) {
        this.displayString = displayString;
    }
}

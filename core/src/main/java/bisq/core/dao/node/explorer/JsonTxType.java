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

import lombok.Getter;

// Need to be in sync with TxOutputType
enum JsonTxType {
    UNDEFINED("Undefined"),
    UNDEFINED_TX_TYPE("Undefined tx type"),
    UNVERIFIED("Unverified"),
    INVALID("Invalid"),
    GENESIS("Genesis"),
    TRANSFER_BSQ("Transfer BSQ"),
    PAY_TRADE_FEE("Pay trade fee"),
    PROPOSAL("Proposal"),
    COMPENSATION_REQUEST("Compensation request"),
    REIMBURSEMENT_REQUEST("Reimbursement request"),
    BLIND_VOTE("Blind vote"),
    VOTE_REVEAL("Vote reveal"),
    LOCKUP("Lockup"),
    UNLOCK("Unlock"),
    ASSET_LISTING_FEE("Asset listing fee"),
    PROOF_OF_BURN("Proof of burn"),
    IRREGULAR("Irregular");

    @Getter
    private final String displayString;

    JsonTxType(String displayString) {
        this.displayString = displayString;
    }
}

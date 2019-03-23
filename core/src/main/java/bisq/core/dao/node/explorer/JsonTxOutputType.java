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
enum JsonTxOutputType {
    UNDEFINED("Undefined"),
    UNDEFINED_OUTPUT("Undefined output"),
    GENESIS_OUTPUT("Genesis"),
    BSQ_OUTPUT("BSQ"),
    BTC_OUTPUT("BTC"),
    PROPOSAL_OP_RETURN_OUTPUT("Proposal opReturn"),
    COMP_REQ_OP_RETURN_OUTPUT("Compensation request opReturn"),
    REIMBURSEMENT_OP_RETURN_OUTPUT("Reimbursement request opReturn"),
    CONFISCATE_BOND_OP_RETURN_OUTPUT("Confiscate bond opReturn"),
    ISSUANCE_CANDIDATE_OUTPUT("Issuance candidate"),
    BLIND_VOTE_LOCK_STAKE_OUTPUT("Blind vote lock stake"),
    BLIND_VOTE_OP_RETURN_OUTPUT("Blind vote opReturn"),
    VOTE_REVEAL_UNLOCK_STAKE_OUTPUT("Vote reveal unlock stake"),
    VOTE_REVEAL_OP_RETURN_OUTPUT("Vote reveal opReturn"),
    ASSET_LISTING_FEE_OP_RETURN_OUTPUT("Asset listing fee OpReturn"),
    PROOF_OF_BURN_OP_RETURN_OUTPUT("Proof of burn opReturn"),
    LOCKUP_OUTPUT("Lockup"),
    LOCKUP_OP_RETURN_OUTPUT("Lockup opReturn"),
    UNLOCK_OUTPUT("Unlock"),
    INVALID_OUTPUT("Invalid");

    @Getter
    private String displayString;

    JsonTxOutputType(String displayString) {
        this.displayString = displayString;
    }
}

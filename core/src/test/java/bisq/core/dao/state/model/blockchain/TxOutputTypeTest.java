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

package bisq.core.dao.state.model.blockchain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TxOutputTypeTest {
    @Test
    public void getCodeMatchesProtobufOrder() {
        assertEquals(20, TxOutputType.values().length);
        assertEquals(0, TxOutputType.UNDEFINED.getCode());
        assertEquals(1, TxOutputType.UNDEFINED_OUTPUT.getCode());
        assertEquals(2, TxOutputType.GENESIS_OUTPUT.getCode());
        assertEquals(3, TxOutputType.BSQ_OUTPUT.getCode());
        assertEquals(4, TxOutputType.BTC_OUTPUT.getCode());
        assertEquals(5, TxOutputType.PROPOSAL_OP_RETURN_OUTPUT.getCode());
        assertEquals(6, TxOutputType.COMP_REQ_OP_RETURN_OUTPUT.getCode());
        assertEquals(7, TxOutputType.REIMBURSEMENT_OP_RETURN_OUTPUT.getCode());
        assertEquals(8, TxOutputType.CONFISCATE_BOND_OP_RETURN_OUTPUT.getCode());
        assertEquals(9, TxOutputType.ISSUANCE_CANDIDATE_OUTPUT.getCode());
        assertEquals(10, TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT.getCode());
        assertEquals(11, TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT.getCode());
        assertEquals(12, TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT.getCode());
        assertEquals(13, TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT.getCode());
        assertEquals(14, TxOutputType.ASSET_LISTING_FEE_OP_RETURN_OUTPUT.getCode());
        assertEquals(15, TxOutputType.PROOF_OF_BURN_OP_RETURN_OUTPUT.getCode());
        assertEquals(16, TxOutputType.LOCKUP_OUTPUT.getCode());
        assertEquals(17, TxOutputType.LOCKUP_OP_RETURN_OUTPUT.getCode());
        assertEquals(18, TxOutputType.UNLOCK_OUTPUT.getCode());
        assertEquals(19, TxOutputType.INVALID_OUTPUT.getCode());
    }
}

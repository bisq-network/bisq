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

import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.ProtoUtil;

import javax.annotation.concurrent.Immutable;

@Immutable
public enum TxOutputType implements ImmutableDaoStateModel {
    UNDEFINED, // only fallback for backward compatibility in case we add a new value and old clients fall back to UNDEFINED
    UNDEFINED_OUTPUT,
    GENESIS_OUTPUT,
    BSQ_OUTPUT,
    BTC_OUTPUT,
    PROPOSAL_OP_RETURN_OUTPUT,
    COMP_REQ_OP_RETURN_OUTPUT,
    REIMBURSEMENT_OP_RETURN_OUTPUT,
    CONFISCATE_BOND_OP_RETURN_OUTPUT,
    ISSUANCE_CANDIDATE_OUTPUT,
    BLIND_VOTE_LOCK_STAKE_OUTPUT,
    BLIND_VOTE_OP_RETURN_OUTPUT,
    VOTE_REVEAL_UNLOCK_STAKE_OUTPUT,
    VOTE_REVEAL_OP_RETURN_OUTPUT,
    ASSET_LISTING_FEE_OP_RETURN_OUTPUT,
    PROOF_OF_BURN_OP_RETURN_OUTPUT,
    LOCKUP_OUTPUT,
    LOCKUP_OP_RETURN_OUTPUT,
    UNLOCK_OUTPUT,
    INVALID_OUTPUT;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TxOutputType fromProto(protobuf.TxOutputType txOutputType) {
        return ProtoUtil.enumFromProto(TxOutputType.class, txOutputType.name());
    }

    public protobuf.TxOutputType toProtoMessage() {
        return protobuf.TxOutputType.valueOf(name());
    }
}

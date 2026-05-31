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

import bisq.core.encoding.canonical.CanonicalEnum;
import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.ProtoUtil;

import javax.annotation.concurrent.Immutable;

@Immutable
public enum TxOutputType implements ImmutableDaoStateModel, CanonicalEnum {
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



    ///////////////////////////////////////////////////////////////////////////////////////////
    // CanonicalEnum
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final int UNDEFINED_CODE = protobuf.TxOutputType.PB_ERROR_TX_OUTPUT_TYPE.getNumber();
    private static final int UNDEFINED_OUTPUT_CODE = protobuf.TxOutputType.UNDEFINED_OUTPUT.getNumber();
    private static final int GENESIS_OUTPUT_CODE = protobuf.TxOutputType.GENESIS_OUTPUT.getNumber();
    private static final int BSQ_OUTPUT_CODE = protobuf.TxOutputType.BSQ_OUTPUT.getNumber();
    private static final int BTC_OUTPUT_CODE = protobuf.TxOutputType.BTC_OUTPUT.getNumber();
    private static final int PROPOSAL_OP_RETURN_OUTPUT_CODE = protobuf.TxOutputType.PROPOSAL_OP_RETURN_OUTPUT.getNumber();
    private static final int COMP_REQ_OP_RETURN_OUTPUT_CODE = protobuf.TxOutputType.COMP_REQ_OP_RETURN_OUTPUT.getNumber();
    private static final int REIMBURSEMENT_OP_RETURN_OUTPUT_CODE = protobuf.TxOutputType.REIMBURSEMENT_OP_RETURN_OUTPUT.getNumber();
    private static final int CONFISCATE_BOND_OP_RETURN_OUTPUT_CODE = protobuf.TxOutputType.CONFISCATE_BOND_OP_RETURN_OUTPUT.getNumber();
    private static final int ISSUANCE_CANDIDATE_OUTPUT_CODE = protobuf.TxOutputType.ISSUANCE_CANDIDATE_OUTPUT.getNumber();
    private static final int BLIND_VOTE_LOCK_STAKE_OUTPUT_CODE = protobuf.TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT.getNumber();
    private static final int BLIND_VOTE_OP_RETURN_OUTPUT_CODE = protobuf.TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT.getNumber();
    private static final int VOTE_REVEAL_UNLOCK_STAKE_OUTPUT_CODE = protobuf.TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT.getNumber();
    private static final int VOTE_REVEAL_OP_RETURN_OUTPUT_CODE = protobuf.TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT.getNumber();
    private static final int ASSET_LISTING_FEE_OP_RETURN_OUTPUT_CODE = protobuf.TxOutputType.ASSET_LISTING_FEE_OP_RETURN_OUTPUT.getNumber();
    private static final int PROOF_OF_BURN_OP_RETURN_OUTPUT_CODE = protobuf.TxOutputType.PROOF_OF_BURN_OP_RETURN_OUTPUT.getNumber();
    private static final int LOCKUP_OUTPUT_CODE = protobuf.TxOutputType.LOCKUP_OUTPUT.getNumber();
    private static final int LOCKUP_OP_RETURN_OUTPUT_CODE = protobuf.TxOutputType.LOCKUP_OP_RETURN_OUTPUT.getNumber();
    private static final int UNLOCK_OUTPUT_CODE = protobuf.TxOutputType.UNLOCK_OUTPUT.getNumber();
    private static final int INVALID_OUTPUT_CODE = protobuf.TxOutputType.INVALID_OUTPUT.getNumber();

    @Override
    public int getCode() {
        switch (this) {
            case UNDEFINED:
                return UNDEFINED_CODE;
            case UNDEFINED_OUTPUT:
                return UNDEFINED_OUTPUT_CODE;
            case GENESIS_OUTPUT:
                return GENESIS_OUTPUT_CODE;
            case BSQ_OUTPUT:
                return BSQ_OUTPUT_CODE;
            case BTC_OUTPUT:
                return BTC_OUTPUT_CODE;
            case PROPOSAL_OP_RETURN_OUTPUT:
                return PROPOSAL_OP_RETURN_OUTPUT_CODE;
            case COMP_REQ_OP_RETURN_OUTPUT:
                return COMP_REQ_OP_RETURN_OUTPUT_CODE;
            case REIMBURSEMENT_OP_RETURN_OUTPUT:
                return REIMBURSEMENT_OP_RETURN_OUTPUT_CODE;
            case CONFISCATE_BOND_OP_RETURN_OUTPUT:
                return CONFISCATE_BOND_OP_RETURN_OUTPUT_CODE;
            case ISSUANCE_CANDIDATE_OUTPUT:
                return ISSUANCE_CANDIDATE_OUTPUT_CODE;
            case BLIND_VOTE_LOCK_STAKE_OUTPUT:
                return BLIND_VOTE_LOCK_STAKE_OUTPUT_CODE;
            case BLIND_VOTE_OP_RETURN_OUTPUT:
                return BLIND_VOTE_OP_RETURN_OUTPUT_CODE;
            case VOTE_REVEAL_UNLOCK_STAKE_OUTPUT:
                return VOTE_REVEAL_UNLOCK_STAKE_OUTPUT_CODE;
            case VOTE_REVEAL_OP_RETURN_OUTPUT:
                return VOTE_REVEAL_OP_RETURN_OUTPUT_CODE;
            case ASSET_LISTING_FEE_OP_RETURN_OUTPUT:
                return ASSET_LISTING_FEE_OP_RETURN_OUTPUT_CODE;
            case PROOF_OF_BURN_OP_RETURN_OUTPUT:
                return PROOF_OF_BURN_OP_RETURN_OUTPUT_CODE;
            case LOCKUP_OUTPUT:
                return LOCKUP_OUTPUT_CODE;
            case LOCKUP_OP_RETURN_OUTPUT:
                return LOCKUP_OP_RETURN_OUTPUT_CODE;
            case UNLOCK_OUTPUT:
                return UNLOCK_OUTPUT_CODE;
            case INVALID_OUTPUT:
                return INVALID_OUTPUT_CODE;
            default:
                throw new IllegalStateException("Unhandled tx output type " + this);
        }
    }
}

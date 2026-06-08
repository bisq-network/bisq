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
import bisq.core.encoding.canonical.CanonicalEnum;

import bisq.common.proto.ProtoUtil;

import lombok.Getter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
public enum TxType implements ImmutableDaoStateModel, CanonicalEnum {
    UNDEFINED(false, false), // only fallback for backward compatibility in case we add a new value and old clients fall back to UNDEFINED
    UNDEFINED_TX_TYPE(false, false),
    UNVERIFIED(false, false),
    INVALID(false, false),
    GENESIS(false, false),
    TRANSFER_BSQ(false, false),
    PAY_TRADE_FEE(false, true),
    PROPOSAL(true, true),
    COMPENSATION_REQUEST(true, true),
    REIMBURSEMENT_REQUEST(true, true),
    BLIND_VOTE(true, true),
    VOTE_REVEAL(true, false),
    LOCKUP(true, false),
    UNLOCK(true, false),
    ASSET_LISTING_FEE(true, true),
    PROOF_OF_BURN(true, true),
    IRREGULAR(false, false); // the params are irrelevant here as we can have any tx that violated the rules set to irregular


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Getter
    private final boolean hasOpReturn;
    @Getter
    private final boolean requiresFee;

    TxType(boolean hasOpReturn, boolean requiresFee) {
        this.hasOpReturn = hasOpReturn;
        this.requiresFee = requiresFee;
    }

    @Nullable
    public static TxType fromProto(protobuf.TxType txType) {
        return ProtoUtil.enumFromProto(TxType.class, txType.name());
    }

    public protobuf.TxType toProtoMessage() {
        return protobuf.TxType.valueOf(name());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CanonicalEnum
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public int getCode() {
        switch (this) {
            case UNDEFINED:
                return 0;
            case UNDEFINED_TX_TYPE:
                return 1;
            case UNVERIFIED:
                return 2;
            case INVALID:
                return 3;
            case GENESIS:
                return 4;
            case TRANSFER_BSQ:
                return 5;
            case PAY_TRADE_FEE:
                return 6;
            case PROPOSAL:
                return 7;
            case COMPENSATION_REQUEST:
                return 8;
            case REIMBURSEMENT_REQUEST:
                return 9;
            case BLIND_VOTE:
                return 10;
            case VOTE_REVEAL:
                return 11;
            case LOCKUP:
                return 12;
            case UNLOCK:
                return 13;
            case ASSET_LISTING_FEE:
                return 14;
            case PROOF_OF_BURN:
                return 15;
            case IRREGULAR:
                return 16;
            default:
                throw new IllegalStateException("Unhandled tx type " + this);
        }
    }
}

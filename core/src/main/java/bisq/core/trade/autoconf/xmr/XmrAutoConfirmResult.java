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

package bisq.core.trade.autoconf.xmr;

import bisq.core.locale.Res;
import bisq.core.trade.autoconf.AutoConfirmResult;

import bisq.common.proto.ProtoUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Getter
@EqualsAndHashCode(callSuper = true)
public class XmrAutoConfirmResult extends AutoConfirmResult {
    public enum State {
        UNDEFINED,
        FEATURE_DISABLED,
        TX_NOT_FOUND,
        TX_NOT_CONFIRMED,
        PROOF_OK,
        CONNECTION_FAIL,
        API_FAILURE,
        API_INVALID,
        TX_KEY_REUSED,
        TX_HASH_INVALID,
        TX_KEY_INVALID,
        ADDRESS_INVALID,
        NO_MATCH_FOUND,
        AMOUNT_NOT_MATCHING,
        TRADE_LIMIT_EXCEEDED,
        TRADE_DATE_NOT_MATCHING
    }

    private final State state;
    private final transient int confirmCount;
    private final transient int confirmsRequired;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////////////////////

    public XmrAutoConfirmResult() {
        this(State.UNDEFINED, 0, 0);
    }

    XmrAutoConfirmResult(State state) {
        this(state, 0, 0);
    }

    // alternate constructor for showing confirmation progress information
    XmrAutoConfirmResult(State state, int confirmCount, int confirmsRequired) {
        super(state.name());
        this.state = state;
        this.confirmCount = confirmCount;
        this.confirmsRequired = confirmsRequired;
    }

    // alternate constructor for error scenarios
    XmrAutoConfirmResult(State state, @Nullable String errorMsg) {
        this(state, 0, 0);

        if (!isPendingState() && !isSuccessState() && state != State.FEATURE_DISABLED && state != State.UNDEFINED) {
            log.error(errorMsg != null ? errorMsg : state.toString());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.AutoConfirmResult toProtoMessage() {
        return protobuf.AutoConfirmResult.newBuilder().setStateName(state.name()).build();
    }

    public static XmrAutoConfirmResult fromProto(protobuf.AutoConfirmResult proto) {
        XmrAutoConfirmResult.State state = ProtoUtil.enumFromProto(XmrAutoConfirmResult.State.class, proto.getStateName());
        return state != null ? new XmrAutoConfirmResult(state) : new XmrAutoConfirmResult(State.UNDEFINED);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getStatusAsDisplayString() {
        switch (state) {
            case TX_NOT_CONFIRMED:
                return Res.get("portfolio.pending.autoConfirmPending")
                        + " " + confirmCount
                        + "/" + confirmsRequired;
            case TX_NOT_FOUND:
                return Res.get("portfolio.pending.autoConfirmTxNotFound");
            case FEATURE_DISABLED:
                return Res.get("portfolio.pending.autoConfirmDisabled");
            case PROOF_OK:
                return Res.get("portfolio.pending.autoConfirmSuccess");
            default:
                // any other statuses we display the enum name
                return this.state.toString();
        }
    }

    @Override
    public boolean isSuccessState() {
        return (state == State.PROOF_OK);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean isPendingState() {
        return (state == State.TX_NOT_FOUND || state == State.TX_NOT_CONFIRMED);
    }
}

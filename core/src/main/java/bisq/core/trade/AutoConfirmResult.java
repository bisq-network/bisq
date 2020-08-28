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

package bisq.core.trade;

import bisq.core.locale.Res;

import bisq.common.proto.ProtoUtil;

import javax.annotation.Nullable;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
public class AutoConfirmResult {
    public enum State {
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
        AMOUNT_NOT_MATCHING,
        TRADE_LIMIT_EXCEEDED,
        TRADE_DATE_NOT_MATCHING;

        public static AutoConfirmResult.State fromProto(protobuf.Trade.AutoConfirmResult result) {
            return ProtoUtil.enumFromProto(AutoConfirmResult.State.class, result.name());
        }

        public static protobuf.Trade.AutoConfirmResult toProtoMessage(AutoConfirmResult.State result) {
            return protobuf.Trade.AutoConfirmResult.valueOf(result.name());
        }
    }

    private final State state;
    private final transient int confirmCount;
    private final transient int confirmsRequired;

    public AutoConfirmResult(State state) {
        this.state = state;
        this.confirmCount = 0;
        this.confirmsRequired = 0;
    }

    // alternate constructor for showing confirmation progress information
    public AutoConfirmResult(State state, int confirmCount, int confirmsRequired) {
        this.state = state;
        this.confirmCount = confirmCount;
        this.confirmsRequired = confirmsRequired;
    }

    // alternate constructor for error scenarios
    public AutoConfirmResult(State state, @Nullable String errorMsg) {
        this.state = state;
        this.confirmCount = 0;
        this.confirmsRequired = 0;
        if (isErrorState())
            log.error(errorMsg != null ? errorMsg : state.toString());
    }

    public String getTextStatus() {
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

    public boolean isPendingState() {
        return (state == State.TX_NOT_FOUND || state == State.TX_NOT_CONFIRMED);
    }

    public boolean isSuccessState() {
        return (state == State.PROOF_OK);
    }

    public boolean isErrorState() {
        return (!isPendingState() && !isSuccessState());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTOBUF
    ///////////////////////////////////////////////////////////////////////////////////////////

    public protobuf.Trade.AutoConfirmResult toProtoMessage() {
        return State.toProtoMessage(state);
    }

    public static AutoConfirmResult fromProto(protobuf.Trade.AutoConfirmResult proto) {
        return new AutoConfirmResult(AutoConfirmResult.State.fromProto(proto));
    }
}

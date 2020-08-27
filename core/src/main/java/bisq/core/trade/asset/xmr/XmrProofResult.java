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

package bisq.core.trade.asset.xmr;

import bisq.core.locale.Res;

import javax.annotation.Nullable;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
public class XmrProofResult {
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
    }

    private final int confirmCount;
    private final int confirmsRequired;
    private final State state;

    public XmrProofResult(int confirmCount, int confirmsRequired, State state) {
        this.confirmCount = confirmCount;
        this.confirmsRequired = confirmsRequired;
        this.state = state;
    }

    // alternate constructor for error scenarios
    public XmrProofResult(State state, @Nullable String errorMsg) {
        this.confirmCount = 0;
        this.confirmsRequired = 0;
        this.state = state;
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

}

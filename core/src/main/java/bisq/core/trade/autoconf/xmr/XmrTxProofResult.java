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
import bisq.core.trade.autoconf.AssetTxProofResult;

import bisq.common.proto.ProtoUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@ToString
public class XmrTxProofResult extends AssetTxProofResult {
    public enum State {
        UNDEFINED,

        // Feature disable cases
        FEATURE_DISABLED,
        TRADE_LIMIT_EXCEEDED,

        // Pending state
        REQUEST_STARTED,
        TX_NOT_FOUND,
        PENDING_SERVICE_RESULTS,
        PENDING_CONFIRMATIONS,

        SINGLE_SERVICE_SUCCEEDED,   // Individual service has delivered proof ok

        // Success state
        ALL_SERVICES_SUCCEEDED, // All services delivered PROOF_OK

        // Error state
        CONNECTION_FAIL,
        API_FAILURE,
        API_INVALID,
        TX_KEY_REUSED,
        TX_HASH_INVALID,
        TX_KEY_INVALID,
        ADDRESS_INVALID,
        NO_MATCH_FOUND,
        AMOUNT_NOT_MATCHING,
        TRADE_DATE_NOT_MATCHING
    }

    @Getter
    private transient final State state;
    @Setter
    private transient int numConfirmations;
    @Setter
    private transient int requiredConfirmations;
    @Nullable
    private transient String errorMsg;
    @Setter
    private transient int pendingServiceResults;
    @Setter
    private transient int requiredServiceResults;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////////////////////

    public XmrTxProofResult() {
        this(State.UNDEFINED);
    }

    XmrTxProofResult(State state) {
        super(state.name());

        this.state = state;
    }

    XmrTxProofResult(State state, String errorMsg) {
        this(state);

        this.errorMsg = errorMsg;
        log.error(errorMsg);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.AutoConfirmResult toProtoMessage() {
        return protobuf.AutoConfirmResult.newBuilder().setStateName(state.name()).build();
    }

    public static XmrTxProofResult fromProto(protobuf.AutoConfirmResult proto) {
        XmrTxProofResult.State state = ProtoUtil.enumFromProto(XmrTxProofResult.State.class, proto.getStateName());
        return state != null ? new XmrTxProofResult(state) : new XmrTxProofResult(State.UNDEFINED);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getStatusAsDisplayString() {
        String key = "portfolio.pending.autoConf.state." + state;
        switch (state) {
            // Invalid protobuf data
            case UNDEFINED:
                return state.toString();

            // Feature disable cases
            case FEATURE_DISABLED:
            case TRADE_LIMIT_EXCEEDED:

                // Pending state
            case REQUEST_STARTED:
            case TX_NOT_FOUND: // Tx still not confirmed and not in mempool
                return Res.get(key);
            case PENDING_SERVICE_RESULTS:
                return Res.get(key, pendingServiceResults, requiredServiceResults);
            case PENDING_CONFIRMATIONS:
                return Res.get(key, numConfirmations, requiredConfirmations);
            case SINGLE_SERVICE_SUCCEEDED:

                // Success state
            case ALL_SERVICES_SUCCEEDED:
                return Res.get(key);

            // Error state
            case CONNECTION_FAIL:
            case API_FAILURE:
            case API_INVALID:
            case TX_KEY_REUSED:
            case TX_HASH_INVALID:
            case TX_KEY_INVALID:
            case ADDRESS_INVALID:
            case NO_MATCH_FOUND:
            case AMOUNT_NOT_MATCHING:
            case TRADE_DATE_NOT_MATCHING:
                return getErrorMsg();

            default:
                return state.toString();
        }
    }

    @Override
    public boolean isSuccessState() {
        return (state == State.ALL_SERVICES_SUCCEEDED);
    }

    boolean isErrorState() {
        switch (state) {
            case CONNECTION_FAIL:
            case API_FAILURE:
            case API_INVALID:
            case TX_KEY_REUSED:
            case TX_HASH_INVALID:
            case TX_KEY_INVALID:
            case ADDRESS_INVALID:
            case NO_MATCH_FOUND:
            case AMOUNT_NOT_MATCHING:
            case TRADE_DATE_NOT_MATCHING:
                return true;

            default:
                return false;
        }
    }

    boolean isPendingState() {
        switch (state) {
            case REQUEST_STARTED:
            case TX_NOT_FOUND:
            case PENDING_SERVICE_RESULTS:
            case PENDING_CONFIRMATIONS:
                return true;

            default:
                return false;
        }
    }

    private String getErrorMsg() {
        return errorMsg != null ? errorMsg : state.name();
    }
}

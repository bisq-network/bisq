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

package bisq.core.support.dispute.mediation;

import bisq.common.proto.ProtoUtil;

public enum MediationResultState {
    UNDEFINED_MEDIATION_RESULT,
    MEDIATION_RESULT_ACCEPTED(),
    MEDIATION_RESULT_REJECTED,
    SIG_MSG_SENT,
    SIG_MSG_ARRIVED,
    SIG_MSG_IN_MAILBOX,
    SIG_MSG_SEND_FAILED,
    RECEIVED_SIG_MSG,
    PAYOUT_TX_PUBLISHED,
    PAYOUT_TX_PUBLISHED_MSG_SENT,
    PAYOUT_TX_PUBLISHED_MSG_ARRIVED,
    PAYOUT_TX_PUBLISHED_MSG_IN_MAILBOX,
    PAYOUT_TX_PUBLISHED_MSG_SEND_FAILED,
    RECEIVED_PAYOUT_TX_PUBLISHED_MSG,
    PAYOUT_TX_SEEN_IN_NETWORK;

    public static MediationResultState fromProto(protobuf.MediationResultState mediationResultState) {
        return ProtoUtil.enumFromProto(MediationResultState.class, mediationResultState.name());
    }

    public static protobuf.MediationResultState toProtoMessage(MediationResultState mediationResultState) {
        return protobuf.MediationResultState.valueOf(mediationResultState.name());
    }
}

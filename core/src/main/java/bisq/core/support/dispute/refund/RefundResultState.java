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

package bisq.core.support.dispute.refund;

import bisq.common.proto.ProtoUtil;

// todo
public enum RefundResultState {
    UNDEFINED_REFUND_RESULT;

    public static RefundResultState fromProto(protobuf.RefundResultState refundResultState) {
        return ProtoUtil.enumFromProto(RefundResultState.class, refundResultState.name());
    }

    public static protobuf.RefundResultState toProtoMessage(RefundResultState refundResultState) {
        return protobuf.RefundResultState.valueOf(refundResultState.name());
    }
}

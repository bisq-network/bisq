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

package bisq.seednode.reporting;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.network.NetworkPayload;

import java.util.Optional;

public interface ReportingItem extends NetworkPayload {
    String getKey();

    String getGroup();

    String getPath();

    default protobuf.ReportingItem.Builder getBuilder() {
        return protobuf.ReportingItem.newBuilder()
                .setGroup(getGroup())
                .setKey(getKey());
    }

    protobuf.ReportingItem toProtoMessage();

    static Optional<? extends ReportingItem> fromProto(protobuf.ReportingItem proto) {
        switch (proto.getMessageCase()) {
            case STRING_VALUE_REPORTING_ITEM:
                return StringValueReportingItem.fromProto(proto, proto.getStringValueReportingItem());
            case LONG_VALUE_REPORTING_ITEM:
                return LongValueReportingItem.fromProto(proto, proto.getLongValueReportingItem());
            case DOUBLE_VALUE_REPORTING_ITEM:
                return DoubleValueReportingItem.fromProto(proto, proto.getDoubleValueReportingItem());
            case MESSAGE_NOT_SET:
            default:
                throw new ProtobufferRuntimeException("Unknown message case: " + proto);
        }
    }
}

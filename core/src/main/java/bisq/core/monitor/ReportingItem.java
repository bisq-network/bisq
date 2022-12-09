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

package bisq.core.monitor;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.network.NetworkPayload;

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

    static ReportingItem fromProto(protobuf.ReportingItem proto) {
        switch (proto.getMessageCase()) {
            case STRING_VALUE_ITEM:
                return StringValueItem.fromProto(proto, proto.getStringValueItem());
            case LONG_VALUE_ITEM:
                return LongValueItem.fromProto(proto, proto.getLongValueItem());
            case DOUBLE_VALUE_ITEM:
                return DoubleValueItem.fromProto(proto, proto.getDoubleValueItem());
            case MESSAGE_NOT_SET:
            default:
                throw new ProtobufferRuntimeException("Unknown message case: " + proto);
        }
    }
}

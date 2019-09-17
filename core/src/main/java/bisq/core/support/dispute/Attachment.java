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

package bisq.core.support.dispute;

import bisq.common.proto.network.NetworkPayload;

import com.google.protobuf.ByteString;

import lombok.Value;

@Value
public final class Attachment implements NetworkPayload {
    private final String fileName;
    private final byte[] bytes;

    public Attachment(String fileName, byte[] bytes) {
        this.fileName = fileName;
        this.bytes = bytes;
    }

    @Override
    public protobuf.Attachment toProtoMessage() {
        return protobuf.Attachment.newBuilder()
                .setFileName(fileName)
                .setBytes(ByteString.copyFrom(bytes))
                .build();
    }

    public static Attachment fromProto(protobuf.Attachment proto) {
        return new Attachment(proto.getFileName(), proto.getBytes().toByteArray());
    }
}

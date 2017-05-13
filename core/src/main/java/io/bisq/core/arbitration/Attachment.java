package io.bisq.core.arbitration;

import com.google.protobuf.ByteString;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;

@Value
public final class Attachment implements NetworkPayload {
    private final byte[] bytes;
    private final String fileName;

    public Attachment(String fileName, byte[] bytes) {
        this.fileName = fileName;
        this.bytes = bytes;
    }

    @Override
    public PB.Attachment toProtoMessage() {
        return PB.Attachment.newBuilder()
                .setBytes(ByteString.copyFrom(bytes))
                .setFileName(fileName)
                .build();
    }

    public static Attachment fromProto(PB.Attachment proto) {
        return new Attachment(proto.getFileName(), proto.getBytes().toByteArray());
    }
}
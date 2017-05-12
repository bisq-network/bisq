package io.bisq.core.arbitration;

import com.google.protobuf.ByteString;
import io.bisq.common.network.NetworkPayload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import org.bouncycastle.util.encoders.Hex;

@EqualsAndHashCode
public final class Attachment implements NetworkPayload {
    // Payload
    private final byte[] bytes;
    private final String fileName;

    public Attachment(String fileName, byte[] bytes) {
        this.fileName = fileName;
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getFileName() {
        return fileName;
    }


    // Hex
    @Override
    public String toString() {
        return "Attachment{" +
                "description=" + fileName +
                ", data=" + Hex.toHexString(bytes) +
                '}';
    }

    @Override
    public PB.Attachment toProtoMessage() {
        return PB.Attachment.newBuilder().setBytes(ByteString.copyFrom(bytes))
                .setFileName(fileName).build();
    }
}
package io.bisq.protobuffer.payload.arbitration;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.payload.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public final class Attachment implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final Logger log = LoggerFactory.getLogger(Attachment.class);

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Attachment)) return false;

        Attachment that = (Attachment) o;

        if (!Arrays.equals(bytes, that.bytes)) return false;
        return !(fileName != null ? !fileName.equals(that.fileName) : that.fileName != null);

    }

    @Override
    public int hashCode() {
        int result = bytes != null ? Arrays.hashCode(bytes) : 0;
        result = 31 * result + (fileName != null ? fileName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Attachment{" +
                "description=" + fileName +
                ", data=" + Arrays.toString(bytes) +
                '}';
    }

    @Override
    public PB.Attachment toProto() {
        return PB.Attachment.newBuilder().setBytes(ByteString.copyFrom(bytes))
                .setFileName(fileName).build();
    }
}
package io.bisq.wire.payload;

import com.google.protobuf.Message;
import io.bisq.wire.message.ToProtoBuffer;

import java.io.Serializable;

/**
 * Marker interface for data which is sent over the wire
 */
public interface Payload extends Serializable, ToProtoBuffer {
    Message toProtoBuf();
}

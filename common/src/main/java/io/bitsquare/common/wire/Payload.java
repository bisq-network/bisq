package io.bitsquare.common.wire;

import com.google.protobuf.Message;
import io.bitsquare.messages.ToProtoBuffer;

import java.io.Serializable;

/**
 * Marker interface for data which is sent over the wire
 */
public interface Payload extends Serializable, ToProtoBuffer {
    Message toProtoBuf();
}

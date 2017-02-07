package io.bitsquare.common.wire;

import io.bitsquare.common.wire.proto.Messages;

import java.io.Serializable;

/**
 * Marker interface for data which is sent over the wire
 */
public interface Payload extends Serializable {
    Object toProtoBuf();
}

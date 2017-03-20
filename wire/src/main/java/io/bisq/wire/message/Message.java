package io.bisq.wire.message;

import io.bisq.wire.proto.Messages;

import java.io.Serializable;

public interface Message extends Serializable, ToProtoBuffer {
    int getMessageVersion();

    Messages.Envelope toProtoBuf();
}

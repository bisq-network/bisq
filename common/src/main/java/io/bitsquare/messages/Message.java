package io.bitsquare.messages;

import io.bitsquare.common.wire.proto.Messages;

import java.io.Serializable;

public interface Message extends Serializable, ToProtoBuffer {
    int getMessageVersion();

     Messages.Envelope toProtoBuf();
}

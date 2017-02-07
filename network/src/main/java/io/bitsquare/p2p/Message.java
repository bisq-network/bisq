package io.bitsquare.p2p;

import com.google.protobuf.ByteString;
import io.bitsquare.common.wire.proto.Messages;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface Message extends Serializable {
    int getMessageVersion();

     Messages.Envelope toProtoBuf();
}

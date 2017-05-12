package io.bisq.common.network;

import com.google.protobuf.Message;
import io.bisq.common.Envelope;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;

/**
 * Interface for the outside envelope object sent over the network.
 */
public interface NetworkEnvelope extends Envelope {
    static PB.NetworkEnvelope.Builder getMsgBuilder() {
        return PB.NetworkEnvelope.newBuilder().setMsgVersion(Version.getP2PMessageVersion());
    }

    default Message toProtoMessage() {
        return toProtoMsg();
    }

    int getMsgVersion();

    PB.NetworkEnvelope toProtoMsg();
}
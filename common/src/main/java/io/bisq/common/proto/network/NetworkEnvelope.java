package io.bisq.common.proto.network;

import com.google.protobuf.Message;
import io.bisq.common.Envelope;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;

/**
 * Interface for the outside envelope object sent over the network.
 */
public interface NetworkEnvelope extends Envelope {
    static PB.NetworkEnvelope.Builder getDefaultBuilder() {
        return PB.NetworkEnvelope.newBuilder().setMessageVersion(Version.getP2PMessageVersion());
    }

    default int getMessageVersion() {
        return Version.getP2PMessageVersion();
    }

    default Message toProtoMessage() {
        return toProtoNetworkEnvelope();
    }

    PB.NetworkEnvelope toProtoNetworkEnvelope();
}
package io.bitsquare.p2p;

import io.bitsquare.common.wire.proto.Messages;
import io.bitsquare.p2p.network.messages.CloseConnectionMessage;
import io.bitsquare.p2p.peers.getdata.messages.GetDataRequest;
import io.bitsquare.p2p.peers.getdata.messages.GetDataResponse;
import io.bitsquare.p2p.peers.keepalive.messages.Ping;
import io.bitsquare.p2p.peers.keepalive.messages.Pong;
import io.bitsquare.p2p.storage.messages.RefreshTTLMessage;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;

import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by mike on 31/01/2017.
 */
public class ProtoBufferUtilities {

    public static boolean isPrefixedSealedAndSignedMessage(Messages.Envelope envelope) {
        return false;
    }

    public static boolean isSendersNodeAddressMessage(Messages.Envelope envelope) {
        return false;
    }

    public static boolean isGetDataResponse(Messages.Envelope envelope) {
        return !Messages.GetDataResponse.getDefaultInstance().equals(envelope.getGetDataResponse());
    }

    // TODO protectedstorageentry is NULL
    public static GetDataResponse createGetDataResponse(Messages.Envelope envelope) {
        HashSet<ProtectedStorageEntry> set =new HashSet<ProtectedStorageEntry>(
                envelope.getGetDataResponse().getDataSetList()
                        .stream()
                        .map(protectedStorageEntry ->
                                new ProtectedStorageEntry(null, null, 0, null)).collect(Collectors.toList()));
        return new GetDataResponse(set, envelope.getGetDataResponse().getRequestNonce(), envelope.getGetDataResponse().getIsGetUpdatedDataResponse());
    }


    public static boolean isGetDataRequest(Messages.Envelope envelope) {
        return false;
    }


    public static boolean isPing(Messages.Envelope envelope) {
        return !Messages.Ping.getDefaultInstance().equals(envelope.getPing());
    }

    public static Ping createPing(Messages.Envelope envelope) {
        return new Ping(envelope.getPing().getNonce(), envelope.getPing().getLastRoundTripTime());
    }

    public static boolean isPong(Messages.Envelope envelope) {
        return !Messages.Pong.getDefaultInstance().equals(envelope.getPong());
    }

    public static Pong createPong(Messages.Envelope envelope) {
        return new Pong(envelope.getPong().getRequestNonce());
    }

    public static boolean isRefreshTTLMessage(Messages.Envelope envelope) {
        return !Messages.RefreshTTLMessage.getDefaultInstance().equals(envelope.getRefreshTtlMessage());
    }

    public static RefreshTTLMessage createRefreshTTLMessage(Messages.Envelope envelope) {
        Messages.RefreshTTLMessage msg = envelope.getRefreshTtlMessage();
        return new RefreshTTLMessage(msg.getHashOfDataAndSeqNr().toByteArray(),
                msg.getSignature().toByteArray(),
                msg.getHashOfPayload().toByteArray(), msg.getSequenceNumber());
    }

    public static boolean isCloseConnectionMessage(Messages.Envelope envelope) {
        return !Messages.CloseConnectionMessage.getDefaultInstance().equals(envelope.getCloseConnectionMessage());
    }

    public static CloseConnectionMessage createCloseConnectionMessage(Messages.Envelope envelope) {
        return new CloseConnectionMessage(envelope.getCloseConnectionMessage().getReason());
    }


    public static Optional<Message> fromProtoBuf(Messages.Envelope envelope) {
        if (isPing(envelope)) {
            return Optional.of(createPing(envelope));
        } else if (isPong(envelope)) {
            return Optional.of(createPong(envelope));
        } else if (isGetDataResponse(envelope)) {
            return Optional.of(createGetDataResponse(envelope));
        } else if (isRefreshTTLMessage(envelope)) {
            return Optional.of(createRefreshTTLMessage(envelope));
        } else if (isCloseConnectionMessage(envelope)) {
            return Optional.of(createCloseConnectionMessage(envelope));
        }
        /*
        else if (isPong(envelope)) {
            return Optional.of(createPong(envelope));
        } else if (isPong(envelope)) {
            return Optional.of(createPong(envelope));
        } else if (isPong(envelope)) {
            return Optional.of(createPong(envelope));
        }
        */
        return Optional.empty();
    }


}

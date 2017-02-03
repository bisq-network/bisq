package io.bitsquare.p2p;

import com.google.protobuf.ByteString;
import io.bitsquare.common.crypto.SealedAndSigned;
import io.bitsquare.p2p.messaging.PrefixedSealedAndSignedMessage;
import io.bitsquare.p2p.network.messages.CloseConnectionMessage;
import io.bitsquare.common.wire.proto.Messages;
import io.bitsquare.p2p.peers.getdata.messages.GetDataRequest;
import io.bitsquare.p2p.peers.getdata.messages.GetDataResponse;
import io.bitsquare.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import io.bitsquare.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import io.bitsquare.p2p.peers.keepalive.messages.Ping;
import io.bitsquare.p2p.peers.keepalive.messages.Pong;
import io.bitsquare.p2p.peers.peerexchange.Peer;
import io.bitsquare.p2p.peers.peerexchange.messages.GetPeersRequest;
import io.bitsquare.p2p.peers.peerexchange.messages.GetPeersResponse;
import io.bitsquare.p2p.storage.P2PDataStorage;
import io.bitsquare.p2p.storage.messages.RefreshTTLMessage;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by mike on 31/01/2017.
 */
public class ProtoBufferUtilities {

    public static boolean isPrefixedSealedAndSignedMessage(Messages.Envelope envelope) {
        return !Messages.PrefixedSealedAndSignedMessage.getDefaultInstance()
                .equals(envelope.getPrefixedSealedAndSignedMessage());
    }

    public static PrefixedSealedAndSignedMessage createPrefixedSealedAndSignedMessage(Messages.Envelope envelope) {
        Messages.PrefixedSealedAndSignedMessage msg = envelope.getPrefixedSealedAndSignedMessage();
        NodeAddress nodeAddress = new NodeAddress(msg.getNodeAddress().getHostName(), msg.getNodeAddress().getPort());
        SealedAndSigned sealedAndSigned = new SealedAndSigned(msg.getSealedAndSigned().getEncryptedSecretKey().toByteArray(),
                msg.getSealedAndSigned().getEncryptedPayloadWithHmac().toByteArray(),
                msg.getSealedAndSigned().getSignature().toByteArray(), msg.getSealedAndSigned().getSigPublicKeyBytes().toByteArray());
        return new PrefixedSealedAndSignedMessage(nodeAddress, sealedAndSigned, msg.getAddressPrefixHash().toByteArray());
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
                                new ProtectedStorageEntry(null, new byte[]{}, 0, null)).collect(Collectors.toList()));
        return new GetDataResponse(set, envelope.getGetDataResponse().getRequestNonce(), envelope.getGetDataResponse().getIsGetUpdatedDataResponse());
    }


    // This is an interface, maybe not needed
    public static boolean isGetDataRequest(Messages.Envelope envelope) {
        return isGetUpdatedDataRequest(envelope) || isPreliminaryGetDataRequest(envelope);
    }

    public static boolean isPreliminaryGetDataRequest(Messages.Envelope envelope) {
        return !Messages.PreliminaryGetDataRequest.getDefaultInstance().equals(envelope.getPreliminaryGetDataRequest());
    }

    private static PreliminaryGetDataRequest createPreliminaryGetDataRequest(Messages.Envelope envelope) {
        return new PreliminaryGetDataRequest(envelope.getPreliminaryGetDataRequest().getNonce(),
                getByteSet(envelope.getPreliminaryGetDataRequest().getExcludedKeysList()));
    }


    public static boolean isGetUpdatedDataRequest(Messages.Envelope envelope) {
        return !Messages.GetUpdatedDataRequest.getDefaultInstance().equals(envelope.getGetUpdatedDataRequest());
    }

    private static GetUpdatedDataRequest createGetUpdatedDataRequest(Messages.Envelope envelope) {
        Messages.GetUpdatedDataRequest msg = envelope.getGetUpdatedDataRequest();
        NodeAddress nodeAddress = new NodeAddress(msg.getSenderNodeAddress().getHostName(), msg.getSenderNodeAddress().getPort());
        Set<byte[]> set = getByteSet(msg.getExcludedKeysList());
        return new GetUpdatedDataRequest(nodeAddress, msg.getNonce(), set);
    }

    public static boolean isGetPeersRequest(Messages.Envelope envelope) {
        return !Messages.GetPeersRequest.getDefaultInstance().equals(envelope.getGetPeersRequest());
    }

    private static GetPeersRequest createGetPeersRequest(Messages.Envelope envelope) {
        Messages.GetPeersRequest msg = envelope.getGetPeersRequest();
        NodeAddress nodeAddress = new NodeAddress(msg.getSenderNodeAddress().getHostName(), msg.getSenderNodeAddress().getPort());
        HashSet<Peer> set =new HashSet<>(
                msg.getReportedPeersList()
                        .stream()
                        .map(peer ->
                                new Peer(new NodeAddress(peer.getNodeAddress().getHostName(),
                                        peer.getNodeAddress().getPort()))).collect(Collectors.toList()));
        return new GetPeersRequest(nodeAddress, msg.getNonce(), set);
    }

    public static boolean isGetPeersResponse(Messages.Envelope envelope) {
        return !Messages.GetPeersResponse.getDefaultInstance().equals(envelope.getGetPeersResponse());
    }

    private static GetPeersResponse createGetPeersResponse(Messages.Envelope envelope) {
        Messages.GetPeersResponse msg = envelope.getGetPeersResponse();
        HashSet<Peer> set =new HashSet<>(
                msg.getReportedPeersList()
                        .stream()
                        .map(peer ->
                                new Peer(new NodeAddress(peer.getNodeAddress().getHostName(),
                                        peer.getNodeAddress().getPort()))).collect(Collectors.toList()));
        return new GetPeersResponse(msg.getRequestNonce(), set);
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

    private static Set<byte[]> getByteSet(List<ByteString> byteStringList) {
        return new HashSet<>(
                byteStringList
                        .stream()
                        .map(ByteString::toByteArray).collect(Collectors.toList()));
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
        } else if (isPrefixedSealedAndSignedMessage(envelope)) {
            return Optional.of(createPrefixedSealedAndSignedMessage(envelope));
        } else if (isGetUpdatedDataRequest(envelope)) {
            return Optional.of(createGetUpdatedDataRequest(envelope));
        }  else if (isPreliminaryGetDataRequest(envelope)) {
            return Optional.of(createPreliminaryGetDataRequest(envelope));
        } else if (isGetPeersRequest(envelope)) {
            return Optional.of(createGetPeersRequest(envelope));
        } else if (isGetPeersResponse(envelope)) {
            return Optional.of(createGetPeersResponse(envelope));
        }
        //envelope.
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

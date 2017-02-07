package io.bitsquare.p2p;

import com.google.protobuf.ByteString;
import io.bitsquare.app.Version;
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
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**

 If the Messages class is giving errors in IntelliJ, you should change the IntelliJ IDEA Platform Properties file,
 idea.properties, to something bigger like 12500:

 #---------------------------------------------------------------------
 # Maximum file size (kilobytes) IDE should provide code assistance for.
 # The larger file is the slower its editor works and higher overall system memory requirements are
 # if code assistance is enabled. Remove this property or set to very large number if you need
 # code assistance for any files available regardless their size.
 #---------------------------------------------------------------------
 idea.max.intellisense.filesize=2500

 */
@Slf4j
public class ProtoBufferUtilities {



    public static Optional<Message> fromProtoBuf(Messages.Envelope envelope) {
        Message result = null;
        switch(envelope.getMessageCase()) {
            case PING:
                result = new Ping(envelope.getPing().getNonce(), envelope.getPing().getLastRoundTripTime());
                break;
            case PONG:
                break;
            case REFRESH_TTL_MESSAGE: break;
            case GET_DATA_RESPONSE: break;
            case CLOSE_CONNECTION_MESSAGE: break;
            case PREFIXED_SEALED_AND_SIGNED_MESSAGE: break;
            case GET_UPDATED_DATA_REQUEST: break;
            case PRELIMINARY_GET_DATA_REQUEST: break;
            case GET_PEERS_REQUEST: break;
            case GET_PEERS_RESPONSE: break;
            default: log.warn("Unknown message case:{}", envelope.getMessageCase());
        }
        return Optional.ofNullable(result);
    }


    public static Messages.Envelope.Builder getBaseEnvelope() {
        return Messages.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);
    }



    public static PrefixedSealedAndSignedMessage createPrefixedSealedAndSignedMessage(Messages.Envelope envelope) {
        Messages.PrefixedSealedAndSignedMessage msg = envelope.getPrefixedSealedAndSignedMessage();
        NodeAddress nodeAddress = new NodeAddress(msg.getNodeAddress().getHostName(), msg.getNodeAddress().getPort());
        SealedAndSigned sealedAndSigned = new SealedAndSigned(msg.getSealedAndSigned().getEncryptedSecretKey().toByteArray(),
                msg.getSealedAndSigned().getEncryptedPayloadWithHmac().toByteArray(),
                msg.getSealedAndSigned().getSignature().toByteArray(), msg.getSealedAndSigned().getSigPublicKeyBytes().toByteArray());
        return new PrefixedSealedAndSignedMessage(nodeAddress, sealedAndSigned, msg.getAddressPrefixHash().toByteArray());
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



    private static GetUpdatedDataRequest createGetUpdatedDataRequest(Messages.Envelope envelope) {
        Messages.GetUpdatedDataRequest msg = envelope.getGetUpdatedDataRequest();
        NodeAddress nodeAddress = new NodeAddress(msg.getSenderNodeAddress().getHostName(), msg.getSenderNodeAddress().getPort());
        Set<byte[]> set = getByteSet(msg.getExcludedKeysList());
        return new GetUpdatedDataRequest(nodeAddress, msg.getNonce(), set);
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

    public static Ping createPing(Messages.Envelope envelope) {
        return new Ping(envelope.getPing().getNonce(), envelope.getPing().getLastRoundTripTime());
    }

    public static Pong createPong(Messages.Envelope envelope) {
        return new Pong(envelope.getPong().getRequestNonce());
    }

    public static RefreshTTLMessage createRefreshTTLMessage(Messages.Envelope envelope) {
        Messages.RefreshTTLMessage msg = envelope.getRefreshTtlMessage();
        return new RefreshTTLMessage(msg.getHashOfDataAndSeqNr().toByteArray(),
                msg.getSignature().toByteArray(),
                msg.getHashOfPayload().toByteArray(), msg.getSequenceNumber());
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

/*
    public static Messages.StoragePayload convertToStoragePayload(StoragePayload storagePayload) {
        Messages.StoragePayload.Builder builder = Messages.StoragePayload.newBuilder();
        switch(storagePayload.getClass().getSimpleName()) {
            case "Alert": builder.setAlert((Alert)storagePayload);

        }


    }
*/

}

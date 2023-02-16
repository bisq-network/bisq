package bisq.core.support;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.PersistableEnvelope;

import com.google.protobuf.Message;

import javax.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * We store only the payload in the PB file to save disc space. The hash of the payload can be created anyway and
 * is only used as key in the map. So we have a hybrid data structure which is represented as list in the protobuffer
 * definition and provide a hashMap for the domain access.
 */
@Slf4j
public class DelayedPayoutRecoveryStore implements PersistableEnvelope {
    @Getter
    private final Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> map = new ConcurrentHashMap<>();

    @Inject
    DelayedPayoutRecoveryStore() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DelayedPayoutRecoveryStore(List<ProtectedStorageEntry> list) {
        list.forEach(entry -> map.put(P2PDataStorage.get32ByteHashAsByteArray(entry.getProtectedStoragePayload()), entry));
    }

    public Message toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setDelayedPayoutRecoveryStore(getBuilder())
                .build();
    }

    private protobuf.DelayedPayoutRecoveryStore.Builder getBuilder() {
        final List<protobuf.ProtectedStorageEntry> protoList = map.values().stream()
                .map(ProtectedStorageEntry::toProtectedStorageEntry)
                .collect(Collectors.toList());
        return protobuf.DelayedPayoutRecoveryStore.newBuilder().addAllItems(protoList);
    }

    public static DelayedPayoutRecoveryStore fromProto(protobuf.DelayedPayoutRecoveryStore proto, NetworkProtoResolver networkProtoResolver) {
        List<ProtectedStorageEntry> list = proto.getItemsList().stream()
                .map(entry -> ProtectedStorageEntry.fromProto(entry, networkProtoResolver))
                .collect(Collectors.toList());
        return new DelayedPayoutRecoveryStore(list);
    }

    public boolean containsKey(P2PDataStorage.ByteArray hash) {
        return map.containsKey(hash);
    }
}

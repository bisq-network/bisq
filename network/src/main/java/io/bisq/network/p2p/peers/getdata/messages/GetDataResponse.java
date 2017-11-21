package io.bisq.network.p2p.peers.getdata.messages;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.ExtendedDataSizePermission;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;
import io.bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import io.bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class GetDataResponse extends NetworkEnvelope implements SupportedCapabilitiesMessage, ExtendedDataSizePermission {
    // Set of ProtectedStorageEntry objects
    private final Set<ProtectedStorageEntry> dataSet;

    // Set of PersistableNetworkPayload objects
    // We added that in v 0.6 and we would get a null object from older peers, so keep it annotated with @Nullable
    @Nullable
    private final Set<PersistableNetworkPayload> persistableNetworkPayloadSet;

    private final int requestNonce;
    private final boolean isGetUpdatedDataResponse;
    @Nullable
    private final List<Integer> supportedCapabilities;

    public GetDataResponse(Set<ProtectedStorageEntry> dataSet,
                           @Nullable Set<PersistableNetworkPayload> persistableNetworkPayloadSet,
                           int requestNonce,
                           boolean isGetUpdatedDataResponse) {
        this(dataSet,
                persistableNetworkPayloadSet,
                requestNonce,
                isGetUpdatedDataResponse,
                Capabilities.getSupportedCapabilities(),
                Version.getP2PMessageVersion());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetDataResponse(Set<ProtectedStorageEntry> dataSet,
                            @Nullable Set<PersistableNetworkPayload> persistableNetworkPayloadSet,
                            int requestNonce,
                            boolean isGetUpdatedDataResponse,
                            @Nullable List<Integer> supportedCapabilities,
                            int messageVersion) {
        super(messageVersion);

        this.dataSet = dataSet;
        this.persistableNetworkPayloadSet = persistableNetworkPayloadSet;
        this.requestNonce = requestNonce;
        this.isGetUpdatedDataResponse = isGetUpdatedDataResponse;
        this.supportedCapabilities = supportedCapabilities;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        final PB.GetDataResponse.Builder builder = PB.GetDataResponse.newBuilder()
                .addAllDataSet(dataSet.stream()
                        .map(protectedStorageEntry -> protectedStorageEntry instanceof ProtectedMailboxStorageEntry ?
                                PB.StorageEntryWrapper.newBuilder()
                                        .setProtectedMailboxStorageEntry((PB.ProtectedMailboxStorageEntry) protectedStorageEntry.toProtoMessage())
                                        .build()
                                :
                                PB.StorageEntryWrapper.newBuilder()
                                        .setProtectedStorageEntry((PB.ProtectedStorageEntry) protectedStorageEntry.toProtoMessage())
                                        .build())
                        .collect(Collectors.toList()))
                .setRequestNonce(requestNonce)
                .setIsGetUpdatedDataResponse(isGetUpdatedDataResponse);

        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(supportedCapabilities));
        Optional.ofNullable(persistableNetworkPayloadSet).ifPresent(set -> builder.addAllPersistableNetworkPayloadItems(set.stream()
                .map(PersistableNetworkPayload::toProtoMessage)
                .collect(Collectors.toList())));

        return getNetworkEnvelopeBuilder()
                .setGetDataResponse(builder)
                .build();
    }

    public static GetDataResponse fromProto(PB.GetDataResponse proto, NetworkProtoResolver resolver, int messageVersion) {
        Set<ProtectedStorageEntry> dataSet = new HashSet<>(
                proto.getDataSetList().stream()
                        .map(entry -> (ProtectedStorageEntry) resolver.fromProto(entry))
                        .collect(Collectors.toSet()));

        Set<PersistableNetworkPayload> persistableNetworkPayloadSet = proto.getPersistableNetworkPayloadItemsList().isEmpty() ?
                null :
                new HashSet<>(
                        proto.getPersistableNetworkPayloadItemsList().stream()
                                .map(e -> (PersistableNetworkPayload) resolver.fromProto(e))
                                .collect(Collectors.toSet()));

        //PersistableNetworkPayload
        return new GetDataResponse(dataSet,
                persistableNetworkPayloadSet,
                proto.getRequestNonce(),
                proto.getIsGetUpdatedDataResponse(),
                proto.getSupportedCapabilitiesList().isEmpty() ? null : proto.getSupportedCapabilitiesList(),
                messageVersion);
    }
}

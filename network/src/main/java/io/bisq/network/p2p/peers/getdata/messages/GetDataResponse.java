package io.bisq.network.p2p.peers.getdata.messages;

import io.bisq.common.app.Capabilities;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.ExtendedDataSizePermission;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;
import io.bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import lombok.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

@Value
public final class GetDataResponse implements SupportedCapabilitiesMessage, ExtendedDataSizePermission {
    private final HashSet<ProtectedStorageEntry> dataSet;
    private final int requestNonce;
    private final boolean isGetUpdatedDataResponse;
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public GetDataResponse(HashSet<ProtectedStorageEntry> dataSet, int requestNonce, boolean isGetUpdatedDataResponse) {
        this.dataSet = dataSet;
        this.requestNonce = requestNonce;
        this.isGetUpdatedDataResponse = isGetUpdatedDataResponse;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setGetDataResponse(PB.GetDataResponse.newBuilder()
                        .addAllDataSet(dataSet.stream()
                                .map(protectedStorageEntry -> {
                                    return protectedStorageEntry instanceof ProtectedMailboxStorageEntry ?
                                            PB.StorageEntryWrapper.newBuilder()
                                                    .setProtectedMailboxStorageEntry((PB.ProtectedMailboxStorageEntry) protectedStorageEntry.toProtoMessage())
                                                    .build()
                                            :
                                            PB.StorageEntryWrapper.newBuilder()
                                                    .setProtectedStorageEntry((PB.ProtectedStorageEntry) protectedStorageEntry.toProtoMessage())
                                                    .build();
                                })
                                .collect(Collectors.toList()))
                        .setRequestNonce(requestNonce)
                        .setIsGetUpdatedDataResponse(isGetUpdatedDataResponse)
                        .addAllSupportedCapabilities(supportedCapabilities))
                .build();
    }

    public static GetDataResponse fromProto(PB.GetDataResponse proto, NetworkProtoResolver resolver) {
        HashSet<ProtectedStorageEntry> dataSet = new HashSet<>(
                proto.getDataSetList().stream()
                        .map(entry -> (ProtectedStorageEntry) resolver.fromProto(entry))
                        .collect(Collectors.toSet()));
        return new GetDataResponse(dataSet,
                proto.getRequestNonce(),
                proto.getIsGetUpdatedDataResponse());
    }
}

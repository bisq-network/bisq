package io.bisq.network.p2p.peers.getdata.messages;

import io.bisq.common.app.Capabilities;
import io.bisq.common.network.NetworkEnvelope;
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
        PB.GetDataResponse.Builder builder = PB.GetDataResponse.newBuilder();
        builder.addAllDataSet(
                dataSet.stream()
                        .map(protectedStorageEntry -> {
                            PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.Builder builder1 =
                                    PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.newBuilder();
                            if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry) {
                                builder1.setProtectedMailboxStorageEntry((PB.ProtectedMailboxStorageEntry) protectedStorageEntry.toProtoMessage());
                            } else {
                                builder1.setProtectedStorageEntry((PB.ProtectedStorageEntry) protectedStorageEntry.toProtoMessage());
                            }
                            return builder1.build();
                        })
                        .collect(Collectors.toList()))
                .setRequestNonce(requestNonce)
                .setIsGetUpdatedDataResponse(isGetUpdatedDataResponse);
        return NetworkEnvelope.getDefaultBuilder().setGetDataResponse(builder).build();
    }
}

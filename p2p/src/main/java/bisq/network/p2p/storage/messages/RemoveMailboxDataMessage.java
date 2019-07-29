/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.storage.messages;

import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkProtoResolver;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class RemoveMailboxDataMessage extends BroadcastMessage {
    private final ProtectedMailboxStorageEntry protectedMailboxStorageEntry;

    public RemoveMailboxDataMessage(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        this(protectedMailboxStorageEntry, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RemoveMailboxDataMessage(ProtectedMailboxStorageEntry protectedMailboxStorageEntry,
                                     int messageVersion) {
        super(messageVersion);
        this.protectedMailboxStorageEntry = protectedMailboxStorageEntry;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setRemoveMailboxDataMessage(protobuf.RemoveMailboxDataMessage.newBuilder()
                        .setProtectedStorageEntry(protectedMailboxStorageEntry.toProtoMessage()))
                .build();
    }

    public static RemoveMailboxDataMessage fromProto(protobuf.RemoveMailboxDataMessage proto, NetworkProtoResolver resolver, int messageVersion) {
        return new RemoveMailboxDataMessage(ProtectedMailboxStorageEntry.fromProto(proto.getProtectedStorageEntry(), resolver), messageVersion);
    }
}

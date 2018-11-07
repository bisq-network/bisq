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
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkProtoResolver;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class AddDataMessage extends BroadcastMessage {
    private final ProtectedStorageEntry protectedStorageEntry;

    public AddDataMessage(ProtectedStorageEntry protectedStorageEntry) {
        this(protectedStorageEntry, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AddDataMessage(ProtectedStorageEntry protectedStorageEntry, int messageVersion) {
        super(messageVersion);
        this.protectedStorageEntry = protectedStorageEntry;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.StorageEntryWrapper.Builder builder = PB.StorageEntryWrapper.newBuilder();
        final Message message = protectedStorageEntry.toProtoMessage();
        if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
            builder.setProtectedMailboxStorageEntry((PB.ProtectedMailboxStorageEntry) message);
        else
            builder.setProtectedStorageEntry((PB.ProtectedStorageEntry) message);

        return getNetworkEnvelopeBuilder()
                .setAddDataMessage(PB.AddDataMessage.newBuilder()
                        .setEntry(builder))
                .build();
    }

    public static AddDataMessage fromProto(PB.AddDataMessage proto, NetworkProtoResolver resolver, int messageVersion) {
        return new AddDataMessage((ProtectedStorageEntry) resolver.fromProto(proto.getEntry()), messageVersion);
    }
}

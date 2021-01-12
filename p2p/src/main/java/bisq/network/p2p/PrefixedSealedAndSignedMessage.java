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

package bisq.network.p2p;

import bisq.network.p2p.mailbox.MailboxMessage;

import bisq.common.app.Version;
import bisq.common.crypto.SealedAndSigned;
import bisq.common.proto.network.NetworkEnvelope;

import com.google.protobuf.ByteString;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import lombok.EqualsAndHashCode;
import lombok.Value;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(callSuper = true)
@Value
public final class PrefixedSealedAndSignedMessage extends NetworkEnvelope implements MailboxMessage, SendersNodeAddressMessage {
    public static final long TTL = TimeUnit.DAYS.toMillis(15);

    private final NodeAddress senderNodeAddress;
    private final SealedAndSigned sealedAndSigned;

    // From v1.4.0 on addressPrefixHash can be an empty byte array.
    // We cannot make it nullable as not updated nodes would get a nullPointer exception at protobuf serialisation.
    private final byte[] addressPrefixHash;

    private final String uid;

    public PrefixedSealedAndSignedMessage(NodeAddress senderNodeAddress, SealedAndSigned sealedAndSigned) {
        this(senderNodeAddress,
                sealedAndSigned,
                new byte[0],
                UUID.randomUUID().toString(),
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PrefixedSealedAndSignedMessage(NodeAddress senderNodeAddress,
                                           SealedAndSigned sealedAndSigned,
                                           byte[] addressPrefixHash,
                                           String uid,
                                           int messageVersion) {
        super(messageVersion);
        this.senderNodeAddress = checkNotNull(senderNodeAddress, "senderNodeAddress must not be null");
        this.sealedAndSigned = sealedAndSigned;
        this.addressPrefixHash = addressPrefixHash;
        this.uid = uid;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setPrefixedSealedAndSignedMessage(protobuf.PrefixedSealedAndSignedMessage.newBuilder()
                        .setNodeAddress(senderNodeAddress.toProtoMessage())
                        .setSealedAndSigned(sealedAndSigned.toProtoMessage())
                        .setAddressPrefixHash(ByteString.copyFrom(addressPrefixHash))
                        .setUid(uid))
                .build();
    }

    public static PrefixedSealedAndSignedMessage fromProto(protobuf.PrefixedSealedAndSignedMessage proto,
                                                           int messageVersion) {
        return new PrefixedSealedAndSignedMessage(NodeAddress.fromProto(proto.getNodeAddress()),
                SealedAndSigned.fromProto(proto.getSealedAndSigned()),
                proto.getAddressPrefixHash().toByteArray(),
                proto.getUid(),
                messageVersion);
    }

    public static PrefixedSealedAndSignedMessage fromPayloadProto(protobuf.PrefixedSealedAndSignedMessage proto) {
        // We have the case that an envelope got wrapped into a payload.
        // We don't check the message version here as it was checked in the carrier envelope already (in connection class)
        // Payloads dont have a message version and are also used for persistence
        // We set the value to -1 to indicate it is set but irrelevant
        return new PrefixedSealedAndSignedMessage(NodeAddress.fromProto(proto.getNodeAddress()),
                SealedAndSigned.fromProto(proto.getSealedAndSigned()),
                proto.getAddressPrefixHash().toByteArray(),
                proto.getUid(),
                -1);
    }

    @Override
    public long getTTL() {
        return TTL;
    }
}

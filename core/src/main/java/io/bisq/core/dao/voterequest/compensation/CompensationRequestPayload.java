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

package io.bisq.core.dao.voterequest.compensation;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.common.util.JsonExclude;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import io.bisq.network.p2p.storage.payload.LazyProcessedPayload;
import io.bisq.network.p2p.storage.payload.PersistableProtectedPayload;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Utils;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Payload sent over wire as well as it gets persisted, containing all base data for a compensation request
 */
@Slf4j
@Data
public final class CompensationRequestPayload implements LazyProcessedPayload, PersistableProtectedPayload, CapabilityRequiringPayload {
    private final String uid;
    private final String name;
    private final String title;
    private final String description;
    private final String link;
    private final long requestedBsq;
    private final String bsqAddress;
    private final String nodeAddress;
    // used for json
    private String ownerPubPubKeyAsHex;
    // Signature of the JSON data of this object excluding the signature and feeTxId fields using the standard Bitcoin
    // messaging signing format as a base64 encoded string.
    @JsonExclude
    private String signature;
    // Set after we signed and set the hash. The hash is used in the OP_RETURN of the fee tx
    @JsonExclude
    private String txId;

    private final byte version;
    private final long creationDate;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    // Used just for caching
    @JsonExclude
    @Nullable
    private transient PublicKey ownerPubKey;

    public CompensationRequestPayload(String uid,
                                      String name,
                                      String title,
                                      String description,
                                      String link,
                                      Coin requestedBsq,
                                      String bsqAddress,
                                      NodeAddress nodeAddress,
                                      PublicKey ownerPubKey,
                                      Date creationDate) {
        this(uid,
                name,
                title,
                description,
                link,
                requestedBsq.value,
                bsqAddress,
                nodeAddress.getFullAddress(),
                Utils.HEX.encode(ownerPubKey.getEncoded()),
                Version.COMPENSATION_REQUEST_VERSION,
                creationDate.getTime(),
                null,
                null,
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CompensationRequestPayload(String uid,
                                       String name,
                                       String title,
                                       String description,
                                       String link,
                                       long requestedBsq,
                                       String bsqAddress,
                                       String nodeAddress,
                                       String ownerPubPubKeyAsHex,
                                       byte version,
                                       long creationDate,
                                       String signature,
                                       String txId,
                                       @Nullable Map<String, String> extraDataMap) {
        this.uid = uid;
        this.name = name;
        this.title = title;
        this.description = description;
        this.link = link;
        this.requestedBsq = requestedBsq;
        this.bsqAddress = bsqAddress;
        this.nodeAddress = nodeAddress;
        this.ownerPubPubKeyAsHex = ownerPubPubKeyAsHex;
        this.version = version;
        this.creationDate = creationDate;
        this.signature = signature;
        this.txId = txId;
        this.extraDataMap = extraDataMap;
    }

    public PB.CompensationRequestPayload.Builder getCompensationRequestPayloadBuilder() {
        final PB.CompensationRequestPayload.Builder builder = PB.CompensationRequestPayload.newBuilder()
                .setUid(uid)
                .setName(name)
                .setTitle(title)
                .setDescription(description)
                .setLink(link)
                .setRequestedBsq(requestedBsq)
                .setBsqAddress(bsqAddress)
                .setNodeAddress(nodeAddress)
                .setOwnerPubKeyAsHex(ownerPubPubKeyAsHex)
                .setVersion(version)
                .setCreationDate(creationDate)
                .setSignature(signature)
                .setTxId(txId);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder;
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        return PB.StoragePayload.newBuilder().setCompensationRequestPayload(getCompensationRequestPayloadBuilder()).build();
    }

    public static CompensationRequestPayload fromProto(PB.CompensationRequestPayload proto) {
        return new CompensationRequestPayload(
                proto.getUid(),
                proto.getName(),
                proto.getTitle(),
                proto.getDescription(),
                proto.getLink(),
                proto.getRequestedBsq(),
                proto.getBsqAddress(),
                proto.getNodeAddress(),
                proto.getOwnerPubKeyAsHex(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getSignature(),
                proto.getTxId(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO not needed?
    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(30);
    }

    @Override
    public PublicKey getOwnerPubKey() {
        if (ownerPubKey == null)
            ownerPubKey = Sig.getPublicKeyFromBytes(Utils.HEX.decode(ownerPubPubKeyAsHex));
        return ownerPubKey;
    }

    // Pre 0.6 version don't know the new message type and throw an error which leads to disconnecting the peer.
    @Override
    public List<Integer> getRequiredCapabilities() {
        return new ArrayList<>(Collections.singletonList(
                Capabilities.Capability.COMP_REQUEST.ordinal()
        ));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Date getCreationDate() {
        return new Date(creationDate);
    }

    public Coin getRequestedBsq() {
        return Coin.valueOf(requestedBsq);
    }

    public NodeAddress getNodeAddress() {
        return new NodeAddress(nodeAddress);
    }

    public String getShortId() {
        return uid.substring(0, 8);
    }

    @Override
    public String toString() {
        return "CompensationRequestPayload{" +
                "\n     uid='" + uid + '\'' +
                ",\n     name='" + name + '\'' +
                ",\n     title='" + title + '\'' +
                ",\n     description='" + description + '\'' +
                ",\n     link='" + link + '\'' +
                ",\n     requestedBsq=" + requestedBsq +
                ",\n     bsqAddress='" + bsqAddress + '\'' +
                ",\n     nodeAddress='" + nodeAddress + '\'' +
                ",\n     ownerPubPubKeyAsHex='" + ownerPubPubKeyAsHex + '\'' +
                ",\n     signature='" + signature + '\'' +
                ",\n     txId='" + txId + '\'' +
                ",\n     version=" + version +
                ",\n     creationDate=" + creationDate +
                ",\n     extraDataMap=" + extraDataMap +
                "\n}";
    }
}

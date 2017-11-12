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

package io.bisq.core.dao.compensation;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.common.util.JsonExclude;
import io.bisq.common.util.Utilities;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.storage.payload.LazyProcessedPayload;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Utils;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Data
public final class CompensationRequestPayload implements LazyProcessedPayload, ProtectedStoragePayload, PersistablePayload, PersistableEnvelope {
    private final String uid;
    private final String name;
    private final String title;
    private final String category;
    private final String description;
    private final String link;
    private final long startDate;
    private final long endDate;
    private final long requestedBtc;
    private final String btcAddress;
    private final String nodeAddress;
    @JsonExclude
    private final byte[] ownerPubKeyBytes;
    // used for json
    private String ownerPubPubKeyAsHex;
    // Signature of the JSON data of this object excluding the signature and feeTxId fields using the standard Bitcoin
    // messaging signing format as a base64 encoded string.
    @JsonExclude
    private String signature;
    // Set after we signed and set the hash. The hash is used in the OP_RETURN of the fee tx
    @JsonExclude
    private String feeTxId;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    private final byte version;
    private final long creationDate;

    @JsonExclude
    private transient PublicKey ownerPubKey;

    public CompensationRequestPayload(String uid,
                                      String name,
                                      String title,
                                      String category,
                                      String description,
                                      String link,
                                      Date startDate,
                                      Date endDate,
                                      Coin requestedBtc,
                                      String btcAddress,
                                      NodeAddress nodeAddress,
                                      PublicKey ownerPubKey) {
        this(uid,
                name,
                title,
                category,
                description,
                link,
                startDate,
                endDate,
                requestedBtc,
                btcAddress,
                nodeAddress.getFullAddress(),
                Sig.getPublicKeyBytes(ownerPubKey),
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CompensationRequestPayload(String uid,
                                       String name,
                                       String title,
                                       String category,
                                       String description,
                                       String link,
                                       Date startDate,
                                       Date endDate,
                                       Coin requestedBtc,
                                       String btcAddress,
                                       String nodeAddress,
                                       byte[] ownerPubKeyBytes,
                                       @Nullable Map<String, String> extraDataMap) {
        this.uid = uid;
        this.name = name;
        this.title = title;
        this.category = category;
        this.description = description;
        this.link = link;
        this.startDate = startDate.getTime();
        this.endDate = endDate.getTime();
        this.requestedBtc = requestedBtc.value;
        this.btcAddress = btcAddress;
        this.nodeAddress = nodeAddress;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.extraDataMap = extraDataMap;

        version = Version.COMPENSATION_REQUEST_VERSION;
        creationDate = new Date().getTime();
        this.ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyBytes);
        ownerPubPubKeyAsHex = Utils.HEX.encode(this.ownerPubKey.getEncoded());
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        final PB.CompensationRequestPayload.Builder builder = PB.CompensationRequestPayload.newBuilder()
                .setUid(uid)
                .setName(name)
                .setTitle(title)
                .setCategory(category)
                .setDescription(description)
                .setLink(link)
                .setStartDate(startDate)
                .setEndDate(endDate)
                .setRequestedBtc(requestedBtc)
                .setBtcAddress(btcAddress)
                .setNodeAddress(nodeAddress)
                .setOwnerPubKeyAsHex(ownerPubPubKeyAsHex)
                .setSignature(signature)
                .setFeeTxId(feeTxId)
                .setVersion(version)
                .setCreationDate(creationDate);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return PB.StoragePayload.newBuilder().setCompensationRequestPayload(builder).build();
    }

    public static CompensationRequestPayload fromProto(PB.CompensationRequestPayload proto) {
        return new CompensationRequestPayload(proto.getUid(),
                proto.getName(),
                proto.getTitle(),
                proto.getCategory(),
                proto.getDescription(), proto.getLink(),
                new Date(proto.getStartDate()),
                new Date(proto.getEndDate()),
                Coin.valueOf(proto.getRequestedBtc()),
                proto.getBtcAddress(),
                proto.getNodeAddress(), proto.getOwnerPubKeyBytes().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(30);
    }

    public Date getStartDate() {
        return new Date(startDate);
    }

    public Date getEndDate() {
        return new Date(endDate);
    }

    public Date getCreationDate() {
        return new Date(creationDate);
    }

    public Coin getRequestedBtc() {
        return Coin.valueOf(requestedBtc);
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
                "version=" + version +
                ", creationDate=" + getCreationDate() +
                ", uid='" + uid + '\'' +
                ", name='" + name + '\'' +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", description='" + description + '\'' +
                ", link='" + link + '\'' +
                ", startDate=" + getStartDate() +
                ", endDate=" + getEndDate() +
                ", requestedBtc=" + requestedBtc +
                ", btcAddress='" + btcAddress + '\'' +
                ", nodeAddress='" + getNodeAddress() + '\'' +
                ", p2pStorageSignaturePubKeyBytes=" + Utilities.bytesAsHexString(ownerPubKeyBytes) +
                ", p2pStorageSignaturePubKeyAsHex='" + ownerPubPubKeyAsHex + '\'' +
                ", signature='" + signature + '\'' +
                ", feeTxId='" + feeTxId + '\'' +
                ", extraDataMap=" + extraDataMap +
                '}';
    }
}

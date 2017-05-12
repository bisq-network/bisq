/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.compensation;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.common.persistable.PersistableEnvelope;
import io.bisq.common.util.JsonExclude;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.storage.payload.LazyProcessedStoragePayload;
import io.bisq.network.p2p.storage.payload.PersistedStoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Utils;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@EqualsAndHashCode
@Slf4j
@Getter
@Setter
// TODO There will be another object for PersistableEnvelope
public final class CompensationRequestPayload implements LazyProcessedStoragePayload, PersistedStoragePayload, PersistableEnvelope {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    public static final long TTL = TimeUnit.DAYS.toMillis(30);

    // Payload
    private final byte version;
    private final long creationDate;
    private final String uid;
    private final String name;
    private final String title;
    private final String category;
    private final String description;
    private final String link;
    public final long startDate;
    public final long endDate;
    private final long requestedBtc;
    private final String btcAddress;
    private final String nodeAddress;
    @JsonExclude
    private final byte[] p2pStorageSignaturePubKeyBytes;
    // used for json
    private String p2pStorageSignaturePubKeyAsHex;
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


    // Domain
    @JsonExclude
    private transient PublicKey p2pStorageSignaturePubKey;

    // Called from domain
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
                                      PublicKey p2pStorageSignaturePubKey) {
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
                new X509EncodedKeySpec(p2pStorageSignaturePubKey.getEncoded()).getEncoded(),
                null);
    }

    // Called from PB
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
                                      String nodeAddress,
                                      byte[] p2pStorageSignaturePubKeyBytes,
                                      @Nullable Map<String, String> extraDataMap) {

        version = Version.COMPENSATION_REQUEST_VERSION;
        creationDate = new Date().getTime();

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
        this.p2pStorageSignaturePubKeyBytes = p2pStorageSignaturePubKeyBytes;

        this.extraDataMap = extraDataMap;
        init();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            init();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    private void init() {
        try {
            p2pStorageSignaturePubKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC")
                    .generatePublic(new X509EncodedKeySpec(p2pStorageSignaturePubKeyBytes));
            this.p2pStorageSignaturePubKeyAsHex = Utils.HEX.encode(p2pStorageSignaturePubKey.getEncoded());
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("Couldn't create the p2p storage public key", e);
        }
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return p2pStorageSignaturePubKey;
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
    public PB.StoragePayload toProtoMessage() {
        final PB.CompensationRequestPayload.Builder builder = PB.CompensationRequestPayload.newBuilder()
                .setVersion(version)
                .setCreationDate(creationDate)
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
                .setP2PStorageSignaturePubKeyAsHex(p2pStorageSignaturePubKeyAsHex)
                .setSignature(signature)
                .setFeeTxId(feeTxId);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraDataMap);
        return PB.StoragePayload.newBuilder().setCompensationRequestPayload(builder).build();
    }

    public static CompensationRequestPayload fromProto(PB.CompensationRequestPayload proto) {
        return new CompensationRequestPayload(proto.getUid(), proto.getName(), proto.getTitle(), proto.getCategory(),
                proto.getDescription(), proto.getLink(), new Date(proto.getStartDate()), new Date(proto.getEndDate()),
                Coin.valueOf(proto.getRequestedBtc()), proto.getBtcAddress(),
                proto.getNodeAddress(), proto.getP2PStorageSignaturePubKeyBytes().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMapMap()) ? null : proto.getExtraDataMapMap());
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
                ", p2pStorageSignaturePubKeyBytes=" + Hex.toHexString(p2pStorageSignaturePubKeyBytes) +
                ", p2pStorageSignaturePubKeyAsHex='" + p2pStorageSignaturePubKeyAsHex + '\'' +
                ", signature='" + signature + '\'' +
                ", feeTxId='" + feeTxId + '\'' +
                ", extraDataMap=" + extraDataMap +
                '}';
    }
}

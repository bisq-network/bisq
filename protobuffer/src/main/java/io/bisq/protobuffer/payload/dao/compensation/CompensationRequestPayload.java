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

package io.bisq.protobuffer.payload.dao.compensation;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.common.util.JsonExclude;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.payload.LazyProcessedStoragePayload;
import io.bisq.protobuffer.payload.PersistedStoragePayload;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Utils;
import org.bouncycastle.util.encoders.Hex;

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
public final class CompensationRequestPayload implements LazyProcessedStoragePayload, PersistedStoragePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    public static final long TTL = TimeUnit.DAYS.toMillis(30);

    // Payload
    public final byte version;
    private final long creationDate;
    public final String uid;
    public final String name;
    public final String title;
    public final String category;
    public final String description;
    public final String link;
    public final long startDate;
    public final long endDate;
    private final long requestedBtc;
    public final String btcAddress;
    private final String nodeAddress;
    @JsonExclude
    private final byte[] p2pStorageSignaturePubKeyBytes;
    // used for json
    private String p2pStorageSignaturePubKeyAsHex;
    // Signature of the JSON data of this object excluding the signature and feeTxId fields using the standard Bitcoin
    // messaging signing format as a base64 encoded string.
    @JsonExclude
    public String signature;
    // Set after we signed and set the hash. The hash is used in the OP_RETURN of the fee tx
    @JsonExclude
    public String feeTxId;
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
                nodeAddress,
                new X509EncodedKeySpec(p2pStorageSignaturePubKey.getEncoded()).getEncoded(),
                null);

        init();
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
                                      NodeAddress nodeAddress,
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
        this.nodeAddress = nodeAddress.getFullAddress();
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

    public void setSignature(String signature) {
        this.signature = signature;
    }

    // Called after tx is published
    public void setFeeTxId(String feeTxId) {
        this.feeTxId = feeTxId;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return p2pStorageSignaturePubKey;
    }

    @Nullable
    @Override
    public Map<String, String> getExtraDataMap() {
        return extraDataMap;
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
    public PB.StoragePayload toProto() {
        final PB.CompensationRequestPayload.Builder builder = PB.CompensationRequestPayload.newBuilder()
                .setTTL(TTL)
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

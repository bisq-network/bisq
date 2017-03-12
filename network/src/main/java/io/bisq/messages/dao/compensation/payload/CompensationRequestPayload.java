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

package io.bisq.messages.dao.compensation.payload;

import io.bisq.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.common.util.JsonExclude;
import io.bisq.common.wire.proto.Messages;
import io.bisq.p2p.NodeAddress;
import io.bisq.p2p.storage.payload.LazyProcessedStoragePayload;
import io.bisq.p2p.storage.payload.PersistedStoragePayload;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;

// The data of the CompensationRequest
public final class CompensationRequestPayload implements LazyProcessedStoragePayload, PersistedStoragePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(CompensationRequestPayload.class);

    public static final long TTL = TimeUnit.DAYS.toMillis(30);

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
    private transient PublicKey p2pStorageSignaturePubKey;
    @JsonExclude
    private final byte[] p2pStorageSignaturePubKeyBytes;

    // used for json
    private final String p2pStorageSignaturePubKeyAsHex;

    // Signature of the JSON data of this object excluding the signature and feeTxId fields using the standard Bitcoin
    // messaging signing format as a base64 encoded string.
    @JsonExclude
    public String signature;

    // Set after we signed and set the hash. The hash is used in the OP_RETURN of the fee tx
    @JsonExclude
    public String feeTxId;

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
        this.p2pStorageSignaturePubKey = p2pStorageSignaturePubKey;
        this.p2pStorageSignaturePubKeyBytes = new X509EncodedKeySpec(p2pStorageSignaturePubKey.getEncoded()).getEncoded();
        p2pStorageSignaturePubKeyAsHex = Utils.HEX.encode(p2pStorageSignaturePubKey.getEncoded());
        init();
    }

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
                                      byte[] p2pStorageSignaturePubKeyBytes) {

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
        this.p2pStorageSignaturePubKey = p2pStorageSignaturePubKey;
        this.p2pStorageSignaturePubKeyBytes = p2pStorageSignaturePubKeyBytes;
        init();
        this.p2pStorageSignaturePubKeyAsHex = Utils.HEX.encode(p2pStorageSignaturePubKey.getEncoded());
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
            p2pStorageSignaturePubKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(p2pStorageSignaturePubKeyBytes));
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
    public Messages.StoragePayload toProtoBuf() {
        return Messages.StoragePayload.newBuilder().setCompensationRequestPayload(
                Messages.CompensationRequestPayload.newBuilder()
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
                        .setFeeTxId(feeTxId)).build();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CompensationRequestPayload that = (CompensationRequestPayload) o;

        if (version != that.version) return false;
        if (startDate != that.startDate) return false;
        if (endDate != that.endDate) return false;
        if (requestedBtc != that.requestedBtc) return false;
        if (creationDate != that.creationDate) return false;
        if (uid != null ? !uid.equals(that.uid) : that.uid != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (title != null ? !title.equals(that.title) : that.title != null) return false;
        if (category != null ? !category.equals(that.category) : that.category != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (link != null ? !link.equals(that.link) : that.link != null) return false;
        if (btcAddress != null ? !btcAddress.equals(that.btcAddress) : that.btcAddress != null) return false;
        if (nodeAddress != null ? !nodeAddress.equals(that.nodeAddress) : that.nodeAddress != null) return false;
        if (p2pStorageSignaturePubKey != null ? !p2pStorageSignaturePubKey.equals(that.p2pStorageSignaturePubKey) : that.p2pStorageSignaturePubKey != null)
            return false;
        if (p2pStorageSignaturePubKeyAsHex != null ? !p2pStorageSignaturePubKeyAsHex.equals(that.p2pStorageSignaturePubKeyAsHex) : that.p2pStorageSignaturePubKeyAsHex != null)
            return false;
        if (signature != null ? !signature.equals(that.signature) : that.signature != null) return false;
        return !(feeTxId != null ? !feeTxId.equals(that.feeTxId) : that.feeTxId != null);

    }

    @Override
    public int hashCode() {
        int result = (int) version;
        result = 31 * result + (uid != null ? uid.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (link != null ? link.hashCode() : 0);
        result = 31 * result + (int) (startDate ^ (startDate >>> 32));
        result = 31 * result + (int) (endDate ^ (endDate >>> 32));
        result = 31 * result + (int) (requestedBtc ^ (requestedBtc >>> 32));
        result = 31 * result + (btcAddress != null ? btcAddress.hashCode() : 0);
        result = 31 * result + (nodeAddress != null ? nodeAddress.hashCode() : 0);
        result = 31 * result + (int) (creationDate ^ (creationDate >>> 32));
        result = 31 * result + (p2pStorageSignaturePubKey != null ? p2pStorageSignaturePubKey.hashCode() : 0);
        result = 31 * result + (p2pStorageSignaturePubKeyAsHex != null ? p2pStorageSignaturePubKeyAsHex.hashCode() : 0);
        result = 31 * result + (signature != null ? signature.hashCode() : 0);
        result = 31 * result + (feeTxId != null ? feeTxId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CompensationRequestPayload{" +
                "version=" + version +
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
                ", creationDate=" + getCreationDate() +
                ", p2pStorageSignaturePubKeyAsHex='" + p2pStorageSignaturePubKeyAsHex + '\'' +
                ", signature='" + signature + '\'' +
                ", feeTxId='" + feeTxId + '\'' +
                '}';
    }
}

/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.dao.proposals;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.storage.payload.LazyProcessedStoragePayload;
import io.bitsquare.p2p.storage.payload.PersistedStoragePayload;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public final class ProposalPayload implements LazyProcessedStoragePayload, PersistedStoragePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(ProposalPayload.class);

    public static final long TTL = TimeUnit.DAYS.toMillis(30);

    public final String uid;
    public final String name;
    public final String title;
    public final String category;
    public final String description;
    public final String link;
    public final Date startDate;
    public final Date endDate;
    public final Coin requestedBtc;
    public final String btcAddress;
    public final NodeAddress nodeAddress;
    public final Date creationDate;
    //TODO store as byte array
    public final PublicKey p2pStorageSignaturePubKey;
    public final byte[] squPubKey;

    // Signature of proposal data without signature, hash and feeTxId
    public byte[] signature;
    // Sha256Hash of proposal data including the signature but without feeTxId and hash
    public byte[] hash;

    // Set after we signed and set the hash. The hash is used in the OP_RETURN of the fee tx
    public String feeTxId;

    public ProposalPayload(String uid,
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
                           PublicKey p2pStorageSignaturePubKey,
                           byte[] squPubKey) {

        creationDate = new Date();
        this.uid = uid;
        this.name = name;
        this.title = title;
        this.category = category;
        this.description = description;
        this.link = link;
        this.startDate = startDate;
        this.endDate = endDate;
        this.requestedBtc = requestedBtc;
        this.btcAddress = btcAddress;
        this.nodeAddress = nodeAddress;
        this.p2pStorageSignaturePubKey = p2pStorageSignaturePubKey;
        this.squPubKey = squPubKey;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return p2pStorageSignaturePubKey;
    }


    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    // Called after tx is published
    public void setFeeTxId(String feeTxId) {
        this.feeTxId = feeTxId;
    }

    public String getShortId() {
        return uid.substring(0, 8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProposalPayload proposal = (ProposalPayload) o;

        if (uid != null ? !uid.equals(proposal.uid) : proposal.uid != null) return false;
        if (name != null ? !name.equals(proposal.name) : proposal.name != null) return false;
        if (title != null ? !title.equals(proposal.title) : proposal.title != null) return false;
        if (category != null ? !category.equals(proposal.category) : proposal.category != null) return false;
        if (description != null ? !description.equals(proposal.description) : proposal.description != null)
            return false;
        if (link != null ? !link.equals(proposal.link) : proposal.link != null) return false;
        if (startDate != null ? !startDate.equals(proposal.startDate) : proposal.startDate != null) return false;
        if (endDate != null ? !endDate.equals(proposal.endDate) : proposal.endDate != null) return false;
        if (requestedBtc != null ? !requestedBtc.equals(proposal.requestedBtc) : proposal.requestedBtc != null)
            return false;
        if (btcAddress != null ? !btcAddress.equals(proposal.btcAddress) : proposal.btcAddress != null) return false;
        if (nodeAddress != null ? !nodeAddress.equals(proposal.nodeAddress) : proposal.nodeAddress != null)
            return false;
        if (creationDate != null ? !creationDate.equals(proposal.creationDate) : proposal.creationDate != null)
            return false;
        if (p2pStorageSignaturePubKey != null ? !p2pStorageSignaturePubKey.equals(proposal.p2pStorageSignaturePubKey) : proposal.p2pStorageSignaturePubKey != null)
            return false;
        return Arrays.equals(squPubKey, proposal.squPubKey);

    }

    @Override
    public int hashCode() {
        int result = uid != null ? uid.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (link != null ? link.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (requestedBtc != null ? requestedBtc.hashCode() : 0);
        result = 31 * result + (btcAddress != null ? btcAddress.hashCode() : 0);
        result = 31 * result + (nodeAddress != null ? nodeAddress.hashCode() : 0);
        result = 31 * result + (creationDate != null ? creationDate.hashCode() : 0);
        result = 31 * result + (p2pStorageSignaturePubKey != null ? p2pStorageSignaturePubKey.hashCode() : 0);
        result = 31 * result + (squPubKey != null ? Arrays.hashCode(squPubKey) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Proposal{" +
                "uid='" + uid + '\'' +
                ", text='" + name + '\'' +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", description='" + description + '\'' +
                ", link='" + link + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", requestedBtc=" + requestedBtc +
                ", btcAddress='" + btcAddress + '\'' +
                ", nodeAddress=" + nodeAddress +
                ", pubKey=" + Arrays.toString(squPubKey) +
                ", signature=" + signature +
                ", hash=" + hash +
                ", feeTxId=" + feeTxId +
                '}';
    }

}

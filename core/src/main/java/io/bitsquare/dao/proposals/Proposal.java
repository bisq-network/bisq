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

public class Proposal implements LazyProcessedStoragePayload, PersistedStoragePayload {
    private static final Logger log = LoggerFactory.getLogger(Proposal.class);

    public static final long TTL = TimeUnit.DAYS.toMillis(30);

    public final String uid;
    public final String text;
    public final String title;
    public final String category;
    public final String description;
    public final String link;
    public final Date startDate;
    public final Date endDate;
    public final Coin requestedBtc;
    public final String btcAddress;
    public final NodeAddress nodeAddress;
    private PublicKey p2pStorageSignaturePubKey;
    public final byte[] squPubKey;

    // Signature of proposal data without signature, hash and feeTxId
    public byte[] signature;
    // Sha256Hash of proposal data including the signature but without feeTxId and hash
    public byte[] hash;

    // Set after we signed and set the hash. The hash is used in the OP_RETURN of the fee tx
    public String feeTxId;

    public Proposal(String uid,
                    String text,
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

        this.uid = uid;
        this.text = text;
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

    // Called after sig and hash
    public void setFeeTxId(String feeTxId) {
        this.feeTxId = feeTxId;
    }

    @Override
    public String toString() {
        return "Proposal{" +
                "uid='" + uid + '\'' +
                ", text='" + text + '\'' +
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

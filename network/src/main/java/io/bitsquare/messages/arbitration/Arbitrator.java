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

package io.bitsquare.messages.arbitration;

import com.google.protobuf.ByteString;
import io.bitsquare.messages.app.Version;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.wire.proto.Messages;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.storage.payload.StoragePayload;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class Arbitrator implements StoragePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public static final long TTL = TimeUnit.DAYS.toMillis(10);

    // Persisted fields
    private final byte[] btcPubKey;
    private final PubKeyRing pubKeyRing;
    private final NodeAddress arbitratorNodeAddress;
    private final List<String> languageCodes;
    private final String btcAddress;
    private final long registrationDate;
    private final String registrationSignature;
    private final byte[] registrationPubKey;

    public Arbitrator(NodeAddress arbitratorNodeAddress,
                      byte[] btcPubKey,
                      String btcAddress,
                      PubKeyRing pubKeyRing,
                      List<String> languageCodes,
                      Date registrationDate,
                      byte[] registrationPubKey,
                      String registrationSignature) {
        this.arbitratorNodeAddress = arbitratorNodeAddress;
        this.btcPubKey = btcPubKey;
        this.btcAddress = btcAddress;
        this.pubKeyRing = pubKeyRing;
        this.languageCodes = languageCodes;
        this.registrationDate = registrationDate.getTime();
        this.registrationPubKey = registrationPubKey;
        this.registrationSignature = registrationSignature;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }

    public byte[] getBtcPubKey() {
        return btcPubKey;
    }

    public PubKeyRing getPubKeyRing() {
        return pubKeyRing;
    }

    public NodeAddress getArbitratorNodeAddress() {
        return arbitratorNodeAddress;
    }

    public Date getRegistrationDate() {
        return new Date(registrationDate);
    }

    public String getBtcAddress() {
        return btcAddress;
    }

    public List<String> getLanguageCodes() {
        return languageCodes;
    }

    public String getRegistrationSignature() {
        return registrationSignature;
    }

    public byte[] getRegistrationPubKey() {
        return registrationPubKey;
    }


    @Override
    public Messages.StoragePayload toProtoBuf() {
        return Messages.StoragePayload.newBuilder().setArbitrator(Messages.Arbitrator.newBuilder()
                .setTTL(TTL)
                .setBtcPubKey(ByteString.copyFrom(btcPubKey))
                .setPubKeyRing((Messages.PubKeyRing) pubKeyRing.toProtoBuf())
                .setArbitratorNodeAddress(arbitratorNodeAddress.toProtoBuf())
                .addAllLanguageCodes(languageCodes)
                .setBtcAddress(btcAddress)
                .setRegistrationDate(registrationDate)
                .setRegistrationSignature(registrationSignature)
                .setRegistrationPubKey(ByteString.copyFrom(registrationPubKey))).build();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Arbitrator)) return false;

        Arbitrator that = (Arbitrator) o;

        if (registrationDate != that.registrationDate) return false;
        if (!Arrays.equals(btcPubKey, that.btcPubKey)) return false;
        if (pubKeyRing != null ? !pubKeyRing.equals(that.pubKeyRing) : that.pubKeyRing != null) return false;
        if (arbitratorNodeAddress != null ? !arbitratorNodeAddress.equals(that.arbitratorNodeAddress) : that.arbitratorNodeAddress != null)
            return false;
        if (languageCodes != null ? !languageCodes.equals(that.languageCodes) : that.languageCodes != null)
            return false;
        if (btcAddress != null ? !btcAddress.equals(that.btcAddress) : that.btcAddress != null) return false;
        if (registrationSignature != null ? !registrationSignature.equals(that.registrationSignature) : that.registrationSignature != null)
            return false;
        return Arrays.equals(registrationPubKey, that.registrationPubKey);

    }

    @Override
    public int hashCode() {
        int result = btcPubKey != null ? Arrays.hashCode(btcPubKey) : 0;
        result = 31 * result + (pubKeyRing != null ? pubKeyRing.hashCode() : 0);
        result = 31 * result + (arbitratorNodeAddress != null ? arbitratorNodeAddress.hashCode() : 0);
        result = 31 * result + (languageCodes != null ? languageCodes.hashCode() : 0);
        result = 31 * result + (btcAddress != null ? btcAddress.hashCode() : 0);
        result = 31 * result + (int) (registrationDate ^ (registrationDate >>> 32));
        result = 31 * result + (registrationSignature != null ? registrationSignature.hashCode() : 0);
        result = 31 * result + (registrationPubKey != null ? Arrays.hashCode(registrationPubKey) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Arbitrator{" +
                "\n\tarbitratorAddress=" + arbitratorNodeAddress +
                "\n\tlanguageCodes=" + languageCodes +
                "\n\tbtcAddress='" + btcAddress + '\'' +
                "\n\tregistrationDate=" + registrationDate +
                "\n\tbtcPubKey.hashCode()=" + Arrays.toString(btcPubKey).hashCode() +
                "\n\tpubKeyRing.hashCode()=" + pubKeyRing.hashCode() +
                "\n\tregistrationSignature.hashCode()='" + registrationSignature.hashCode() + '\'' +
                "\n\tregistrationPubKey.hashCode()=" + Arrays.toString(registrationPubKey).hashCode() +
                '}';
    }
}

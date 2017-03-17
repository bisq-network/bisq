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

package io.bisq.wire.payload.filter;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.wire.payload.StoragePayload;
import io.bisq.wire.proto.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class Filter implements StoragePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final Logger log = LoggerFactory.getLogger(Filter.class);
    private static final long TTL = TimeUnit.DAYS.toMillis(21);

    public final List<String> bannedNodeAddress;
    public final List<String> bannedOfferIds;
    public final List<PaymentAccountFilter> bannedPaymentAccounts;
    private String signatureAsBase64;
    private byte[] publicKeyBytes;

    private transient PublicKey publicKey;

    public Filter(List<String> bannedOfferIds, List<String> bannedNodeAddress, List<PaymentAccountFilter> bannedPaymentAccounts) {
        this.bannedOfferIds = bannedOfferIds;
        this.bannedNodeAddress = bannedNodeAddress;
        this.bannedPaymentAccounts = bannedPaymentAccounts;
    }

    public Filter(List<String> bannedOfferIds, List<String> bannedNodeAddress, List<PaymentAccountFilter> bannedPaymentAccounts,
                  String signatureAsBase64, byte[] publicKeyBytes) {
        this(bannedOfferIds, bannedNodeAddress, bannedPaymentAccounts);
        init();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            init();
        } catch (Throwable t) {
            log.warn("Exception at readObject: " + t.getMessage());
        }
    }

    private void init() {
        try {
            publicKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("Couldn't create the storage public key", e);
        }
    }

    public void setSigAndPubKey(String signatureAsBase64, PublicKey storagePublicKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.publicKey = storagePublicKey;
        this.publicKeyBytes = new X509EncodedKeySpec(this.publicKey.getEncoded()).getEncoded();
    }

    public String getSignatureAsBase64() {
        return signatureAsBase64;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return publicKey;
    }


    @Override
    public Messages.StoragePayload toProtoBuf() {
        List<Messages.PaymentAccountFilter> paymentAccountFilterList;
        paymentAccountFilterList = bannedPaymentAccounts.stream()
                .map(paymentAccountFilter -> paymentAccountFilter.toProtoBuf()).collect(Collectors.toList());
        return Messages.StoragePayload.newBuilder().setFilter(Messages.Filter.newBuilder()
                .setTTL(TTL)
                .addAllBannedNodeAddress(bannedNodeAddress)
                .addAllBannedOfferIds(bannedOfferIds)
                .addAllBannedPaymentAccounts(paymentAccountFilterList)
                .setSignatureAsBase64(signatureAsBase64)
                .setPublicKeyBytes(ByteString.copyFrom(publicKeyBytes))).build();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Filter)) return false;

        Filter filter = (Filter) o;

        if (bannedNodeAddress != null ? !bannedNodeAddress.equals(filter.bannedNodeAddress) : filter.bannedNodeAddress != null)
            return false;
        if (bannedOfferIds != null ? !bannedOfferIds.equals(filter.bannedOfferIds) : filter.bannedOfferIds != null)
            return false;
        if (bannedPaymentAccounts != null ? !bannedPaymentAccounts.equals(filter.bannedPaymentAccounts) : filter.bannedPaymentAccounts != null)
            return false;
        if (signatureAsBase64 != null ? !signatureAsBase64.equals(filter.signatureAsBase64) : filter.signatureAsBase64 != null)
            return false;
        return Arrays.equals(publicKeyBytes, filter.publicKeyBytes);

    }

    @Override
    public int hashCode() {
        int result = bannedNodeAddress != null ? bannedNodeAddress.hashCode() : 0;
        result = 31 * result + (bannedOfferIds != null ? bannedOfferIds.hashCode() : 0);
        result = 31 * result + (bannedPaymentAccounts != null ? bannedPaymentAccounts.hashCode() : 0);
        result = 31 * result + (signatureAsBase64 != null ? signatureAsBase64.hashCode() : 0);
        result = 31 * result + (publicKeyBytes != null ? Arrays.hashCode(publicKeyBytes) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Filter{" +
                "bannedNodeAddress=" + bannedNodeAddress +
                ", bannedOfferIds=" + bannedOfferIds +
                ", bannedPaymentAccounts=" + bannedPaymentAccounts +
                '}';
    }
}

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

package io.bisq.protobuffer.payload.filter;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.payload.StoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@EqualsAndHashCode
@Slf4j
public final class Filter implements StoragePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final long TTL = TimeUnit.DAYS.toMillis(21);

    // Payload
    public final List<String> bannedNodeAddress;
    public final List<String> bannedOfferIds;
    public final List<PaymentAccountFilter> bannedPaymentAccounts;
    @Getter
    private String signatureAsBase64;
    private byte[] publicKeyBytes;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility 
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new 
    // field in a class would break that hash and therefore break the storage mechanism.
    @Getter
    @Nullable
    private Map<String, String> extraDataMap;

    // Domain
    private transient PublicKey publicKey;

    // Called from domain
    public Filter(List<String> bannedOfferIds,
                  List<String> bannedNodeAddress,
                  List<PaymentAccountFilter> bannedPaymentAccounts) {
        this.bannedOfferIds = bannedOfferIds;
        this.bannedNodeAddress = bannedNodeAddress;
        this.bannedPaymentAccounts = bannedPaymentAccounts;
        this.extraDataMap = Maps.newHashMap();
    }

    // Called from PB
    public Filter(List<String> bannedOfferIds,
                  List<String> bannedNodeAddress,
                  List<PaymentAccountFilter> bannedPaymentAccounts,
                  String signatureAsBase64,
                  byte[] publicKeyBytes,
                  @Nullable Map<String, String> extraDataMap) {
        this(bannedOfferIds, bannedNodeAddress, bannedPaymentAccounts);
        this.signatureAsBase64 = signatureAsBase64;
        this.publicKeyBytes = publicKeyBytes;
        this.extraDataMap = extraDataMap;

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

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return publicKey;
    }

    @Override
    public PB.StoragePayload toProto() {
        List<PB.PaymentAccountFilter> paymentAccountFilterList;
        paymentAccountFilterList = bannedPaymentAccounts.stream()
                .map(PaymentAccountFilter::toProtoBuf).collect(Collectors.toList());
        final PB.Filter.Builder builder = PB.Filter.newBuilder()
                .setTTL(TTL)
                .addAllBannedNodeAddress(bannedNodeAddress)
                .addAllBannedOfferIds(bannedOfferIds)
                .addAllBannedPaymentAccounts(paymentAccountFilterList)
                .setSignatureAsBase64(signatureAsBase64)
                .setPublicKeyBytes(ByteString.copyFrom(publicKeyBytes));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraDataMap);
        return PB.StoragePayload.newBuilder().setFilter(builder).build();
    }


    @Override
    public String toString() {
        return "Filter{" +
                "bannedNodeAddress=" + bannedNodeAddress +
                ", bannedOfferIds=" + bannedOfferIds +
                ", bannedPaymentAccounts=" + bannedPaymentAccounts +
                ", signatureAsBase64='" + signatureAsBase64 + '\'' +
                ", publicKey=" + Hex.toHexString(publicKey.getEncoded()) +
                ", extraDataMap=" + extraDataMap +
                '}';
    }
}

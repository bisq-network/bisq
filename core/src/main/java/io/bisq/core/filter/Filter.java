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

package io.bisq.core.filter;

import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import io.bisq.common.crypto.Sig;
import io.bisq.core.proto.ProtoUtil;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.StoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
@Getter
@EqualsAndHashCode
@ToString
public final class Filter implements StoragePayload {
    private static final long TTL = TimeUnit.DAYS.toMillis(21);

    public final List<String> bannedNodeAddress;
    public final List<String> bannedOfferIds;
    public final List<PaymentAccountFilter> bannedPaymentAccounts;
    private String signatureAsBase64;
    private byte[] ownerPubKeyBytes;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility 
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new 
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;
    private PublicKey ownerPubKey;

    public Filter(List<String> bannedOfferIds,
                  List<String> bannedNodeAddress,
                  List<PaymentAccountFilter> bannedPaymentAccounts) {
        this.bannedOfferIds = bannedOfferIds;
        this.bannedNodeAddress = bannedNodeAddress;
        this.bannedPaymentAccounts = bannedPaymentAccounts;
        this.extraDataMap = Maps.newHashMap();
    }

    public Filter(List<String> bannedOfferIds,
                  List<String> bannedNodeAddress,
                  List<PaymentAccountFilter> bannedPaymentAccounts,
                  String signatureAsBase64,
                  byte[] ownerPubKeyBytes,
                  @Nullable Map<String, String> extraDataMap) {
        this(bannedOfferIds, bannedNodeAddress, bannedPaymentAccounts);
        this.signatureAsBase64 = signatureAsBase64;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.extraDataMap = extraDataMap;

        ownerPubKey = Sig.getSigPublicKeyFromBytes(ownerPubKeyBytes);
    }


    public void setSigAndPubKey(String signatureAsBase64, PublicKey ownerPubKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.ownerPubKey = ownerPubKey;
        ownerPubKeyBytes = Sig.getSigPublicKeyBytes(this.ownerPubKey);
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        List<PB.PaymentAccountFilter> paymentAccountFilterList;
        paymentAccountFilterList = bannedPaymentAccounts.stream()
                .map(PaymentAccountFilter::toProtoBuf).collect(Collectors.toList());
        final PB.Filter.Builder builder = PB.Filter.newBuilder()
                .addAllBannedNodeAddress(bannedNodeAddress)
                .addAllBannedOfferIds(bannedOfferIds)
                .addAllBannedPaymentAccounts(paymentAccountFilterList)
                .setSignatureAsBase64(signatureAsBase64)
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraDataMap);
        return PB.StoragePayload.newBuilder().setFilter(builder).build();
    }

    public static Filter fromProto(PB.Filter filter) {
        List<PaymentAccountFilter> paymentAccountFilters = filter.getBannedPaymentAccountsList()
                .stream().map(accountFilter -> ProtoUtil.getPaymentAccountFilter(accountFilter)).collect(Collectors.toList());
        return new Filter(filter.getBannedOfferIdsList().stream().collect(Collectors.toList()),
                filter.getBannedNodeAddressList().stream().collect(Collectors.toList()),
                paymentAccountFilters,
                filter.getSignatureAsBase64(),
                filter.getOwnerPubKeyBytes().toByteArray(),
                CollectionUtils.isEmpty(filter.getExtraDataMapMap()) ?
                        null : filter.getExtraDataMapMap());
    }


    @Override
    public String toString() {
        return "Filter{" +
                "bannedNodeAddress=" + bannedNodeAddress +
                ", bannedOfferIds=" + bannedOfferIds +
                ", bannedPaymentAccounts=" + bannedPaymentAccounts +
                ", signatureAsBase64='" + signatureAsBase64 + '\'' +
                ", publicKey=" + Hex.toHexString(ownerPubKey.getEncoded()) +
                ", extraDataMap=" + extraDataMap +
                '}';
    }
}

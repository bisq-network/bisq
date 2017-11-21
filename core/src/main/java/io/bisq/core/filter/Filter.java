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

package io.bisq.core.filter;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Getter
@EqualsAndHashCode
@ToString
public final class Filter implements ProtectedStoragePayload {
    private final List<String> bannedOfferIds;
    private final List<String> bannedNodeAddress;
    private final List<PaymentAccountFilter> bannedPaymentAccounts;

    // Because we added those fields in v 0.5.4 and old versions do not have it we annotate it with @Nullable
    @Nullable
    private final List<String> bannedCurrencies;
    @Nullable
    private final List<String> bannedPaymentMethods;

    // added in v0.6
    @Nullable
    private final List<String> arbitrators;
    @Nullable
    private final List<String> seedNodes;
    @Nullable
    private final List<String> priceRelayNodes;
    private final boolean preventPublicBtcNetwork;

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
                  List<PaymentAccountFilter> bannedPaymentAccounts,
                  @Nullable List<String> bannedCurrencies,
                  @Nullable List<String> bannedPaymentMethods,
                  @Nullable List<String> arbitrators,
                  @Nullable List<String> seedNodes,
                  @Nullable List<String> priceRelayNodes,
                  boolean preventPublicBtcNetwork) {
        this.bannedOfferIds = bannedOfferIds;
        this.bannedNodeAddress = bannedNodeAddress;
        this.bannedPaymentAccounts = bannedPaymentAccounts;
        this.bannedCurrencies = bannedCurrencies;
        this.bannedPaymentMethods = bannedPaymentMethods;
        this.arbitrators = arbitrators;
        this.seedNodes = seedNodes;
        this.priceRelayNodes = priceRelayNodes;
        this.preventPublicBtcNetwork = preventPublicBtcNetwork;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    public Filter(List<String> bannedOfferIds,
                  List<String> bannedNodeAddress,
                  List<PaymentAccountFilter> bannedPaymentAccounts,
                  @Nullable List<String> bannedCurrencies,
                  @Nullable List<String> bannedPaymentMethods,
                  @Nullable List<String> arbitrators,
                  @Nullable List<String> seedNodes,
                  @Nullable List<String> priceRelayNodes,
                  boolean preventPublicBtcNetwork,
                  String signatureAsBase64,
                  byte[] ownerPubKeyBytes,
                  @Nullable Map<String, String> extraDataMap) {
        this(bannedOfferIds,
                bannedNodeAddress,
                bannedPaymentAccounts,
                bannedCurrencies,
                bannedPaymentMethods,
                arbitrators,
                seedNodes,
                priceRelayNodes,
                preventPublicBtcNetwork);
        this.signatureAsBase64 = signatureAsBase64;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.extraDataMap = extraDataMap;

        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyBytes);
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        checkNotNull(signatureAsBase64, "signatureAsBase64 must nto be null");
        checkNotNull(ownerPubKeyBytes, "ownerPubKeyBytes must nto be null");
        List<PB.PaymentAccountFilter> paymentAccountFilterList = bannedPaymentAccounts.stream()
                .map(PaymentAccountFilter::toProtoMessage)
                .collect(Collectors.toList());
        final PB.Filter.Builder builder = PB.Filter.newBuilder()
                .addAllBannedOfferIds(bannedOfferIds)
                .addAllBannedNodeAddress(bannedNodeAddress)
                .addAllBannedPaymentAccounts(paymentAccountFilterList)
                .setSignatureAsBase64(signatureAsBase64)
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes))
                .setPreventPublicBtcNetwork(preventPublicBtcNetwork);

        Optional.ofNullable(bannedCurrencies).ifPresent(builder::addAllBannedCurrencies);
        Optional.ofNullable(bannedPaymentMethods).ifPresent(builder::addAllBannedPaymentMethods);
        Optional.ofNullable(arbitrators).ifPresent(builder::addAllArbitrators);
        Optional.ofNullable(seedNodes).ifPresent(builder::addAllSeedNodes);
        Optional.ofNullable(priceRelayNodes).ifPresent(builder::addAllPriceRelayNodes);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);

        return PB.StoragePayload.newBuilder().setFilter(builder).build();
    }

    public static Filter fromProto(PB.Filter proto) {
        return new Filter(proto.getBannedOfferIdsList().stream().collect(Collectors.toList()),
                proto.getBannedNodeAddressList().stream().collect(Collectors.toList()),
                proto.getBannedPaymentAccountsList().stream()
                        .map(PaymentAccountFilter::fromProto)
                        .collect(Collectors.toList()),
                CollectionUtils.isEmpty(proto.getBannedCurrenciesList()) ? null : proto.getBannedCurrenciesList().stream().collect(Collectors.toList()),
                CollectionUtils.isEmpty(proto.getBannedPaymentMethodsList()) ? null : proto.getBannedPaymentMethodsList().stream().collect(Collectors.toList()),
                CollectionUtils.isEmpty(proto.getArbitratorsList()) ? null : proto.getArbitratorsList().stream().collect(Collectors.toList()),
                CollectionUtils.isEmpty(proto.getSeedNodesList()) ? null : proto.getSeedNodesList().stream().collect(Collectors.toList()),
                CollectionUtils.isEmpty(proto.getPriceRelayNodesList()) ? null : proto.getPriceRelayNodesList().stream().collect(Collectors.toList()),
                proto.getPreventPublicBtcNetwork(),
                proto.getSignatureAsBase64(),
                proto.getOwnerPubKeyBytes().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(30);
    }

    public void setSigAndPubKey(String signatureAsBase64, PublicKey ownerPubKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.ownerPubKey = ownerPubKey;

        ownerPubKeyBytes = Sig.getPublicKeyBytes(this.ownerPubKey);
    }
}

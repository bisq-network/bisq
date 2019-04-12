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

package bisq.core.filter;

import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.Sig;
import bisq.common.util.ExtraDataMapValidator;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import org.springframework.util.CollectionUtils;

import com.google.common.annotations.VisibleForTesting;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Getter
@EqualsAndHashCode
@ToString
public final class Filter implements ProtectedStoragePayload, ExpirablePayload {
    private final List<String> bannedOfferIds;
    private final List<String> bannedNodeAddress;
    private final List<PaymentAccountFilter> bannedPaymentAccounts;

    // Because we added those fields in v 0.5.4 and old versions do not have it we annotate it with @Nullable
    @Nullable
    private final List<String> bannedCurrencies;
    @Nullable
    private final List<String> bannedPaymentMethods;

    // added in v0.6.0
    @Nullable
    private final List<String> arbitrators;
    @Nullable
    private final List<String> seedNodes;
    @Nullable
    private final List<String> priceRelayNodes;
    private final boolean preventPublicBtcNetwork;

    // added in v0.6.2
    @Nullable
    private final List<String> btcNodes;


    private String signatureAsBase64;
    private byte[] ownerPubKeyBytes;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;
    private PublicKey ownerPubKey;

    // added in v0.9.4
    private final boolean disableDao;

    // added in v0.9.8
    @Nullable
    private final String disableDaoBelowVersion;

    public Filter(List<String> bannedOfferIds,
                  List<String> bannedNodeAddress,
                  List<PaymentAccountFilter> bannedPaymentAccounts,
                  @Nullable List<String> bannedCurrencies,
                  @Nullable List<String> bannedPaymentMethods,
                  @Nullable List<String> arbitrators,
                  @Nullable List<String> seedNodes,
                  @Nullable List<String> priceRelayNodes,
                  boolean preventPublicBtcNetwork,
                  @Nullable List<String> btcNodes,
                  boolean disableDao,
                  @Nullable String disableDaoBelowVersion) {
        this.bannedOfferIds = bannedOfferIds;
        this.bannedNodeAddress = bannedNodeAddress;
        this.bannedPaymentAccounts = bannedPaymentAccounts;
        this.bannedCurrencies = bannedCurrencies;
        this.bannedPaymentMethods = bannedPaymentMethods;
        this.arbitrators = arbitrators;
        this.seedNodes = seedNodes;
        this.priceRelayNodes = priceRelayNodes;
        this.preventPublicBtcNetwork = preventPublicBtcNetwork;
        this.btcNodes = btcNodes;
        this.disableDao = disableDao;
        this.disableDaoBelowVersion = disableDaoBelowVersion;
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
                  @Nullable List<String> btcNodes,
                  boolean disableDao,
                  @Nullable String disableDaoBelowVersion,
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
                preventPublicBtcNetwork,
                btcNodes,
                disableDao,
                disableDaoBelowVersion);
        this.signatureAsBase64 = signatureAsBase64;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);

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
                .setPreventPublicBtcNetwork(preventPublicBtcNetwork)
                .setDisableDao(disableDao);

        Optional.ofNullable(bannedCurrencies).ifPresent(builder::addAllBannedCurrencies);
        Optional.ofNullable(bannedPaymentMethods).ifPresent(builder::addAllBannedPaymentMethods);
        Optional.ofNullable(arbitrators).ifPresent(builder::addAllArbitrators);
        Optional.ofNullable(seedNodes).ifPresent(builder::addAllSeedNodes);
        Optional.ofNullable(priceRelayNodes).ifPresent(builder::addAllPriceRelayNodes);
        Optional.ofNullable(btcNodes).ifPresent(builder::addAllBtcNodes);
        Optional.ofNullable(disableDaoBelowVersion).ifPresent(builder::setDisableDaoBelowVersion);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);

        return PB.StoragePayload.newBuilder().setFilter(builder).build();
    }

    public static Filter fromProto(PB.Filter proto) {
        return new Filter(new ArrayList<>(proto.getBannedOfferIdsList()),
                new ArrayList<>(proto.getBannedNodeAddressList()),
                proto.getBannedPaymentAccountsList().stream()
                        .map(PaymentAccountFilter::fromProto)
                        .collect(Collectors.toList()),
                CollectionUtils.isEmpty(proto.getBannedCurrenciesList()) ? null : new ArrayList<>(proto.getBannedCurrenciesList()),
                CollectionUtils.isEmpty(proto.getBannedPaymentMethodsList()) ? null : new ArrayList<>(proto.getBannedPaymentMethodsList()),
                CollectionUtils.isEmpty(proto.getArbitratorsList()) ? null : new ArrayList<>(proto.getArbitratorsList()),
                CollectionUtils.isEmpty(proto.getSeedNodesList()) ? null : new ArrayList<>(proto.getSeedNodesList()),
                CollectionUtils.isEmpty(proto.getPriceRelayNodesList()) ? null : new ArrayList<>(proto.getPriceRelayNodesList()),
                proto.getPreventPublicBtcNetwork(),
                CollectionUtils.isEmpty(proto.getBtcNodesList()) ? null : new ArrayList<>(proto.getBtcNodesList()),
                proto.getDisableDao(),
                proto.getDisableDaoBelowVersion().isEmpty() ? null : proto.getDisableDaoBelowVersion(),
                proto.getSignatureAsBase64(),
                proto.getOwnerPubKeyBytes().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(180);
    }

    public void setSigAndPubKey(String signatureAsBase64, PublicKey ownerPubKey) {
        this.signatureAsBase64 = signatureAsBase64;
        this.ownerPubKey = ownerPubKey;

        ownerPubKeyBytes = Sig.getPublicKeyBytes(this.ownerPubKey);
    }
}

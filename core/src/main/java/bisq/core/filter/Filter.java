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
import bisq.common.proto.ProtoUtil;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import com.google.common.annotations.VisibleForTesting;

import java.security.PublicKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Value
public final class Filter implements ProtectedStoragePayload, ExpirablePayload {
    private final List<String> bannedOfferIds;
    private final List<String> bannedNodeAddress;
    private final List<PaymentAccountFilter> bannedPaymentAccounts;
    private final List<String> bannedCurrencies;
    private final List<String> bannedPaymentMethods;
    private final List<String> arbitrators;
    private final List<String> seedNodes;
    private final List<String> priceRelayNodes;
    private final boolean preventPublicBtcNetwork;
    private final List<String> btcNodes;
    // SignatureAsBase64 is not set initially as we use the serialized data for signing. We set it after signature is
    // created by cloning the object with a non-null sig.
    @Nullable
    private final String signatureAsBase64;
    // The pub EC key from the dev who has signed and published the filter (different to ownerPubKeyBytes)
    private final String signerPubKeyAsHex;

    // The pub key used for the data protection in the p2p storage
    private final byte[] ownerPubKeyBytes;
    private final boolean disableDao;
    private final String disableDaoBelowVersion;
    private final String disableTradeBelowVersion;
    private final List<String> mediators;
    private final List<String> refundAgents;

    private final List<String> bannedAccountWitnessSignerPubKeys;

    private final List<String> btcFeeReceiverAddresses;

    private final long creationDate;

    private final List<String> bannedPrivilegedDevPubKeys;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    private transient PublicKey ownerPubKey;

    // added at v1.3.8
    private final boolean disableAutoConf;

    // After we have created the signature from the filter data we clone it and apply the signature
    static Filter cloneWithSig(Filter filter, String signatureAsBase64) {
        return new Filter(filter.getBannedOfferIds(),
                filter.getBannedNodeAddress(),
                filter.getBannedPaymentAccounts(),
                filter.getBannedCurrencies(),
                filter.getBannedPaymentMethods(),
                filter.getArbitrators(),
                filter.getSeedNodes(),
                filter.getPriceRelayNodes(),
                filter.isPreventPublicBtcNetwork(),
                filter.getBtcNodes(),
                filter.isDisableDao(),
                filter.getDisableDaoBelowVersion(),
                filter.getDisableTradeBelowVersion(),
                filter.getMediators(),
                filter.getRefundAgents(),
                filter.getBannedAccountWitnessSignerPubKeys(),
                filter.getBtcFeeReceiverAddresses(),
                filter.getOwnerPubKeyBytes(),
                filter.getCreationDate(),
                filter.getExtraDataMap(),
                signatureAsBase64,
                filter.getSignerPubKeyAsHex(),
                filter.getBannedPrivilegedDevPubKeys(),
                filter.isDisableAutoConf());
    }

    // Used for signature verification as we created the sig without the signatureAsBase64 field we set it to null again
    static Filter cloneWithoutSig(Filter filter) {
        return new Filter(filter.getBannedOfferIds(),
                filter.getBannedNodeAddress(),
                filter.getBannedPaymentAccounts(),
                filter.getBannedCurrencies(),
                filter.getBannedPaymentMethods(),
                filter.getArbitrators(),
                filter.getSeedNodes(),
                filter.getPriceRelayNodes(),
                filter.isPreventPublicBtcNetwork(),
                filter.getBtcNodes(),
                filter.isDisableDao(),
                filter.getDisableDaoBelowVersion(),
                filter.getDisableTradeBelowVersion(),
                filter.getMediators(),
                filter.getRefundAgents(),
                filter.getBannedAccountWitnessSignerPubKeys(),
                filter.getBtcFeeReceiverAddresses(),
                filter.getOwnerPubKeyBytes(),
                filter.getCreationDate(),
                filter.getExtraDataMap(),
                null,
                filter.getSignerPubKeyAsHex(),
                filter.getBannedPrivilegedDevPubKeys(),
                filter.isDisableAutoConf());
    }

    public Filter(List<String> bannedOfferIds,
                  List<String> bannedNodeAddress,
                  List<PaymentAccountFilter> bannedPaymentAccounts,
                  List<String> bannedCurrencies,
                  List<String> bannedPaymentMethods,
                  List<String> arbitrators,
                  List<String> seedNodes,
                  List<String> priceRelayNodes,
                  boolean preventPublicBtcNetwork,
                  List<String> btcNodes,
                  boolean disableDao,
                  String disableDaoBelowVersion,
                  String disableTradeBelowVersion,
                  List<String> mediators,
                  List<String> refundAgents,
                  List<String> bannedAccountWitnessSignerPubKeys,
                  List<String> btcFeeReceiverAddresses,
                  PublicKey ownerPubKey,
                  String signerPubKeyAsHex,
                  List<String> bannedPrivilegedDevPubKeys,
                  boolean disableAutoConf) {
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
                disableDaoBelowVersion,
                disableTradeBelowVersion,
                mediators,
                refundAgents,
                bannedAccountWitnessSignerPubKeys,
                btcFeeReceiverAddresses,
                Sig.getPublicKeyBytes(ownerPubKey),
                System.currentTimeMillis(),
                null,
                null,
                signerPubKeyAsHex,
                bannedPrivilegedDevPubKeys,
                disableAutoConf);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    public Filter(List<String> bannedOfferIds,
                  List<String> bannedNodeAddress,
                  List<PaymentAccountFilter> bannedPaymentAccounts,
                  List<String> bannedCurrencies,
                  List<String> bannedPaymentMethods,
                  List<String> arbitrators,
                  List<String> seedNodes,
                  List<String> priceRelayNodes,
                  boolean preventPublicBtcNetwork,
                  List<String> btcNodes,
                  boolean disableDao,
                  String disableDaoBelowVersion,
                  String disableTradeBelowVersion,
                  List<String> mediators,
                  List<String> refundAgents,
                  List<String> bannedAccountWitnessSignerPubKeys,
                  List<String> btcFeeReceiverAddresses,
                  byte[] ownerPubKeyBytes,
                  long creationDate,
                  @Nullable Map<String, String> extraDataMap,
                  @Nullable String signatureAsBase64,
                  String signerPubKeyAsHex,
                  List<String> bannedPrivilegedDevPubKeys,
                  boolean disableAutoConf) {
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
        this.disableTradeBelowVersion = disableTradeBelowVersion;
        this.mediators = mediators;
        this.refundAgents = refundAgents;
        this.bannedAccountWitnessSignerPubKeys = bannedAccountWitnessSignerPubKeys;
        this.btcFeeReceiverAddresses = btcFeeReceiverAddresses;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.creationDate = creationDate;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
        this.signatureAsBase64 = signatureAsBase64;
        this.signerPubKeyAsHex = signerPubKeyAsHex;
        this.bannedPrivilegedDevPubKeys = bannedPrivilegedDevPubKeys;
        this.disableAutoConf = disableAutoConf;

        // ownerPubKeyBytes can be null when called from tests
        if (ownerPubKeyBytes != null) {
            ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyBytes);
        } else {
            ownerPubKey = null;
        }
    }

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        List<protobuf.PaymentAccountFilter> paymentAccountFilterList = bannedPaymentAccounts.stream()
                .map(PaymentAccountFilter::toProtoMessage)
                .collect(Collectors.toList());

        protobuf.Filter.Builder builder = protobuf.Filter.newBuilder().addAllBannedOfferIds(bannedOfferIds)
                .addAllBannedNodeAddress(bannedNodeAddress)
                .addAllBannedPaymentAccounts(paymentAccountFilterList)
                .addAllBannedCurrencies(bannedCurrencies)
                .addAllBannedPaymentMethods(bannedPaymentMethods)
                .addAllArbitrators(arbitrators)
                .addAllSeedNodes(seedNodes)
                .addAllPriceRelayNodes(priceRelayNodes)
                .setPreventPublicBtcNetwork(preventPublicBtcNetwork)
                .addAllBtcNodes(btcNodes)
                .setDisableDao(disableDao)
                .setDisableDaoBelowVersion(disableDaoBelowVersion)
                .setDisableTradeBelowVersion(disableTradeBelowVersion)
                .addAllMediators(mediators)
                .addAllRefundAgents(refundAgents)
                .addAllBannedSignerPubKeys(bannedAccountWitnessSignerPubKeys)
                .addAllBtcFeeReceiverAddresses(btcFeeReceiverAddresses)
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes))
                .setSignerPubKeyAsHex(signerPubKeyAsHex)
                .setCreationDate(creationDate)
                .addAllBannedPrivilegedDevPubKeys(bannedPrivilegedDevPubKeys)
                .setDisableAutoConf(disableAutoConf);

        Optional.ofNullable(signatureAsBase64).ifPresent(builder::setSignatureAsBase64);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);

        return protobuf.StoragePayload.newBuilder().setFilter(builder).build();
    }

    public static Filter fromProto(protobuf.Filter proto) {
        List<PaymentAccountFilter> bannedPaymentAccountsList = proto.getBannedPaymentAccountsList().stream()
                .map(PaymentAccountFilter::fromProto)
                .collect(Collectors.toList());


        return new Filter(ProtoUtil.protocolStringListToList(proto.getBannedOfferIdsList()),
                ProtoUtil.protocolStringListToList(proto.getBannedNodeAddressList()),
                bannedPaymentAccountsList,
                ProtoUtil.protocolStringListToList(proto.getBannedCurrenciesList()),
                ProtoUtil.protocolStringListToList(proto.getBannedPaymentMethodsList()),
                ProtoUtil.protocolStringListToList(proto.getArbitratorsList()),
                ProtoUtil.protocolStringListToList(proto.getSeedNodesList()),
                ProtoUtil.protocolStringListToList(proto.getPriceRelayNodesList()),
                proto.getPreventPublicBtcNetwork(),
                ProtoUtil.protocolStringListToList(proto.getBtcNodesList()),
                proto.getDisableDao(),
                proto.getDisableDaoBelowVersion(),
                proto.getDisableTradeBelowVersion(),
                ProtoUtil.protocolStringListToList(proto.getMediatorsList()),
                ProtoUtil.protocolStringListToList(proto.getRefundAgentsList()),
                ProtoUtil.protocolStringListToList(proto.getBannedSignerPubKeysList()),
                ProtoUtil.protocolStringListToList(proto.getBtcFeeReceiverAddressesList()),
                proto.getOwnerPubKeyBytes().toByteArray(),
                proto.getCreationDate(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap(),
                proto.getSignatureAsBase64(),
                proto.getSignerPubKeyAsHex(),
                ProtoUtil.protocolStringListToList(proto.getBannedPrivilegedDevPubKeysList()),
                proto.getDisableAutoConf()
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(180);
    }

    @Override
    public String toString() {
        return "Filter{" +
                "\n     bannedOfferIds=" + bannedOfferIds +
                ",\n     bannedNodeAddress=" + bannedNodeAddress +
                ",\n     bannedPaymentAccounts=" + bannedPaymentAccounts +
                ",\n     bannedCurrencies=" + bannedCurrencies +
                ",\n     bannedPaymentMethods=" + bannedPaymentMethods +
                ",\n     arbitrators=" + arbitrators +
                ",\n     seedNodes=" + seedNodes +
                ",\n     priceRelayNodes=" + priceRelayNodes +
                ",\n     preventPublicBtcNetwork=" + preventPublicBtcNetwork +
                ",\n     btcNodes=" + btcNodes +
                ",\n     signatureAsBase64='" + signatureAsBase64 + '\'' +
                ",\n     signerPubKeyAsHex='" + signerPubKeyAsHex + '\'' +
                ",\n     ownerPubKeyBytes=" + Utilities.bytesAsHexString(ownerPubKeyBytes) +
                ",\n     disableDao=" + disableDao +
                ",\n     disableDaoBelowVersion='" + disableDaoBelowVersion + '\'' +
                ",\n     disableTradeBelowVersion='" + disableTradeBelowVersion + '\'' +
                ",\n     mediators=" + mediators +
                ",\n     refundAgents=" + refundAgents +
                ",\n     bannedAccountWitnessSignerPubKeys=" + bannedAccountWitnessSignerPubKeys +
                ",\n     bannedPrivilegedDevPubKeys=" + bannedPrivilegedDevPubKeys +
                ",\n     btcFeeReceiverAddresses=" + btcFeeReceiverAddresses +
                ",\n     creationDate=" + creationDate +
                ",\n     extraDataMap=" + extraDataMap +
                ",\n     disableAutoConf=" + disableAutoConf +
                "\n}";
    }
}

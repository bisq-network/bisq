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

package bisq.core.trade.model.bisq_v1;

import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.util.JsonUtil;
import bisq.core.util.VolumeUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@Getter
@EqualsAndHashCode
public final class Contract implements NetworkPayload {
    private final OfferPayload offerPayload;
    private final long tradeAmount;
    private final long tradePrice;
    private final String takerFeeTxID;
    private final NodeAddress buyerNodeAddress;
    private final NodeAddress sellerNodeAddress;
    private final NodeAddress mediatorNodeAddress;
    private final boolean isBuyerMakerAndSellerTaker;
    private final String makerAccountId;
    private final String takerAccountId;

    // Changed in v1.7.0: Not a final field anymore but initially set to null and later once the data is transmitted
    // set via a setter. This breaks the immutability of the contract but as there are several areas where we access
    // that data it is the less painful solution.
    @Nullable
    private PaymentAccountPayload makerPaymentAccountPayload;
    @Nullable
    private PaymentAccountPayload takerPaymentAccountPayload;

    @JsonExclude
    private final PubKeyRing makerPubKeyRing;
    @JsonExclude
    private final PubKeyRing takerPubKeyRing;
    private final String makerPayoutAddressString;
    private final String takerPayoutAddressString;
    @JsonExclude
    private final byte[] makerMultiSigPubKey;
    @JsonExclude
    private final byte[] takerMultiSigPubKey;

    // Added in v1.2.0
    private final long lockTime;
    private final NodeAddress refundAgentNodeAddress;


    // Added in v1.7.0
    @Nullable
    private final byte[] hashOfMakersPaymentAccountPayload;
    @Nullable
    private final byte[] hashOfTakersPaymentAccountPayload;
    @Nullable
    private final String makerPaymentMethodId;
    @Nullable
    private final String takerPaymentMethodId;

    public Contract(OfferPayload offerPayload,
                    long tradeAmount,
                    long tradePrice,
                    String takerFeeTxID,
                    NodeAddress buyerNodeAddress,
                    NodeAddress sellerNodeAddress,
                    NodeAddress mediatorNodeAddress,
                    boolean isBuyerMakerAndSellerTaker,
                    String makerAccountId,
                    String takerAccountId,
                    @Nullable PaymentAccountPayload makerPaymentAccountPayload,
                    @Nullable PaymentAccountPayload takerPaymentAccountPayload,
                    PubKeyRing makerPubKeyRing,
                    PubKeyRing takerPubKeyRing,
                    String makerPayoutAddressString,
                    String takerPayoutAddressString,
                    byte[] makerMultiSigPubKey,
                    byte[] takerMultiSigPubKey,
                    long lockTime,
                    NodeAddress refundAgentNodeAddress,
                    @Nullable byte[] hashOfMakersPaymentAccountPayload,
                    @Nullable byte[] hashOfTakersPaymentAccountPayload,
                    @Nullable String makerPaymentMethodId,
                    @Nullable String takerPaymentMethodId) {
        this.offerPayload = offerPayload;
        this.tradeAmount = tradeAmount;
        this.tradePrice = tradePrice;
        this.takerFeeTxID = takerFeeTxID;
        this.buyerNodeAddress = buyerNodeAddress;
        this.sellerNodeAddress = sellerNodeAddress;
        this.mediatorNodeAddress = mediatorNodeAddress;
        this.isBuyerMakerAndSellerTaker = isBuyerMakerAndSellerTaker;
        this.makerAccountId = makerAccountId;
        this.takerAccountId = takerAccountId;
        this.makerPaymentAccountPayload = makerPaymentAccountPayload;
        this.takerPaymentAccountPayload = takerPaymentAccountPayload;
        this.makerPubKeyRing = makerPubKeyRing;
        this.takerPubKeyRing = takerPubKeyRing;
        this.makerPayoutAddressString = makerPayoutAddressString;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.makerMultiSigPubKey = makerMultiSigPubKey;
        this.takerMultiSigPubKey = takerMultiSigPubKey;
        this.lockTime = lockTime;
        this.refundAgentNodeAddress = refundAgentNodeAddress;
        this.hashOfMakersPaymentAccountPayload = hashOfMakersPaymentAccountPayload;
        this.hashOfTakersPaymentAccountPayload = hashOfTakersPaymentAccountPayload;
        this.makerPaymentMethodId = makerPaymentMethodId;
        this.takerPaymentMethodId = takerPaymentMethodId;

        // Either makerPaymentMethodId is set, or obtained from offerPayload.
        if (makerPaymentMethodId == null) {
            makerPaymentMethodId = checkNotNull(offerPayload).getPaymentMethodId();
        }
        if (takerPaymentMethodId == null) {
            takerPaymentMethodId = checkNotNull(offerPayload).getPaymentMethodId();
        }
        checkNotNull(makerPaymentMethodId);
        checkNotNull(takerPaymentMethodId);

        // For SEPA offers we accept also SEPA_INSTANT takers
        // Otherwise both ids need to be the same
        boolean result = (makerPaymentMethodId.equals(PaymentMethod.SEPA_ID) && takerPaymentMethodId.equals(PaymentMethod.SEPA_INSTANT_ID)) ||
                makerPaymentMethodId.equals(takerPaymentMethodId);
        checkArgument(result, "payment methods of maker and taker must be the same.\n" +
                "makerPaymentMethodId=" + makerPaymentMethodId + "\n" +
                "takerPaymentMethodId=" + takerPaymentMethodId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Contract fromProto(protobuf.Contract proto, CoreProtoResolver coreProtoResolver) {
        PaymentAccountPayload makerPaymentAccountPayload = proto.hasMakerPaymentAccountPayload() ?
                coreProtoResolver.fromProto(proto.getMakerPaymentAccountPayload()) : null;
        PaymentAccountPayload takerPaymentAccountPayload = proto.hasTakerPaymentAccountPayload() ?
                coreProtoResolver.fromProto(proto.getTakerPaymentAccountPayload()) : null;
        return new Contract(OfferPayload.fromProto(proto.getOfferPayload()),
                proto.getTradeAmount(),
                proto.getTradePrice(),
                proto.getTakerFeeTxId(),
                NodeAddress.fromProto(proto.getBuyerNodeAddress()),
                NodeAddress.fromProto(proto.getSellerNodeAddress()),
                NodeAddress.fromProto(proto.getMediatorNodeAddress()),
                proto.getIsBuyerMakerAndSellerTaker(),
                proto.getMakerAccountId(),
                proto.getTakerAccountId(),
                makerPaymentAccountPayload,
                takerPaymentAccountPayload,
                PubKeyRing.fromProto(proto.getMakerPubKeyRing()),
                PubKeyRing.fromProto(proto.getTakerPubKeyRing()),
                proto.getMakerPayoutAddressString(),
                proto.getTakerPayoutAddressString(),
                proto.getMakerMultiSigPubKey().toByteArray(),
                proto.getTakerMultiSigPubKey().toByteArray(),
                proto.getLockTime(),
                NodeAddress.fromProto(proto.getRefundAgentNodeAddress()),
                ProtoUtil.byteArrayOrNullFromProto(proto.getHashOfMakersPaymentAccountPayload()),
                ProtoUtil.byteArrayOrNullFromProto(proto.getHashOfTakersPaymentAccountPayload()),
                ProtoUtil.stringOrNullFromProto(proto.getMakerPaymentMethodId()),
                ProtoUtil.stringOrNullFromProto(proto.getTakerPaymentMethodId())
        );
    }

    @Override
    public protobuf.Contract toProtoMessage() {
        protobuf.Contract.Builder builder = protobuf.Contract.newBuilder()
                .setOfferPayload(offerPayload.toProtoMessage().getOfferPayload())
                .setTradeAmount(tradeAmount)
                .setTradePrice(tradePrice)
                .setTakerFeeTxId(takerFeeTxID)
                .setBuyerNodeAddress(buyerNodeAddress.toProtoMessage())
                .setSellerNodeAddress(sellerNodeAddress.toProtoMessage())
                .setMediatorNodeAddress(mediatorNodeAddress.toProtoMessage())
                .setIsBuyerMakerAndSellerTaker(isBuyerMakerAndSellerTaker)
                .setMakerAccountId(makerAccountId)
                .setTakerAccountId(takerAccountId)
                .setMakerPubKeyRing(makerPubKeyRing.toProtoMessage())
                .setTakerPubKeyRing(takerPubKeyRing.toProtoMessage())
                .setMakerPayoutAddressString(makerPayoutAddressString)
                .setTakerPayoutAddressString(takerPayoutAddressString)
                .setMakerMultiSigPubKey(ByteString.copyFrom(makerMultiSigPubKey))
                .setTakerMultiSigPubKey(ByteString.copyFrom(takerMultiSigPubKey))
                .setLockTime(lockTime)
                .setRefundAgentNodeAddress(refundAgentNodeAddress.toProtoMessage());

        Optional.ofNullable(hashOfMakersPaymentAccountPayload)
                .ifPresent(e -> builder.setHashOfMakersPaymentAccountPayload(ByteString.copyFrom(hashOfMakersPaymentAccountPayload)));
        Optional.ofNullable(hashOfTakersPaymentAccountPayload)
                .ifPresent(e -> builder.setHashOfTakersPaymentAccountPayload(ByteString.copyFrom(hashOfTakersPaymentAccountPayload)));
        Optional.ofNullable(makerPaymentAccountPayload)
                .ifPresent(e -> builder.setMakerPaymentAccountPayload((protobuf.PaymentAccountPayload) makerPaymentAccountPayload.toProtoMessage()));
        Optional.ofNullable(takerPaymentAccountPayload)
                .ifPresent(e -> builder.setTakerPaymentAccountPayload((protobuf.PaymentAccountPayload) takerPaymentAccountPayload.toProtoMessage()));
        Optional.ofNullable(makerPaymentMethodId).ifPresent(e -> builder.setMakerPaymentMethodId(makerPaymentMethodId));
        Optional.ofNullable(takerPaymentMethodId).ifPresent(e -> builder.setTakerPaymentMethodId(takerPaymentMethodId));
        return builder.build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getBuyerPayoutAddressString() {
        return isBuyerMakerAndSellerTaker ? makerPayoutAddressString : takerPayoutAddressString;
    }

    public String getSellerPayoutAddressString() {
        return isBuyerMakerAndSellerTaker ? takerPayoutAddressString : makerPayoutAddressString;
    }

    public PubKeyRing getBuyerPubKeyRing() {
        return isBuyerMakerAndSellerTaker ? makerPubKeyRing : takerPubKeyRing;
    }

    public PubKeyRing getSellerPubKeyRing() {
        return isBuyerMakerAndSellerTaker ? takerPubKeyRing : makerPubKeyRing;
    }

    public byte[] getBuyerMultiSigPubKey() {
        return isBuyerMakerAndSellerTaker ? makerMultiSigPubKey : takerMultiSigPubKey;
    }

    public byte[] getSellerMultiSigPubKey() {
        return isBuyerMakerAndSellerTaker ? takerMultiSigPubKey : makerMultiSigPubKey;
    }

    @Nullable
    public PaymentAccountPayload getBuyerPaymentAccountPayload() {
        return isBuyerMakerAndSellerTaker ? makerPaymentAccountPayload : takerPaymentAccountPayload;
    }

    @Nullable
    public PaymentAccountPayload getSellerPaymentAccountPayload() {
        return isBuyerMakerAndSellerTaker ? takerPaymentAccountPayload : makerPaymentAccountPayload;
    }

    public void setPaymentAccountPayloads(PaymentAccountPayload peersPaymentAccountPayload,
                                          PaymentAccountPayload myPaymentAccountPayload,
                                          PubKeyRing myPubKeyRing) {
        if (isMyRoleMaker(myPubKeyRing)) {
            makerPaymentAccountPayload = myPaymentAccountPayload;
            takerPaymentAccountPayload = peersPaymentAccountPayload;
        } else {
            takerPaymentAccountPayload = myPaymentAccountPayload;
            makerPaymentAccountPayload = peersPaymentAccountPayload;
        }
    }

    public byte[] getHashOfPeersPaymentAccountPayload(PubKeyRing myPubKeyRing) {
        return isMyRoleMaker(myPubKeyRing) ? hashOfTakersPaymentAccountPayload : hashOfMakersPaymentAccountPayload;
    }

    public String getPaymentMethodId() {
        // Either makerPaymentMethodId is set or available in offerPayload
        return makerPaymentMethodId != null ? makerPaymentMethodId : Objects.requireNonNull(getOfferPayload()).getPaymentMethodId();
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmount);
    }

    public Volume getTradeVolume() {
        Volume volumeByAmount = getTradePrice().getVolumeByAmount(getTradeAmount());

        if (getPaymentMethodId().equals(PaymentMethod.HAL_CASH_ID))
            volumeByAmount = VolumeUtil.getAdjustedVolumeForHalCash(volumeByAmount);
        else if (CurrencyUtil.isFiatCurrency(getOfferPayload().getCurrencyCode()))
            volumeByAmount = VolumeUtil.getRoundedFiatVolume(volumeByAmount);

        return volumeByAmount;
    }

    public Price getTradePrice() {
        return Price.valueOf(offerPayload.getCurrencyCode(), tradePrice);
    }

    public NodeAddress getMyNodeAddress(PubKeyRing myPubKeyRing) {
        if (myPubKeyRing.equals(getBuyerPubKeyRing()))
            return buyerNodeAddress;
        else
            return sellerNodeAddress;
    }

    public NodeAddress getPeersNodeAddress(PubKeyRing myPubKeyRing) {
        if (myPubKeyRing.equals(getSellerPubKeyRing()))
            return buyerNodeAddress;
        else
            return sellerNodeAddress;
    }

    public PubKeyRing getPeersPubKeyRing(PubKeyRing myPubKeyRing) {
        if (myPubKeyRing.equals(getSellerPubKeyRing()))
            return getBuyerPubKeyRing();
        else
            return getSellerPubKeyRing();
    }

    public boolean isMyRoleBuyer(PubKeyRing myPubKeyRing) {
        return getBuyerPubKeyRing().equals(myPubKeyRing);
    }

    public boolean isMyRoleMaker(PubKeyRing myPubKeyRing) {
        return isBuyerMakerAndSellerTaker() == isMyRoleBuyer(myPubKeyRing);
    }

    public boolean maybeClearSensitiveData() {
        boolean changed = false;
        if (makerPaymentAccountPayload != null) {
            makerPaymentAccountPayload = null;
            changed = true;
        }
        if (takerPaymentAccountPayload != null) {
            takerPaymentAccountPayload = null;
            changed = true;
        }
        return changed;
    }

    // edits a contract json string, removing the payment account payloads
    public static String sanitizeContractAsJson(String contractAsJson) {
        return contractAsJson
                .replaceAll(
                        "\"takerPaymentAccountPayload\": \\{[^}]*}",
                        "\"takerPaymentAccountPayload\": null")
                .replaceAll(
                        "\"makerPaymentAccountPayload\": \\{[^}]*}",
                        "\"makerPaymentAccountPayload\": null");
    }

    public void printDiff(@Nullable String peersContractAsJson) {
        String json = JsonUtil.objectToJson(this);
        String diff = StringUtils.difference(json, peersContractAsJson);
        if (!diff.isEmpty()) {
            log.warn("Diff of both contracts: \n" + diff);
            log.warn("\n\n------------------------------------------------------------\n"
                    + "Contract as json\n"
                    + json
                    + "\n------------------------------------------------------------\n");

            log.warn("\n\n------------------------------------------------------------\n"
                    + "Peers contract as json\n"
                    + peersContractAsJson
                    + "\n------------------------------------------------------------\n");
        } else {
            log.debug("Both contracts are the same");
        }
    }

    @Override
    public String toString() {
        return "Contract{" +
                "\n     offerPayload=" + offerPayload +
                ",\n     tradeAmount=" + tradeAmount +
                ",\n     tradePrice=" + tradePrice +
                ",\n     takerFeeTxID='" + takerFeeTxID + '\'' +
                ",\n     buyerNodeAddress=" + buyerNodeAddress +
                ",\n     sellerNodeAddress=" + sellerNodeAddress +
                ",\n     mediatorNodeAddress=" + mediatorNodeAddress +
                ",\n     refundAgentNodeAddress=" + refundAgentNodeAddress +
                ",\n     isBuyerMakerAndSellerTaker=" + isBuyerMakerAndSellerTaker +
                ",\n     makerAccountId='" + makerAccountId + '\'' +
                ",\n     takerAccountId='" + takerAccountId + '\'' +
                ",\n     makerPubKeyRing=" + makerPubKeyRing +
                ",\n     takerPubKeyRing=" + takerPubKeyRing +
                ",\n     makerPayoutAddressString='" + makerPayoutAddressString + '\'' +
                ",\n     takerPayoutAddressString='" + takerPayoutAddressString + '\'' +
                ",\n     makerMultiSigPubKey=" + Utilities.bytesAsHexString(makerMultiSigPubKey) +
                ",\n     takerMultiSigPubKey=" + Utilities.bytesAsHexString(takerMultiSigPubKey) +
                ",\n     buyerMultiSigPubKey=" + Utilities.bytesAsHexString(getBuyerMultiSigPubKey()) +
                ",\n     sellerMultiSigPubKey=" + Utilities.bytesAsHexString(getSellerMultiSigPubKey()) +
                ",\n     lockTime=" + lockTime +
                ",\n     hashOfMakersPaymentAccountPayload=" + Utilities.bytesAsHexString(hashOfMakersPaymentAccountPayload) +
                ",\n     hashOfTakersPaymentAccountPayload=" + Utilities.bytesAsHexString(hashOfTakersPaymentAccountPayload) +
                ",\n     makerPaymentMethodId=" + makerPaymentMethodId +
                ",\n     takerPaymentMethodId=" + takerPaymentMethodId +
                "\n}";
    }
}

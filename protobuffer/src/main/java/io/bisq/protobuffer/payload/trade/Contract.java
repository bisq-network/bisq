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

package io.bisq.protobuffer.payload.trade;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.monetary.Price;
import io.bisq.common.util.JsonExclude;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.payload.Payload;
import io.bisq.protobuffer.payload.crypto.PubKeyRing;
import io.bisq.protobuffer.payload.offer.OfferPayload;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.concurrent.Immutable;

@EqualsAndHashCode
@Slf4j
@SuppressWarnings("WeakerAccess")
@Immutable
public final class Contract implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    @JsonExclude
    public static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    // Payload
    public final OfferPayload offerPayload;
    private final long tradeAmount;
    private final long tradePrice;
    public final String takeOfferFeeTxID;
    public final NodeAddress arbitratorNodeAddress;
    private final boolean isBuyerMakerAndSellerTaker;
    private final String makerAccountId;
    private final String takerAccountId;
    private final PaymentAccountPayload makerPaymentAccountPayload;
    private final PaymentAccountPayload takerPaymentAccountPayload;
    @JsonExclude
    private final PubKeyRing makerPubKeyRing;
    @JsonExclude
    private final PubKeyRing takerPubKeyRing;
    @Getter
    private final NodeAddress buyerNodeAddress;
    @Getter
    private final NodeAddress sellerNodeAddress;
    private final String makerPayoutAddressString;
    private final String takerPayoutAddressString;
    @JsonExclude
    private final byte[] makerMultiSigPubKey;
    @JsonExclude
    private final byte[] takerMultiSigPubKey;

    public Contract(OfferPayload offerPayload,
                    Coin tradeAmount,
                    Price tradePrice,
                    String takeOfferFeeTxID,
                    NodeAddress buyerNodeAddress,
                    NodeAddress sellerNodeAddress,
                    NodeAddress arbitratorNodeAddress,
                    boolean isBuyerMakerAndSellerTaker,
                    String makerAccountId,
                    String takerAccountId,
                    PaymentAccountPayload makerPaymentAccountPayload,
                    PaymentAccountPayload takerPaymentAccountPayload,
                    PubKeyRing makerPubKeyRing,
                    PubKeyRing takerPubKeyRing,
                    String makerPayoutAddressString,
                    String takerPayoutAddressString,
                    byte[] makerMultiSigPubKey,
                    byte[] takerMultiSigPubKey) {
        this.offerPayload = offerPayload;
        this.tradePrice = tradePrice.getValue();
        this.buyerNodeAddress = buyerNodeAddress;
        this.sellerNodeAddress = sellerNodeAddress;
        this.tradeAmount = tradeAmount.value;
        this.takeOfferFeeTxID = takeOfferFeeTxID;
        this.arbitratorNodeAddress = arbitratorNodeAddress;
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

        // PaymentMethod need to be the same
        Preconditions.checkArgument(makerPaymentAccountPayload.getPaymentMethodId()
                        .equals(takerPaymentAccountPayload.getPaymentMethodId()),
                "payment methods of maker and taker must be the same.\n" +
                        "makerPaymentMethodId=" + makerPaymentAccountPayload.getPaymentMethodId() + "\n" +
                        "takerPaymentMethodId=" + takerPaymentAccountPayload.getPaymentMethodId());
    }

    public boolean isBuyerMakerAndSellerTaker() {
        return isBuyerMakerAndSellerTaker;
    }

    public String getBuyerAccountId() {
        return isBuyerMakerAndSellerTaker ? makerAccountId : takerAccountId;
    }

    public String getSellerAccountId() {
        return isBuyerMakerAndSellerTaker ? takerAccountId : makerAccountId;
    }

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

    public PaymentAccountPayload getBuyerPaymentAccountPayload() {
        return isBuyerMakerAndSellerTaker ? makerPaymentAccountPayload : takerPaymentAccountPayload;
    }

    public PaymentAccountPayload getSellerPaymentAccountPayload() {
        return isBuyerMakerAndSellerTaker ? takerPaymentAccountPayload : makerPaymentAccountPayload;
    }

    public String getPaymentMethodId() {
        return makerPaymentAccountPayload.getPaymentMethodId();
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmount);
    }

    public Price getTradePrice() {
        return Price.valueOf(offerPayload.getCurrencyCode(), tradePrice);
    }


    @Override
    public PB.Contract toProto() {
        return PB.Contract.newBuilder()
                .setOfferPayload(offerPayload.toProto().getOfferPayload())
                .setTradeAmount(tradeAmount)
                .setTradePrice(tradePrice)
                .setTakeOfferFeeTxId(takeOfferFeeTxID)
                .setArbitratorNodeAddress(arbitratorNodeAddress.toProto())
                .setIsBuyerMakerAndSellerTaker(isBuyerMakerAndSellerTaker)
                .setMakerAccountId(makerAccountId)
                .setTakerAccountId(takerAccountId)
                .setMakerPaymentAccountPayload((PB.PaymentAccountPayload) makerPaymentAccountPayload.toProto())
                .setTakerPaymentAccountPayload((PB.PaymentAccountPayload) takerPaymentAccountPayload.toProto())
                .setMakerPubKeyRing(makerPubKeyRing.toProto())
                .setTakerPubKeyRing(takerPubKeyRing.toProto())
                .setBuyerNodeAddress(buyerNodeAddress.toProto())
                .setSellerNodeAddress(sellerNodeAddress.toProto())
                .setMakerPayoutAddressString(makerPayoutAddressString)
                .setTakerPayoutAddressString(takerPayoutAddressString)
                .setMakerBtcPubKey(ByteString.copyFrom(makerMultiSigPubKey))
                .setTakerBtcPubKey(ByteString.copyFrom(takerMultiSigPubKey)).build();
    }

    @Override
    public String toString() {
        return "Contract{" +
                "\n\toffer=" + offerPayload +
                "\n\ttradeAmount=" + tradeAmount +
                "\n\ttradePrice=" + tradePrice +
                "\n\ttakeOfferFeeTxID='" + takeOfferFeeTxID + '\'' +
                "\n\tarbitratorAddress=" + arbitratorNodeAddress +
                "\n\tisBuyerMakerAndSellerTaker=" + isBuyerMakerAndSellerTaker +
                "\n\tmakerAccountId='" + makerAccountId + '\'' +
                "\n\ttakerAccountId='" + takerAccountId + '\'' +
                "\n\tmakerPaymentAccountPayload=" + makerPaymentAccountPayload +
                "\n\ttakerPaymentAccountPayload=" + takerPaymentAccountPayload +
                "\n\tmakerPubKeyRing=" + makerPubKeyRing +
                "\n\ttakerPubKeyRing=" + takerPubKeyRing +
                "\n\tbuyerAddress=" + buyerNodeAddress +
                "\n\tsellerAddress=" + sellerNodeAddress +
                "\n\tmakerPayoutAddressString='" + makerPayoutAddressString + '\'' +
                "\n\ttakerPayoutAddressString='" + takerPayoutAddressString + '\'' +
                "\n\tmakerMultiSigPubKey=" + Hex.toHexString(makerMultiSigPubKey) +
                "\n\ttakerMultiSigPubKey=" + Hex.toHexString(takerMultiSigPubKey) +
                "\n\tBuyerMultiSigPubKey=" + Hex.toHexString(getBuyerMultiSigPubKey()) +
                "\n\tSellerMultiSigPubKey=" + Hex.toHexString(getSellerMultiSigPubKey()) +
                '}';
    }
}

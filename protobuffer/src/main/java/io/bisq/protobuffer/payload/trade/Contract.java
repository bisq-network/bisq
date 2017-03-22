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
    private final boolean isBuyerOffererAndSellerTaker;
    private final String offererAccountId;
    private final String takerAccountId;
    private final PaymentAccountPayload offererPaymentAccountPayload;
    private final PaymentAccountPayload takerPaymentAccountPayload;
    @JsonExclude
    private final PubKeyRing offererPubKeyRing;
    @JsonExclude
    private final PubKeyRing takerPubKeyRing;
    @Getter
    private final NodeAddress buyerNodeAddress;
    @Getter
    private final NodeAddress sellerNodeAddress;
    private final String offererPayoutAddressString;
    private final String takerPayoutAddressString;
    @JsonExclude
    private final byte[] offererMultiSigPubKey;
    @JsonExclude
    private final byte[] takerMultiSigPubKey;

    public Contract(OfferPayload offerPayload,
                    Coin tradeAmount,
                    Price tradePrice,
                    String takeOfferFeeTxID,
                    NodeAddress buyerNodeAddress,
                    NodeAddress sellerNodeAddress,
                    NodeAddress arbitratorNodeAddress,
                    boolean isBuyerOffererAndSellerTaker,
                    String offererAccountId,
                    String takerAccountId,
                    PaymentAccountPayload offererPaymentAccountPayload,
                    PaymentAccountPayload takerPaymentAccountPayload,
                    PubKeyRing offererPubKeyRing,
                    PubKeyRing takerPubKeyRing,
                    String offererPayoutAddressString,
                    String takerPayoutAddressString,
                    byte[] offererMultiSigPubKey,
                    byte[] takerMultiSigPubKey) {
        this.offerPayload = offerPayload;
        this.tradePrice = tradePrice.getValue();
        this.buyerNodeAddress = buyerNodeAddress;
        this.sellerNodeAddress = sellerNodeAddress;
        this.tradeAmount = tradeAmount.value;
        this.takeOfferFeeTxID = takeOfferFeeTxID;
        this.arbitratorNodeAddress = arbitratorNodeAddress;
        this.isBuyerOffererAndSellerTaker = isBuyerOffererAndSellerTaker;
        this.offererAccountId = offererAccountId;
        this.takerAccountId = takerAccountId;
        this.offererPaymentAccountPayload = offererPaymentAccountPayload;
        this.takerPaymentAccountPayload = takerPaymentAccountPayload;
        this.offererPubKeyRing = offererPubKeyRing;
        this.takerPubKeyRing = takerPubKeyRing;
        this.offererPayoutAddressString = offererPayoutAddressString;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.offererMultiSigPubKey = offererMultiSigPubKey;
        this.takerMultiSigPubKey = takerMultiSigPubKey;

        // PaymentMethod need to be the same
        Preconditions.checkArgument(offererPaymentAccountPayload.getPaymentMethodId()
                        .equals(takerPaymentAccountPayload.getPaymentMethodId()),
                "payment methods of maker and taker must be the same.\n" +
                        "offererPaymentMethodId=" + offererPaymentAccountPayload.getPaymentMethodId() + "\n" +
                        "takerPaymentMethodId=" + takerPaymentAccountPayload.getPaymentMethodId());
    }

    public boolean isBuyerOffererAndSellerTaker() {
        return isBuyerOffererAndSellerTaker;
    }

    public String getBuyerAccountId() {
        return isBuyerOffererAndSellerTaker ? offererAccountId : takerAccountId;
    }

    public String getSellerAccountId() {
        return isBuyerOffererAndSellerTaker ? takerAccountId : offererAccountId;
    }

    public String getBuyerPayoutAddressString() {
        return isBuyerOffererAndSellerTaker ? offererPayoutAddressString : takerPayoutAddressString;
    }

    public String getSellerPayoutAddressString() {
        return isBuyerOffererAndSellerTaker ? takerPayoutAddressString : offererPayoutAddressString;
    }

    public PubKeyRing getBuyerPubKeyRing() {
        return isBuyerOffererAndSellerTaker ? offererPubKeyRing : takerPubKeyRing;
    }

    public PubKeyRing getSellerPubKeyRing() {
        return isBuyerOffererAndSellerTaker ? takerPubKeyRing : offererPubKeyRing;
    }

    public byte[] getBuyerMultiSigPubKey() {
        return isBuyerOffererAndSellerTaker ? offererMultiSigPubKey : takerMultiSigPubKey;
    }

    public byte[] getSellerMultiSigPubKey() {
        return isBuyerOffererAndSellerTaker ? takerMultiSigPubKey : offererMultiSigPubKey;
    }

    public PaymentAccountPayload getBuyerPaymentAccountPayload() {
        return isBuyerOffererAndSellerTaker ? offererPaymentAccountPayload : takerPaymentAccountPayload;
    }

    public PaymentAccountPayload getSellerPaymentAccountPayload() {
        return isBuyerOffererAndSellerTaker ? takerPaymentAccountPayload : offererPaymentAccountPayload;
    }

    public String getPaymentMethodId() {
        return offererPaymentAccountPayload.getPaymentMethodId();
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
                .setIsBuyerOffererAndSellerTaker(isBuyerOffererAndSellerTaker)
                .setOffererAccountId(offererAccountId)
                .setTakerAccountId(takerAccountId)
                .setOffererPaymentAccountPayload((PB.PaymentAccountPayload) offererPaymentAccountPayload.toProto())
                .setTakerPaymentAccountPayload((PB.PaymentAccountPayload) takerPaymentAccountPayload.toProto())
                .setOffererPubKeyRing(offererPubKeyRing.toProto())
                .setTakerPubKeyRing(takerPubKeyRing.toProto())
                .setBuyerNodeAddress(buyerNodeAddress.toProto())
                .setSellerNodeAddress(sellerNodeAddress.toProto())
                .setOffererPayoutAddressString(offererPayoutAddressString)
                .setTakerPayoutAddressString(takerPayoutAddressString)
                .setOffererBtcPubKey(ByteString.copyFrom(offererMultiSigPubKey))
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
                "\n\tisBuyerOffererAndSellerTaker=" + isBuyerOffererAndSellerTaker +
                "\n\toffererAccountId='" + offererAccountId + '\'' +
                "\n\ttakerAccountId='" + takerAccountId + '\'' +
                "\n\toffererPaymentAccountPayload=" + offererPaymentAccountPayload +
                "\n\ttakerPaymentAccountPayload=" + takerPaymentAccountPayload +
                "\n\toffererPubKeyRing=" + offererPubKeyRing +
                "\n\ttakerPubKeyRing=" + takerPubKeyRing +
                "\n\tbuyerAddress=" + buyerNodeAddress +
                "\n\tsellerAddress=" + sellerNodeAddress +
                "\n\toffererPayoutAddressString='" + offererPayoutAddressString + '\'' +
                "\n\ttakerPayoutAddressString='" + takerPayoutAddressString + '\'' +
                "\n\toffererMultiSigPubKey=" + Hex.toHexString(offererMultiSigPubKey) +
                "\n\ttakerMultiSigPubKey=" + Hex.toHexString(takerMultiSigPubKey) +
                "\n\tBuyerMultiSigPubKey=" + Hex.toHexString(getBuyerMultiSigPubKey()) +
                "\n\tSellerMultiSigPubKey=" + Hex.toHexString(getSellerMultiSigPubKey()) +
                '}';
    }
}

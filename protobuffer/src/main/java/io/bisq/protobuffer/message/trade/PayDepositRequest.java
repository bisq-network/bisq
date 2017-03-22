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

package io.bisq.protobuffer.message.trade;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.p2p.MailboxMessage;
import io.bisq.protobuffer.payload.btc.RawTransactionInput;
import io.bisq.protobuffer.payload.crypto.PubKeyRing;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Immutable
public final class PayDepositRequest extends TradeMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final long tradeAmount;
    public final long tradePrice;
    public final byte[] takerMultiSigPubKey;
    public final Coin txFee;
    public final Coin takeOfferFee;
    public final List<RawTransactionInput> rawTransactionInputs;
    public final long changeOutputValue;
    @Nullable
    public final String changeOutputAddress;
    public final String takerPayoutAddressString;
    public final PubKeyRing takerPubKeyRing;
    public final PaymentAccountPayload takerPaymentAccountPayload;
    public final String takerAccountId;
    public final String takeOfferFeeTxId;
    public final List<NodeAddress> acceptedArbitratorNodeAddresses;
    public final NodeAddress arbitratorNodeAddress;
    private final NodeAddress senderNodeAddress;
    private final String uid;

    public PayDepositRequest(NodeAddress senderNodeAddress,
                             String tradeId,
                             long tradeAmount,
                             long tradePrice,
                             Coin txFee,
                             Coin takeOfferFee,
                             List<RawTransactionInput> rawTransactionInputs,
                             long changeOutputValue,
                             String changeOutputAddress,
                             byte[] takerMultiSigPubKey,
                             String takerPayoutAddressString,
                             PubKeyRing takerPubKeyRing,
                             PaymentAccountPayload takerPaymentAccountPayload,
                             String takerAccountId,
                             String takeOfferFeeTxId,
                             List<NodeAddress> acceptedArbitratorNodeAddresses,
                             NodeAddress arbitratorNodeAddress) {
        super(tradeId);
        this.senderNodeAddress = senderNodeAddress;
        this.tradeAmount = tradeAmount;
        this.tradePrice = tradePrice;
        this.txFee = txFee;
        this.takeOfferFee = takeOfferFee;
        this.rawTransactionInputs = rawTransactionInputs;
        this.changeOutputValue = changeOutputValue;
        this.changeOutputAddress = changeOutputAddress;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.takerPubKeyRing = takerPubKeyRing;
        this.takerMultiSigPubKey = takerMultiSigPubKey;
        this.takerPaymentAccountPayload = takerPaymentAccountPayload;
        this.takerAccountId = takerAccountId;
        this.takeOfferFeeTxId = takeOfferFeeTxId;
        this.acceptedArbitratorNodeAddresses = acceptedArbitratorNodeAddresses;
        this.arbitratorNodeAddress = arbitratorNodeAddress;
        uid = UUID.randomUUID().toString();
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public String getUID() {
        return uid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PayDepositRequest)) return false;
        if (!super.equals(o)) return false;

        PayDepositRequest that = (PayDepositRequest) o;

        if (tradeAmount != that.tradeAmount) return false;
        if (changeOutputValue != that.changeOutputValue) return false;
        if (!Arrays.equals(takerMultiSigPubKey, that.takerMultiSigPubKey)) return false;
        if (rawTransactionInputs != null ? !rawTransactionInputs.equals(that.rawTransactionInputs) : that.rawTransactionInputs != null)
            return false;
        if (changeOutputAddress != null ? !changeOutputAddress.equals(that.changeOutputAddress) : that.changeOutputAddress != null)
            return false;
        if (takerPayoutAddressString != null ? !takerPayoutAddressString.equals(that.takerPayoutAddressString) : that.takerPayoutAddressString != null)
            return false;
        if (takerPubKeyRing != null ? !takerPubKeyRing.equals(that.takerPubKeyRing) : that.takerPubKeyRing != null)
            return false;
        if (takerPaymentAccountPayload != null ? !takerPaymentAccountPayload.equals(that.takerPaymentAccountPayload) : that.takerPaymentAccountPayload != null)
            return false;
        if (takerAccountId != null ? !takerAccountId.equals(that.takerAccountId) : that.takerAccountId != null)
            return false;
        if (takeOfferFeeTxId != null ? !takeOfferFeeTxId.equals(that.takeOfferFeeTxId) : that.takeOfferFeeTxId != null)
            return false;
        if (acceptedArbitratorNodeAddresses != null ? !acceptedArbitratorNodeAddresses.equals(that.acceptedArbitratorNodeAddresses) : that.acceptedArbitratorNodeAddresses != null)
            return false;
        if (arbitratorNodeAddress != null ? !arbitratorNodeAddress.equals(that.arbitratorNodeAddress) : that.arbitratorNodeAddress != null)
            return false;
        if (senderNodeAddress != null ? !senderNodeAddress.equals(that.senderNodeAddress) : that.senderNodeAddress != null)
            return false;
        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (tradeAmount ^ (tradeAmount >>> 32));
        result = 31 * result + (takerMultiSigPubKey != null ? Arrays.hashCode(takerMultiSigPubKey) : 0);
        result = 31 * result + (rawTransactionInputs != null ? rawTransactionInputs.hashCode() : 0);
        result = 31 * result + (int) (changeOutputValue ^ (changeOutputValue >>> 32));
        result = 31 * result + (changeOutputAddress != null ? changeOutputAddress.hashCode() : 0);
        result = 31 * result + (takerPayoutAddressString != null ? takerPayoutAddressString.hashCode() : 0);
        result = 31 * result + (takerPubKeyRing != null ? takerPubKeyRing.hashCode() : 0);
        result = 31 * result + (takerPaymentAccountPayload != null ? takerPaymentAccountPayload.hashCode() : 0);
        result = 31 * result + (takerAccountId != null ? takerAccountId.hashCode() : 0);
        result = 31 * result + (takeOfferFeeTxId != null ? takeOfferFeeTxId.hashCode() : 0);
        result = 31 * result + (acceptedArbitratorNodeAddresses != null ? acceptedArbitratorNodeAddresses.hashCode() : 0);
        result = 31 * result + (arbitratorNodeAddress != null ? arbitratorNodeAddress.hashCode() : 0);
        result = 31 * result + (senderNodeAddress != null ? senderNodeAddress.hashCode() : 0);
        result = 31 * result + (uid != null ? uid.hashCode() : 0);
        return result;
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Message.getBaseEnvelope();
        PB.PayDepositRequest.Builder builderForValue = PB.PayDepositRequest.newBuilder()
                .setTradeId(tradeId)
                .setTradeAmount(tradeAmount)
                .setTradePrice(tradePrice)
                .setTakerMultiSigPubKey(ByteString.copyFrom(takerMultiSigPubKey))
                .setTxFee(PB.Coin.newBuilder().setValue(txFee.getValue()))
                .setTakeOfferFee(PB.Coin.newBuilder().setValue(takeOfferFee.getValue()))
                .addAllRawTransactionInputs(rawTransactionInputs.stream().map(rawTransactionInput -> rawTransactionInput.toProto()).collect(Collectors.toList()))
                .setChangeOutputValue(changeOutputValue)
                .setTakerPayoutAddressString(takerPayoutAddressString)
                .setTakerPubKeyRing(takerPubKeyRing.toProto())
                .setTakerPaymentAccountPayload((PB.PaymentAccountPayload) takerPaymentAccountPayload.toProto())
                .setTakerAccountId(takerAccountId)
                .setTakeOfferFeeTxId(takeOfferFeeTxId)
                .addAllAcceptedArbitratorNodeAddresses(acceptedArbitratorNodeAddresses.stream().map(nodeAddress -> nodeAddress.toProto()).collect(Collectors.toList()))
                .setArbitratorNodeAddress(arbitratorNodeAddress.toProto())
                .setSenderNodeAddress(senderNodeAddress.toProto())
                .setUid(uid);
        Optional.ofNullable(changeOutputAddress).ifPresent(builderForValue::setChangeOutputAddress);
        return baseEnvelope.setPayDepositRequest(builderForValue).build();
    }
}

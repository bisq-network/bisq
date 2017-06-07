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

package io.bisq.core.trade.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.proto.ProtoResolver;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@EqualsAndHashCode(callSuper = true)
@Value
public final class PayDepositRequest extends TradeMessage {
    private final NodeAddress senderNodeAddress;
    private final long tradeAmount;
    private final long tradePrice;
    private final byte[] takerMultiSigPubKey;
    private final long txFee;
    private final long takerFee;
    private final boolean isCurrencyForTakerFeeBtc;
    private final List<RawTransactionInput> rawTransactionInputs;
    private final long changeOutputValue;
    @Nullable
    private final String changeOutputAddress;
    private final String takerPayoutAddressString;
    private final PubKeyRing takerPubKeyRing;
    private final PaymentAccountPayload takerPaymentAccountPayload;
    private final String takerAccountId;
    private final String takeOfferFeeTxId;
    private final List<NodeAddress> acceptedArbitratorNodeAddresses;
    private final List<NodeAddress> acceptedMediatorNodeAddresses;
    private final NodeAddress arbitratorNodeAddress;
    private final NodeAddress mediatorNodeAddress;

    public PayDepositRequest(String tradeId,
                             NodeAddress senderNodeAddress,
                             long tradeAmount,
                             long tradePrice,
                             long txFee,
                             long takerFee,
                             boolean isCurrencyForTakerFeeBtc,
                             List<RawTransactionInput> rawTransactionInputs,
                             long changeOutputValue,
                             @Nullable String changeOutputAddress,
                             byte[] takerMultiSigPubKey,
                             String takerPayoutAddressString,
                             PubKeyRing takerPubKeyRing,
                             PaymentAccountPayload takerPaymentAccountPayload,
                             String takerAccountId,
                             String takeOfferFeeTxId,
                             List<NodeAddress> acceptedArbitratorNodeAddresses,
                             List<NodeAddress> acceptedMediatorNodeAddresses,
                             NodeAddress arbitratorNodeAddress,
                             NodeAddress mediatorNodeAddress) {
        super(tradeId);
        this.senderNodeAddress = senderNodeAddress;
        this.tradeAmount = tradeAmount;
        this.tradePrice = tradePrice;
        this.txFee = txFee;
        this.takerFee = takerFee;
        this.isCurrencyForTakerFeeBtc = isCurrencyForTakerFeeBtc;
        this.rawTransactionInputs = rawTransactionInputs;
        this.changeOutputValue = changeOutputValue;
        this.changeOutputAddress = changeOutputAddress;
        this.takerMultiSigPubKey = takerMultiSigPubKey;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.takerPubKeyRing = takerPubKeyRing;
        this.takerPaymentAccountPayload = takerPaymentAccountPayload;
        this.takerAccountId = takerAccountId;
        this.takeOfferFeeTxId = takeOfferFeeTxId;
        this.acceptedArbitratorNodeAddresses = acceptedArbitratorNodeAddresses;
        this.acceptedMediatorNodeAddresses = acceptedMediatorNodeAddresses;
        this.arbitratorNodeAddress = arbitratorNodeAddress;
        this.mediatorNodeAddress = mediatorNodeAddress;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.PayDepositRequest.Builder builder = PB.PayDepositRequest.newBuilder()
                .setTradeId(getTradeId())
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setTradeAmount(tradeAmount)
                .setTradePrice(tradePrice)
                .setTxFee(txFee)
                .setTakerFee(takerFee)
                .setIsCurrencyForTakerFeeBtc(isCurrencyForTakerFeeBtc)
                .addAllRawTransactionInputs(rawTransactionInputs.stream()
                        .map(RawTransactionInput::toProtoMessage).collect(Collectors.toList()))
                .setChangeOutputValue(changeOutputValue)
                .setTakerMultiSigPubKey(ByteString.copyFrom(takerMultiSigPubKey))
                .setTakerPayoutAddressString(takerPayoutAddressString)
                .setTakerPubKeyRing(takerPubKeyRing.toProtoMessage())
                .setTakerPaymentAccountPayload((PB.PaymentAccountPayload) takerPaymentAccountPayload.toProtoMessage())
                .setTakerAccountId(takerAccountId)
                .setTakerFeeTxId(takeOfferFeeTxId)
                .addAllAcceptedArbitratorNodeAddresses(acceptedArbitratorNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage).collect(Collectors.toList()))
                .addAllAcceptedMediatorNodeAddresses(acceptedMediatorNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage).collect(Collectors.toList()))
                .setArbitratorNodeAddress(arbitratorNodeAddress.toProtoMessage())
                .setMediatorNodeAddress(mediatorNodeAddress.toProtoMessage());
        Optional.ofNullable(changeOutputAddress).ifPresent(builder::setChangeOutputAddress);
        return NetworkEnvelope.getDefaultBuilder().setPayDepositRequest(builder).build();
    }

    public static PayDepositRequest fromProto(PB.PayDepositRequest proto, ProtoResolver protoResolver) {
        List<RawTransactionInput> rawTransactionInputs = proto.getRawTransactionInputsList().stream()
                .map(rawTransactionInput -> new RawTransactionInput(rawTransactionInput.getIndex(),
                        rawTransactionInput.getParentTransaction().toByteArray(), rawTransactionInput.getValue()))
                .collect(Collectors.toList());
        List<NodeAddress> arbitratorNodeAddresses = proto.getAcceptedArbitratorNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());
        List<NodeAddress> mediatorNodeAddresses = proto.getAcceptedMediatorNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());

        return new PayDepositRequest(proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getTradeAmount(),
                proto.getTradePrice(),
                proto.getTxFee(),
                proto.getTakerFee(),
                proto.getIsCurrencyForTakerFeeBtc(),
                rawTransactionInputs,
                proto.getChangeOutputValue(),
                proto.getChangeOutputAddress(),
                proto.getTakerMultiSigPubKey().toByteArray(),
                proto.getTakerPayoutAddressString(),
                PubKeyRing.fromProto(proto.getTakerPubKeyRing()),
                (PaymentAccountPayload) protoResolver.fromProto(proto.getTakerPaymentAccountPayload()),
                proto.getTakerAccountId(),
                proto.getTakerFeeTxId(),
                arbitratorNodeAddresses,
                mediatorNodeAddresses,
                NodeAddress.fromProto(proto.getArbitratorNodeAddress()),
                NodeAddress.fromProto(proto.getMediatorNodeAddress()));
    }
}

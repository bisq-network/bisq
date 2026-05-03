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

package bisq.core.trade.protocol.bisq_v1.messages;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

import static bisq.core.util.Validator.*;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class InputsForDepositTxRequest extends TradeMessage implements DirectMessage {
    private final NodeAddress senderNodeAddress;
    private final long tradeAmount;
    private final long tradePrice;
    private final long txFee;
    private final long takerFee;
    private final boolean isCurrencyForTakerFeeBtc;
    private final List<RawTransactionInput> rawTransactionInputs;
    private final byte[] takerMultiSigPubKey;
    private final String takerPayoutAddressString;
    private final PubKeyRing takerPubKeyRing;

    // Removed with 1.7.0
    @Nullable
    private final PaymentAccountPayload takerPaymentAccountPayload;

    private final String takerAccountId;
    private final String takerFeeTxId;
    private final List<NodeAddress> acceptedArbitratorNodeAddresses;
    private final List<NodeAddress> acceptedMediatorNodeAddresses;
    private final List<NodeAddress> acceptedRefundAgentNodeAddresses;
    @Nullable
    private final NodeAddress arbitratorNodeAddress;
    private final NodeAddress mediatorNodeAddress;
    private final NodeAddress refundAgentNodeAddress;

    private final byte[] accountAgeWitnessSignatureOfOfferId;
    private final long currentDate;

    // Added at 1.7.0
    @Nullable
    private final byte[] hashOfTakersPaymentAccountPayload;
    @Nullable
    private final String takersPaymentMethodId;

    // Added in v 1.9.7
    private final int burningManSelectionHeight;

    public InputsForDepositTxRequest(String tradeId,
                                     NodeAddress senderNodeAddress,
                                     long tradeAmount,
                                     long tradePrice,
                                     long txFee,
                                     long takerFee,
                                     boolean isCurrencyForTakerFeeBtc,
                                     List<RawTransactionInput> rawTransactionInputs,
                                     byte[] takerMultiSigPubKey,
                                     String takerPayoutAddressString,
                                     PubKeyRing takerPubKeyRing,
                                     @Nullable PaymentAccountPayload takerPaymentAccountPayload,
                                     String takerAccountId,
                                     String takerFeeTxId,
                                     List<NodeAddress> acceptedArbitratorNodeAddresses,
                                     List<NodeAddress> acceptedMediatorNodeAddresses,
                                     List<NodeAddress> acceptedRefundAgentNodeAddresses,
                                     @Nullable NodeAddress arbitratorNodeAddress,
                                     NodeAddress mediatorNodeAddress,
                                     NodeAddress refundAgentNodeAddress,
                                     String uid,
                                     int messageVersion,
                                     byte[] accountAgeWitnessSignatureOfOfferId,
                                     long currentDate,
                                     @Nullable byte[] hashOfTakersPaymentAccountPayload,
                                     @Nullable String takersPaymentMethodId,
                                     int burningManSelectionHeight) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.tradeAmount = tradeAmount;
        this.tradePrice = tradePrice;
        this.txFee = txFee;
        this.takerFee = takerFee;
        this.isCurrencyForTakerFeeBtc = isCurrencyForTakerFeeBtc;
        this.rawTransactionInputs = rawTransactionInputs;
        this.takerMultiSigPubKey = takerMultiSigPubKey;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.takerPubKeyRing = takerPubKeyRing;
        this.takerPaymentAccountPayload = takerPaymentAccountPayload;
        this.takerAccountId = takerAccountId;
        this.takerFeeTxId = takerFeeTxId;
        this.acceptedArbitratorNodeAddresses = acceptedArbitratorNodeAddresses;
        this.acceptedMediatorNodeAddresses = acceptedMediatorNodeAddresses;
        this.acceptedRefundAgentNodeAddresses = acceptedRefundAgentNodeAddresses;
        this.arbitratorNodeAddress = arbitratorNodeAddress;
        this.mediatorNodeAddress = mediatorNodeAddress;
        this.refundAgentNodeAddress = refundAgentNodeAddress;
        this.accountAgeWitnessSignatureOfOfferId = accountAgeWitnessSignatureOfOfferId;
        this.currentDate = currentDate;
        this.hashOfTakersPaymentAccountPayload = hashOfTakersPaymentAccountPayload;
        this.takersPaymentMethodId = takersPaymentMethodId;
        this.burningManSelectionHeight = burningManSelectionHeight;

        validate();
    }

    private void validate() {
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null");
        checkArgument(tradeAmount > 0, "tradeAmount must be positive");
        checkArgument(tradePrice > 0, "tradePrice must be positive");
        checkArgument(txFee > 0, "txFee must be positive");
        checkArgument(takerFee > 0, "takerFee must be positive");
        checkList(rawTransactionInputs, true, "rawTransactionInputs");
        checkNonEmptyBytes(takerMultiSigPubKey, "takerMultiSigPubKey");
        checkNonEmptyString(takerPayoutAddressString, "takerPayoutAddressString");
        checkNotNull(takerPubKeyRing, "takerPubKeyRing must not be null");
        checkNonEmptyString(takerAccountId, "takerAccountId");
        checkNonEmptyString(takerFeeTxId, "takerFeeTxId");
        checkList(acceptedArbitratorNodeAddresses, false, "acceptedArbitratorNodeAddresses");
        checkList(acceptedMediatorNodeAddresses, false, "acceptedMediatorNodeAddresses");
        checkList(acceptedRefundAgentNodeAddresses, false, "acceptedRefundAgentNodeAddresses");
        checkNotNull(mediatorNodeAddress, "mediatorNodeAddress must not be null");
        checkNotNull(refundAgentNodeAddress, "refundAgentNodeAddress must not be null");
        checkNonEmptyBytes(accountAgeWitnessSignatureOfOfferId, "accountAgeWitnessSignatureOfOfferId");
        checkArgument(currentDate > 0, "currentDate must be positive");
        checkNullableBytes(hashOfTakersPaymentAccountPayload, "hashOfTakersPaymentAccountPayload");
        checkNullableString(takersPaymentMethodId, "takersPaymentMethodId");
        checkArgument(burningManSelectionHeight > 0, "burningManSelectionHeight must be positive");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.InputsForDepositTxRequest.Builder builder = protobuf.InputsForDepositTxRequest.newBuilder()
                .setTradeId(tradeId)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setTradeAmount(tradeAmount)
                .setTradePrice(tradePrice)
                .setTxFee(txFee)
                .setTakerFee(takerFee)
                .setIsCurrencyForTakerFeeBtc(isCurrencyForTakerFeeBtc)
                .addAllRawTransactionInputs(rawTransactionInputs.stream()
                        .map(RawTransactionInput::toProtoMessage).collect(Collectors.toList()))
                .setTakerMultiSigPubKey(ByteString.copyFrom(takerMultiSigPubKey))
                .setTakerPayoutAddressString(takerPayoutAddressString)
                .setTakerPubKeyRing(takerPubKeyRing.toProtoMessage())
                .setTakerAccountId(takerAccountId)
                .setTakerFeeTxId(takerFeeTxId)
                .addAllAcceptedArbitratorNodeAddresses(acceptedArbitratorNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage).collect(Collectors.toList()))
                .addAllAcceptedMediatorNodeAddresses(acceptedMediatorNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage).collect(Collectors.toList()))
                .addAllAcceptedRefundAgentNodeAddresses(acceptedRefundAgentNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage).collect(Collectors.toList()))
                .setMediatorNodeAddress(mediatorNodeAddress.toProtoMessage())
                .setRefundAgentNodeAddress(refundAgentNodeAddress.toProtoMessage())
                .setUid(uid)
                .setAccountAgeWitnessSignatureOfOfferId(ByteString.copyFrom(accountAgeWitnessSignatureOfOfferId))
                .setCurrentDate(currentDate)
                .setBurningManSelectionHeight(burningManSelectionHeight);

        Optional.ofNullable(arbitratorNodeAddress).ifPresent(e -> builder.setArbitratorNodeAddress(arbitratorNodeAddress.toProtoMessage()));
        Optional.ofNullable(takerPaymentAccountPayload).ifPresent(e -> builder.setTakerPaymentAccountPayload((protobuf.PaymentAccountPayload) takerPaymentAccountPayload.toProtoMessage()));
        Optional.ofNullable(hashOfTakersPaymentAccountPayload).ifPresent(e -> builder.setHashOfTakersPaymentAccountPayload(ByteString.copyFrom(hashOfTakersPaymentAccountPayload)));
        Optional.ofNullable(takersPaymentMethodId).ifPresent(e -> builder.setTakersPayoutMethodId(takersPaymentMethodId));
        return getNetworkEnvelopeBuilder().setInputsForDepositTxRequest(builder).build();
    }

    public static InputsForDepositTxRequest fromProto(protobuf.InputsForDepositTxRequest proto,
                                                      CoreProtoResolver coreProtoResolver,
                                                      int messageVersion) {
        List<RawTransactionInput> rawTransactionInputs = proto.getRawTransactionInputsList().stream()
                .map(RawTransactionInput::fromProto)
                .collect(Collectors.toList());
        List<NodeAddress> acceptedArbitratorNodeAddresses = proto.getAcceptedArbitratorNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());
        List<NodeAddress> acceptedMediatorNodeAddresses = proto.getAcceptedMediatorNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());
        List<NodeAddress> acceptedRefundAgentNodeAddresses = proto.getAcceptedRefundAgentNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());

        PaymentAccountPayload takerPaymentAccountPayload = proto.hasTakerPaymentAccountPayload() ?
                coreProtoResolver.fromProto(proto.getTakerPaymentAccountPayload()) : null;
        byte[] hashOfTakersPaymentAccountPayload = ProtoUtil.byteArrayOrNullFromProto(proto.getHashOfTakersPaymentAccountPayload());

        return new InputsForDepositTxRequest(proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getTradeAmount(),
                proto.getTradePrice(),
                proto.getTxFee(),
                proto.getTakerFee(),
                proto.getIsCurrencyForTakerFeeBtc(),
                rawTransactionInputs,
                proto.getTakerMultiSigPubKey().toByteArray(),
                proto.getTakerPayoutAddressString(),
                PubKeyRing.fromProto(proto.getTakerPubKeyRing()),
                takerPaymentAccountPayload,
                proto.getTakerAccountId(),
                proto.getTakerFeeTxId(),
                acceptedArbitratorNodeAddresses,
                acceptedMediatorNodeAddresses,
                acceptedRefundAgentNodeAddresses,
                NodeAddress.fromProto(proto.getArbitratorNodeAddress()),
                NodeAddress.fromProto(proto.getMediatorNodeAddress()),
                NodeAddress.fromProto(proto.getRefundAgentNodeAddress()),
                proto.getUid(),
                messageVersion,
                ProtoUtil.byteArrayOrNullFromProto(proto.getAccountAgeWitnessSignatureOfOfferId()),
                proto.getCurrentDate(),
                hashOfTakersPaymentAccountPayload,
                ProtoUtil.stringOrNullFromProto(proto.getTakersPayoutMethodId()),
                proto.getBurningManSelectionHeight());
    }

    @Override
    public String toString() {
        return "InputsForDepositTxRequest{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     tradeAmount=" + tradeAmount +
                ",\n     tradePrice=" + tradePrice +
                ",\n     txFee=" + txFee +
                ",\n     takerFee=" + takerFee +
                ",\n     isCurrencyForTakerFeeBtc=" + isCurrencyForTakerFeeBtc +
                ",\n     rawTransactionInputs=" + rawTransactionInputs +
                ",\n     takerMultiSigPubKey=" + Utilities.bytesAsHexString(takerMultiSigPubKey) +
                ",\n     takerPayoutAddressString='" + takerPayoutAddressString + '\'' +
                ",\n     takerPubKeyRing=" + takerPubKeyRing +
                ",\n     takerAccountId='" + takerAccountId + '\'' +
                ",\n     takerFeeTxId='" + takerFeeTxId + '\'' +
                ",\n     acceptedArbitratorNodeAddresses=" + acceptedArbitratorNodeAddresses +
                ",\n     acceptedMediatorNodeAddresses=" + acceptedMediatorNodeAddresses +
                ",\n     acceptedRefundAgentNodeAddresses=" + acceptedRefundAgentNodeAddresses +
                ",\n     arbitratorNodeAddress=" + arbitratorNodeAddress +
                ",\n     mediatorNodeAddress=" + mediatorNodeAddress +
                ",\n     refundAgentNodeAddress=" + refundAgentNodeAddress +
                ",\n     accountAgeWitnessSignatureOfOfferId=" + Utilities.bytesAsHexString(accountAgeWitnessSignatureOfOfferId) +
                ",\n     currentDate=" + currentDate +
                ",\n     hashOfTakersPaymentAccountPayload=" + Utilities.bytesAsHexString(hashOfTakersPaymentAccountPayload) +
                ",\n     takersPaymentMethodId=" + takersPaymentMethodId +
                ",\n     burningManSelectionHeight=" + burningManSelectionHeight +
                "\n} " + super.toString();
    }
}

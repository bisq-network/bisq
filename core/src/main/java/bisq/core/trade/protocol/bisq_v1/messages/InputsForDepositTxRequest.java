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
import bisq.core.dao.burningman.BurningManAddressListService;
import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendersSignaturePubKeyProvidingPayload;

import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;

import java.security.PublicKey;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

import static bisq.core.trade.protocol.bisq_v1.messages.TradeMessageValidator.checkNodeAddress;
import static bisq.core.trade.protocol.bisq_v1.messages.TradeMessageValidator.checkNodeAddressList;
import static bisq.core.trade.protocol.bisq_v1.messages.TradeMessageValidator.checkRawTransactionInputList;
import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class InputsForDepositTxRequest extends TradeMessage
        implements DirectMessage, SendersSignaturePubKeyProvidingPayload {
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
    private final String takerAccountId;
    private final String takerFeeTxId;
    private final List<NodeAddress> acceptedMediatorNodeAddresses;
    private final List<NodeAddress> acceptedRefundAgentNodeAddresses;
    private final NodeAddress mediatorNodeAddress;
    private final NodeAddress refundAgentNodeAddress;

    private final byte[] accountAgeWitnessSignatureOfOfferId;
    private final long currentDate;

    // Added at 1.7.0
    private final byte[] hashOfTakersPaymentAccountPayload;
    private final String takersPaymentMethodId;

    // Added in v 1.9.7
    private final int burningManSelectionHeight;
    private final List<Integer> supportedBurningManAddressListVersions;

    @Nullable
    private final PubKeyRing mediatorPubKeyRing;
    @Nullable
    private final PubKeyRing refundAgentPubKeyRing;

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
                                     String takerAccountId,
                                     String takerFeeTxId,
                                     List<NodeAddress> acceptedMediatorNodeAddresses,
                                     List<NodeAddress> acceptedRefundAgentNodeAddresses,
                                     NodeAddress mediatorNodeAddress,
                                     NodeAddress refundAgentNodeAddress,
                                     String uid,
                                     int messageVersion,
                                     byte[] accountAgeWitnessSignatureOfOfferId,
                                     long currentDate,
                                     byte[] hashOfTakersPaymentAccountPayload,
                                     String takersPaymentMethodId,
                                     int burningManSelectionHeight,
                                     List<Integer> supportedBurningManAddressListVersions) {
        this(tradeId,
                senderNodeAddress,
                tradeAmount,
                tradePrice,
                txFee,
                takerFee,
                isCurrencyForTakerFeeBtc,
                rawTransactionInputs,
                takerMultiSigPubKey,
                takerPayoutAddressString,
                takerPubKeyRing,
                takerAccountId,
                takerFeeTxId,
                acceptedMediatorNodeAddresses,
                acceptedRefundAgentNodeAddresses,
                mediatorNodeAddress,
                refundAgentNodeAddress,
                uid,
                messageVersion,
                accountAgeWitnessSignatureOfOfferId,
                currentDate,
                hashOfTakersPaymentAccountPayload,
                takersPaymentMethodId,
                burningManSelectionHeight,
                supportedBurningManAddressListVersions,
                null,
                null);
    }

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
                                     String takerAccountId,
                                     String takerFeeTxId,
                                     List<NodeAddress> acceptedMediatorNodeAddresses,
                                     List<NodeAddress> acceptedRefundAgentNodeAddresses,
                                     NodeAddress mediatorNodeAddress,
                                     NodeAddress refundAgentNodeAddress,
                                     String uid,
                                     int messageVersion,
                                     byte[] accountAgeWitnessSignatureOfOfferId,
                                     long currentDate,
                                     byte[] hashOfTakersPaymentAccountPayload,
                                     String takersPaymentMethodId,
                                     int burningManSelectionHeight,
                                     List<Integer> supportedBurningManAddressListVersions,
                                     @Nullable PubKeyRing mediatorPubKeyRing,
                                     @Nullable PubKeyRing refundAgentPubKeyRing) {
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
        this.takerAccountId = takerAccountId;
        this.takerFeeTxId = takerFeeTxId;
        this.acceptedMediatorNodeAddresses = acceptedMediatorNodeAddresses;
        this.acceptedRefundAgentNodeAddresses = acceptedRefundAgentNodeAddresses;
        this.mediatorNodeAddress = mediatorNodeAddress;
        this.refundAgentNodeAddress = refundAgentNodeAddress;
        this.accountAgeWitnessSignatureOfOfferId = accountAgeWitnessSignatureOfOfferId;
        this.currentDate = currentDate;
        this.hashOfTakersPaymentAccountPayload = hashOfTakersPaymentAccountPayload;
        this.takersPaymentMethodId = takersPaymentMethodId;
        this.burningManSelectionHeight = burningManSelectionHeight;
        this.supportedBurningManAddressListVersions = supportedBurningManAddressListVersions;
        this.mediatorPubKeyRing = mediatorPubKeyRing;
        this.refundAgentPubKeyRing = refundAgentPubKeyRing;

        validate();
    }

    private void validate() {
        checkNodeAddress(senderNodeAddress, "senderNodeAddress");
        checkArgument(tradeAmount > 0, "tradeAmount must be positive");
        checkArgument(tradePrice > 0, "tradePrice must be positive");
        checkArgument(txFee > 0, "txFee must be positive");
        checkArgument(takerFee > 0, "takerFee must be positive");
        checkRawTransactionInputList(rawTransactionInputs, true, "rawTransactionInputs");
        checkNonEmptyBytes(takerMultiSigPubKey, "takerMultiSigPubKey");
        checkNonBlankString(takerPayoutAddressString, "takerPayoutAddressString");
        checkNotNull(takerPubKeyRing, "takerPubKeyRing must not be null");
        checkNonBlankString(takerAccountId, "takerAccountId");
        checkNonBlankString(takerFeeTxId, "takerFeeTxId");
        checkNodeAddressList(acceptedMediatorNodeAddresses, false, "acceptedMediatorNodeAddresses");
        checkNodeAddressList(acceptedRefundAgentNodeAddresses, false, "acceptedRefundAgentNodeAddresses");
        checkNodeAddress(mediatorNodeAddress, "mediatorNodeAddress");
        checkNodeAddress(refundAgentNodeAddress, "refundAgentNodeAddress");
        checkNonEmptyBytes(accountAgeWitnessSignatureOfOfferId, "accountAgeWitnessSignatureOfOfferId");
        checkArgument(currentDate > 0, "currentDate must be positive");
        checkNonEmptyBytes(hashOfTakersPaymentAccountPayload, "hashOfTakersPaymentAccountPayload");
        checkNonBlankString(takersPaymentMethodId, "takersPaymentMethodId");
        checkArgument(burningManSelectionHeight > 0, "burningManSelectionHeight must be positive");
        BurningManAddressListService.getValidatedSupportedVersions(supportedBurningManAddressListVersions);
        checkArgument((mediatorPubKeyRing == null) == (refundAgentPubKeyRing == null),
                "mediatorPubKeyRing and refundAgentPubKeyRing must both be set or both be null");
    }

    @Override
    public PublicKey getSenderSignaturePubKey() {
        return takerPubKeyRing.getSignaturePubKey();
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
                .addAllAcceptedMediatorNodeAddresses(acceptedMediatorNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage).collect(Collectors.toList()))
                .addAllAcceptedRefundAgentNodeAddresses(acceptedRefundAgentNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage).collect(Collectors.toList()))
                .setMediatorNodeAddress(mediatorNodeAddress.toProtoMessage())
                .setRefundAgentNodeAddress(refundAgentNodeAddress.toProtoMessage())
                .setUid(uid)
                .setAccountAgeWitnessSignatureOfOfferId(ByteString.copyFrom(accountAgeWitnessSignatureOfOfferId))
                .setCurrentDate(currentDate)
                .setBurningManSelectionHeight(burningManSelectionHeight)
                .addAllSupportedBurningManAddressListVersions(supportedBurningManAddressListVersions)
                .setHashOfTakersPaymentAccountPayload(ByteString.copyFrom(hashOfTakersPaymentAccountPayload))
                .setTakersPayoutMethodId(takersPaymentMethodId);

        Optional.ofNullable(mediatorPubKeyRing).ifPresent(e -> builder.setMediatorPubKeyRing(e.toProtoMessage()));
        Optional.ofNullable(refundAgentPubKeyRing).ifPresent(e -> builder.setRefundAgentPubKeyRing(e.toProtoMessage()));

        return getNetworkEnvelopeBuilder().setInputsForDepositTxRequest(builder).build();
    }

    public static InputsForDepositTxRequest fromProto(protobuf.InputsForDepositTxRequest proto,
                                                      int messageVersion) {
        List<RawTransactionInput> rawTransactionInputs = proto.getRawTransactionInputsList().stream()
                .map(RawTransactionInput::fromProto)
                .collect(Collectors.toList());
        List<NodeAddress> acceptedMediatorNodeAddresses = proto.getAcceptedMediatorNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());
        List<NodeAddress> acceptedRefundAgentNodeAddresses = proto.getAcceptedRefundAgentNodeAddressesList().stream()
                .map(NodeAddress::fromProto).collect(Collectors.toList());

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
                proto.getTakerAccountId(),
                proto.getTakerFeeTxId(),
                acceptedMediatorNodeAddresses,
                acceptedRefundAgentNodeAddresses,
                NodeAddress.fromProto(proto.getMediatorNodeAddress()),
                NodeAddress.fromProto(proto.getRefundAgentNodeAddress()),
                proto.getUid(),
                messageVersion,
                ProtoUtil.byteArrayOrNullFromProto(proto.getAccountAgeWitnessSignatureOfOfferId()),
                proto.getCurrentDate(),
                proto.getHashOfTakersPaymentAccountPayload().toByteArray(),
                proto.getTakersPayoutMethodId(),
                proto.getBurningManSelectionHeight(),
                proto.getSupportedBurningManAddressListVersionsList(),
                proto.hasMediatorPubKeyRing() ? PubKeyRing.fromProto(proto.getMediatorPubKeyRing()) : null,
                proto.hasRefundAgentPubKeyRing() ? PubKeyRing.fromProto(proto.getRefundAgentPubKeyRing()) : null);
    }

    public boolean hasDisputeAgentPubKeyRings() {
        return mediatorPubKeyRing != null && refundAgentPubKeyRing != null;
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
                ",\n     acceptedMediatorNodeAddresses=" + acceptedMediatorNodeAddresses +
                ",\n     acceptedRefundAgentNodeAddresses=" + acceptedRefundAgentNodeAddresses +
                ",\n     mediatorNodeAddress=" + mediatorNodeAddress +
                ",\n     refundAgentNodeAddress=" + refundAgentNodeAddress +
                ",\n     accountAgeWitnessSignatureOfOfferId=" + Utilities.bytesAsHexString(accountAgeWitnessSignatureOfOfferId) +
                ",\n     currentDate=" + currentDate +
                ",\n     hashOfTakersPaymentAccountPayload=" + Utilities.bytesAsHexString(hashOfTakersPaymentAccountPayload) +
                ",\n     takersPaymentMethodId=" + takersPaymentMethodId +
                ",\n     burningManSelectionHeight=" + burningManSelectionHeight +
                ",\n     supportedBurningManAddressListVersions=" + supportedBurningManAddressListVersions +
                ",\n     mediatorPubKeyRing=" + mediatorPubKeyRing +
                ",\n     refundAgentPubKeyRing=" + refundAgentPubKeyRing +
                "\n} " + super.toString();
    }

    public Coin getTradeAmountAsCoin() {
        return Coin.valueOf(tradeAmount);
    }

    public Coin getTxFeeAsCoin() {
        return Coin.valueOf(txFee);
    }

    public Coin getTakerFeeAsCoin() {
        return Coin.valueOf(takerFee);
    }
}

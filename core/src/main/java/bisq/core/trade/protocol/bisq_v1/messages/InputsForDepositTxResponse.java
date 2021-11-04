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

import bisq.common.app.Version;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class InputsForDepositTxResponse extends TradeMessage implements DirectMessage {
    // Removed with 1.7.0
    @Nullable
    private final PaymentAccountPayload makerPaymentAccountPayload;

    private final String makerAccountId;
    private final byte[] makerMultiSigPubKey;
    private final String makerContractAsJson;
    private final String makerContractSignature;
    private final String makerPayoutAddressString;
    private final byte[] preparedDepositTx;
    private final List<RawTransactionInput> makerInputs;
    private final NodeAddress senderNodeAddress;

    // added in v 0.6. can be null if we trade with an older peer
    @Nullable
    private final byte[] accountAgeWitnessSignatureOfPreparedDepositTx;
    private final long currentDate;
    private final long lockTime;

    // Added at 1.7.0
    @Nullable
    private final byte[] hashOfMakersPaymentAccountPayload;
    @Nullable
    private final String makersPaymentMethodId;

    public InputsForDepositTxResponse(String tradeId,
                                      @Nullable PaymentAccountPayload makerPaymentAccountPayload,
                                      String makerAccountId,
                                      byte[] makerMultiSigPubKey,
                                      String makerContractAsJson,
                                      String makerContractSignature,
                                      String makerPayoutAddressString,
                                      byte[] preparedDepositTx,
                                      List<RawTransactionInput> makerInputs,
                                      NodeAddress senderNodeAddress,
                                      String uid,
                                      @Nullable byte[] accountAgeWitnessSignatureOfPreparedDepositTx,
                                      long currentDate,
                                      long lockTime,
                                      @Nullable byte[] hashOfMakersPaymentAccountPayload,
                                      @Nullable String makersPaymentMethodId) {
        this(tradeId,
                makerPaymentAccountPayload,
                makerAccountId,
                makerMultiSigPubKey,
                makerContractAsJson,
                makerContractSignature,
                makerPayoutAddressString,
                preparedDepositTx,
                makerInputs,
                senderNodeAddress,
                uid,
                Version.getP2PMessageVersion(),
                accountAgeWitnessSignatureOfPreparedDepositTx,
                currentDate,
                lockTime,
                hashOfMakersPaymentAccountPayload,
                makersPaymentMethodId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private InputsForDepositTxResponse(String tradeId,
                                       @Nullable PaymentAccountPayload makerPaymentAccountPayload,
                                       String makerAccountId,
                                       byte[] makerMultiSigPubKey,
                                       String makerContractAsJson,
                                       String makerContractSignature,
                                       String makerPayoutAddressString,
                                       byte[] preparedDepositTx,
                                       List<RawTransactionInput> makerInputs,
                                       NodeAddress senderNodeAddress,
                                       String uid,
                                       int messageVersion,
                                       @Nullable byte[] accountAgeWitnessSignatureOfPreparedDepositTx,
                                       long currentDate,
                                       long lockTime,
                                       @Nullable byte[] hashOfMakersPaymentAccountPayload,
                                       @Nullable String makersPaymentMethodId) {
        super(messageVersion, tradeId, uid);
        this.makerPaymentAccountPayload = makerPaymentAccountPayload;
        this.makerAccountId = makerAccountId;
        this.makerMultiSigPubKey = makerMultiSigPubKey;
        this.makerContractAsJson = makerContractAsJson;
        this.makerContractSignature = makerContractSignature;
        this.makerPayoutAddressString = makerPayoutAddressString;
        this.preparedDepositTx = preparedDepositTx;
        this.makerInputs = makerInputs;
        this.senderNodeAddress = senderNodeAddress;
        this.accountAgeWitnessSignatureOfPreparedDepositTx = accountAgeWitnessSignatureOfPreparedDepositTx;
        this.currentDate = currentDate;
        this.lockTime = lockTime;
        this.hashOfMakersPaymentAccountPayload = hashOfMakersPaymentAccountPayload;
        this.makersPaymentMethodId = makersPaymentMethodId;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        final protobuf.InputsForDepositTxResponse.Builder builder = protobuf.InputsForDepositTxResponse.newBuilder()
                .setTradeId(tradeId)
                .setMakerAccountId(makerAccountId)
                .setMakerMultiSigPubKey(ByteString.copyFrom(makerMultiSigPubKey))
                .setMakerContractAsJson(makerContractAsJson)
                .setMakerContractSignature(makerContractSignature)
                .setMakerPayoutAddressString(makerPayoutAddressString)
                .setPreparedDepositTx(ByteString.copyFrom(preparedDepositTx))
                .addAllMakerInputs(makerInputs.stream().map(RawTransactionInput::toProtoMessage).collect(Collectors.toList()))
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setUid(uid)
                .setLockTime(lockTime);

        Optional.ofNullable(accountAgeWitnessSignatureOfPreparedDepositTx).ifPresent(e -> builder.setAccountAgeWitnessSignatureOfPreparedDepositTx(ByteString.copyFrom(e)));
        builder.setCurrentDate(currentDate);
        Optional.ofNullable(makerPaymentAccountPayload).ifPresent(e -> builder.setMakerPaymentAccountPayload((protobuf.PaymentAccountPayload) makerPaymentAccountPayload.toProtoMessage()));
        Optional.ofNullable(hashOfMakersPaymentAccountPayload).ifPresent(e -> builder.setHashOfMakersPaymentAccountPayload(ByteString.copyFrom(hashOfMakersPaymentAccountPayload)));
        Optional.ofNullable(makersPaymentMethodId).ifPresent(e -> builder.setMakersPayoutMethodId(makersPaymentMethodId));
        return getNetworkEnvelopeBuilder()
                .setInputsForDepositTxResponse(builder)
                .build();
    }

    public static InputsForDepositTxResponse fromProto(protobuf.InputsForDepositTxResponse proto,
                                                       CoreProtoResolver coreProtoResolver,
                                                       int messageVersion) {
        List<RawTransactionInput> makerInputs = proto.getMakerInputsList().stream()
                .map(RawTransactionInput::fromProto)
                .collect(Collectors.toList());

        PaymentAccountPayload makerPaymentAccountPayload = proto.hasMakerPaymentAccountPayload() ?
                coreProtoResolver.fromProto(proto.getMakerPaymentAccountPayload()) : null;
        byte[] hashOfMakersPaymentAccountPayload = ProtoUtil.byteArrayOrNullFromProto(proto.getHashOfMakersPaymentAccountPayload());

        return new InputsForDepositTxResponse(proto.getTradeId(),
                makerPaymentAccountPayload,
                proto.getMakerAccountId(),
                proto.getMakerMultiSigPubKey().toByteArray(),
                proto.getMakerContractAsJson(),
                proto.getMakerContractSignature(),
                proto.getMakerPayoutAddressString(),
                proto.getPreparedDepositTx().toByteArray(),
                makerInputs,
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid(),
                messageVersion,
                ProtoUtil.byteArrayOrNullFromProto(proto.getAccountAgeWitnessSignatureOfPreparedDepositTx()),
                proto.getCurrentDate(),
                proto.getLockTime(),
                hashOfMakersPaymentAccountPayload,
                ProtoUtil.stringOrNullFromProto(proto.getMakersPayoutMethodId()));
    }

    @Override
    public String toString() {
        return "InputsForDepositTxResponse{" +
                ",\n     makerAccountId='" + makerAccountId + '\'' +
                ",\n     makerMultiSigPubKey=" + Utilities.bytesAsHexString(makerMultiSigPubKey) +
                ",\n     makerContractAsJson='" + makerContractAsJson + '\'' +
                ",\n     makerContractSignature='" + makerContractSignature + '\'' +
                ",\n     makerPayoutAddressString='" + makerPayoutAddressString + '\'' +
                ",\n     preparedDepositTx=" + Utilities.bytesAsHexString(preparedDepositTx) +
                ",\n     makerInputs=" + makerInputs +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     uid='" + uid + '\'' +
                ",\n     accountAgeWitnessSignatureOfPreparedDepositTx=" + Utilities.bytesAsHexString(accountAgeWitnessSignatureOfPreparedDepositTx) +
                ",\n     currentDate=" + new Date(currentDate) +
                ",\n     lockTime=" + lockTime +
                ",\n     hashOfMakersPaymentAccountPayload=" + Utilities.bytesAsHexString(hashOfMakersPaymentAccountPayload) +
                ",\n     makersPaymentMethodId=" + makersPaymentMethodId +
                "\n} " + super.toString();
    }
}

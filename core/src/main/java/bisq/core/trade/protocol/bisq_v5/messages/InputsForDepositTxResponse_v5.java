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

package bisq.core.trade.protocol.bisq_v5.messages;

import bisq.core.btc.model.RawTransactionInput;
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

// Copy of InputsForDepositTxResponse with added buyersUnsignedWarningTx and buyersWarningTxSignature.
// Also removed @Nullable annotations which are not relevant anymore

@EqualsAndHashCode(callSuper = true)
@Getter
public final class InputsForDepositTxResponse_v5 extends TradeMessage implements DirectMessage {
    private final String makerAccountId;
    private final byte[] makerMultiSigPubKey;
    private final String makerContractAsJson;
    private final String makerContractSignature;
    private final String makerPayoutAddressString;
    private final byte[] preparedDepositTx;
    private final List<RawTransactionInput> makerInputs;
    private final NodeAddress senderNodeAddress;
    private final byte[] accountAgeWitnessSignatureOfPreparedDepositTx;
    private final long currentDate;
    private final long lockTime;
    private final byte[] hashOfMakersPaymentAccountPayload;
    private final String makersPaymentMethodId;
    private final String makersWarningTxFeeBumpAddress;
    private final String makersRedirectTxFeeBumpAddress;
    private final byte[] buyersWarningTxMakerSignature;
    private final byte[] sellersWarningTxMakerSignature;
    private final byte[] buyersRedirectTxMakerSignature;
    private final byte[] sellersRedirectTxMakerSignature;

    public InputsForDepositTxResponse_v5(String tradeId,
                                         String makerAccountId,
                                         byte[] makerMultiSigPubKey,
                                         String makerContractAsJson,
                                         String makerContractSignature,
                                         String makerPayoutAddressString,
                                         byte[] preparedDepositTx,
                                         List<RawTransactionInput> makerInputs,
                                         NodeAddress senderNodeAddress,
                                         String uid,
                                         byte[] accountAgeWitnessSignatureOfPreparedDepositTx,
                                         long currentDate,
                                         long lockTime,
                                         byte[] hashOfMakersPaymentAccountPayload,
                                         String makersPaymentMethodId,
                                         String makersWarningTxFeeBumpAddress,
                                         String makersRedirectTxFeeBumpAddress,
                                         byte[] buyersWarningTxMakerSignature,
                                         byte[] sellersWarningTxMakerSignature,
                                         byte[] buyersRedirectTxMakerSignature,
                                         byte[] sellersRedirectTxMakerSignature) {
        this(tradeId,
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
                makersPaymentMethodId,
                makersWarningTxFeeBumpAddress,
                makersRedirectTxFeeBumpAddress,
                buyersWarningTxMakerSignature,
                sellersWarningTxMakerSignature,
                buyersRedirectTxMakerSignature,
                sellersRedirectTxMakerSignature);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private InputsForDepositTxResponse_v5(String tradeId,
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
                                          byte[] accountAgeWitnessSignatureOfPreparedDepositTx,
                                          long currentDate,
                                          long lockTime,
                                          byte[] hashOfMakersPaymentAccountPayload,
                                          String makersPaymentMethodId,
                                          String makersWarningTxFeeBumpAddress,
                                          String makersRedirectTxFeeBumpAddress,
                                          byte[] buyersWarningTxMakerSignature,
                                          byte[] sellersWarningTxMakerSignature,
                                          byte[] buyersRedirectTxMakerSignature,
                                          byte[] sellersRedirectTxMakerSignature) {
        super(messageVersion, tradeId, uid);
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
        this.makersWarningTxFeeBumpAddress = makersWarningTxFeeBumpAddress;
        this.makersRedirectTxFeeBumpAddress = makersRedirectTxFeeBumpAddress;
        this.buyersWarningTxMakerSignature = buyersWarningTxMakerSignature;
        this.sellersWarningTxMakerSignature = sellersWarningTxMakerSignature;
        this.buyersRedirectTxMakerSignature = buyersRedirectTxMakerSignature;
        this.sellersRedirectTxMakerSignature = sellersRedirectTxMakerSignature;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        final protobuf.InputsForDepositTxResponse_v5.Builder builder = protobuf.InputsForDepositTxResponse_v5.newBuilder()
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
                .setLockTime(lockTime)
                .setMakersWarningTxFeeBumpAddress(makersWarningTxFeeBumpAddress)
                .setMakersRedirectTxFeeBumpAddress(makersRedirectTxFeeBumpAddress)
                .setBuyersWarningTxMakerSignature(ByteString.copyFrom(buyersWarningTxMakerSignature))
                .setSellersWarningTxMakerSignature(ByteString.copyFrom(sellersWarningTxMakerSignature))
                .setBuyersRedirectTxMakerSignature(ByteString.copyFrom(buyersRedirectTxMakerSignature))
                .setSellersRedirectTxMakerSignature(ByteString.copyFrom(sellersRedirectTxMakerSignature));

        Optional.ofNullable(accountAgeWitnessSignatureOfPreparedDepositTx).ifPresent(e -> builder.setAccountAgeWitnessSignatureOfPreparedDepositTx(ByteString.copyFrom(e)));
        builder.setCurrentDate(currentDate);
        Optional.ofNullable(hashOfMakersPaymentAccountPayload).ifPresent(e -> builder.setHashOfMakersPaymentAccountPayload(ByteString.copyFrom(hashOfMakersPaymentAccountPayload)));
        Optional.ofNullable(makersPaymentMethodId).ifPresent(e -> builder.setMakersPayoutMethodId(makersPaymentMethodId));
        return getNetworkEnvelopeBuilder()
                .setInputsForDepositTxResponseV5(builder)
                .build();
    }

    public static InputsForDepositTxResponse_v5 fromProto(protobuf.InputsForDepositTxResponse_v5 proto,
                                                          int messageVersion) {
        List<RawTransactionInput> makerInputs = proto.getMakerInputsList().stream()
                .map(RawTransactionInput::fromProto)
                .collect(Collectors.toList());

        byte[] hashOfMakersPaymentAccountPayload = ProtoUtil.byteArrayOrNullFromProto(proto.getHashOfMakersPaymentAccountPayload());

        return new InputsForDepositTxResponse_v5(proto.getTradeId(),
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
                ProtoUtil.stringOrNullFromProto(proto.getMakersPayoutMethodId()),
                proto.getMakersWarningTxFeeBumpAddress(),
                proto.getMakersRedirectTxFeeBumpAddress(),
                proto.getBuyersWarningTxMakerSignature().toByteArray(),
                proto.getSellersWarningTxMakerSignature().toByteArray(),
                proto.getBuyersRedirectTxMakerSignature().toByteArray(),
                proto.getSellersRedirectTxMakerSignature().toByteArray());
    }

    @Override
    public String toString() {
        return "InputsForDepositTxResponse_v5{" +
                "\n     makerAccountId='" + makerAccountId + '\'' +
                ",\n     makerMultiSigPubKey=" + Utilities.bytesAsHexString(makerMultiSigPubKey) +
                ",\n     makerContractAsJson='" + makerContractAsJson + '\'' +
                ",\n     makerContractSignature='" + makerContractSignature + '\'' +
                ",\n     makerPayoutAddressString='" + makerPayoutAddressString + '\'' +
                ",\n     preparedDepositTx=" + Utilities.bytesAsHexString(preparedDepositTx) +
                ",\n     makerInputs=" + makerInputs +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     accountAgeWitnessSignatureOfPreparedDepositTx=" + Utilities.bytesAsHexString(accountAgeWitnessSignatureOfPreparedDepositTx) +
                ",\n     currentDate=" + new Date(currentDate) +
                ",\n     lockTime=" + lockTime +
                ",\n     hashOfMakersPaymentAccountPayload=" + Utilities.bytesAsHexString(hashOfMakersPaymentAccountPayload) +
                ",\n     makersPaymentMethodId=" + makersPaymentMethodId +
                ",\n     makersWarningTxFeeBumpAddress=" + makersWarningTxFeeBumpAddress +
                ",\n     makersRedirectTxFeeBumpAddress=" + makersRedirectTxFeeBumpAddress +
                ",\n     buyersWarningTxMakerSignature=" + Utilities.bytesAsHexString(buyersWarningTxMakerSignature) +
                ",\n     sellersWarningTxMakerSignature=" + Utilities.bytesAsHexString(sellersWarningTxMakerSignature) +
                ",\n     buyersRedirectTxMakerSignature=" + Utilities.bytesAsHexString(buyersRedirectTxMakerSignature) +
                ",\n     sellersRedirectTxMakerSignature=" + Utilities.bytesAsHexString(sellersRedirectTxMakerSignature) +
                "\n} " + super.toString();
    }
}

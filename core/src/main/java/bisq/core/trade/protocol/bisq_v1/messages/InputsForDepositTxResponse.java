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
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import static bisq.core.trade.protocol.bisq_v1.messages.TradeMessageValidator.checkNodeAddress;
import static bisq.core.trade.protocol.bisq_v1.messages.TradeMessageValidator.checkRawTransactionInputList;
import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class InputsForDepositTxResponse extends TradeMessage implements DirectMessage {
    private final String makerAccountId;
    private final byte[] makerMultiSigPubKey;
    private final String makerContractAsJson;
    private final String makerContractSignature;
    private final String makerPayoutAddressString;
    private final byte[] preparedDepositTx;
    private final List<RawTransactionInput> makerInputs;
    private final NodeAddress senderNodeAddress;

    // added in v 0.6. It was null in earlier versions which are not supported anymore
    private final byte[] accountAgeWitnessSignatureOfPreparedDepositTx;
    private final long currentDate;
    private final long lockTime;

    // Added at 1.7.0
    private final byte[] hashOfMakersPaymentAccountPayload;
    private final String makersPaymentMethodId;
    private final List<Integer> supportedBurningManAddressListVersions;

    public InputsForDepositTxResponse(String tradeId,
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
                                      List<Integer> supportedBurningManAddressListVersions) {
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
                supportedBurningManAddressListVersions);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER

    ///////////////////////////////////////////////////////////////////////////////////////////

    private InputsForDepositTxResponse(String tradeId,
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
                                       List<Integer> supportedBurningManAddressListVersions) {
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
        this.supportedBurningManAddressListVersions = supportedBurningManAddressListVersions;

        validate();
    }

    private void validate() {
        checkNonBlankString(makerAccountId, "makerAccountId");
        checkNonEmptyBytes(makerMultiSigPubKey, "makerMultiSigPubKey");
        checkNonBlankString(makerContractAsJson, "makerContractAsJson");
        checkNonBlankString(makerContractSignature, "makerContractSignature");
        checkNonBlankString(makerPayoutAddressString, "makerPayoutAddressString");
        checkNonEmptyBytes(preparedDepositTx, "preparedDepositTx");
        checkRawTransactionInputList(makerInputs, true, "makerInputs");
        checkNodeAddress(senderNodeAddress, "senderNodeAddress");
        checkNonEmptyBytes(accountAgeWitnessSignatureOfPreparedDepositTx,
                "accountAgeWitnessSignatureOfPreparedDepositTx");
        checkArgument(currentDate > 0, "currentDate must be positive");
        checkArgument(lockTime > 0, "lockTime must be positive");
        checkNonEmptyBytes(hashOfMakersPaymentAccountPayload, "hashOfMakersPaymentAccountPayload");
        checkNonBlankString(makersPaymentMethodId, "makersPaymentMethodId");
        BurningManAddressListService.getValidatedSupportedVersions(supportedBurningManAddressListVersions);
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
                .setLockTime(lockTime)
                .setAccountAgeWitnessSignatureOfPreparedDepositTx(ByteString.copyFrom(accountAgeWitnessSignatureOfPreparedDepositTx))
                .setHashOfMakersPaymentAccountPayload(ByteString.copyFrom(hashOfMakersPaymentAccountPayload))
                .setMakersPayoutMethodId(makersPaymentMethodId)
                .addAllSupportedBurningManAddressListVersions(supportedBurningManAddressListVersions)
                .setCurrentDate(currentDate);

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

        return new InputsForDepositTxResponse(proto.getTradeId(),
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
                proto.getAccountAgeWitnessSignatureOfPreparedDepositTx().toByteArray(),
                proto.getCurrentDate(),
                proto.getLockTime(),
                proto.getHashOfMakersPaymentAccountPayload().toByteArray(),
                proto.getMakersPayoutMethodId(),
                proto.getSupportedBurningManAddressListVersionsList());
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
                ",\n     supportedBurningManAddressListVersions=" + supportedBurningManAddressListVersions +
                "\n} " + super.toString();
    }
}

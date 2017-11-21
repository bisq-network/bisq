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

package io.bisq.core.trade.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.MailboxMessage;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// We use a MailboxMessage here because the taker has paid already the trade fee and it could be that
// we lost connection to him but we are complete on our side. So even if the peer is offline he can
// continue later to complete the deposit tx.

@EqualsAndHashCode(callSuper = true)
@Value
public final class PublishDepositTxRequest extends TradeMessage implements MailboxMessage {
    private final PaymentAccountPayload makerPaymentAccountPayload;
    private final String makerAccountId;
    private final byte[] makerMultiSigPubKey;
    private final String makerContractAsJson;
    private final String makerContractSignature;
    private final String makerPayoutAddressString;
    private final byte[] preparedDepositTx;
    private final List<RawTransactionInput> makerInputs;
    private final NodeAddress senderNodeAddress;
    private final String uid;

    // added in v 0.6. can be null if we trade with an older peer
    @Nullable
    private final byte[] accountAgeWitnessSignatureOfPreparedDepositTx;
    private final long currentDate;

    public PublishDepositTxRequest(String tradeId,
                                   PaymentAccountPayload makerPaymentAccountPayload,
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
                                   long currentDate) {
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
                currentDate);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PublishDepositTxRequest(String tradeId,
                                    PaymentAccountPayload makerPaymentAccountPayload,
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
                                    long currentDate) {
        super(messageVersion, tradeId);
        this.makerPaymentAccountPayload = makerPaymentAccountPayload;
        this.makerAccountId = makerAccountId;
        this.makerMultiSigPubKey = makerMultiSigPubKey;
        this.makerContractAsJson = makerContractAsJson;
        this.makerContractSignature = makerContractSignature;
        this.makerPayoutAddressString = makerPayoutAddressString;
        this.preparedDepositTx = preparedDepositTx;
        this.makerInputs = makerInputs;
        this.senderNodeAddress = senderNodeAddress;
        this.uid = uid;
        this.accountAgeWitnessSignatureOfPreparedDepositTx = accountAgeWitnessSignatureOfPreparedDepositTx;
        this.currentDate = currentDate;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        final PB.PublishDepositTxRequest.Builder builder = PB.PublishDepositTxRequest.newBuilder()
                .setTradeId(tradeId)
                .setMakerPaymentAccountPayload((PB.PaymentAccountPayload) makerPaymentAccountPayload.toProtoMessage())
                .setMakerAccountId(makerAccountId)
                .setMakerMultiSigPubKey(ByteString.copyFrom(makerMultiSigPubKey))
                .setMakerContractAsJson(makerContractAsJson)
                .setMakerContractSignature(makerContractSignature)
                .setMakerPayoutAddressString(makerPayoutAddressString)
                .setPreparedDepositTx(ByteString.copyFrom(preparedDepositTx))
                .addAllMakerInputs(makerInputs.stream().map(RawTransactionInput::toProtoMessage).collect(Collectors.toList()))
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setUid(uid);

        Optional.ofNullable(accountAgeWitnessSignatureOfPreparedDepositTx).ifPresent(e -> builder.setAccountAgeWitnessSignatureOfPreparedDepositTx(ByteString.copyFrom(e)));
        builder.setCurrentDate(currentDate);

        return getNetworkEnvelopeBuilder()
                .setPublishDepositTxRequest(builder)
                .build();
    }

    public static PublishDepositTxRequest fromProto(PB.PublishDepositTxRequest proto, CoreProtoResolver coreProtoResolver, int messageVersion) {
        List<RawTransactionInput> makerInputs = proto.getMakerInputsList().stream()
                .map(RawTransactionInput::fromProto)
                .collect(Collectors.toList());

        return new PublishDepositTxRequest(proto.getTradeId(),
                coreProtoResolver.fromProto(proto.getMakerPaymentAccountPayload()),
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
                proto.getCurrentDate());
    }


    @Override
    public String toString() {
        return "PublishDepositTxRequest{" +
                "\n     makerPaymentAccountPayload=" + makerPaymentAccountPayload +
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
                "\n} " + super.toString();
    }
}

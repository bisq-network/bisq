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
import io.bisq.common.util.Utilities;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.p2p.MailboxMessage;
import io.bisq.protobuffer.payload.btc.RawTransactionInput;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import lombok.EqualsAndHashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Immutable
// We use a MailboxMessage here because the taker has paid already the trade fee and it could be that 
// we lost connection to him but we are complete on our side. So even if the peer is offline he can 
// continue later to complete the deposit tx.
public final class PublishDepositTxRequest extends TradeMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(PublishDepositTxRequest.class);

    public final PaymentAccountPayload offererPaymentAccountPayload;
    public final String offererAccountId;
    public final String offererContractAsJson;
    public final String offererContractSignature;
    public final String offererPayoutAddressString;
    public final byte[] preparedDepositTx;
    public final List<RawTransactionInput> offererInputs;
    public final byte[] offererMultiSigPubKey;
    private final NodeAddress senderNodeAddress;
    private final String uid;

    public PublishDepositTxRequest(String tradeId,
                                   PaymentAccountPayload offererPaymentAccountPayload,
                                   String offererAccountId,
                                   byte[] offererMultiSigPubKey,
                                   String offererContractAsJson,
                                   String offererContractSignature,
                                   String offererPayoutAddressString,
                                   byte[] preparedDepositTx,
                                   List<RawTransactionInput> offererInputs,
                                   NodeAddress senderNodeAddress,
                                   String uid) {
        super(tradeId);
        this.offererPaymentAccountPayload = offererPaymentAccountPayload;
        this.offererAccountId = offererAccountId;
        this.offererMultiSigPubKey = offererMultiSigPubKey;
        this.offererContractAsJson = offererContractAsJson;
        this.offererContractSignature = offererContractSignature;
        this.offererPayoutAddressString = offererPayoutAddressString;
        this.preparedDepositTx = preparedDepositTx;
        this.offererInputs = offererInputs;
        this.senderNodeAddress = senderNodeAddress;
        this.uid = uid;

        log.trace("offererPaymentAccount size " + Utilities.serialize(offererPaymentAccountPayload).length);
        log.trace("offererTradeWalletPubKey size " + offererMultiSigPubKey.length);
        log.trace("preparedDepositTx size " + preparedDepositTx.length);
        log.trace("offererInputs size " + Utilities.serialize(new ArrayList<>(offererInputs)).length);
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Message.getBaseEnvelope();
        return baseEnvelope.setPublishDepositTxRequest(baseEnvelope.getPublishDepositTxRequestBuilder()
                .setMessageVersion(getMessageVersion())
                .setTradeId(tradeId)
                .setOffererPaymentAccountPayload((PB.PaymentAccountPayload) offererPaymentAccountPayload.toProto())
                .setOffererAccountId(offererAccountId)
                .setOffererMultiSigPubKey(ByteString.copyFrom(offererMultiSigPubKey))
                .setOffererContractAsJson(offererContractAsJson)
                .setOffererContractSignature(offererContractSignature)
                .setOffererPayoutAddressString(offererPayoutAddressString)
                .setPreparedDepositTx(ByteString.copyFrom(preparedDepositTx))
                .setSenderNodeAddress(senderNodeAddress.toProto())
                .setUid(uid)
                .addAllOffererInputs(offererInputs.stream().map(rawTransactionInput -> rawTransactionInput.toProto()).collect(Collectors.toList()))).build();
    }

    @Override
    public String toString() {
        return "PublishDepositTxRequest{" +
                "offererPaymentAccountPayload=" + offererPaymentAccountPayload +
                ", offererAccountId='" + offererAccountId + '\'' +
                ", offererContractAsJson='" + offererContractAsJson + '\'' +
                ", offererContractSignature='" + offererContractSignature + '\'' +
                ", offererPayoutAddressString='" + offererPayoutAddressString + '\'' +
                ", preparedDepositTx=" + Arrays.toString(preparedDepositTx) +
                ", offererInputs=" + offererInputs +
                ", offererMultiSigPubKey=" + Arrays.toString(offererMultiSigPubKey) +
                "} " + super.toString();
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return null;
    }

    @Override
    public String getUID() {
        return null;
    }
}

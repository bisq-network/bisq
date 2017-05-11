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
import io.bisq.common.app.Version;
import io.bisq.common.network.Msg;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.MailboxMsg;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Immutable
// We use a MailboxMessage here because the taker has paid already the trade fee and it could be that
// we lost connection to him but we are complete on our side. So even if the peer is offline he can
// continue later to complete the deposit tx.
public final class PublishDepositTxRequest extends TradeMsg implements MailboxMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(PublishDepositTxRequest.class);

    public final PaymentAccountPayload makerPaymentAccountPayload;
    public final String makerAccountId;
    public final String makerContractAsJson;
    public final String makerContractSignature;
    public final String makerPayoutAddressString;
    public final byte[] preparedDepositTx;
    public final List<RawTransactionInput> makerInputs;
    public final byte[] makerMultiSigPubKey;
    private final NodeAddress senderNodeAddress;
    private final String uid;

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
                                   String uid) {
        super(tradeId);
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

        log.trace("makerPaymentAccount size " + Utilities.serialize(makerPaymentAccountPayload).length);
        log.trace("makerTradeWalletPubKey size " + makerMultiSigPubKey.length);
        log.trace("preparedDepositTx size " + preparedDepositTx.length);
        log.trace("makerInputs size " + Utilities.serialize(new ArrayList<>(makerInputs)).length);
    }

    @Override
    public PB.Msg toEnvelopeProto() {
        PB.Msg.Builder baseEnvelope = Msg.getEnv();
        return baseEnvelope.setPublishDepositTxRequest(baseEnvelope.getPublishDepositTxRequestBuilder()
                .setMessageVersion(getMessageVersion())
                .setTradeId(tradeId)
                .setMakerPaymentAccountPayload((PB.PaymentAccountPayload) makerPaymentAccountPayload.toProto())
                .setMakerAccountId(makerAccountId)
                .setMakerMultiSigPubKey(ByteString.copyFrom(makerMultiSigPubKey))
                .setMakerContractAsJson(makerContractAsJson)
                .setMakerContractSignature(makerContractSignature)
                .setMakerPayoutAddressString(makerPayoutAddressString)
                .setPreparedDepositTx(ByteString.copyFrom(preparedDepositTx))
                .setSenderNodeAddress(senderNodeAddress.toProto())
                .setUid(uid)
                .addAllMakerInputs(makerInputs.stream().map(rawTransactionInput -> rawTransactionInput.toProto()).collect(Collectors.toList()))).build();
    }

    @Override
    public String toString() {
        return "PublishDepositTxRequest{" +
                "makerPaymentAccountPayload=" + makerPaymentAccountPayload +
                ", makerAccountId='" + makerAccountId + '\'' +
                ", makerContractAsJson='" + makerContractAsJson + '\'' +
                ", makerContractSignature='" + makerContractSignature + '\'' +
                ", makerPayoutAddressString='" + makerPayoutAddressString + '\'' +
                ", preparedDepositTx=" + Hex.toHexString(preparedDepositTx) +
                ", makerInputs=" + makerInputs +
                ", makerMultiSigPubKey=" + Hex.toHexString(makerMultiSigPubKey) +
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

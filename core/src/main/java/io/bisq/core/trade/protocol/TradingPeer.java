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

package io.bisq.core.trade.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.proto.ProtoCollectionUtil;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@Slf4j
@Getter
@Setter
public final class TradingPeer implements PersistablePayload {
    @Nullable
    private String accountId;
    @Nullable
    private PaymentAccountPayload paymentAccountPayload;
    @Nullable
    private String payoutAddressString;
    @Nullable
    private String contractAsJson;
    @Nullable
    private String contractSignature;
    @Nullable
    private byte[] signature;
    @Nullable
    private PubKeyRing pubKeyRing;
    @Nullable
    private byte[] multiSigPubKey;
    @Nullable
    private List<RawTransactionInput> rawTransactionInputs;
    private long changeOutputValue;
    @Nullable
    private String changeOutputAddress;

    public TradingPeer() {
    }

    @Override
    public Message toProtoMessage() {
        final PB.TradingPeer.Builder builder = PB.TradingPeer.newBuilder()
                .setChangeOutputValue(changeOutputValue);
        Optional.ofNullable(accountId).ifPresent(builder::setAccountId);
        Optional.ofNullable(paymentAccountPayload).ifPresent(e -> builder.setPaymentAccountPayload((PB.PaymentAccountPayload) paymentAccountPayload.toProtoMessage()));
        Optional.ofNullable(payoutAddressString).ifPresent(builder::setPayoutAddressString);
        Optional.ofNullable(contractAsJson).ifPresent(builder::setContractAsJson);
        Optional.ofNullable(contractSignature).ifPresent(builder::setContractSignature);
        Optional.ofNullable(signature).ifPresent(e -> builder.setSignature(ByteString.copyFrom(signature)));
        Optional.ofNullable(pubKeyRing).ifPresent(e -> builder.setPubKeyRing(pubKeyRing.toProtoMessage()));
        Optional.ofNullable(multiSigPubKey).ifPresent(e -> builder.setMultiSigPubKey(ByteString.copyFrom(multiSigPubKey)));
        Optional.ofNullable(rawTransactionInputs).ifPresent(e -> builder.addAllRawTransactionInputs(ProtoCollectionUtil.collectionToProto(rawTransactionInputs)));
        Optional.ofNullable(changeOutputAddress).ifPresent(builder::setChangeOutputAddress);
        return builder.build();
    }
}

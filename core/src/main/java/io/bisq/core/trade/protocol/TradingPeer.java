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

package io.bisq.core.trade.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.proto.CoreProtoResolver;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    // added in v 0.6
    @Nullable
    private byte[] accountAgeWitnessNonce;
    @Nullable
    private byte[] accountAgeWitnessSignature;
    private long currentDate;

    public TradingPeer() {
    }

    @Override
    public Message toProtoMessage() {
        final PB.TradingPeer.Builder builder = PB.TradingPeer.newBuilder()
                .setChangeOutputValue(changeOutputValue);
        Optional.ofNullable(accountId).ifPresent(builder::setAccountId);
        Optional.ofNullable(paymentAccountPayload).ifPresent(e -> builder.setPaymentAccountPayload((PB.PaymentAccountPayload) e.toProtoMessage()));
        Optional.ofNullable(payoutAddressString).ifPresent(builder::setPayoutAddressString);
        Optional.ofNullable(contractAsJson).ifPresent(builder::setContractAsJson);
        Optional.ofNullable(contractSignature).ifPresent(builder::setContractSignature);
        Optional.ofNullable(signature).ifPresent(e -> builder.setSignature(ByteString.copyFrom(e)));
        Optional.ofNullable(pubKeyRing).ifPresent(e -> builder.setPubKeyRing(e.toProtoMessage()));
        Optional.ofNullable(multiSigPubKey).ifPresent(e -> builder.setMultiSigPubKey(ByteString.copyFrom(e)));
        Optional.ofNullable(rawTransactionInputs).ifPresent(e -> builder.addAllRawTransactionInputs(ProtoUtil.collectionToProto(e)));
        Optional.ofNullable(changeOutputAddress).ifPresent(builder::setChangeOutputAddress);
        Optional.ofNullable(accountAgeWitnessNonce).ifPresent(e -> builder.setAccountAgeWitnessNonce(ByteString.copyFrom(e)));
        Optional.ofNullable(accountAgeWitnessSignature).ifPresent(e -> builder.setAccountAgeWitnessSignature(ByteString.copyFrom(e)));
        builder.setCurrentDate(currentDate);
        return builder.build();
    }

    public static TradingPeer fromProto(PB.TradingPeer proto, CoreProtoResolver coreProtoResolver) {
        if (proto.getDefaultInstanceForType().equals(proto)) {
            return null;
        } else {
            TradingPeer tradingPeer = new TradingPeer();
            tradingPeer.setChangeOutputValue(proto.getChangeOutputValue());
            tradingPeer.setAccountId(ProtoUtil.stringOrNullFromProto(proto.getAccountId()));
            tradingPeer.setPaymentAccountPayload(proto.hasPaymentAccountPayload() ? coreProtoResolver.fromProto(proto.getPaymentAccountPayload()) : null);
            tradingPeer.setPayoutAddressString(ProtoUtil.stringOrNullFromProto(proto.getPayoutAddressString()));
            tradingPeer.setContractAsJson(ProtoUtil.stringOrNullFromProto(proto.getContractAsJson()));
            tradingPeer.setContractSignature(ProtoUtil.stringOrNullFromProto(proto.getContractSignature()));
            tradingPeer.setSignature(ProtoUtil.byteArrayOrNullFromProto(proto.getSignature()));
            tradingPeer.setPubKeyRing(proto.hasPubKeyRing() ? PubKeyRing.fromProto(proto.getPubKeyRing()) : null);
            tradingPeer.setMultiSigPubKey(ProtoUtil.byteArrayOrNullFromProto(proto.getMultiSigPubKey()));
            List<RawTransactionInput> rawTransactionInputs = proto.getRawTransactionInputsList().isEmpty() ?
                    null :
                    proto.getRawTransactionInputsList().stream()
                            .map(RawTransactionInput::fromProto)
                            .collect(Collectors.toList());
            tradingPeer.setRawTransactionInputs(rawTransactionInputs);
            tradingPeer.setChangeOutputAddress(ProtoUtil.stringOrNullFromProto(proto.getChangeOutputAddress()));
            tradingPeer.setAccountAgeWitnessNonce(ProtoUtil.byteArrayOrNullFromProto(proto.getAccountAgeWitnessNonce()));
            tradingPeer.setAccountAgeWitnessSignature(ProtoUtil.byteArrayOrNullFromProto(proto.getAccountAgeWitnessSignature()));
            tradingPeer.setCurrentDate(proto.getCurrentDate());
            return tradingPeer;
        }
    }
}

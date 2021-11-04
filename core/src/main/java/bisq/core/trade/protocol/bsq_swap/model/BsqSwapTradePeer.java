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

package bisq.core.trade.protocol.bsq_swap.model;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.trade.protocol.TradePeer;

import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.TransactionInput;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Getter
@Setter
public final class BsqSwapTradePeer implements TradePeer {
    @Nullable
    private PubKeyRing pubKeyRing;
    @Nullable
    private String btcAddress;
    @Nullable
    private String bsqAddress;

    @Nullable
    private List<RawTransactionInput> inputs;
    private long change;
    private long payout;
    @Nullable
    @Setter
    private byte[] tx;
    @Nullable
    transient private List<TransactionInput> transactionInputs;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.BsqSwapTradePeer toProtoMessage() {
        final protobuf.BsqSwapTradePeer.Builder builder = protobuf.BsqSwapTradePeer.newBuilder()
                .setChange(change)
                .setPayout(payout);
        Optional.ofNullable(pubKeyRing).ifPresent(e -> builder.setPubKeyRing(e.toProtoMessage()));
        Optional.ofNullable(btcAddress).ifPresent(builder::setBtcAddress);
        Optional.ofNullable(bsqAddress).ifPresent(builder::setBsqAddress);
        Optional.ofNullable(inputs).ifPresent(e -> builder.addAllInputs(
                ProtoUtil.collectionToProto(e, protobuf.RawTransactionInput.class)));
        Optional.ofNullable(tx).ifPresent(e -> builder.setTx(ByteString.copyFrom(e)));
        return builder.build();
    }

    public static BsqSwapTradePeer fromProto(protobuf.BsqSwapTradePeer proto) {
        if (proto.getDefaultInstanceForType().equals(proto)) {
            return null;
        } else {
            BsqSwapTradePeer bsqSwapTradePeer = new BsqSwapTradePeer();
            bsqSwapTradePeer.setPubKeyRing(proto.hasPubKeyRing() ? PubKeyRing.fromProto(proto.getPubKeyRing()) : null);
            bsqSwapTradePeer.setChange(proto.getChange());
            bsqSwapTradePeer.setPayout(proto.getPayout());
            bsqSwapTradePeer.setBtcAddress(proto.getBtcAddress());
            bsqSwapTradePeer.setBsqAddress(proto.getBsqAddress());
            List<RawTransactionInput> inputs = proto.getInputsList().isEmpty() ?
                    null :
                    proto.getInputsList().stream()
                            .map(RawTransactionInput::fromProto)
                            .collect(Collectors.toList());
            bsqSwapTradePeer.setInputs(inputs);
            bsqSwapTradePeer.setTx(ProtoUtil.byteArrayOrNullFromProto(proto.getTx()));
            return bsqSwapTradePeer;
        }
    }


    @Override
    public String toString() {
        return "BsqSwapTradePeer{" +
                "\r\n     pubKeyRing=" + pubKeyRing +
                ",\r\n     btcAddress='" + btcAddress + '\'' +
                ",\r\n     bsqAddress='" + bsqAddress + '\'' +
                ",\r\n     inputs=" + inputs +
                ",\r\n     change=" + change +
                ",\r\n     payout=" + payout +
                ",\r\n     tx=" + Utilities.encodeToHex(tx) +
                "\r\n}";
    }
}

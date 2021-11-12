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

package bisq.core.api.model;

import bisq.core.api.model.builder.BsqSwapTradeInfoBuilder;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import bisq.common.Payload;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@Getter
public class BsqSwapTradeInfo implements Payload {

    private final String txId;
    private final long bsqTradeAmount;
    private final long btcTradeAmount;
    private final long bsqMakerTradeFee;
    private final long bsqTakerTradeFee;
    private final long txFeePerVbyte;
    private final String makerBsqAddress;
    private final String makerBtcAddress;
    private final String takerBsqAddress;
    private final String takerBtcAddress;
    private final String errorMessage;

    public BsqSwapTradeInfo(BsqSwapTradeInfoBuilder builder) {
        this.txId = builder.getTxId();
        this.bsqTradeAmount = builder.getBsqTradeAmount();
        this.btcTradeAmount = builder.getBtcTradeAmount();
        this.bsqMakerTradeFee = builder.getBsqMakerTradeFee();
        this.bsqTakerTradeFee = builder.getBsqTakerTradeFee();
        this.txFeePerVbyte = builder.getTxFeePerVbyte();
        this.makerBsqAddress = builder.getMakerBsqAddress();
        this.makerBtcAddress = builder.getMakerBtcAddress();
        this.takerBsqAddress = builder.getTakerBsqAddress();
        this.takerBtcAddress = builder.getTakerBtcAddress();
        this.errorMessage = builder.getErrorMessage();
    }

    public static BsqSwapTradeInfo toBsqSwapTradeInfo(BsqSwapTrade trade, boolean wasMyOffer) {
        var protocolModel = trade.getBsqSwapProtocolModel();
        var swapPeer = protocolModel.getTradePeer();
        var makerBsqAddress = wasMyOffer ? protocolModel.getBsqAddress() : swapPeer.getBsqAddress();
        var makerBtcAddress = wasMyOffer ? protocolModel.getBtcAddress() : swapPeer.getBtcAddress();
        var takerBsqAddress = wasMyOffer ? swapPeer.getBsqAddress() : protocolModel.getBsqAddress();
        var takerBtcAddress = wasMyOffer ? swapPeer.getBtcAddress() : protocolModel.getBtcAddress();
        return new BsqSwapTradeInfoBuilder()
                .withTxId(trade.getTxId())
                .withBsqTradeAmount(trade.getBsqTradeAmount())
                .withBtcTradeAmount(trade.getAmountAsLong())
                .withBsqMakerTradeFee(trade.getMakerFeeAsLong())
                .withBsqTakerTradeFee(trade.getTakerFeeAsLong())
                .withTxFeePerVbyte(trade.getTxFeePerVbyte())
                .withMakerBsqAddress(makerBsqAddress)
                .withMakerBtcAddress(makerBtcAddress)
                .withTakerBsqAddress(takerBsqAddress)
                .withTakerBtcAddress(takerBtcAddress)
                .withErrorMessage(trade.getErrorMessage())
                .build();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.BsqSwapTradeInfo toProtoMessage() {
        return bisq.proto.grpc.BsqSwapTradeInfo.newBuilder()
                .setTxId(txId != null ? txId : "")
                .setBsqTradeAmount(bsqTradeAmount)
                .setBtcTradeAmount(btcTradeAmount)
                .setBsqMakerTradeFee(bsqMakerTradeFee)
                .setBsqTakerTradeFee(bsqTakerTradeFee)
                .setTxFeePerVbyte(txFeePerVbyte)
                .setMakerBsqAddress(makerBsqAddress != null ? makerBsqAddress : "")
                .setTakerBsqAddress(takerBsqAddress != null ? takerBsqAddress : "")
                .setMakerBtcAddress(makerBtcAddress != null ? makerBtcAddress : "")
                .setTakerBtcAddress(takerBtcAddress != null ? takerBtcAddress : "")
                .setErrorMessage(errorMessage != null ? errorMessage : "")
                .build();
    }

    public static BsqSwapTradeInfo fromProto(bisq.proto.grpc.BsqSwapTradeInfo proto) {
        return new BsqSwapTradeInfoBuilder()
                .withTxId(proto.getTxId())
                .withBsqTradeAmount(proto.getBsqTradeAmount())
                .withBtcTradeAmount(proto.getBtcTradeAmount())
                .withBsqMakerTradeFee(proto.getBsqMakerTradeFee())
                .withBsqTakerTradeFee(proto.getBsqTakerTradeFee())
                .withTxFeePerVbyte(proto.getTxFeePerVbyte())
                .withMakerBsqAddress(proto.getMakerBsqAddress())
                .withMakerBtcAddress(proto.getMakerBtcAddress())
                .withTakerBsqAddress(proto.getTakerBsqAddress())
                .withTakerBtcAddress(proto.getTakerBtcAddress())
                .withErrorMessage(proto.getErrorMessage())
                .build();
    }

    @Override
    public String toString() {
        return "BsqSwapTradeInfo{" +
                ", txId='" + txId + '\'' +
                ", bsqTradeAmount=" + bsqTradeAmount +
                ", btcTradeAmount=" + btcTradeAmount +
                ", bsqMakerTradeFee=" + bsqMakerTradeFee +
                ", bsqTakerTradeFee=" + bsqTakerTradeFee +
                ", txFeePerVbyte=" + txFeePerVbyte +
                ", makerBsqAddress='" + makerBsqAddress + '\'' +
                ", makerBtcAddress='" + makerBtcAddress + '\'' +
                ", takerBsqAddress='" + takerBsqAddress + '\'' +
                ", takerBtcAddress='" + takerBtcAddress + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}

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

import static bisq.core.offer.OfferDirection.BUY;
import static bisq.core.offer.OfferDirection.SELL;

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
    private final long numConfirmations;
    private final String errorMessage;
    private final long payout;
    private final long swapPeerPayout;

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
        this.numConfirmations = builder.getNumConfirmations();
        this.errorMessage = builder.getErrorMessage();
        this.payout = builder.getPayout();
        this.swapPeerPayout = builder.getSwapPeerPayout();
    }

    public static BsqSwapTradeInfo toBsqSwapTradeInfo(BsqSwapTrade trade,
                                                      boolean wasMyOffer,
                                                      int numConfirmations) {
        var protocolModel = trade.getBsqSwapProtocolModel();
        var swapPeer = protocolModel.getTradePeer();
        var makerBsqAddress = wasMyOffer ? protocolModel.getBsqAddress() : swapPeer.getBsqAddress();
        var makerBtcAddress = wasMyOffer ? protocolModel.getBtcAddress() : swapPeer.getBtcAddress();
        var takerBsqAddress = wasMyOffer ? swapPeer.getBsqAddress() : protocolModel.getBsqAddress();
        var takerBtcAddress = wasMyOffer ? swapPeer.getBtcAddress() : protocolModel.getBtcAddress();
        // A BSQ Swap trade fee is paid in full by the BTC buyer (selling BSQ).
        // The transferred BSQ (payout) is reduced by the fee of the peer.
        var makerTradeFee = wasMyOffer && trade.getOffer().getDirection().equals(BUY)
                ? trade.getMakerFeeAsLong()
                : 0L;
        var takerTradeFee = !wasMyOffer && trade.getOffer().getDirection().equals(SELL)
                ? trade.getTakerFeeAsLong()
                : 0L;
        return new BsqSwapTradeInfoBuilder()
                .withTxId(trade.getTxId())
                .withBsqTradeAmount(trade.getBsqTradeAmount())
                .withBtcTradeAmount(trade.getAmountAsLong())
                .withBsqMakerTradeFee(makerTradeFee)
                .withBsqTakerTradeFee(takerTradeFee)
                .withTxFeePerVbyte(trade.getTxFeePerVbyte())
                .withMakerBsqAddress(makerBsqAddress)
                .withMakerBtcAddress(makerBtcAddress)
                .withTakerBsqAddress(takerBsqAddress)
                .withTakerBtcAddress(takerBtcAddress)
                .withNumConfirmations(numConfirmations)
                .withErrorMessage(trade.getErrorMessage())
                .withPayout(protocolModel.getPayout())
                .withSwapPeerPayout(protocolModel.getTradePeer().getPayout())
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
                .setTakerBtcAddress(takerBtcAddress != null ? takerBtcAddress : "")
                .setNumConfirmations(numConfirmations)
                .setErrorMessage(errorMessage != null ? errorMessage : "")
                .setPayout(payout)
                .setSwapPeerPayout(swapPeerPayout)
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
                .withNumConfirmations(proto.getNumConfirmations())
                .withErrorMessage(proto.getErrorMessage())
                .withPayout(proto.getPayout())
                .withSwapPeerPayout(proto.getSwapPeerPayout())
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
                ", numConfirmations='" + numConfirmations + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", payout=" + payout +
                ", swapPeerPayout=" + swapPeerPayout +
                '}';
    }
}

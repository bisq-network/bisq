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

import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import bisq.common.Payload;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static bisq.core.api.model.OfferInfo.toMyOfferInfo;
import static bisq.core.api.model.OfferInfo.toOfferInfo;

@EqualsAndHashCode
@ToString
@Getter
public class BsqSwapTradeInfo implements Payload {

    private final OfferInfo bsqSwapOffer;
    private final String tradeId;
    private final String tempTradingPeerNodeAddress;
    private final String peerNodeAddress;
    private final String txId;
    private final long bsqTradeAmount;
    private final long btcTradeAmount;
    private final long tradePrice;
    private final long bsqMakerTradeFee;
    private final long bsqTakerTradeFee;
    private final long txFeePerVbyte;
    private final long txFee;
    private final String makerBsqAddress;
    private final String makerBtcAddress;
    private final String takerBsqAddress;
    private final String takerBtcAddress;
    private final long takeOfferDate;
    private final String role;
    private final String state;
    private final String errorMessage;

    public BsqSwapTradeInfo(Builder builder) {
        this.bsqSwapOffer = builder.bsqSwapOffer;
        this.tradeId = builder.tradeId;
        this.tempTradingPeerNodeAddress = builder.tempTradingPeerNodeAddress;
        this.peerNodeAddress = builder.peerNodeAddress;
        this.txId = builder.txId;
        this.bsqTradeAmount = builder.bsqTradeAmount;
        this.btcTradeAmount = builder.btcTradeAmount;
        this.tradePrice = builder.tradePrice;
        this.bsqMakerTradeFee = builder.bsqMakerTradeFee;
        this.bsqTakerTradeFee = builder.bsqTakerTradeFee;
        this.txFeePerVbyte = builder.txFeePerVbyte;
        this.txFee = builder.txFee;
        this.makerBsqAddress = builder.makerBsqAddress;
        this.makerBtcAddress = builder.makerBtcAddress;
        this.takerBsqAddress = builder.takerBsqAddress;
        this.takerBtcAddress = builder.takerBtcAddress;
        this.takeOfferDate = builder.takeOfferDate;
        this.role = builder.role;
        this.state = builder.state;
        this.errorMessage = builder.errorMessage;
    }

    public static BsqSwapTradeInfo toBsqSwapTradeInfo(BsqSwapTrade trade, String role, boolean wasMyOffer) {
        var protocolModel = trade.getBsqSwapProtocolModel();
        var swapPeer = protocolModel.getTradePeer();
        var makerBsqAddress = wasMyOffer ? protocolModel.getBsqAddress() : swapPeer.getBsqAddress();
        var makerBtcAddress = wasMyOffer ? protocolModel.getBtcAddress() : swapPeer.getBtcAddress();
        var takerBsqAddress = wasMyOffer ? swapPeer.getBsqAddress() : protocolModel.getBsqAddress();
        var takerBtcAddress = wasMyOffer ? swapPeer.getBtcAddress() : protocolModel.getBtcAddress();
        var offerInfo = wasMyOffer ? toMyOfferInfo(trade.getOffer()) : toOfferInfo(trade.getOffer());
        return new Builder()
                .withBsqSwapOffer(offerInfo)
                .withTradeId(trade.getId())
                .withTempTradingPeerNodeAddress(trade.getBsqSwapProtocolModel().getTempTradingPeerNodeAddress().getFullAddress())
                .withPeerNodeAddress(trade.getTradingPeerNodeAddress().getFullAddress())
                .withTxId(trade.getTxId())
                .withBsqTradeAmount(trade.getBsqTradeAmount())
                .withBtcTradeAmount(trade.getAmountAsLong())
                .withTradePrice(trade.getPrice().getValue())
                .withBsqMakerTradeFee(trade.getMakerFeeAsLong())
                .withBsqTakerTradeFee(trade.getTakerFeeAsLong())
                .withTxFeePerVbyte(trade.getTxFeePerVbyte())
                .withTxFee(trade.getTxFee().value)
                .withMakerBsqAddress(makerBsqAddress)
                .withMakerBtcAddress(makerBtcAddress)
                .withTakerBsqAddress(takerBsqAddress)
                .withTakerBtcAddress(takerBtcAddress)
                .withTakeOfferDate(trade.getTakeOfferDate())
                .withRole(role == null ? "" : role)
                .withState(trade.getTradeState().name())
                .withErrorMessage(trade.getErrorMessage())
                .build();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.BsqSwapTradeInfo toProtoMessage() {
        return bisq.proto.grpc.BsqSwapTradeInfo.newBuilder()
                .setOffer(bsqSwapOffer.toProtoMessage())
                .setTradeId(tradeId)
                .setTempTradingPeerNodeAddress(tempTradingPeerNodeAddress != null ? tempTradingPeerNodeAddress : "")
                .setPeerNodeAddress(peerNodeAddress != null ? peerNodeAddress : "")
                .setTxId(txId != null ? txId : "")
                .setBsqTradeAmount(bsqTradeAmount)
                .setBtcTradeAmount(btcTradeAmount)
                .setTradePrice(tradePrice)
                .setBsqMakerTradeFee(bsqMakerTradeFee)
                .setBsqTakerTradeFee(bsqTakerTradeFee)
                .setTxFeePerVbyte(txFeePerVbyte)
                .setTxFee(txFee)
                .setMakerBsqAddress(makerBsqAddress != null ? makerBsqAddress : "")
                .setTakerBsqAddress(takerBsqAddress != null ? takerBsqAddress : "")
                .setMakerBtcAddress(makerBtcAddress != null ? makerBtcAddress : "")
                .setTakerBtcAddress(takerBtcAddress != null ? takerBtcAddress : "")
                .setTakeOfferDate(takeOfferDate)
                .setRole(role)
                .setState(state)
                .setErrorMessage(errorMessage != null ? errorMessage : "")
                .build();
    }

    public static BsqSwapTradeInfo fromProto(bisq.proto.grpc.BsqSwapTradeInfo proto) {
        return new Builder()
                .withBsqSwapOffer(OfferInfo.fromProto(proto.getOffer()))
                .withTradeId(proto.getTradeId())
                .withTempTradingPeerNodeAddress(proto.getTempTradingPeerNodeAddress())
                .withPeerNodeAddress(proto.getPeerNodeAddress())
                .withTxId(proto.getTxId())
                .withBsqTradeAmount(proto.getBsqTradeAmount())
                .withBtcTradeAmount(proto.getBtcTradeAmount())
                .withTradePrice(proto.getTradePrice())
                .withBsqMakerTradeFee(proto.getBsqMakerTradeFee())
                .withBsqTakerTradeFee(proto.getBsqTakerTradeFee())
                .withTxFeePerVbyte(proto.getTxFeePerVbyte())
                .withTxFee(proto.getTxFee())
                .withMakerBsqAddress(proto.getMakerBsqAddress())
                .withMakerBtcAddress(proto.getMakerBtcAddress())
                .withTakerBsqAddress(proto.getTakerBsqAddress())
                .withTakerBtcAddress(proto.getTakerBtcAddress())
                .withTakeOfferDate(proto.getTakeOfferDate())
                .withRole(proto.getRole())
                .withState(proto.getState())
                .withErrorMessage(proto.getErrorMessage())
                .build();
    }

    private static class Builder {
        private OfferInfo bsqSwapOffer;
        private String tradeId;
        private String tempTradingPeerNodeAddress;
        private String peerNodeAddress;
        private String txId;
        private long bsqTradeAmount;
        private long btcTradeAmount;
        private long tradePrice;
        private long bsqMakerTradeFee;
        private long bsqTakerTradeFee;
        private long txFeePerVbyte;
        private long txFee;
        private String makerBsqAddress;
        private String makerBtcAddress;
        private String takerBsqAddress;
        private String takerBtcAddress;
        private long takeOfferDate;
        private String role;
        private String state;
        private String errorMessage;

        public Builder withBsqSwapOffer(OfferInfo bsqSwapOffer) {
            this.bsqSwapOffer = bsqSwapOffer;
            return this;
        }

        public Builder withTradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public Builder withTempTradingPeerNodeAddress(String tempTradingPeerNodeAddress) {
            this.tempTradingPeerNodeAddress = tempTradingPeerNodeAddress;
            return this;
        }

        public Builder withPeerNodeAddress(String peerNodeAddress) {
            this.peerNodeAddress = peerNodeAddress;
            return this;
        }

        public Builder withTxId(String txId) {
            this.txId = txId;
            return this;
        }

        public Builder withBsqTradeAmount(long bsqTradeAmount) {
            this.bsqTradeAmount = bsqTradeAmount;
            return this;
        }

        public Builder withBtcTradeAmount(long btcTradeAmount) {
            this.btcTradeAmount = btcTradeAmount;
            return this;
        }

        public Builder withTradePrice(long tradePrice) {
            this.tradePrice = tradePrice;
            return this;
        }

        public Builder withBsqMakerTradeFee(long bsqMakerTradeFee) {
            this.bsqMakerTradeFee = bsqMakerTradeFee;
            return this;
        }

        public Builder withBsqTakerTradeFee(long bsqTakerTradeFee) {
            this.bsqTakerTradeFee = bsqTakerTradeFee;
            return this;
        }

        public Builder withTxFeePerVbyte(long txFeePerVbyte) {
            this.txFeePerVbyte = txFeePerVbyte;
            return this;
        }

        public Builder withTxFee(long txFee) {
            this.txFee = txFee;
            return this;
        }

        public Builder withMakerBsqAddress(String makerBsqAddress) {
            this.makerBsqAddress = makerBsqAddress;
            return this;
        }

        public Builder withMakerBtcAddress(String makerBtcAddress) {
            this.makerBtcAddress = makerBtcAddress;
            return this;
        }

        public Builder withTakerBsqAddress(String takerBsqAddress) {
            this.takerBsqAddress = takerBsqAddress;
            return this;
        }

        public Builder withTakerBtcAddress(String takerBtcAddress) {
            this.takerBtcAddress = takerBtcAddress;
            return this;
        }

        public Builder withTakeOfferDate(long takeOfferDate) {
            this.takeOfferDate = takeOfferDate;
            return this;
        }

        public Builder withRole(String role) {
            this.role = role;
            return this;
        }

        public Builder withState(String state) {
            this.state = state;
            return this;
        }

        public Builder withErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public BsqSwapTradeInfo build() {
            return new BsqSwapTradeInfo(this);
        }
    }
}

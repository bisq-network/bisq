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

import static bisq.core.api.model.BsqSwapOfferInfo.toBsqSwapOfferInfo;

@EqualsAndHashCode
@ToString
@Getter
public class BsqSwapTradeInfo implements Payload {

    private final BsqSwapOfferInfo bsqSwapOffer;
    private final String tradeId;
    private final String tempTradingPeerNodeAddress;
    private final String peerNodeAddress;
    private final String txId;
    private final long bsqTradeAmount;
    private final long bsqMaxTradeAmount;
    private final long bsqMinTradeAmount;
    private final long btcTradeAmount;
    private final long btcMaxTradeAmount;
    private final long btcMinTradeAmount;
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
    private final String state;
    private final String errorMessage;

    public BsqSwapTradeInfo(BsqSwapTradeInfoBuilder builder) {
        this.bsqSwapOffer = builder.bsqSwapOfferInfo;
        this.tradeId = builder.tradeId;
        this.tempTradingPeerNodeAddress = builder.tempTradingPeerNodeAddress;
        this.peerNodeAddress = builder.peerNodeAddress;
        this.txId = builder.txId;
        this.bsqTradeAmount = builder.bsqTradeAmount;
        this.bsqMaxTradeAmount = builder.bsqMaxTradeAmount;
        this.bsqMinTradeAmount = builder.bsqMinTradeAmount;
        this.btcTradeAmount = builder.btcTradeAmount;
        this.btcMaxTradeAmount = builder.btcMaxTradeAmount;
        this.btcMinTradeAmount = builder.btcMinTradeAmount;
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
        this.state = builder.state;
        this.errorMessage = builder.errorMessage;
    }

    public static BsqSwapTradeInfo toBsqSwapTradeInfo(BsqSwapTrade trade) {
        return toBsqSwapTradeInfo(trade, null);
    }

    //TODO
    public static BsqSwapTradeInfo toBsqSwapTradeInfo(BsqSwapTrade trade, String role) {
        return new BsqSwapTradeInfoBuilder()
                .withBsqSwapOffer(toBsqSwapOfferInfo(trade.getOffer()))
                .withTradeId(trade.getId())
                .withTempTradingPeerNodeAddress(trade.getBsqSwapProtocolModel().getTempTradingPeerNodeAddress().getFullAddress())
                .withPeerNodeAddress(trade.getTradingPeerNodeAddress().getFullAddress())
                .withTxId(trade.getTxId())
                /*   .withBsqTradeAmount(trade.getBsqSwapProtocolModel().getBsqTradeAmount())
                   .withBsqMaxTradeAmount(trade.getBsqSwapProtocolModel().getBsqMaxTradeAmount())
                   .withBsqMinTradeAmount(trade.getBsqSwapProtocolModel().getBsqMinTradeAmount())
                   .withBtcTradeAmount(trade.getBsqSwapProtocolModel().getBtcTradeAmount())
                   .withBtcMaxTradeAmount(trade.getBsqSwapProtocolModel().getBtcMaxTradeAmount())
                   .withBtcMinTradeAmount(trade.getBsqSwapProtocolModel().getBtcMinTradeAmount())
                   .withTradePrice(trade.getBsqSwapProtocolModel().getTradePrice())
                   .withBsqMakerTradeFee(trade.getBsqSwapProtocolModel().getBsqMakerTradeFee())
                   .withBsqTakerTradeFee(trade.getBsqSwapProtocolModel().getBsqTakerTradeFee())
                   .withTxFeePerVbyte(trade.getBsqSwapProtocolModel().getTxFeePerVbyte())
                   .withTxFee(trade.getBsqSwapProtocolModel().getTxFee())
                   .withMakerBsqAddress(trade.getBsqSwapProtocolModel().getMakerBsqAddress())
                   .withMakerBtcAddress(trade.getBsqSwapProtocolModel().getMakerBtcAddress())
                   .withTakerBsqAddress(trade.getBsqSwapProtocolModel().getTakerBsqAddress())
                   .withTakerBtcAddress(trade.getBsqSwapProtocolModel().getTakerBtcAddress())*/
                .withTakeOfferDate(trade.getTakeOfferDate())
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
                .setBsqSwapOfferInfo(bsqSwapOffer.toProtoMessage())
                .setTradeId(tradeId)
                .setTempTradingPeerNodeAddress(tempTradingPeerNodeAddress != null ? tempTradingPeerNodeAddress : "")
                .setPeerNodeAddress(peerNodeAddress != null ? peerNodeAddress : "")
                .setTxId(txId != null ? txId : "")
                .setBsqTradeAmount(bsqTradeAmount)
                .setBsqMaxTradeAmount(bsqMaxTradeAmount)
                .setBsqMinTradeAmount(bsqMinTradeAmount)
                .setBtcTradeAmount(btcTradeAmount)
                .setBtcMaxTradeAmount(btcMaxTradeAmount)
                .setBtcMinTradeAmount(btcMinTradeAmount)
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
                .setState(state)
                .setErrorMessage(errorMessage != null ? errorMessage : "")
                .build();
    }

    public static BsqSwapTradeInfo fromProto(bisq.proto.grpc.BsqSwapTradeInfo proto) {
        return new BsqSwapTradeInfoBuilder()
                .withBsqSwapOffer(BsqSwapOfferInfo.fromProto(proto.getBsqSwapOfferInfo()))
                .withTradeId(proto.getTradeId())
                .withTempTradingPeerNodeAddress(proto.getTempTradingPeerNodeAddress())
                .withPeerNodeAddress(proto.getPeerNodeAddress())
                .withTxId(proto.getTxId())
                .withBsqTradeAmount(proto.getBsqTradeAmount())
                .withBsqMaxTradeAmount(proto.getBsqMaxTradeAmount())
                .withBsqMinTradeAmount(proto.getBsqMinTradeAmount())
                .withBtcTradeAmount(proto.getBtcTradeAmount())
                .withBtcMaxTradeAmount(proto.getBtcMaxTradeAmount())
                .withBtcMinTradeAmount(proto.getBtcMinTradeAmount())
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
                .withState(proto.getState())
                .withErrorMessage(proto.getErrorMessage())
                .build();
    }

    public static class BsqSwapTradeInfoBuilder {
        private BsqSwapOfferInfo bsqSwapOfferInfo;
        private String tradeId;
        private String tempTradingPeerNodeAddress;
        private String peerNodeAddress;
        private String txId;
        private long bsqTradeAmount;
        private long bsqMaxTradeAmount;
        private long bsqMinTradeAmount;
        private long btcTradeAmount;
        private long btcMaxTradeAmount;
        private long btcMinTradeAmount;
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
        private String state;
        private String errorMessage;

        public BsqSwapTradeInfoBuilder withBsqSwapOffer(BsqSwapOfferInfo bsqSwapOfferInfo) {
            this.bsqSwapOfferInfo = bsqSwapOfferInfo;
            return this;
        }

        public BsqSwapTradeInfoBuilder withTradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public BsqSwapTradeInfoBuilder withTempTradingPeerNodeAddress(String tempTradingPeerNodeAddress) {
            this.tempTradingPeerNodeAddress = tempTradingPeerNodeAddress;
            return this;
        }

        public BsqSwapTradeInfoBuilder withPeerNodeAddress(String peerNodeAddress) {
            this.peerNodeAddress = peerNodeAddress;
            return this;
        }

        public BsqSwapTradeInfoBuilder withTxId(String txId) {
            this.txId = txId;
            return this;
        }

        public BsqSwapTradeInfoBuilder withBsqTradeAmount(long bsqTradeAmount) {
            this.bsqTradeAmount = bsqTradeAmount;
            return this;
        }

        public BsqSwapTradeInfoBuilder withBsqMaxTradeAmount(long bsqMaxTradeAmount) {
            this.bsqMaxTradeAmount = bsqMaxTradeAmount;
            return this;
        }

        public BsqSwapTradeInfoBuilder withBsqMinTradeAmount(long bsqMinTradeAmount) {
            this.bsqMinTradeAmount = bsqMinTradeAmount;
            return this;
        }

        public BsqSwapTradeInfoBuilder withBtcTradeAmount(long btcTradeAmount) {
            this.btcTradeAmount = btcTradeAmount;
            return this;
        }

        public BsqSwapTradeInfoBuilder withBtcMaxTradeAmount(long btcMaxTradeAmount) {
            this.btcMaxTradeAmount = btcMaxTradeAmount;
            return this;
        }

        public BsqSwapTradeInfoBuilder withBtcMinTradeAmount(long btcMinTradeAmount) {
            this.btcMinTradeAmount = btcMinTradeAmount;
            return this;
        }

        public BsqSwapTradeInfoBuilder withTradePrice(long tradePrice) {
            this.tradePrice = tradePrice;
            return this;
        }

        public BsqSwapTradeInfoBuilder withBsqMakerTradeFee(long bsqMakerTradeFee) {
            this.bsqMakerTradeFee = bsqMakerTradeFee;
            return this;
        }

        public BsqSwapTradeInfoBuilder withBsqTakerTradeFee(long bsqTakerTradeFee) {
            this.bsqTakerTradeFee = bsqTakerTradeFee;
            return this;
        }

        public BsqSwapTradeInfoBuilder withTxFeePerVbyte(long txFeePerVbyte) {
            this.txFeePerVbyte = txFeePerVbyte;
            return this;
        }

        public BsqSwapTradeInfoBuilder withTxFee(long txFee) {
            this.txFee = txFee;
            return this;
        }

        public BsqSwapTradeInfoBuilder withMakerBsqAddress(String makerBsqAddress) {
            this.makerBsqAddress = makerBsqAddress;
            return this;
        }

        public BsqSwapTradeInfoBuilder withMakerBtcAddress(String makerBtcAddress) {
            this.makerBtcAddress = makerBtcAddress;
            return this;
        }

        public BsqSwapTradeInfoBuilder withTakerBsqAddress(String takerBsqAddress) {
            this.takerBsqAddress = takerBsqAddress;
            return this;
        }

        public BsqSwapTradeInfoBuilder withTakerBtcAddress(String takerBtcAddress) {
            this.takerBtcAddress = takerBtcAddress;
            return this;
        }

        public BsqSwapTradeInfoBuilder withTakeOfferDate(long takeOfferDate) {
            this.takeOfferDate = takeOfferDate;
            return this;
        }

        public BsqSwapTradeInfoBuilder withState(String state) {
            this.state = state;
            return this;
        }

        public BsqSwapTradeInfoBuilder withErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public BsqSwapTradeInfo build() {
            return new BsqSwapTradeInfo(this);
        }
    }
}

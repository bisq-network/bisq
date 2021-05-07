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

import bisq.core.trade.atomic.AtomicTrade;

import bisq.common.Payload;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static bisq.core.api.model.AtomicOfferInfo.toAtomicOfferInfo;

@EqualsAndHashCode
@ToString
@Getter
public class AtomicTradeInfo implements Payload {

    private final AtomicOfferInfo atomicOffer;
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
    private final boolean isCurrencyForMakerFeeBtc;
    private final boolean isCurrencyForTakerFeeBtc;
    private final long bsqMakerTradeFee;
    private final long btcMakerTradeFee;
    private final long bsqTakerTradeFee;
    private final long btcTakerTradeFee;
    private final long txFeePerVbyte;
    private final long txFee;
    private final String makerBsqAddress;
    private final String makerBtcAddress;
    private final String takerBsqAddress;
    private final String takerBtcAddress;
    private final long takeOfferDate;
    private final String state;
    private final String errorMessage;

    public AtomicTradeInfo(AtomicTradeInfoBuilder builder) {
        this.atomicOffer = builder.atomicOffer;
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
        this.isCurrencyForMakerFeeBtc = builder.isCurrencyForMakerFeeBtc;
        this.isCurrencyForTakerFeeBtc = builder.isCurrencyForTakerFeeBtc;
        this.bsqMakerTradeFee = builder.bsqMakerTradeFee;
        this.btcMakerTradeFee = builder.btcMakerTradeFee;
        this.bsqTakerTradeFee = builder.bsqTakerTradeFee;
        this.btcTakerTradeFee = builder.btcTakerTradeFee;
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

    public static AtomicTradeInfo toAtomicTradeInfo(AtomicTrade trade) {
        return toAtomicTradeInfo(trade, null);
    }

    public static AtomicTradeInfo toAtomicTradeInfo(AtomicTrade trade, String role) {
        return new AtomicTradeInfoBuilder()
                .withAtomicOffer(toAtomicOfferInfo(trade.getOffer()))
                .withTradeId(trade.getId())
                .withTempTradingPeerNodeAddress(trade.getAtomicProcessModel().getTempTradingPeerNodeAddress().getFullAddress())
                .withPeerNodeAddress(trade.getPeerNodeAddress().getFullAddress())
                .withTxId(trade.getTxId())
                .withBsqTradeAmount(trade.getAtomicProcessModel().getBsqTradeAmount())
                .withBsqMaxTradeAmount(trade.getAtomicProcessModel().getBsqMaxTradeAmount())
                .withBsqMinTradeAmount(trade.getAtomicProcessModel().getBsqMinTradeAmount())
                .withBtcTradeAmount(trade.getAtomicProcessModel().getBtcTradeAmount())
                .withBtcMaxTradeAmount(trade.getAtomicProcessModel().getBtcMaxTradeAmount())
                .withBtcMinTradeAmount(trade.getAtomicProcessModel().getBtcMinTradeAmount())
                .withTradePrice(trade.getAtomicProcessModel().getTradePrice())
                .withIsCurrencyForMakerFeeBtc(trade.isCurrencyForMakerFeeBtc())
                .withIsCurrencyForTakerFeeBtc(trade.isCurrencyForTakerFeeBtc())
                .withBsqMakerTradeFee(trade.getAtomicProcessModel().getBsqMakerTradeFee())
                .withBtcMakerTradeFee(trade.getAtomicProcessModel().getBtcMakerTradeFee())
                .withBsqTakerTradeFee(trade.getAtomicProcessModel().getBsqTakerTradeFee())
                .withBtcTakerTradeFee(trade.getAtomicProcessModel().getBtcTakerTradeFee())
                .withTxFeePerVbyte(trade.getAtomicProcessModel().getTxFeePerVbyte())
                .withTxFee(trade.getAtomicProcessModel().getTxFee())
                .withMakerBsqAddress(trade.getAtomicProcessModel().getMakerBsqAddress())
                .withMakerBtcAddress(trade.getAtomicProcessModel().getMakerBtcAddress())
                .withTakerBsqAddress(trade.getAtomicProcessModel().getTakerBsqAddress())
                .withTakerBtcAddress(trade.getAtomicProcessModel().getTakerBtcAddress())
                .withTakeOfferDate(trade.getTakeOfferDate())
                .withState(trade.getState().name())
                .withErrorMessage(trade.getErrorMessage())
                .build();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.AtomicTradeInfo toProtoMessage() {
        return bisq.proto.grpc.AtomicTradeInfo.newBuilder()
                .setAtomicOffer(atomicOffer.toProtoMessage())
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
                .setIsCurrencyForMakerFeeBtc(isCurrencyForMakerFeeBtc)
                .setIsCurrencyForTakerFeeBtc(isCurrencyForTakerFeeBtc)
                .setBsqMakerTradeFee(bsqMakerTradeFee)
                .setBtcMakerTradeFee(btcMakerTradeFee)
                .setBsqTakerTradeFee(bsqTakerTradeFee)
                .setBtcTakerTradeFee(btcTakerTradeFee)
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

    public static AtomicTradeInfo fromProto(bisq.proto.grpc.AtomicTradeInfo proto) {
        return new AtomicTradeInfoBuilder()
                .withAtomicOffer(AtomicOfferInfo.fromProto(proto.getAtomicOffer()))
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
                .withIsCurrencyForMakerFeeBtc(proto.getIsCurrencyForMakerFeeBtc())
                .withIsCurrencyForTakerFeeBtc(proto.getIsCurrencyForTakerFeeBtc())
                .withBsqMakerTradeFee(proto.getBsqMakerTradeFee())
                .withBtcMakerTradeFee(proto.getBtcMakerTradeFee())
                .withBsqTakerTradeFee(proto.getBsqTakerTradeFee())
                .withBtcTakerTradeFee(proto.getBtcTakerTradeFee())
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

    public static class AtomicTradeInfoBuilder {
        private AtomicOfferInfo atomicOffer;
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
        private boolean isCurrencyForMakerFeeBtc;
        private boolean isCurrencyForTakerFeeBtc;
        private long bsqMakerTradeFee;
        private long btcMakerTradeFee;
        private long bsqTakerTradeFee;
        private long btcTakerTradeFee;
        private long txFeePerVbyte;
        private long txFee;
        private String makerBsqAddress;
        private String makerBtcAddress;
        private String takerBsqAddress;
        private String takerBtcAddress;
        private long takeOfferDate;
        private String state;
        private String errorMessage;

        public AtomicTradeInfoBuilder withAtomicOffer(AtomicOfferInfo atomicOffer) {
            this.atomicOffer = atomicOffer;
            return this;
        }

        public AtomicTradeInfoBuilder withTradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public AtomicTradeInfoBuilder withTempTradingPeerNodeAddress(String tempTradingPeerNodeAddress) {
            this.tempTradingPeerNodeAddress = tempTradingPeerNodeAddress;
            return this;
        }

        public AtomicTradeInfoBuilder withPeerNodeAddress(String peerNodeAddress) {
            this.peerNodeAddress = peerNodeAddress;
            return this;
        }

        public AtomicTradeInfoBuilder withTxId(String txId) {
            this.txId = txId;
            return this;
        }

        public AtomicTradeInfoBuilder withBsqTradeAmount(long bsqTradeAmount) {
            this.bsqTradeAmount = bsqTradeAmount;
            return this;
        }

        public AtomicTradeInfoBuilder withBsqMaxTradeAmount(long bsqMaxTradeAmount) {
            this.bsqMaxTradeAmount = bsqMaxTradeAmount;
            return this;
        }

        public AtomicTradeInfoBuilder withBsqMinTradeAmount(long bsqMinTradeAmount) {
            this.bsqMinTradeAmount = bsqMinTradeAmount;
            return this;
        }

        public AtomicTradeInfoBuilder withBtcTradeAmount(long btcTradeAmount) {
            this.btcTradeAmount = btcTradeAmount;
            return this;
        }

        public AtomicTradeInfoBuilder withBtcMaxTradeAmount(long btcMaxTradeAmount) {
            this.btcMaxTradeAmount = btcMaxTradeAmount;
            return this;
        }

        public AtomicTradeInfoBuilder withBtcMinTradeAmount(long btcMinTradeAmount) {
            this.btcMinTradeAmount = btcMinTradeAmount;
            return this;
        }

        public AtomicTradeInfoBuilder withTradePrice(long tradePrice) {
            this.tradePrice = tradePrice;
            return this;
        }

        public AtomicTradeInfoBuilder withIsCurrencyForMakerFeeBtc(boolean isCurrencyForMakerFeeBtc) {
            this.isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc;
            return this;
        }

        public AtomicTradeInfoBuilder withIsCurrencyForTakerFeeBtc(boolean isCurrencyForTakerFeeBtc) {
            this.isCurrencyForTakerFeeBtc = isCurrencyForTakerFeeBtc;
            return this;
        }

        public AtomicTradeInfoBuilder withBsqMakerTradeFee(long bsqMakerTradeFee) {
            this.bsqMakerTradeFee = bsqMakerTradeFee;
            return this;
        }

        public AtomicTradeInfoBuilder withBtcMakerTradeFee(long btcMakerTradeFee) {
            this.btcMakerTradeFee = btcMakerTradeFee;
            return this;
        }

        public AtomicTradeInfoBuilder withBsqTakerTradeFee(long bsqTakerTradeFee) {
            this.bsqTakerTradeFee = bsqTakerTradeFee;
            return this;
        }

        public AtomicTradeInfoBuilder withBtcTakerTradeFee(long btcTakerTradeFee) {
            this.btcTakerTradeFee = btcTakerTradeFee;
            return this;
        }

        public AtomicTradeInfoBuilder withTxFeePerVbyte(long txFeePerVbyte) {
            this.txFeePerVbyte = txFeePerVbyte;
            return this;
        }

        public AtomicTradeInfoBuilder withTxFee(long txFee) {
            this.txFee = txFee;
            return this;
        }

        public AtomicTradeInfoBuilder withMakerBsqAddress(String makerBsqAddress) {
            this.makerBsqAddress = makerBsqAddress;
            return this;
        }

        public AtomicTradeInfoBuilder withMakerBtcAddress(String makerBtcAddress) {
            this.makerBtcAddress = makerBtcAddress;
            return this;
        }

        public AtomicTradeInfoBuilder withTakerBsqAddress(String takerBsqAddress) {
            this.takerBsqAddress = takerBsqAddress;
            return this;
        }

        public AtomicTradeInfoBuilder withTakerBtcAddress(String takerBtcAddress) {
            this.takerBtcAddress = takerBtcAddress;
            return this;
        }

        public AtomicTradeInfoBuilder withTakeOfferDate(long takeOfferDate) {
            this.takeOfferDate = takeOfferDate;
            return this;
        }

        public AtomicTradeInfoBuilder withState(String state) {
            this.state = state;
            return this;
        }

        public AtomicTradeInfoBuilder withErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public AtomicTradeInfo build() {
            return new AtomicTradeInfo(this);
        }
    }
}

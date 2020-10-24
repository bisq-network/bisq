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

import bisq.core.trade.Trade;

import bisq.common.Payload;

import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import static bisq.core.api.model.OfferInfo.toOfferInfo;

@EqualsAndHashCode
@Getter
public class TradeInfo implements Payload {

    // The client cannot see bisq.core.trade.Trade or its fromProto method.  We use the
    // lighter weight TradeInfo proto wrapper instead, containing just enough fields to
    // view and interact with trades.

    private final OfferInfo offer;
    private final String tradeId;
    private final String shortId;
    private final long date;
    private final boolean isCurrencyForTakerFeeBtc;
    private final long txFeeAsLong;
    private final long takerFeeAsLong;
    private final String takerFeeTxId;
    private final String depositTxId;
    private final String payoutTxId;
    private final long tradeAmountAsLong;
    private final long tradePrice;
    private final String tradingPeerNodeAddress;
    private final String state;
    private final String phase;
    private final String tradePeriodState;
    private final boolean isDepositPublished;
    private final boolean isDepositConfirmed;
    private final boolean isFiatSent;
    private final boolean isFiatReceived;
    private final boolean isPayoutPublished;
    private final boolean isWithdrawn;
    private final String contractAsJson;

    public TradeInfo(TradeInfoBuilder builder) {
        this.offer = builder.offer;
        this.tradeId = builder.tradeId;
        this.shortId = builder.shortId;
        this.date = builder.date;
        this.isCurrencyForTakerFeeBtc = builder.isCurrencyForTakerFeeBtc;
        this.txFeeAsLong = builder.txFeeAsLong;
        this.takerFeeAsLong = builder.takerFeeAsLong;
        this.takerFeeTxId = builder.takerFeeTxId;
        this.depositTxId = builder.depositTxId;
        this.payoutTxId = builder.payoutTxId;
        this.tradeAmountAsLong = builder.tradeAmountAsLong;
        this.tradePrice = builder.tradePrice;
        this.tradingPeerNodeAddress = builder.tradingPeerNodeAddress;
        this.state = builder.state;
        this.phase = builder.phase;
        this.tradePeriodState = builder.tradePeriodState;
        this.isDepositPublished = builder.isDepositPublished;
        this.isDepositConfirmed = builder.isDepositConfirmed;
        this.isFiatSent = builder.isFiatSent;
        this.isFiatReceived = builder.isFiatReceived;
        this.isPayoutPublished = builder.isPayoutPublished;
        this.isWithdrawn = builder.isWithdrawn;
        this.contractAsJson = builder.contractAsJson;
    }

    public static TradeInfo toTradeInfo(Trade trade) {
        return new TradeInfo.TradeInfoBuilder()
                .withOffer(toOfferInfo(trade.getOffer()))
                .withTradeId(trade.getId())
                .withShortId(trade.getShortId())
                .withDate(trade.getDate().getTime())
                .withIsCurrencyForTakerFeeBtc(trade.isCurrencyForTakerFeeBtc())
                .withTxFeeAsLong(trade.getTxFeeAsLong())
                .withTakerFeeAsLong(trade.getTakerFeeAsLong())
                .withTakerFeeAsLong(trade.getTakerFeeAsLong())
                .withTakerFeeTxId(trade.getTakerFeeTxId())
                .withDepositTxId(trade.getDepositTxId())
                .withPayoutTxId(trade.getPayoutTxId())
                .withTradeAmountAsLong(trade.getTradeAmountAsLong())
                .withTradePrice(trade.getTradePrice().getValue())
                .withTradingPeerNodeAddress(Objects.requireNonNull(
                        trade.getTradingPeerNodeAddress()).getHostNameWithoutPostFix())
                .withState(trade.getState().name())
                .withPhase(trade.getPhase().name())
                .withTradePeriodState(trade.getTradePeriodState().name())
                .withIsDepositPublished(trade.isDepositPublished())
                .withIsDepositConfirmed(trade.isDepositConfirmed())
                .withIsFiatSent(trade.isFiatSent())
                .withIsFiatReceived(trade.isFiatReceived())
                .withIsPayoutPublished(trade.isPayoutPublished())
                .withIsWithdrawn(trade.isWithdrawn())
                .withContractAsJson(trade.getContractAsJson())
                .build();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.TradeInfo toProtoMessage() {
        return bisq.proto.grpc.TradeInfo.newBuilder()
                .setOffer(offer.toProtoMessage())
                .setTradeId(tradeId)
                .setShortId(shortId)
                .setDate(date)
                .setIsCurrencyForTakerFeeBtc(isCurrencyForTakerFeeBtc)
                .setTxFeeAsLong(txFeeAsLong)
                .setTakerFeeAsLong(takerFeeAsLong)
                .setTakerFeeTxId(takerFeeTxId == null ? "" : takerFeeTxId)
                .setDepositTxId(depositTxId == null ? "" : depositTxId)
                .setPayoutTxId(payoutTxId == null ? "" : payoutTxId)
                .setTradeAmountAsLong(tradeAmountAsLong)
                .setTradePrice(tradePrice)
                .setTradingPeerNodeAddress(tradingPeerNodeAddress)
                .setState(state)
                .setPhase(phase)
                .setTradePeriodState(tradePeriodState)
                .setIsDepositPublished(isDepositPublished)
                .setIsDepositConfirmed(isDepositConfirmed)
                .setIsFiatSent(isFiatSent)
                .setIsFiatReceived(isFiatReceived)
                .setIsPayoutPublished(isPayoutPublished)
                .setIsWithdrawn(isWithdrawn)
                .setContractAsJson(contractAsJson == null ? "" : contractAsJson)
                .build();
    }

    @SuppressWarnings({"unused", "SameReturnValue"})
    public static TradeInfo fromProto(bisq.proto.grpc.TradeInfo proto) {
        return null;  // TODO
    }

    /*
     * TradeInfoBuilder helps avoid bungling use of a large TradeInfo constructor
     * argument list.  If consecutive argument values of the same type are not
     * ordered correctly, the compiler won't complain but the resulting bugs could
     * be hard to find and fix.
     */
    public static class TradeInfoBuilder {
        private OfferInfo offer;
        private String tradeId;
        private String shortId;
        private long date;
        private boolean isCurrencyForTakerFeeBtc;
        private long txFeeAsLong;
        private long takerFeeAsLong;
        private String takerFeeTxId;
        private String depositTxId;
        private String payoutTxId;
        private long tradeAmountAsLong;
        private long tradePrice;
        private String tradingPeerNodeAddress;
        private String state;
        private String phase;
        private String tradePeriodState;
        private boolean isDepositPublished;
        private boolean isDepositConfirmed;
        private boolean isFiatSent;
        private boolean isFiatReceived;
        private boolean isPayoutPublished;
        private boolean isWithdrawn;
        private String contractAsJson;

        public TradeInfoBuilder withOffer(OfferInfo offer) {
            this.offer = offer;
            return this;
        }

        public TradeInfoBuilder withTradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public TradeInfoBuilder withShortId(String shortId) {
            this.shortId = shortId;
            return this;
        }

        public TradeInfoBuilder withDate(long date) {
            this.date = date;
            return this;
        }

        public TradeInfoBuilder withIsCurrencyForTakerFeeBtc(boolean isCurrencyForTakerFeeBtc) {
            this.isCurrencyForTakerFeeBtc = isCurrencyForTakerFeeBtc;
            return this;
        }

        public TradeInfoBuilder withTxFeeAsLong(long txFeeAsLong) {
            this.txFeeAsLong = txFeeAsLong;
            return this;
        }

        public TradeInfoBuilder withTakerFeeAsLong(long takerFeeAsLong) {
            this.takerFeeAsLong = takerFeeAsLong;
            return this;
        }

        public TradeInfoBuilder withTakerFeeTxId(String takerFeeTxId) {
            this.takerFeeTxId = takerFeeTxId;
            return this;
        }

        public TradeInfoBuilder withDepositTxId(String depositTxId) {
            this.depositTxId = depositTxId;
            return this;
        }

        public TradeInfoBuilder withPayoutTxId(String payoutTxId) {
            this.payoutTxId = payoutTxId;
            return this;
        }

        public TradeInfoBuilder withTradeAmountAsLong(long tradeAmountAsLong) {
            this.tradeAmountAsLong = tradeAmountAsLong;
            return this;
        }

        public TradeInfoBuilder withTradePrice(long tradePrice) {
            this.tradePrice = tradePrice;
            return this;
        }

        public TradeInfoBuilder withTradePeriodState(String tradePeriodState) {
            this.tradePeriodState = tradePeriodState;
            return this;
        }

        public TradeInfoBuilder withState(String state) {
            this.state = state;
            return this;
        }

        public TradeInfoBuilder withPhase(String phase) {
            this.phase = phase;
            return this;
        }

        public TradeInfoBuilder withTradingPeerNodeAddress(String tradingPeerNodeAddress) {
            this.tradingPeerNodeAddress = tradingPeerNodeAddress;
            return this;
        }

        public TradeInfoBuilder withIsDepositPublished(boolean isDepositPublished) {
            this.isDepositPublished = isDepositPublished;
            return this;
        }

        public TradeInfoBuilder withIsDepositConfirmed(boolean isDepositConfirmed) {
            this.isDepositConfirmed = isDepositConfirmed;
            return this;
        }

        public TradeInfoBuilder withIsFiatSent(boolean isFiatSent) {
            this.isFiatSent = isFiatSent;
            return this;
        }

        public TradeInfoBuilder withIsFiatReceived(boolean isFiatReceived) {
            this.isFiatReceived = isFiatReceived;
            return this;
        }

        public TradeInfoBuilder withIsPayoutPublished(boolean isPayoutPublished) {
            this.isPayoutPublished = isPayoutPublished;
            return this;
        }

        public TradeInfoBuilder withIsWithdrawn(boolean isWithdrawn) {
            this.isWithdrawn = isWithdrawn;
            return this;
        }

        public TradeInfoBuilder withContractAsJson(String contractAsJson) {
            this.contractAsJson = contractAsJson;
            return this;
        }

        public TradeInfo build() {
            return new TradeInfo(this);
        }
    }

    @Override
    public String toString() {
        return "TradeInfo{" +
                "  tradeId='" + tradeId + '\'' + "\n" +
                ", shortId='" + shortId + '\'' + "\n" +
                ", date='" + date + '\'' + "\n" +
                ", isCurrencyForTakerFeeBtc='" + isCurrencyForTakerFeeBtc + '\'' + "\n" +
                ", txFeeAsLong='" + txFeeAsLong + '\'' + "\n" +
                ", takerFeeAsLong='" + takerFeeAsLong + '\'' + "\n" +
                ", takerFeeTxId='" + takerFeeTxId + '\'' + "\n" +
                ", depositTxId='" + depositTxId + '\'' + "\n" +
                ", payoutTxId='" + payoutTxId + '\'' + "\n" +
                ", tradeAmountAsLong='" + tradeAmountAsLong + '\'' + "\n" +
                ", tradePrice='" + tradePrice + '\'' + "\n" +
                ", tradingPeerNodeAddress='" + tradingPeerNodeAddress + '\'' + "\n" +
                ", state='" + state + '\'' + "\n" +
                ", phase='" + phase + '\'' + "\n" +
                ", tradePeriodState='" + tradePeriodState + '\'' + "\n" +
                ", isDepositPublished=" + isDepositPublished + "\n" +
                ", isDepositConfirmed=" + isDepositConfirmed + "\n" +
                ", isFiatSent=" + isFiatSent + "\n" +
                ", isFiatReceived=" + isFiatReceived + "\n" +
                ", isPayoutPublished=" + isPayoutPublished + "\n" +
                ", isWithdrawn=" + isWithdrawn + "\n" +
                ", offer=" + offer + "\n" +
                ", contractAsJson=" + contractAsJson + "\n" +
                '}';
    }
}

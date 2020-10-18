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
    private final String state;
    private final String phase;
    private final String tradePeriodState;
    private final boolean isDepositPublished;
    private final boolean isDepositConfirmed;
    private final boolean isFiatSent;
    private final boolean isFiatReceived;
    private final boolean isPayoutPublished;
    private final boolean isWithdrawn;

    public TradeInfo(TradeInfoBuilder builder) {
        this.offer = builder.offer;
        this.tradeId = builder.tradeId;
        this.shortId = builder.shortId;
        this.state = builder.state;
        this.phase = builder.phase;
        this.tradePeriodState = builder.tradePeriodState;
        this.isDepositPublished = builder.isDepositPublished;
        this.isDepositConfirmed = builder.isDepositConfirmed;
        this.isFiatSent = builder.isFiatSent;
        this.isFiatReceived = builder.isFiatReceived;
        this.isPayoutPublished = builder.isPayoutPublished;
        this.isWithdrawn = builder.isWithdrawn;
    }

    public static TradeInfo toTradeInfo(Trade trade) {
        return new TradeInfo.TradeInfoBuilder()
                .withOffer(toOfferInfo(trade.getOffer()))
                .withTradeId(trade.getId())
                .withShortId(trade.getShortId())
                .withState(trade.getState().name())
                .withPhase(trade.getPhase().name())
                .withTradePeriodState(trade.getTradePeriodState().name())
                .withIsDepositPublished(trade.isDepositPublished())
                .withIsDepositConfirmed(trade.isDepositConfirmed())
                .withIsFiatSent(trade.isFiatSent())
                .withIsFiatReceived(trade.isFiatReceived())
                .withIsPayoutPublished(trade.isPayoutPublished())
                .withIsWithdrawn(trade.isWithdrawn())
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
                .setState(state)
                .setPhase(phase)
                .setTradePeriodState(tradePeriodState)
                .setIsDepositPublished(isDepositPublished)
                .setIsDepositConfirmed(isDepositConfirmed)
                .setIsFiatSent(isFiatSent)
                .setIsFiatReceived(isFiatReceived)
                .setIsPayoutPublished(isPayoutPublished)
                .setIsWithdrawn(isWithdrawn)
                .build();
    }

    public static TradeInfo fromProto(bisq.proto.grpc.TradeInfo proto) {
        // TODO
        return null;
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
        private String state;
        private String phase;
        private String tradePeriodState;
        private boolean isDepositPublished;
        private boolean isDepositConfirmed;
        private boolean isFiatSent;
        private boolean isFiatReceived;
        private boolean isPayoutPublished;
        private boolean isWithdrawn;

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

        public TradeInfoBuilder withState(String state) {
            this.state = state;
            return this;
        }

        public TradeInfoBuilder withPhase(String phase) {
            this.phase = phase;
            return this;
        }

        public TradeInfoBuilder withTradePeriodState(String tradePeriodState) {
            this.tradePeriodState = tradePeriodState;
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

        public TradeInfo build() {
            return new TradeInfo(this);
        }
    }

    @Override
    public String toString() {
        return "TradeInfo{" +
                "  tradeId='" + tradeId + '\'' + "\n" +
                ", shortId='" + shortId + '\'' + "\n" +
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
                '}';
    }
}

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

import bisq.common.Payload;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@Getter
public class OfferInfo implements Payload {

    private final String id;
    private final String direction;
    private final long price;
    private final boolean useMarketBasedPrice;
    private final double marketPriceMargin;
    private final long amount;
    private final long minAmount;
    private final long volume;
    private final long minVolume;
    private final long buyerSecurityDeposit;
    private final String paymentAccountId;   // only used when creating offer
    private final String paymentMethodId;
    private final String paymentMethodShortName;
    // For fiat offer the baseCurrencyCode is BTC and the counterCurrencyCode is the fiat currency
    // For altcoin offers it is the opposite. baseCurrencyCode is the altcoin and the counterCurrencyCode is BTC.
    private final String baseCurrencyCode;
    private final String counterCurrencyCode;
    private final long date;

    public OfferInfo(OfferInfoBuilder builder) {
        this.id = builder.id;
        this.direction = builder.direction;
        this.price = builder.price;
        this.useMarketBasedPrice = builder.useMarketBasedPrice;
        this.marketPriceMargin = builder.marketPriceMargin;
        this.amount = builder.amount;
        this.minAmount = builder.minAmount;
        this.volume = builder.volume;
        this.minVolume = builder.minVolume;
        this.buyerSecurityDeposit = builder.buyerSecurityDeposit;
        this.paymentAccountId = builder.paymentAccountId;
        this.paymentMethodId = builder.paymentMethodId;
        this.paymentMethodShortName = builder.paymentMethodShortName;
        this.baseCurrencyCode = builder.baseCurrencyCode;
        this.counterCurrencyCode = builder.counterCurrencyCode;
        this.date = builder.date;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.OfferInfo toProtoMessage() {
        return bisq.proto.grpc.OfferInfo.newBuilder()
                .setId(id)
                .setDirection(direction)
                .setPrice(price)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setMarketPriceMargin(marketPriceMargin)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setVolume(volume)
                .setMinVolume(minVolume)
                .setBuyerSecurityDeposit(buyerSecurityDeposit)
                .setPaymentAccountId(paymentAccountId)
                .setPaymentMethodId(paymentMethodId)
                .setPaymentMethodShortName(paymentMethodShortName)
                .setBaseCurrencyCode(baseCurrencyCode)
                .setCounterCurrencyCode(counterCurrencyCode)
                .setDate(date)
                .build();
    }

    public static OfferInfo fromProto(bisq.proto.grpc.OfferInfo proto) {
        /*
        TODO (will be needed by the createoffer method)
        return new OfferInfo(proto.getOfferPayload().getId(),
                proto.getOfferPayload().getDate());
        */
        return null;
    }

    /*
     * OfferInfoBuilder helps avoid bungling use of a large OfferInfo constructor
     * argument list.  If consecutive argument values of the same type are not
     * ordered correctly, the compiler won't complain but the resulting bugs could
     * be hard to find and fix.
     */
    public static class OfferInfoBuilder {
        private String id;
        private String direction;
        private long price;
        private boolean useMarketBasedPrice;
        private double marketPriceMargin;
        private long amount;
        private long minAmount;
        private long volume;
        private long minVolume;
        private long buyerSecurityDeposit;
        private String paymentAccountId;
        private String paymentMethodId;
        private String paymentMethodShortName;
        private String baseCurrencyCode;
        private String counterCurrencyCode;
        private long date;

        public OfferInfoBuilder() {
        }

        public OfferInfoBuilder withId(String id) {
            this.id = id;
            return this;
        }

        public OfferInfoBuilder withDirection(String direction) {
            this.direction = direction;
            return this;
        }

        public OfferInfoBuilder withPrice(long price) {
            this.price = price;
            return this;
        }

        public OfferInfoBuilder withUseMarketBasedPrice(boolean useMarketBasedPrice) {
            this.useMarketBasedPrice = useMarketBasedPrice;
            return this;
        }

        public OfferInfoBuilder withMarketPriceMargin(double useMarketBasedPrice) {
            this.marketPriceMargin = useMarketBasedPrice;
            return this;
        }

        public OfferInfoBuilder withAmount(long amount) {
            this.amount = amount;
            return this;
        }

        public OfferInfoBuilder withMinAmount(long minAmount) {
            this.minAmount = minAmount;
            return this;
        }

        public OfferInfoBuilder withVolume(long volume) {
            this.volume = volume;
            return this;
        }

        public OfferInfoBuilder withMinVolume(long minVolume) {
            this.minVolume = minVolume;
            return this;
        }

        public OfferInfoBuilder withBuyerSecurityDeposit(long buyerSecurityDeposit) {
            this.buyerSecurityDeposit = buyerSecurityDeposit;
            return this;
        }

        public OfferInfoBuilder withPaymentAccountId(String paymentAccountId) {
            this.paymentAccountId = paymentAccountId;
            return this;
        }

        public OfferInfoBuilder withPaymentMethodId(String paymentMethodId) {
            this.paymentMethodId = paymentMethodId;
            return this;
        }

        public OfferInfoBuilder withPaymentMethodShortName(String paymentMethodShortName) {
            this.paymentMethodShortName = paymentMethodShortName;
            return this;
        }

        public OfferInfoBuilder withBaseCurrencyCode(String baseCurrencyCode) {
            this.baseCurrencyCode = baseCurrencyCode;
            return this;
        }

        public OfferInfoBuilder withCounterCurrencyCode(String counterCurrencyCode) {
            this.counterCurrencyCode = counterCurrencyCode;
            return this;
        }

        public OfferInfoBuilder withDate(long date) {
            this.date = date;
            return this;
        }

        public OfferInfo build() {
            return new OfferInfo(this);
        }
    }
}

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

package bisq.core.trade.statistics;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.OfferPayload;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

@Immutable
@EqualsAndHashCode
@ToString
@Slf4j
public final class TradeStatisticsForJson {

    public final String currency;
    public final OfferPayload.Direction direction;
    public final long tradePrice;
    public final long tradeAmount;
    public final long tradeDate;
    public final String paymentMethod;
    public final long offerDate;
    public final boolean useMarketBasedPrice;
    public final double marketPriceMargin;
    public final long offerAmount;
    public final long offerMinAmount;
    public final String offerId;
    public final String depositTxId;

    // primaryMarket fields are based on industry standard where primaryMarket is always in the focus (in the app BTC is always in the focus - will be changed in a larger refactoring once)
    public String currencyPair;
    public OfferPayload.Direction primaryMarketDirection;

    public long primaryMarketTradePrice;
    public long primaryMarketTradeAmount;
    public long primaryMarketTradeVolume;

    public TradeStatisticsForJson(TradeStatistics2 tradeStatistics) {
        this.direction = OfferPayload.Direction.valueOf(tradeStatistics.getDirection().name());
        this.currency = tradeStatistics.getCurrencyCode();
        this.paymentMethod = tradeStatistics.getOfferPaymentMethod();
        this.offerDate = tradeStatistics.getOfferDate();
        this.useMarketBasedPrice = tradeStatistics.isOfferUseMarketBasedPrice();
        this.marketPriceMargin = tradeStatistics.getOfferMarketPriceMargin();
        this.offerAmount = tradeStatistics.getOfferAmount();
        this.offerMinAmount = tradeStatistics.getOfferMinAmount();
        this.offerId = tradeStatistics.getOfferId();
        this.tradePrice = tradeStatistics.getTradePrice().getValue();
        this.tradeAmount = tradeStatistics.getTradeAmount().getValue();
        this.tradeDate = tradeStatistics.getTradeDate().getTime();
        this.depositTxId = tradeStatistics.getDepositTxId();

        try {
            final Price tradePrice = getTradePrice();
            if (CurrencyUtil.isCryptoCurrency(currency)) {
                primaryMarketDirection = direction == OfferPayload.Direction.BUY ? OfferPayload.Direction.SELL : OfferPayload.Direction.BUY;
                currencyPair = currency + "/" + Res.getBaseCurrencyCode();

                primaryMarketTradePrice = tradePrice.getValue();

                primaryMarketTradeAmount = getTradeVolume() != null ? getTradeVolume().getValue() : 0;
                primaryMarketTradeVolume = getTradeAmount().getValue();
            } else {
                primaryMarketDirection = direction;
                currencyPair = Res.getBaseCurrencyCode() + "/" + currency;

                // we use precision 4 for fiat based price but on the markets api we use precision 8 so we scale up by 10000
                primaryMarketTradePrice = (long) MathUtils.scaleUpByPowerOf10(tradePrice.getValue(), 4);

                primaryMarketTradeAmount = getTradeAmount().getValue();
                // we use precision 4 for fiat but on the markets api we use precision 8 so we scale up by 10000
                primaryMarketTradeVolume = getTradeVolume() != null ?
                        (long) MathUtils.scaleUpByPowerOf10(getTradeVolume().getValue(), 4) : 0;
            }
        } catch (Throwable t) {
            log.error(t.getMessage());
            t.printStackTrace();
        }
    }

    public Price getTradePrice() {
        return Price.valueOf(currency, tradePrice);
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmount);
    }

    public Volume getTradeVolume() {
        return getTradePrice().getVolumeByAmount(getTradeAmount());
    }
}

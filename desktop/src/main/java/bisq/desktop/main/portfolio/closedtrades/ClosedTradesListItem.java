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

package bisq.desktop.main.portfolio.closedtrades;

import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.filtering.FilterableListItem;
import bisq.desktop.util.filtering.FilteringUtils;

import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.trade.ClosedTradableFormatter;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.model.Tradable;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import org.bitcoinj.core.Coin;

import org.apache.commons.lang3.StringUtils;

import java.util.Date;

import lombok.Getter;

public class ClosedTradesListItem implements FilterableListItem {
    @Getter
    private final Tradable tradable;
    private final ClosedTradableFormatter closedTradableFormatter;
    private final ClosedTradableManager closedTradableManager;

    public ClosedTradesListItem(
            Tradable tradable,
            ClosedTradableFormatter closedTradableFormatter,
            ClosedTradableManager closedTradableManager) {

        this.tradable = tradable;
        this.closedTradableFormatter = closedTradableFormatter;
        this.closedTradableManager = closedTradableManager;
    }

    public String getTradeId() {
        return tradable.getShortId();
    }

    public Coin getAmount() {
        return tradable.getOptionalAmount().orElse(null);
    }

    public String getAmountAsString() {
        return closedTradableFormatter.getAmountAsString(tradable);
    }

    public Price getPrice() {
        return tradable.getOptionalPrice().orElse(null);
    }

    public String getPriceAsString() {
        return closedTradableFormatter.getPriceAsString(tradable);
    }

    public String getPriceDeviationAsString() {
        return closedTradableFormatter.getPriceDeviationAsString(tradable);
    }

    public String getVolumeAsString(boolean appendCode) {
        return closedTradableFormatter.getVolumeAsString(tradable, appendCode);
    }

    public String getVolumeCurrencyAsString() {
        return closedTradableFormatter.getVolumeCurrencyAsString(tradable);
    }

    public String getTxFeeAsString() {
        return closedTradableFormatter.getTxFeeAsString(tradable);
    }

    public String getTradeFeeAsString(boolean appendCode) {
        return closedTradableFormatter.getTradeFeeAsString(tradable, appendCode);
    }

    public String getBuyerSecurityDepositAsString() {
        return closedTradableFormatter.getBuyerSecurityDepositAsString(tradable);
    }

    public String getSellerSecurityDepositAsString() {
        return closedTradableFormatter.getSellerSecurityDepositAsString(tradable);
    }

    public String getDirectionLabel() {
        Offer offer = tradable.getOffer();
        OfferDirection direction = closedTradableManager.wasMyOffer(offer)
                ? offer.getDirection()
                : offer.getMirroredDirection();
        String currencyCode = tradable.getOffer().getCurrencyCode();
        return DisplayUtils.getDirectionWithCode(direction, currencyCode);
    }

    public Date getDate() {
        return tradable.getDate();
    }

    public String getDateAsString() {
        return DisplayUtils.formatDateTime(tradable.getDate());
    }

    public String getMarketLabel() {
        return CurrencyUtil.getCurrencyPair(tradable.getOffer().getCurrencyCode());
    }

    public String getState() {
        return closedTradableFormatter.getStateAsString(tradable);
    }

    public int getNumPastTrades() {
        return closedTradableManager.getNumPastTrades(tradable);
    }

    @Override
    public boolean match(String filterString) {
        if (filterString.isEmpty()) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getDateAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getMarketLabel(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getPriceAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getPriceDeviationAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getVolumeAsString(true), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getAmountAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getTradeFeeAsString(true), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getTxFeeAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getBuyerSecurityDepositAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getSellerSecurityDepositAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getState(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getDirectionLabel(), filterString)) {
            return true;
        }
        if (FilteringUtils.match(getTradable().getOffer(), filterString)) {
            return true;
        }
        if (getTradable() instanceof BsqSwapTrade && FilteringUtils.match((BsqSwapTrade) getTradable(), filterString)) {
            return true;
        }
        return getTradable() instanceof Trade && FilteringUtils.match((Trade) getTradable(), filterString);
    }
}

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

package bisq.desktop.main.market.spread;

import bisq.desktop.common.model.ActivatableViewModel;
import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

class SpreadViewModel extends ActivatableViewModel {

    private final OfferBook offerBook;
    private final PriceFeedService priceFeedService;
    private final CoinFormatter formatter;
    private final ObservableList<OfferBookListItem> offerBookListItems;
    private final ListChangeListener<OfferBookListItem> listChangeListener;
    final ObservableList<SpreadItem> spreadItems = FXCollections.observableArrayList();
    final IntegerProperty maxPlacesForAmount = new SimpleIntegerProperty();
    @Setter
    @Getter
    private boolean includePaymentMethod;
    @Getter
    private boolean expandedView;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SpreadViewModel(OfferBook offerBook, PriceFeedService priceFeedService, @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter) {
        this.offerBook = offerBook;
        this.priceFeedService = priceFeedService;
        this.formatter = formatter;
        includePaymentMethod = false;
        offerBookListItems = offerBook.getOfferBookListItems();
        listChangeListener = c -> update(offerBookListItems);
    }

    public String getKeyColumnName() {
        return includePaymentMethod ? Res.get("shared.paymentMethod") : Res.get("shared.currency");
    }

    public void setExpandedView(boolean expandedView) {
        this.expandedView = expandedView;
        update(offerBookListItems);
    }

    @Override
    protected void activate() {
        offerBookListItems.addListener(listChangeListener);
        offerBook.fillOfferBookListItems();
        update(offerBookListItems);
    }

    @Override
    protected void deactivate() {
        offerBookListItems.removeListener(listChangeListener);
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private void update(ObservableList<OfferBookListItem> offerBookListItems) {
        Map<String, List<Offer>> offersByCurrencyMap = new HashMap<>();
        for (OfferBookListItem offerBookListItem : offerBookListItems) {
            Offer offer = offerBookListItem.getOffer();
            String key = offer.getCurrencyCode();
            if (includePaymentMethod) {
                key = offer.getPaymentMethod().getShortName();
                if (expandedView) {
                    key += ":" + offer.getCurrencyCode();
                }
            }
            if (!offersByCurrencyMap.containsKey(key))
                offersByCurrencyMap.put(key, new ArrayList<>());
            offersByCurrencyMap.get(key).add(offer);
        }
        spreadItems.clear();

        Coin totalAmount = null;

        for (String key : offersByCurrencyMap.keySet()) {
            List<Offer> offers = offersByCurrencyMap.get(key);
            boolean isFiatCurrency = (offers.size() > 0 && offers.get(0).getPaymentMethod().isFiat());

            List<Offer> uniqueOffers = offers.stream().filter(distinctByKey(Offer::getId)).collect(Collectors.toList());

            List<Offer> buyOffers = uniqueOffers
                    .stream()
                    .filter(e -> e.getDirection().equals(OfferDirection.BUY))
                    .sorted((o1, o2) -> {
                        long a = o1.getPrice() != null ? o1.getPrice().getValue() : 0;
                        long b = o2.getPrice() != null ? o2.getPrice().getValue() : 0;
                        if (a != b) {
                            if (isFiatCurrency) {
                                return a < b ? 1 : -1;
                            } else {
                                return a < b ? -1 : 1;
                            }
                        }
                        return 0;
                    })
                    .collect(Collectors.toList());

            List<Offer> sellOffers = uniqueOffers
                    .stream()
                    .filter(e -> e.getDirection().equals(OfferDirection.SELL))
                    .sorted((o1, o2) -> {
                        long a = o1.getPrice() != null ? o1.getPrice().getValue() : 0;
                        long b = o2.getPrice() != null ? o2.getPrice().getValue() : 0;
                        if (a != b) {
                            if (isFiatCurrency) {
                                return a > b ? 1 : -1;
                            } else {
                                return a > b ? -1 : 1;
                            }
                        }
                        return 0;
                    })
                    .collect(Collectors.toList());

            Price spread = null;
            String percentage = "";
            double percentageValue = 0;
            Price bestSellOfferPrice = sellOffers.isEmpty() ? null : sellOffers.get(0).getPrice();
            Price bestBuyOfferPrice = buyOffers.isEmpty() ? null : buyOffers.get(0).getPrice();
            if (bestBuyOfferPrice != null && bestSellOfferPrice != null &&
                    sellOffers.get(0).getCurrencyCode().equals(buyOffers.get(0).getCurrencyCode())) {
                MarketPrice marketPrice = priceFeedService.getMarketPrice(sellOffers.get(0).getCurrencyCode());

                // There have been some bug reports that an offer caused an overflow exception.
                // We never found out which offer it was. So add here a try/catch to get better info if it
                // happens again
                try {
                    if (isFiatCurrency)
                        spread = bestSellOfferPrice.subtract(bestBuyOfferPrice);
                    else
                        spread = bestBuyOfferPrice.subtract(bestSellOfferPrice);

                    // TODO maybe show extra columns with spread and use real amount diff
                    // not % based. e.g. diff between best buy and sell offer (of small amounts its a smaller gain)

                    if (spread != null && marketPrice != null && marketPrice.isPriceAvailable()) {
                        double marketPriceAsDouble = marketPrice.getPrice();
                        final double precision = isFiatCurrency ?
                                Math.pow(10, Fiat.SMALLEST_UNIT_EXPONENT) :
                                Math.pow(10, Altcoin.SMALLEST_UNIT_EXPONENT);

                        BigDecimal marketPriceAsBigDecimal = BigDecimal.valueOf(marketPriceAsDouble)
                                .multiply(BigDecimal.valueOf(precision));
                        // We multiply with 10000 because we use precision of 2 at % (100.00%)
                        percentageValue = BigDecimal.valueOf(spread.getValue())
                                .multiply(BigDecimal.valueOf(10000))
                                .divide(marketPriceAsBigDecimal, RoundingMode.HALF_UP)
                                .doubleValue() / 10000;
                        percentage = FormattingUtils.formatPercentagePrice(percentageValue);
                    }
                } catch (Throwable t) {
                    try {
                        // Don't translate msg. It is just for rare error cases and can be removed probably later if
                        // that error never gets reported again.
                        String msg = "An error occurred at the spread calculation.\n" +
                                "Error msg: " + t.toString() + "\n" +
                                "Details of offer data: \n" +
                                "bestSellOfferPrice: " + bestSellOfferPrice.getValue() + "\n" +
                                "bestBuyOfferPrice: " + bestBuyOfferPrice.getValue() + "\n" +
                                "sellOffer getCurrencyCode: " + sellOffers.get(0).getCurrencyCode() + "\n" +
                                "buyOffer getCurrencyCode: " + buyOffers.get(0).getCurrencyCode() + "\n\n" +
                                "Please copy and paste this data and send it to the developers so they can investigate the issue.";
                        new Popup().error(msg).show();
                        log.error(t.toString());
                        t.printStackTrace();
                    } catch (Throwable t2) {
                        log.error(t2.toString());
                        t2.printStackTrace();
                    }
                }
            }

            totalAmount = Coin.valueOf(offers.stream().mapToLong(offer -> offer.getAmount().getValue()).sum());
            spreadItems.add(new SpreadItem(key, buyOffers.size(), sellOffers.size(),
                    uniqueOffers.size(), spread, percentage, percentageValue, totalAmount));
        }

        maxPlacesForAmount.set(formatAmount(totalAmount, false).length());
    }

    public String getAmount(Coin amount) {
        return formatAmount(amount, true);
    }

    private String formatAmount(Coin amount, boolean decimalAligned) {
        return formatter.formatCoin(amount, GUIUtil.AMOUNT_DECIMALS, decimalAligned, maxPlacesForAmount.get());
    }
}

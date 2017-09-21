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

package io.bisq.gui.main.market.spread;

import com.google.inject.Inject;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.monetary.Altcoin;
import io.bisq.common.monetary.Price;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.provider.price.MarketPrice;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.gui.common.model.ActivatableViewModel;
import io.bisq.gui.main.offer.offerbook.OfferBook;
import io.bisq.gui.main.offer.offerbook.OfferBookListItem;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class SpreadViewModel extends ActivatableViewModel {

    private final OfferBook offerBook;
    private final PriceFeedService priceFeedService;
    private final BSFormatter formatter;
    private final ObservableList<OfferBookListItem> offerBookListItems;
    private final ListChangeListener<OfferBookListItem> listChangeListener;
    final ObservableList<SpreadItem> spreadItems = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SpreadViewModel(OfferBook offerBook, PriceFeedService priceFeedService, BSFormatter formatter) {
        this.offerBook = offerBook;
        this.priceFeedService = priceFeedService;
        this.formatter = formatter;

        offerBookListItems = offerBook.getOfferBookListItems();
        listChangeListener = c -> update(offerBookListItems);
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

    private void update(ObservableList<OfferBookListItem> offerBookListItems) {
        Map<String, List<Offer>> offersByCurrencyMap = new HashMap<>();
        for (OfferBookListItem offerBookListItem : offerBookListItems) {
            Offer offer = offerBookListItem.getOffer();
            String currencyCode = offer.getCurrencyCode();
            if (!offersByCurrencyMap.containsKey(currencyCode))
                offersByCurrencyMap.put(currencyCode, new ArrayList<>());
            offersByCurrencyMap.get(currencyCode).add(offer);
        }
        spreadItems.clear();
        for (String currencyCode : offersByCurrencyMap.keySet()) {
            List<Offer> offers = offersByCurrencyMap.get(currencyCode);
            final boolean isFiatCurrency = CurrencyUtil.isFiatCurrency(currencyCode);
            List<Offer> buyOffers = offers
                    .stream()
                    .filter(e -> e.getDirection().equals(OfferPayload.Direction.BUY))
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

            List<Offer> sellOffers = offers
                    .stream()
                    .filter(e -> e.getDirection().equals(OfferPayload.Direction.SELL))
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
            Price bestSellOfferPrice = sellOffers.isEmpty() ? null : sellOffers.get(0).getPrice();
            Price bestBuyOfferPrice = buyOffers.isEmpty() ? null : buyOffers.get(0).getPrice();
            if (bestBuyOfferPrice != null && bestSellOfferPrice != null) {
                MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);

                // There have been some bug reports that an offer caused an overflow exception.
                // We never found out which offer it was. So add here a try/catch to get better info if it
                // happens again
                try {
                    if (isFiatCurrency)
                        spread = bestSellOfferPrice.subtract(bestBuyOfferPrice);
                    else
                        spread = bestBuyOfferPrice.subtract(bestSellOfferPrice);

                    // TODO maybe show extra colums with spread and use real amount diff
                    // not % based. e.g. diff between best buy and sell offer (of small amounts its a smaller gain)

                    if (spread != null && marketPrice != null && marketPrice.isPriceAvailable()) {
                        double marketPriceAsDouble = marketPrice.getPrice();
                        final double precision = isFiatCurrency ?
                                Math.pow(10, Fiat.SMALLEST_UNIT_EXPONENT) :
                                Math.pow(10, Altcoin.SMALLEST_UNIT_EXPONENT);

                        BigDecimal marketPriceAsBigDecimal = BigDecimal.valueOf(marketPriceAsDouble)
                                .multiply(BigDecimal.valueOf(precision));
                        // We multiply with 10000 because we use precision of 2 at % (100.00%)
                        double result = BigDecimal.valueOf(spread.getValue())
                                .multiply(BigDecimal.valueOf(10000))
                                .divide(marketPriceAsBigDecimal, RoundingMode.HALF_UP)
                                .doubleValue() / 10000;
                        percentage = formatter.formatPercentagePrice(result);
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
                        new Popup<>().error(msg).show();
                        log.error(t.toString());
                        t.printStackTrace();
                    } catch (Throwable t2) {
                        log.error(t2.toString());
                        t2.printStackTrace();
                    }
                }
            }

            Coin totalAmount = Coin.valueOf(offers.stream().mapToLong(offer -> offer.getAmount().getValue()).sum());
            spreadItems.add(new SpreadItem(currencyCode, buyOffers.size(), sellOffers.size(),
                    offers.size(), spread, percentage, totalAmount));
        }
    }
}

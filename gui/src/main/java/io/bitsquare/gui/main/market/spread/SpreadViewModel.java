/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.market.spread;

import com.google.inject.Inject;
import io.bitsquare.btc.pricefeed.MarketPrice;
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.main.offer.offerbook.OfferBook;
import io.bitsquare.gui.main.offer.offerbook.OfferBookListItem;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.trade.offer.Offer;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class SpreadViewModel extends ActivatableViewModel {

    private final OfferBook offerBook;
    private PriceFeedService priceFeedService;
    private BSFormatter formatter;
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
            List<Offer> buyOffers = offers
                    .stream()
                    .filter(e -> e.getDirection().equals(Offer.Direction.BUY))
                    .sorted((o1, o2) -> {
                        long a = o1.getPrice() != null ? o1.getPrice().value : 0;
                        long b = o2.getPrice() != null ? o2.getPrice().value : 0;
                        if (a != b)
                            return a < b ? 1 : -1;
                        return 0;
                    })
                    .collect(Collectors.toList());

            List<Offer> sellOffers = offers
                    .stream()
                    .filter(e -> e.getDirection().equals(Offer.Direction.SELL))
                    .sorted((o1, o2) -> {
                        long a = o1.getPrice() != null ? o1.getPrice().value : 0;
                        long b = o2.getPrice() != null ? o2.getPrice().value : 0;
                        if (a != b)
                            return a > b ? 1 : -1;
                        return 0;
                    })
                    .collect(Collectors.toList());

            Fiat spread = null;
            String percentage = "";
            Fiat bestSellOfferPrice = sellOffers.isEmpty() ? null : sellOffers.get(0).getPrice();
            Fiat bestBuyOfferPrice = buyOffers.isEmpty() ? null : buyOffers.get(0).getPrice();
            if (bestBuyOfferPrice != null && bestSellOfferPrice != null) {
                MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
                spread = bestSellOfferPrice.subtract(bestBuyOfferPrice);
                if (spread != null && marketPrice != null) {
                    double marketPriceAsDouble = marketPrice.getPrice(PriceFeedService.Type.LAST);
                    if (CurrencyUtil.isFiatCurrency(currencyCode)) {
                        double result = ((double) spread.value / 10000D) / marketPriceAsDouble;
                        percentage = " (" + formatter.formatPercentagePrice(result) + ")";
                    } else {
                        final double spreadAsDouble = spread.value != 0 ? 10000D / spread.value : 0;
                        double result = marketPriceAsDouble / spreadAsDouble;
                        percentage = " (" + formatter.formatPercentagePrice(result) + ")";
                    }
                }
            }

            Coin totalAmount = Coin.valueOf(offers.stream().mapToLong(offer -> offer.getAmount().getValue()).sum());
            spreadItems.add(new SpreadItem(currencyCode, buyOffers.size(), sellOffers.size(), offers.size(), spread, percentage, totalAmount));
        }
    }
}

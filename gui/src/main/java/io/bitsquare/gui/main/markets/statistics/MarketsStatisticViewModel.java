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

package io.bitsquare.gui.main.markets.statistics;

import com.google.inject.Inject;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.main.offer.offerbook.OfferBook;
import io.bitsquare.gui.main.offer.offerbook.OfferBookListItem;
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

class MarketsStatisticViewModel extends ActivatableViewModel {

    private final OfferBook offerBook;
    private final ObservableList<OfferBookListItem> offerBookListItems;
    private final ListChangeListener<OfferBookListItem> listChangeListener;
    final ObservableList<MarketStatisticItem> marketStatisticItems = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MarketsStatisticViewModel(OfferBook offerBook) {
        this.offerBook = offerBook;

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
        marketStatisticItems.clear();
        long totalAmount = 0;
        for (String currencyCode : offersByCurrencyMap.keySet()) {
            List<Offer> offers = offersByCurrencyMap.get(currencyCode);
            List<Offer> buyOffers = offers
                    .stream()
                    .filter(e -> e.getDirection().equals(Offer.Direction.BUY))
                    .sorted((o1, o2) -> {
                        long a = o1.getPrice().value;
                        long b = o2.getPrice().value;
                        if (a != b)
                            return a < b ? 1 : -1;
                        return 0;
                    })
                    .collect(Collectors.toList());
            Fiat bestBuyOfferPrice = buyOffers.isEmpty() ? null : buyOffers.get(0).getPrice();
            List<Offer> sellOffers = offers
                    .stream()
                    .filter(e -> e.getDirection().equals(Offer.Direction.SELL))
                    .sorted((o1, o2) -> {
                        long a = o1.getPrice().value;
                        long b = o2.getPrice().value;
                        if (a != b)
                            return a > b ? 1 : -1;
                        return 0;
                    })
                    .collect(Collectors.toList());
            Fiat bestSellOfferPrice = sellOffers.isEmpty() ? null : sellOffers.get(0).getPrice();

            Fiat spread = null;
            if (bestBuyOfferPrice != null && bestSellOfferPrice != null) {
                spread = bestSellOfferPrice.subtract(bestBuyOfferPrice);
            }

            for (Offer offer : offers) {
                totalAmount += offer.getAmount().getValue();
            }
            marketStatisticItems.add(new MarketStatisticItem(currencyCode, offers.size(), spread, Coin.valueOf(totalAmount)));
        }
    }
}

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

package io.bitsquare.gui.main.markets.charts;

import com.google.common.math.LongMath;
import com.google.inject.Inject;
import io.bitsquare.btc.pricefeed.PriceFeed;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.main.offer.offerbook.OfferBook;
import io.bitsquare.gui.main.offer.offerbook.OfferBookListItem;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class MarketsChartsViewModel extends ActivatableViewModel {

    private final OfferBook offerBook;
    private final Preferences preferences;
    private final PriceFeed priceFeed;

    final ObjectProperty<TradeCurrency> tradeCurrency = new SimpleObjectProperty<>(CurrencyUtil.getDefaultTradeCurrency());
    private final List<XYChart.Data> buyData = new ArrayList();
    private final List<XYChart.Data> sellData = new ArrayList();
    private final ObservableList<OfferBookListItem> offerBookListItems;
    private final ListChangeListener<OfferBookListItem> listChangeListener;
    private final ObservableList<Offer> buyOfferList = FXCollections.observableArrayList();
    private final ObservableList<Offer> sellOfferList = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MarketsChartsViewModel(OfferBook offerBook, Preferences preferences, PriceFeed priceFeed) {
        this.offerBook = offerBook;
        this.preferences = preferences;
        this.priceFeed = priceFeed;

        offerBookListItems = offerBook.getOfferBookListItems();
        listChangeListener = c -> updateChartData(offerBookListItems);
    }

    @Override
    protected void activate() {
        priceFeed.setType(PriceFeed.Type.LAST);
        offerBookListItems.addListener(listChangeListener);
        offerBook.fillOfferBookListItems();
        updateChartData(offerBookListItems);
        priceFeed.setCurrencyCode(tradeCurrency.get().getCode());
    }

    @Override
    protected void deactivate() {
        offerBookListItems.removeListener(listChangeListener);
    }

    private void updateChartData(ObservableList<OfferBookListItem> offerBookListItems) {
        List<Offer> offerList = offerBookListItems.stream()
                .map(OfferBookListItem::getOffer)
                .collect(Collectors.toList());

        buyOfferList.clear();
        buyOfferList.addAll(offerList
                .stream()
                .filter(e -> e.getCurrencyCode().equals(tradeCurrency.get().getCode())
                        && e.getDirection().equals(Offer.Direction.BUY))
                .sorted((o1, o2) -> {
                    long a = o1.getPrice().value;
                    long b = o2.getPrice().value;
                    if (a != b)
                        return a < b ? 1 : -1;
                    return 0;
                })
                .collect(Collectors.toList()));
        iterateBuyOffers(buyOfferList, Offer.Direction.BUY, buyData);

        sellOfferList.clear();
        sellOfferList.addAll(offerList
                .stream()
                .filter(e -> e.getCurrencyCode().equals(tradeCurrency.get().getCode())
                        && e.getDirection().equals(Offer.Direction.SELL))
                .sorted((o1, o2) -> {
                    long a = o1.getPrice().value;
                    long b = o2.getPrice().value;
                    if (a != b)
                        return a > b ? 1 : -1;
                    return 0;
                })
                .collect(Collectors.toList()));
        iterateBuyOffers(sellOfferList, Offer.Direction.SELL, sellData);
    }

    private void iterateBuyOffers(List<Offer> sortedList, Offer.Direction direction, List<XYChart.Data> data) {
        data.clear();
        double accumulatedAmount = 0;
        for (Offer offer : sortedList) {
            double price = (double) offer.getPrice().value / LongMath.pow(10, offer.getPrice().smallestUnitExponent());
            double amount = (double) offer.getAmount().value / LongMath.pow(10, offer.getAmount().smallestUnitExponent());
            accumulatedAmount += amount;
            if (direction.equals(Offer.Direction.BUY))
                data.add(0, new XYChart.Data(price, accumulatedAmount));
            else
                data.add(new XYChart.Data(price, accumulatedAmount));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSetTradeCurrency(TradeCurrency tradeCurrency) {
        this.tradeCurrency.set(tradeCurrency);
        updateChartData(offerBookListItems);
        priceFeed.setCurrencyCode(tradeCurrency.getCode());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<XYChart.Data> getBuyData() {
        return buyData;
    }

    public List<XYChart.Data> getSellData() {
        return sellData;
    }

    public String getCurrencyCode() {
        return tradeCurrency.get().getCode();
    }

    public ObservableList<OfferBookListItem> getOfferBookListItems() {
        return offerBookListItems;
    }

    public ObservableList<Offer> getBuyOfferList() {
        return buyOfferList;
    }

    public ObservableList<Offer> getSellOfferList() {
        return sellOfferList;
    }

    public ObservableList<TradeCurrency> getTradeCurrencies() {
        return preferences.getTradeCurrenciesAsObservable();
    }

    public TradeCurrency getTradeCurrency() {
        return tradeCurrency.get();
    }
}

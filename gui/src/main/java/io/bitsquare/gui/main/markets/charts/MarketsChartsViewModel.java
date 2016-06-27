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
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class MarketsChartsViewModel extends ActivatableViewModel {
    private static final Logger log = LoggerFactory.getLogger(MarketsChartsViewModel.class);

    final static String EDIT_FLAG = "EDIT_FLAG";

    private final OfferBook offerBook;
    private final Preferences preferences;
    final PriceFeed priceFeed;

    final ObjectProperty<TradeCurrency> tradeCurrency = new SimpleObjectProperty<>();
    private final List<XYChart.Data> buyData = new ArrayList<>();
    private final List<XYChart.Data> sellData = new ArrayList<>();
    private final ObservableList<OfferBookListItem> offerBookListItems;
    private final ListChangeListener<OfferBookListItem> listChangeListener;
    private final ObservableList<Offer> top3BuyOfferList = FXCollections.observableArrayList();
    private final ObservableList<Offer> top3SellOfferList = FXCollections.observableArrayList();
    private final ChangeListener<Number> currenciesUpdatedListener;
    final IntegerProperty updateChartDataFlag = new SimpleIntegerProperty(0);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MarketsChartsViewModel(OfferBook offerBook, Preferences preferences, PriceFeed priceFeed) {
        this.offerBook = offerBook;
        this.preferences = preferences;
        this.priceFeed = priceFeed;

        Optional<TradeCurrency> tradeCurrencyOptional = CurrencyUtil.getTradeCurrency(preferences.getMarketScreenCurrencyCode());
        if (tradeCurrencyOptional.isPresent())
            tradeCurrency.set(tradeCurrencyOptional.get());
        else {
            tradeCurrency.set(CurrencyUtil.getDefaultTradeCurrency());
        }

        offerBookListItems = offerBook.getOfferBookListItems();
        listChangeListener = c -> {
            c.next();
            if (c.wasAdded() || c.wasRemoved()) {
                ArrayList<OfferBookListItem> list = new ArrayList<>(c.getRemoved());
                list.addAll(c.getAddedSubList());
                if (list.stream()
                        .map(OfferBookListItem::getOffer)
                        .filter(e -> e.getCurrencyCode().equals(tradeCurrency.get().getCode()))
                        .findAny()
                        .isPresent())
                    updateChartData();
            }
        };

        currenciesUpdatedListener = new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (!isAnyPricePresent()) {
                    offerBook.fillOfferBookListItems();
                    updateChartData();
                    priceFeed.currenciesUpdateFlagProperty().removeListener(currenciesUpdatedListener);
                }
            }
        };
    }

    @Override
    protected void activate() {
        priceFeed.setType(PriceFeed.Type.LAST);
        offerBookListItems.addListener(listChangeListener);

        offerBook.fillOfferBookListItems();
        updateChartData();

        if (isAnyPricePresent())
            priceFeed.currenciesUpdateFlagProperty().addListener(currenciesUpdatedListener);

        if (!preferences.getUseStickyMarketPrice())
            priceFeed.setCurrencyCode(tradeCurrency.get().getCode());
    }

    @Override
    protected void deactivate() {
        offerBookListItems.removeListener(listChangeListener);
    }

    private boolean isAnyPricePresent() {
        return offerBookListItems.stream().filter(item -> item.getOffer().getPrice() == null).findAny().isPresent();
    }

    private void updateChartData() {
        updateChartDataFlag.set(updateChartDataFlag.get() + 1);
        List<Offer> allBuyOffers = offerBookListItems.stream()
                .map(OfferBookListItem::getOffer)
                .filter(e -> e.getCurrencyCode().equals(tradeCurrency.get().getCode())
                        && e.getDirection().equals(Offer.Direction.BUY))
                .sorted((o1, o2) -> {
                    long a = o1.getPrice() != null ? o1.getPrice().value : 0;
                    long b = o2.getPrice() != null ? o2.getPrice().value : 0;
                    if (a != b)
                        return a < b ? 1 : -1;
                    return 0;
                })
                .collect(Collectors.toList());
        top3BuyOfferList.setAll(allBuyOffers.subList(0, Math.min(3, allBuyOffers.size())));
        buildChartDataItems(allBuyOffers, Offer.Direction.BUY, buyData);

        List<Offer> allSellOffers = offerBookListItems.stream()
                .map(OfferBookListItem::getOffer)
                .filter(e -> e.getCurrencyCode().equals(tradeCurrency.get().getCode())
                        && e.getDirection().equals(Offer.Direction.SELL))
                .sorted((o1, o2) -> {
                    long a = o1.getPrice() != null ? o1.getPrice().value : 0;
                    long b = o2.getPrice() != null ? o2.getPrice().value : 0;
                    if (a != b)
                        return a > b ? 1 : -1;
                    return 0;
                })
                .collect(Collectors.toList());
        top3SellOfferList.setAll(allSellOffers.subList(0, Math.min(3, allSellOffers.size())));
        buildChartDataItems(allSellOffers, Offer.Direction.SELL, sellData);
    }

    private void buildChartDataItems(List<Offer> sortedList, Offer.Direction direction, List<XYChart.Data> data) {
        data.clear();
        double accumulatedAmount = 0;
        for (Offer offer : sortedList) {
            Fiat priceAsFiat = offer.getPrice();
            if (priceAsFiat != null) {
                double price = (double) priceAsFiat.value / LongMath.pow(10, priceAsFiat.smallestUnitExponent());
                double amount = (double) offer.getAmount().value / LongMath.pow(10, offer.getAmount().smallestUnitExponent());
                accumulatedAmount += amount;
                if (direction.equals(Offer.Direction.BUY))
                    data.add(0, new XYChart.Data(price, accumulatedAmount));
                else
                    data.add(new XYChart.Data(price, accumulatedAmount));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSetTradeCurrency(TradeCurrency tradeCurrency) {
        this.tradeCurrency.set(tradeCurrency);
        updateChartData();

        if (!preferences.getUseStickyMarketPrice())
            priceFeed.setCurrencyCode(tradeCurrency.getCode());

        preferences.setMarketScreenCurrencyCode(tradeCurrency.getCode());
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

    public ObservableList<Offer> getTop3BuyOfferList() {
        return top3BuyOfferList;
    }

    public ObservableList<Offer> getTop3SellOfferList() {
        return top3SellOfferList;
    }

    public ObservableList<TradeCurrency> getTradeCurrencies() {
        return preferences.getTradeCurrenciesAsObservable();
    }

    public TradeCurrency getTradeCurrency() {
        return tradeCurrency.get();
    }
}

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

package io.bitsquare.gui.main.market.offerbook;

import com.google.common.math.LongMath;
import com.google.inject.Inject;
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ActivatableViewModel;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.offer.offerbook.OfferBook;
import io.bitsquare.gui.main.offer.offerbook.OfferBookListItem;
import io.bitsquare.gui.main.settings.SettingsView;
import io.bitsquare.gui.main.settings.preferences.PreferencesView;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.locale.CryptoCurrency;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import javafx.beans.property.ObjectProperty;
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

class OfferBookChartViewModel extends ActivatableViewModel {
    private static final Logger log = LoggerFactory.getLogger(OfferBookChartViewModel.class);

    private static final int TAB_INDEX = 0;

    private final OfferBook offerBook;
    final Preferences preferences;
    final PriceFeedService priceFeedService;
    private Navigation navigation;

    final ObjectProperty<TradeCurrency> selectedTradeCurrencyProperty = new SimpleObjectProperty<>();
    private final List<XYChart.Data> buyData = new ArrayList<>();
    private final List<XYChart.Data> sellData = new ArrayList<>();
    private final ObservableList<OfferBookListItem> offerBookListItems;
    private final ListChangeListener<OfferBookListItem> listChangeListener;
    private final ObservableList<Offer> topBuyOfferList = FXCollections.observableArrayList();
    private final ObservableList<Offer> topSellOfferList = FXCollections.observableArrayList();
    private final ChangeListener<Number> currenciesUpdatedListener;
    private int selectedTabIndex;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookChartViewModel(OfferBook offerBook, Preferences preferences, PriceFeedService priceFeedService, Navigation navigation) {
        this.offerBook = offerBook;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.navigation = navigation;

        Optional<TradeCurrency> tradeCurrencyOptional = CurrencyUtil.getTradeCurrency(preferences.getOfferBookChartScreenCurrencyCode());
        if (tradeCurrencyOptional.isPresent())
            selectedTradeCurrencyProperty.set(tradeCurrencyOptional.get());
        else {
            selectedTradeCurrencyProperty.set(CurrencyUtil.getDefaultTradeCurrency());
        }

        offerBookListItems = offerBook.getOfferBookListItems();
        listChangeListener = c -> {
            c.next();
            if (c.wasAdded() || c.wasRemoved()) {
                ArrayList<OfferBookListItem> list = new ArrayList<>(c.getRemoved());
                list.addAll(c.getAddedSubList());
                if (list.stream()
                        .map(OfferBookListItem::getOffer)
                        .filter(e -> e.getCurrencyCode().equals(selectedTradeCurrencyProperty.get().getCode()))
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
                    priceFeedService.currenciesUpdateFlagProperty().removeListener(currenciesUpdatedListener);
                }
            }
        };
    }

    @Override
    protected void activate() {
        priceFeedService.setType(PriceFeedService.Type.LAST);
        offerBookListItems.addListener(listChangeListener);

        offerBook.fillOfferBookListItems();
        updateChartData();

        if (isAnyPricePresent())
            priceFeedService.currenciesUpdateFlagProperty().addListener(currenciesUpdatedListener);

        syncPriceFeedCurrency();
    }

    @Override
    protected void deactivate() {
        offerBookListItems.removeListener(listChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onSetTradeCurrency(TradeCurrency tradeCurrency) {
        if (tradeCurrency != null) {
            final String code = tradeCurrency.getCode();

            if (isEditEntry(code)) {
                navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class);
            } else {
                selectedTradeCurrencyProperty.set(tradeCurrency);
                preferences.setOfferBookChartScreenCurrencyCode(code);

                updateChartData();

                if (!preferences.getUseStickyMarketPrice())
                    priceFeedService.setCurrencyCode(code);
            }
        }
    }

    void setSelectedTabIndex(int selectedTabIndex) {
        this.selectedTabIndex = selectedTabIndex;
        syncPriceFeedCurrency();
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
        return selectedTradeCurrencyProperty.get().getCode();
    }

    public ObservableList<OfferBookListItem> getOfferBookListItems() {
        return offerBookListItems;
    }

    public ObservableList<Offer> getTopBuyOfferList() {
        return topBuyOfferList;
    }

    public ObservableList<Offer> getTopSellOfferList() {
        return topSellOfferList;
    }

    public ObservableList<TradeCurrency> getTradeCurrencies() {
        final ObservableList<TradeCurrency> list = FXCollections.observableArrayList(preferences.getTradeCurrenciesAsObservable());
        list.add(new CryptoCurrency(GUIUtil.EDIT_FLAG, GUIUtil.EDIT_FLAG));
        return list;
    }

    public TradeCurrency getSelectedTradeCurrencyProperty() {
        return selectedTradeCurrencyProperty.get();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void syncPriceFeedCurrency() {
        if (!preferences.getUseStickyMarketPrice() && selectedTabIndex == TAB_INDEX)
            priceFeedService.setCurrencyCode(selectedTradeCurrencyProperty.get().getCode());
    }

    private boolean isAnyPricePresent() {
        return offerBookListItems.stream().filter(item -> item.getOffer().getPrice() == null).findAny().isPresent();
    }

    private void updateChartData() {
        List<Offer> allBuyOffers = offerBookListItems.stream()
                .map(OfferBookListItem::getOffer)
                .filter(e -> e.getCurrencyCode().equals(selectedTradeCurrencyProperty.get().getCode())
                        && e.getDirection().equals(Offer.Direction.BUY))
                .sorted((o1, o2) -> {
                    long a = o1.getPrice() != null ? o1.getPrice().value : 0;
                    long b = o2.getPrice() != null ? o2.getPrice().value : 0;
                    if (a != b)
                        return a < b ? 1 : -1;
                    return 0;
                })
                .collect(Collectors.toList());
        topBuyOfferList.setAll(allBuyOffers.subList(0, Math.min(100, allBuyOffers.size())));
        buildChartDataItems(allBuyOffers, Offer.Direction.BUY, buyData);

        List<Offer> allSellOffers = offerBookListItems.stream()
                .map(OfferBookListItem::getOffer)
                .filter(e -> e.getCurrencyCode().equals(selectedTradeCurrencyProperty.get().getCode())
                        && e.getDirection().equals(Offer.Direction.SELL))
                .sorted((o1, o2) -> {
                    long a = o1.getPrice() != null ? o1.getPrice().value : 0;
                    long b = o2.getPrice() != null ? o2.getPrice().value : 0;
                    if (a != b)
                        return a > b ? 1 : -1;
                    return 0;
                })
                .collect(Collectors.toList());
        topSellOfferList.setAll(allSellOffers.subList(0, Math.min(100, allSellOffers.size())));
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

    private boolean isEditEntry(String id) {
        return id.equals(GUIUtil.EDIT_FLAG);
    }
}

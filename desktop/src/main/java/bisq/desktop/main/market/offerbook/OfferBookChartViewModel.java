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

package bisq.desktop.main.market.offerbook;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.ActivatableViewModel;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.main.settings.SettingsView;
import bisq.desktop.main.settings.preferences.PreferencesView;
import bisq.desktop.util.CurrencyList;
import bisq.desktop.util.CurrencyListItem;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;

import com.google.inject.Inject;

import com.google.common.math.LongMath;

import javafx.scene.chart.XYChart;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

class OfferBookChartViewModel extends ActivatableViewModel {
    private static final int TAB_INDEX = 0;

    private final OfferBook offerBook;
    final Preferences preferences;
    final PriceFeedService priceFeedService;
    final AccountAgeWitnessService accountAgeWitnessService;
    private final Navigation navigation;

    final ObjectProperty<TradeCurrency> selectedTradeCurrencyProperty = new SimpleObjectProperty<>();
    private final List<XYChart.Data<Number, Number>> buyData = new ArrayList<>();
    private final List<XYChart.Data<Number, Number>> sellData = new ArrayList<>();
    private final ObservableList<OfferBookListItem> offerBookListItems;
    private final ListChangeListener<OfferBookListItem> offerBookListItemsListener;
    final CurrencyList currencyListItems;
    private final ObservableList<OfferListItem> topBuyOfferList = FXCollections.observableArrayList();
    private final ObservableList<OfferListItem> topSellOfferList = FXCollections.observableArrayList();
    private final ChangeListener<Number> currenciesUpdatedListener;
    private int selectedTabIndex;
    public final IntegerProperty maxPlacesForBuyPrice = new SimpleIntegerProperty();
    public final IntegerProperty maxPlacesForBuyVolume = new SimpleIntegerProperty();
    public final IntegerProperty maxPlacesForSellPrice = new SimpleIntegerProperty();
    public final IntegerProperty maxPlacesForSellVolume = new SimpleIntegerProperty();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OfferBookChartViewModel(OfferBook offerBook, Preferences preferences, PriceFeedService priceFeedService,
                            AccountAgeWitnessService accountAgeWitnessService, Navigation navigation) {
        this.offerBook = offerBook;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.navigation = navigation;
        this.accountAgeWitnessService = accountAgeWitnessService;

        String code = preferences.getOfferBookChartScreenCurrencyCode();
        if (code != null) {
            Optional<TradeCurrency> tradeCurrencyOptional = CurrencyUtil.getTradeCurrency(code);
            if (tradeCurrencyOptional.isPresent())
                selectedTradeCurrencyProperty.set(tradeCurrencyOptional.get());
            else {
                selectedTradeCurrencyProperty.set(GlobalSettings.getDefaultTradeCurrency());
            }
        } else {
            selectedTradeCurrencyProperty.set(GlobalSettings.getDefaultTradeCurrency());
        }

        offerBookListItems = offerBook.getOfferBookListItems();
        offerBookListItemsListener = c -> {
            c.next();
            if (c.wasAdded() || c.wasRemoved()) {
                ArrayList<OfferBookListItem> list = new ArrayList<>(c.getRemoved());
                list.addAll(c.getAddedSubList());
                if (list.stream()
                        .map(OfferBookListItem::getOffer)
                        .anyMatch(e -> e.getOfferPayload().getCurrencyCode().equals(selectedTradeCurrencyProperty.get().getCode())))
                    updateChartData();
            }

            fillTradeCurrencies();
        };

        currenciesUpdatedListener = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (!isAnyPricePresent()) {
                    offerBook.fillOfferBookListItems();
                    updateChartData();
                    priceFeedService.updateCounterProperty().removeListener(currenciesUpdatedListener);
                }
            }
        };

        this.currencyListItems = new CurrencyList(preferences);
    }

    private void fillTradeCurrencies() {
        // Don't use a set as we need all entries
        List<TradeCurrency> tradeCurrencyList = offerBookListItems.stream()
                .map(e -> {
                    String currencyCode = e.getOffer().getCurrencyCode();
                    Optional<TradeCurrency> tradeCurrencyOptional = CurrencyUtil.getTradeCurrency(currencyCode);
                    return tradeCurrencyOptional.orElse(null);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        currencyListItems.updateWithCurrencies(tradeCurrencyList, null);
    }

    @Override
    protected void activate() {
        offerBookListItems.addListener(offerBookListItemsListener);

        offerBook.fillOfferBookListItems();
        fillTradeCurrencies();
        updateChartData();

        if (isAnyPricePresent())
            priceFeedService.updateCounterProperty().addListener(currenciesUpdatedListener);

        syncPriceFeedCurrency();
    }

    @Override
    protected void deactivate() {
        offerBookListItems.removeListener(offerBookListItemsListener);
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

    public List<XYChart.Data<Number, Number>> getBuyData() {
        return buyData;
    }

    public List<XYChart.Data<Number, Number>> getSellData() {
        return sellData;
    }

    public String getCurrencyCode() {
        return selectedTradeCurrencyProperty.get().getCode();
    }

    public ObservableList<OfferBookListItem> getOfferBookListItems() {
        return offerBookListItems;
    }

    public ObservableList<OfferListItem> getTopBuyOfferList() {
        return topBuyOfferList;
    }

    public ObservableList<OfferListItem> getTopSellOfferList() {
        return topSellOfferList;
    }

    public ObservableList<CurrencyListItem> getCurrencyListItems() {
        return currencyListItems.getObservableList();
    }

    public Optional<CurrencyListItem> getSelectedCurrencyListItem() {
        return currencyListItems.getObservableList().stream().filter(e -> e.tradeCurrency.equals(selectedTradeCurrencyProperty.get())).findAny();
    }

    public int getMaxNumberOfPriceZeroDecimalsToColorize(Offer offer) {
        return CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()) ? GUIUtil.FIAT_DECIMALS_WITH_ZEROS : GUIUtil.ALTCOINS_DECIMALS_WITH_ZEROS;
    }

    public int getZeroDecimalsForPrice(Offer offer) {
        return CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()) ? GUIUtil.FIAT_PRICE_DECIMALS_WITH_ZEROS : GUIUtil.ALTCOINS_DECIMALS_WITH_ZEROS;
    }

    public String getPrice(Offer offer) {
        return formatPrice(offer, true);
    }

    private String formatPrice(Offer offer, boolean decimalAligned) {
        return DisplayUtils.formatPrice(offer.getPrice(), decimalAligned, offer.isBuyOffer() ? maxPlacesForBuyPrice.get() : maxPlacesForSellPrice.get());
    }

    public String getVolume(Offer offer) {
        return formatVolume(offer, true);
    }

    private String formatVolume(Offer offer, boolean decimalAligned) {
        return DisplayUtils.formatVolume(offer, decimalAligned, offer.isBuyOffer() ? maxPlacesForBuyVolume.get() : maxPlacesForSellVolume.get(), false);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void syncPriceFeedCurrency() {
        if (selectedTabIndex == TAB_INDEX)
            priceFeedService.setCurrencyCode(getCurrencyCode());
    }

    private boolean isAnyPricePresent() {
        return offerBookListItems.stream().anyMatch(item -> item.getOffer().getPrice() == null);
    }

    private void updateChartData() {
        List<Offer> allBuyOffers = offerBookListItems.stream()
                .map(OfferBookListItem::getOffer)
                .filter(e -> e.getCurrencyCode().equals(selectedTradeCurrencyProperty.get().getCode())
                        && e.getDirection().equals(OfferPayload.Direction.BUY))
                .sorted((o1, o2) -> {
                    long a = o1.getPrice() != null ? o1.getPrice().getValue() : 0;
                    long b = o2.getPrice() != null ? o2.getPrice().getValue() : 0;
                    if (a != b) {
                        if (CurrencyUtil.isCryptoCurrency(o1.getCurrencyCode()))
                            return a > b ? 1 : -1;
                        else
                            return a < b ? 1 : -1;
                    } else {
                        return 0;
                    }
                })
                .collect(Collectors.toList());

        final Optional<Offer> highestBuyPriceOffer = allBuyOffers.stream()
                .filter(o -> o.getPrice() != null)
                .max(Comparator.comparingLong(o -> o.getPrice().getValue()));

        if (highestBuyPriceOffer.isPresent()) {
            final Offer offer = highestBuyPriceOffer.get();
            maxPlacesForBuyPrice.set(formatPrice(offer, false).length());
        } else {
            log.debug("highestBuyPriceOffer not present");
        }

        final Optional<Offer> highestBuyVolumeOffer = allBuyOffers.stream()
                .filter(o -> o.getVolume() != null)
                .max(Comparator.comparingLong(o -> o.getVolume().getValue()));

        if (highestBuyVolumeOffer.isPresent()) {
            final Offer offer = highestBuyVolumeOffer.get();
            maxPlacesForBuyVolume.set(formatVolume(offer, false).length());
        }

        buildChartAndTableEntries(allBuyOffers, OfferPayload.Direction.BUY, buyData, topBuyOfferList);

        List<Offer> allSellOffers = offerBookListItems.stream()
                .map(OfferBookListItem::getOffer)
                .filter(e -> e.getCurrencyCode().equals(selectedTradeCurrencyProperty.get().getCode())
                        && e.getDirection().equals(OfferPayload.Direction.SELL))
                .sorted((o1, o2) -> {
                    long a = o1.getPrice() != null ? o1.getPrice().getValue() : 0;
                    long b = o2.getPrice() != null ? o2.getPrice().getValue() : 0;
                    if (a != b) {
                        if (CurrencyUtil.isCryptoCurrency(o1.getCurrencyCode()))
                            return a < b ? 1 : -1;
                        else
                            return a > b ? 1 : -1;
                    } else {
                        return 0;
                    }
                })
                .collect(Collectors.toList());

        final Optional<Offer> highestSellPriceOffer = allSellOffers.stream()
                .filter(o -> o.getPrice() != null)
                .max(Comparator.comparingLong(o -> o.getPrice().getValue()));

        if (highestSellPriceOffer.isPresent()) {
            final Offer offer = highestSellPriceOffer.get();
            maxPlacesForSellPrice.set(formatPrice(offer, false).length());
        }

        final Optional<Offer> highestSellVolumeOffer = allSellOffers.stream()
                .filter(o -> o.getVolume() != null)
                .max(Comparator.comparingLong(o -> o.getVolume().getValue()));

        if (highestSellVolumeOffer.isPresent()) {
            final Offer offer = highestSellVolumeOffer.get();
            maxPlacesForSellVolume.set(formatVolume(offer, false).length());
        }

        buildChartAndTableEntries(allSellOffers, OfferPayload.Direction.SELL, sellData, topSellOfferList);
    }

    private void buildChartAndTableEntries(List<Offer> sortedList,
                                           OfferPayload.Direction direction,
                                           List<XYChart.Data<Number, Number>> data,
                                           ObservableList<OfferListItem> offerTableList) {
        data.clear();
        double accumulatedAmount = 0;
        List<OfferListItem> offerTableListTemp = new ArrayList<>();
        for (Offer offer : sortedList) {
            Price price = offer.getPrice();
            if (price != null) {
                double amount = (double) offer.getAmount().value / LongMath.pow(10, offer.getAmount().smallestUnitExponent());
                accumulatedAmount += amount;
                offerTableListTemp.add(new OfferListItem(offer, accumulatedAmount));

                double priceAsDouble = (double) price.getValue() / LongMath.pow(10, price.smallestUnitExponent());
                if (CurrencyUtil.isCryptoCurrency(getCurrencyCode())) {
                    if (direction.equals(OfferPayload.Direction.SELL))
                        data.add(0, new XYChart.Data<>(priceAsDouble, accumulatedAmount));
                    else
                        data.add(new XYChart.Data<>(priceAsDouble, accumulatedAmount));
                } else {
                    if (direction.equals(OfferPayload.Direction.BUY))
                        data.add(0, new XYChart.Data<>(priceAsDouble, accumulatedAmount));
                    else
                        data.add(new XYChart.Data<>(priceAsDouble, accumulatedAmount));
                }
            }
        }
        offerTableList.setAll(offerTableListTemp);
    }

    private boolean isEditEntry(String id) {
        return id.equals(GUIUtil.EDIT_FLAG);
    }
}

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

package bisq.desktop.main.presentation;

import bisq.desktop.components.ExplorerAddressTextField;
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.main.shared.PriceFeedComboBoxItem;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;

import bisq.common.UserThread;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.Getter;

@Singleton
public class MarketPricePresentation {
    private final Preferences preferences;
    private final PriceFeedService priceFeedService;
    @Getter
    private final ObservableList<PriceFeedComboBoxItem> priceFeedComboBoxItems = FXCollections.observableArrayList();
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<String> marketPriceBinding;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Subscription priceFeedAllLoadedSubscription;

    private final StringProperty marketPriceCurrencyCode = new SimpleStringProperty("");
    private final ObjectProperty<PriceFeedComboBoxItem> selectedPriceFeedComboBoxItemProperty = new SimpleObjectProperty<>();
    private final BooleanProperty isFiatCurrencyPriceFeedSelected = new SimpleBooleanProperty(true);
    private final BooleanProperty isCryptoCurrencyPriceFeedSelected = new SimpleBooleanProperty(false);
    private final BooleanProperty isExternallyProvidedPrice = new SimpleBooleanProperty(true);
    private final BooleanProperty isPriceAvailable = new SimpleBooleanProperty(false);
    private final IntegerProperty marketPriceUpdated = new SimpleIntegerProperty(0);
    private final StringProperty marketPrice = new SimpleStringProperty(Res.get("shared.na"));


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MarketPricePresentation(BtcWalletService btcWalletService,
                                   PriceFeedService priceFeedService,
                                   Preferences preferences,
                                   FeeService feeService) {
        this.priceFeedService = priceFeedService;
        this.preferences = preferences;

        TxIdTextField.setPreferences(preferences);
        ExplorerAddressTextField.setPreferences(preferences);

        // TODO
        TxIdTextField.setWalletService(btcWalletService);

        GUIUtil.setFeeService(feeService);
    }

    public void setup() {
        fillPriceFeedComboBoxItems();
        setupMarketPriceFeed();

    }

    public void setPriceFeedComboBoxItem(PriceFeedComboBoxItem item) {
        if (item != null) {
            Optional<PriceFeedComboBoxItem> itemOptional = findPriceFeedComboBoxItem(priceFeedService.currencyCodeProperty().get());
            if (itemOptional.isPresent())
                selectedPriceFeedComboBoxItemProperty.set(itemOptional.get());
            else
                findPriceFeedComboBoxItem(preferences.getPreferredTradeCurrency().getCode())
                        .ifPresent(selectedPriceFeedComboBoxItemProperty::set);

            priceFeedService.setCurrencyCode(item.currencyCode);
        } else {
            findPriceFeedComboBoxItem(preferences.getPreferredTradeCurrency().getCode())
                    .ifPresent(selectedPriceFeedComboBoxItemProperty::set);
        }
    }

    private void fillPriceFeedComboBoxItems() {
        List<PriceFeedComboBoxItem> currencyItems = preferences.getTradeCurrenciesAsObservable()
                .stream()
                .map(tradeCurrency -> new PriceFeedComboBoxItem(tradeCurrency.getCode()))
                .collect(Collectors.toList());
        priceFeedComboBoxItems.setAll(currencyItems);
    }

    private void setupMarketPriceFeed() {
        priceFeedService.requestPriceFeed(price -> marketPrice.set(FormattingUtils.formatMarketPrice(price, priceFeedService.getCurrencyCode())),
                (errorMessage, throwable) -> marketPrice.set(Res.get("shared.na")));

        marketPriceBinding = EasyBind.combine(
                marketPriceCurrencyCode, marketPrice,
                (currencyCode, price) -> CurrencyUtil.getCurrencyPair(currencyCode) + ": " + price);

        marketPriceBinding.subscribe((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(oldValue)) {
                setMarketPriceInItems();

                String code = priceFeedService.currencyCodeProperty().get();
                Optional<PriceFeedComboBoxItem> itemOptional = findPriceFeedComboBoxItem(code);
                if (itemOptional.isPresent()) {
                    itemOptional.get().setDisplayString(newValue);
                    selectedPriceFeedComboBoxItemProperty.set(itemOptional.get());
                } else {
                    if (CurrencyUtil.isCryptoCurrency(code)) {
                        CurrencyUtil.getCryptoCurrency(code).ifPresent(cryptoCurrency -> {
                            preferences.addCryptoCurrency(cryptoCurrency);
                            fillPriceFeedComboBoxItems();
                        });
                    } else {
                        CurrencyUtil.getFiatCurrency(code).ifPresent(fiatCurrency -> {
                            preferences.addFiatCurrency(fiatCurrency);
                            fillPriceFeedComboBoxItems();
                        });
                    }
                }

                if (selectedPriceFeedComboBoxItemProperty.get() != null)
                    selectedPriceFeedComboBoxItemProperty.get().setDisplayString(newValue);
            }
        });

        marketPriceCurrencyCode.bind(priceFeedService.currencyCodeProperty());

        priceFeedAllLoadedSubscription = EasyBind.subscribe(priceFeedService.updateCounterProperty(), updateCounter -> setMarketPriceInItems());

        preferences.getTradeCurrenciesAsObservable().addListener((ListChangeListener<TradeCurrency>) c -> UserThread.runAfter(() -> {
            fillPriceFeedComboBoxItems();
            setMarketPriceInItems();
        }, 100, TimeUnit.MILLISECONDS));
    }

    private Optional<PriceFeedComboBoxItem> findPriceFeedComboBoxItem(String currencyCode) {
        return priceFeedComboBoxItems.stream()
                .filter(item -> item.currencyCode.equals(currencyCode))
                .findAny();
    }

    private void setMarketPriceInItems() {
        priceFeedComboBoxItems.forEach(item -> {
            String currencyCode = item.currencyCode;
            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
            String priceString;
            if (marketPrice != null && marketPrice.isPriceAvailable()) {
                priceString = FormattingUtils.formatMarketPrice(marketPrice.getPrice(), currencyCode);
                item.setPriceAvailable(true);
                item.setExternallyProvidedPrice(marketPrice.isExternallyProvidedPrice());
            } else {
                priceString = Res.get("shared.na");
                item.setPriceAvailable(false);
            }
            item.setDisplayString(CurrencyUtil.getCurrencyPair(currencyCode) + ": " + priceString);

            final String code = item.currencyCode;
            if (selectedPriceFeedComboBoxItemProperty.get() != null &&
                    selectedPriceFeedComboBoxItemProperty.get().currencyCode.equals(code)) {
                isFiatCurrencyPriceFeedSelected.set(CurrencyUtil.isFiatCurrency(code) && CurrencyUtil.getFiatCurrency(code).isPresent() && item.isPriceAvailable() && item.isExternallyProvidedPrice());
                isCryptoCurrencyPriceFeedSelected.set(CurrencyUtil.isCryptoCurrency(code) && CurrencyUtil.getCryptoCurrency(code).isPresent() && item.isPriceAvailable() && item.isExternallyProvidedPrice());
                isExternallyProvidedPrice.set(item.isExternallyProvidedPrice());
                isPriceAvailable.set(item.isPriceAvailable());
                marketPriceUpdated.set(marketPriceUpdated.get() + 1);
            }
        });
    }

    public ObjectProperty<PriceFeedComboBoxItem> getSelectedPriceFeedComboBoxItemProperty() {
        return selectedPriceFeedComboBoxItemProperty;
    }

    public BooleanProperty getIsFiatCurrencyPriceFeedSelected() {
        return isFiatCurrencyPriceFeedSelected;
    }

    public BooleanProperty getIsCryptoCurrencyPriceFeedSelected() {
        return isCryptoCurrencyPriceFeedSelected;
    }

    public BooleanProperty getIsExternallyProvidedPrice() {
        return isExternallyProvidedPrice;
    }

    public BooleanProperty getIsPriceAvailable() {
        return isPriceAvailable;
    }

    public IntegerProperty getMarketPriceUpdated() {
        return marketPriceUpdated;
    }

    public StringProperty getMarketPrice() {
        return marketPrice;
    }

    public StringProperty getMarketPrice(String currencyCode) {
        SimpleStringProperty marketPrice = new SimpleStringProperty(Res.get("shared.na"));
        MarketPrice marketPriceValue = priceFeedService.getMarketPrice(currencyCode);
        // Market price might not be available yet:
        if (marketPriceValue != null) {
            marketPrice.set(String.valueOf(marketPriceValue.getPrice()));
        }
        return marketPrice;
    }
}

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

package io.bisq.gui.main.settings.preferences;

import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.*;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Tuple3;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BaseCurrencyNetwork;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.user.BlockChainExplorer;
import io.bisq.core.user.Preferences;
import io.bisq.gui.app.BisqApp;
import io.bisq.gui.common.model.Activatable;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.components.TitledGroupBg;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.ImageUtil;
import io.bisq.gui.util.Layout;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;
import javafx.util.StringConverter;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.bisq.gui.util.FormBuilder.*;

@FxmlView
public class PreferencesView extends ActivatableViewAndModel<GridPane, Activatable> {

    // not supported yet
    //private ComboBox<String> btcDenominationComboBox;
    private ComboBox<BlockChainExplorer> blockChainExplorerComboBox;
    private ComboBox<String> userLanguageComboBox;
    private ComboBox<Country> userCountryComboBox;
    private ComboBox<TradeCurrency> preferredTradeCurrencyComboBox;
    private ComboBox<BaseCurrencyNetwork> selectBaseCurrencyNetworkComboBox;

    private CheckBox useAnimationsCheckBox, autoSelectArbitratorsCheckBox, showOwnOffersInOfferBook, sortMarketCurrenciesNumericallyCheckBox, useCustomFeeCheckbox;
    private int gridRow = 0;
    private InputTextField transactionFeeInputTextField, ignoreTradersListInputTextField;
    private ChangeListener<Boolean> transactionFeeFocusedListener;
    private final Preferences preferences;
    private final FeeService feeService;
    private final BisqEnvironment bisqEnvironment;
    private final BSFormatter formatter;

    private ListView<FiatCurrency> fiatCurrenciesListView;
    private ComboBox<FiatCurrency> fiatCurrenciesComboBox;
    private ListView<CryptoCurrency> cryptoCurrenciesListView;
    private ComboBox<CryptoCurrency> cryptoCurrenciesComboBox;
    private Button resetDontShowAgainButton;
    // private ListChangeListener<TradeCurrency> displayCurrenciesListChangeListener;
    private ObservableList<BlockChainExplorer> blockExplorers;
    private ObservableList<String> languageCodes;
    private ObservableList<Country> countries;
    private ObservableList<FiatCurrency> fiatCurrencies;
    private ObservableList<FiatCurrency> allFiatCurrencies;
    private ObservableList<CryptoCurrency> cryptoCurrencies;
    private ObservableList<CryptoCurrency> allCryptoCurrencies;
    private ObservableList<TradeCurrency> tradeCurrencies;
    private InputTextField deviationInputTextField;
    private ChangeListener<String> deviationListener, ignoreTradersListListener;
    private ChangeListener<Boolean> deviationFocusedListener;
    private ChangeListener<Boolean> useCustomFeeCheckboxListener;
    private ChangeListener<Number> transactionFeeChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PreferencesView(Preferences preferences, FeeService feeService,
                           BisqEnvironment bisqEnvironment, BSFormatter formatter) {
        super();
        this.preferences = preferences;
        this.feeService = feeService;
        this.bisqEnvironment = bisqEnvironment;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        blockExplorers = FXCollections.observableArrayList(preferences.getBlockChainExplorers());
        languageCodes = FXCollections.observableArrayList(LanguageUtil.getUserLanguageCodes());
        countries = FXCollections.observableArrayList(CountryUtil.getAllCountries());
        fiatCurrencies = preferences.getFiatCurrenciesAsObservable();
        cryptoCurrencies = preferences.getCryptoCurrenciesAsObservable();
        tradeCurrencies = preferences.getTradeCurrenciesAsObservable();

        allFiatCurrencies = FXCollections.observableArrayList(CurrencyUtil.getAllSortedFiatCurrencies());
        allCryptoCurrencies = FXCollections.observableArrayList(CurrencyUtil.getAllSortedCryptoCurrencies());

        allFiatCurrencies.removeAll(fiatCurrencies);
        allCryptoCurrencies.removeAll(cryptoCurrencies);

        initializeGeneralOptions();
        initializeDisplayCurrencies();
        initializeDisplayOptions();
    }


    @Override
    protected void activate() {
        activateGeneralOptions();
        activateDisplayCurrencies();
        activateDisplayPreferences();
    }

    @Override
    protected void deactivate() {
        deactivateGeneralOptions();
        deactivateDisplayCurrencies();
        deactivateDisplayPreferences();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialize
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initializeGeneralOptions() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 8, Res.get("setting.preferences.general"));
        GridPane.setColumnSpan(titledGroupBg, 4);

        // selectBaseCurrencyNetwork
        //noinspection unchecked
        selectBaseCurrencyNetworkComboBox = addLabelComboBox(root, gridRow,
                Res.getWithCol("settings.preferences.selectCurrencyNetwork"), Layout.FIRST_ROW_DISTANCE).second;

        selectBaseCurrencyNetworkComboBox.setConverter(new StringConverter<BaseCurrencyNetwork>() {
            @Override
            public String toString(BaseCurrencyNetwork baseCurrencyNetwork) {
                return DevEnv.DEV_MODE ? (baseCurrencyNetwork.getCurrencyName() + "_" + baseCurrencyNetwork.getNetwork()) :
                        baseCurrencyNetwork.getCurrencyName();
            }

            @Override
            public BaseCurrencyNetwork fromString(String string) {
                return null;
            }
        });

        // userLanguage
        //noinspection unchecked
        userLanguageComboBox = addLabelComboBox(root, ++gridRow,
                Res.getWithCol("shared.language")).second;

        // userCountry
        //noinspection unchecked
        userCountryComboBox = addLabelComboBox(root, ++gridRow,
                Res.getWithCol("shared.country")).second;

        // blockChainExplorer
        //noinspection unchecked
        blockChainExplorerComboBox = addLabelComboBox(root, ++gridRow,
                Res.get("setting.preferences.explorer")).second;

        // transactionFee
        Tuple3<Label, InputTextField, CheckBox> tuple = addLabelInputTextFieldCheckBox(root, ++gridRow,
                Res.get("setting.preferences.txFee"), Res.get("setting.preferences.useCustomValue"));
        transactionFeeInputTextField = tuple.second;
        useCustomFeeCheckbox = tuple.third;

        useCustomFeeCheckboxListener = (observable, oldValue, newValue) -> {
            preferences.setUseCustomWithdrawalTxFee(newValue);
            transactionFeeInputTextField.setEditable(newValue);
            if (!newValue) {
                transactionFeeInputTextField.setText(String.valueOf(feeService.getTxFeePerByte().value));
                try {
                    preferences.setWithdrawalTxFeeInBytes(feeService.getTxFeePerByte().value);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            preferences.setUseCustomWithdrawalTxFee(newValue);
        };

        transactionFeeFocusedListener = (o, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                String estimatedFee = String.valueOf(feeService.getTxFeePerByte().value);
                try {
                    int withdrawalTxFeeInBytes = Integer.parseInt(transactionFeeInputTextField.getText());
                    if (withdrawalTxFeeInBytes * 1000 < BisqEnvironment.getBaseCurrencyNetwork().getDefaultMinFee().value) {
                        new Popup<>().warning(Res.get("setting.preferences.txFeeMin")).show();
                        transactionFeeInputTextField.setText(estimatedFee);
                    } else if (withdrawalTxFeeInBytes > 5000) {
                        new Popup<>().warning(Res.get("setting.preferences.txFeeTooLarge")).show();
                        transactionFeeInputTextField.setText(estimatedFee);
                    } else {
                        preferences.setWithdrawalTxFeeInBytes(withdrawalTxFeeInBytes);
                    }
                } catch (NumberFormatException t) {
                    log.error(t.toString());
                    t.printStackTrace();
                    new Popup<>().warning(Res.get("validation.integerOnly")).show();
                    transactionFeeInputTextField.setText(estimatedFee);
                } catch (Throwable t) {
                    log.error(t.toString());
                    t.printStackTrace();
                    new Popup<>().warning(Res.get("validation.inputError", t.getMessage())).show();
                    transactionFeeInputTextField.setText(estimatedFee);
                }
            }
        };
        transactionFeeChangeListener = (observable, oldValue, newValue) -> transactionFeeInputTextField.setText(String.valueOf(feeService.getTxFeePerByte().value));

        // deviation
        deviationInputTextField = addLabelInputTextField(root, ++gridRow,
                Res.get("setting.preferences.deviation")).second;

        deviationListener = (observable, oldValue, newValue) -> {
            try {
                double value = formatter.parsePercentStringToDouble(newValue);
                if (value <= 0.3) {
                    preferences.setMaxPriceDistanceInPercent(value);
                } else {
                    new Popup<>().warning(Res.get("setting.preferences.deviationToLarge")).show();
                    UserThread.runAfter(() -> deviationInputTextField.setText(formatter.formatPercentagePrice(preferences.getMaxPriceDistanceInPercent())), 100, TimeUnit.MILLISECONDS);
                }
            } catch (NumberFormatException t) {
                log.error("Exception at parseDouble deviation: " + t.toString());
                UserThread.runAfter(() -> deviationInputTextField.setText(formatter.formatPercentagePrice(preferences.getMaxPriceDistanceInPercent())), 100, TimeUnit.MILLISECONDS);
            }
        };
        deviationFocusedListener = (observable1, oldValue1, newValue1) -> {
            if (oldValue1 && !newValue1)
                UserThread.runAfter(() -> deviationInputTextField.setText(formatter.formatPercentagePrice(preferences.getMaxPriceDistanceInPercent())), 100, TimeUnit.MILLISECONDS);
        };

        // autoSelectArbitrators
        autoSelectArbitratorsCheckBox = addLabelCheckBox(root, ++gridRow,
                Res.get("setting.preferences.autoSelectArbitrators"), "").second;

        // ignoreTraders 
        ignoreTradersListInputTextField = addLabelInputTextField(root, ++gridRow,
                Res.get("setting.preferences.ignorePeers")).second;
        ignoreTradersListListener = (observable, oldValue, newValue) ->
                preferences.setIgnoreTradersList(Arrays.asList(StringUtils.deleteWhitespace(newValue)
                        .replace(":9999", "").replace(".onion", "")
                        .split(",")));
    }

    private void initializeDisplayCurrencies() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 3, Res.get("setting.preferences.currenciesInList"),
                Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 4);

        //noinspection unchecked
        preferredTradeCurrencyComboBox = addLabelComboBox(root, gridRow, Res.get("setting.preferences.prefCurrency"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        preferredTradeCurrencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                // http://boschista.deviantart.com/journal/Cool-ASCII-Symbols-214218618
                return tradeCurrency.getDisplayPrefix() + tradeCurrency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        });

        Tuple2<Label, ListView> fiatTuple = addLabelListView(root, ++gridRow, Res.get("setting.preferences.displayFiat"));
        GridPane.setValignment(fiatTuple.first, VPos.TOP);
        //noinspection unchecked
        fiatCurrenciesListView = fiatTuple.second;
        fiatCurrenciesListView.setMinHeight(2 * Layout.LIST_ROW_HEIGHT + 2);
        fiatCurrenciesListView.setPrefHeight(3 * Layout.LIST_ROW_HEIGHT + 2);
        Label placeholder = new Label(Res.get("setting.preferences.noFiat"));
        placeholder.setWrapText(true);
        fiatCurrenciesListView.setPlaceholder(placeholder);
        fiatCurrenciesListView.setCellFactory(new Callback<ListView<FiatCurrency>, ListCell<FiatCurrency>>() {
            @Override
            public ListCell<FiatCurrency> call(ListView<FiatCurrency> list) {
                return new ListCell<FiatCurrency>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final FiatCurrency item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getNameAndCode());
                            removeButton.setOnAction(e -> {
                                if (item.equals(preferences.getPreferredTradeCurrency())) {
                                    new Popup<>().warning(Res.get("setting.preferences.cannotRemovePrefCurrency")).show();
                                } else {
                                    preferences.removeFiatCurrency(item);
                                    if (!allFiatCurrencies.contains(item))
                                        allFiatCurrencies.add(item);
                                }
                            });
                            setGraphic(pane);
                        } else {
                            setGraphic(null);
                            removeButton.setOnAction(null);
                        }
                    }
                };
            }
        });

        Tuple2<Label, ListView> cryptoCurrenciesTuple = addLabelListView(root, gridRow, Res.get("setting.preferences.displayAltcoins"));
        GridPane.setValignment(cryptoCurrenciesTuple.first, VPos.TOP);
        GridPane.setMargin(cryptoCurrenciesTuple.first, new Insets(0, 0, 0, 20));
        //noinspection unchecked
        cryptoCurrenciesListView = cryptoCurrenciesTuple.second;
        GridPane.setColumnIndex(cryptoCurrenciesTuple.first, 2);
        GridPane.setColumnIndex(cryptoCurrenciesListView, 3);
        cryptoCurrenciesListView.setMinHeight(2 * Layout.LIST_ROW_HEIGHT + 2);
        cryptoCurrenciesListView.setPrefHeight(3 * Layout.LIST_ROW_HEIGHT + 2);
        placeholder = new Label(Res.get("setting.preferences.noAltcoins"));
        placeholder.setWrapText(true);
        cryptoCurrenciesListView.setPlaceholder(placeholder);
        cryptoCurrenciesListView.setCellFactory(new Callback<ListView<CryptoCurrency>, ListCell<CryptoCurrency>>() {
            @Override
            public ListCell<CryptoCurrency> call(ListView<CryptoCurrency> list) {
                return new ListCell<CryptoCurrency>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final CryptoCurrency item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.getNameAndCode());
                            removeButton.setOnAction(e -> {
                                if (item.equals(preferences.getPreferredTradeCurrency())) {
                                    new Popup<>().warning(Res.get("setting.preferences.cannotRemovePrefCurrency")).show();
                                } else {
                                    preferences.removeCryptoCurrency(item);
                                    if (!allCryptoCurrencies.contains(item))
                                        allCryptoCurrencies.add(item);
                                }
                            });
                            setGraphic(pane);
                        } else {
                            setGraphic(null);
                            removeButton.setOnAction(null);
                        }
                    }
                };
            }
        });

        //noinspection unchecked
        fiatCurrenciesComboBox = addLabelComboBox(root, ++gridRow).second;
        fiatCurrenciesComboBox.setPromptText(Res.get("setting.preferences.addFiat"));
        fiatCurrenciesComboBox.setConverter(new StringConverter<FiatCurrency>() {
            @Override
            public String toString(FiatCurrency tradeCurrency) {
                return tradeCurrency.getNameAndCode();
            }

            @Override
            public FiatCurrency fromString(String s) {
                return null;
            }
        });

        Tuple2<Label, ComboBox> labelComboBoxTuple2 = addLabelComboBox(root, gridRow);
        //noinspection unchecked
        cryptoCurrenciesComboBox = labelComboBoxTuple2.second;
        GridPane.setColumnIndex(cryptoCurrenciesComboBox, 3);
        cryptoCurrenciesComboBox.setPromptText(Res.get("setting.preferences.addAltcoin"));
        cryptoCurrenciesComboBox.setConverter(new StringConverter<CryptoCurrency>() {
            @Override
            public String toString(CryptoCurrency tradeCurrency) {
                return tradeCurrency.getNameAndCode();
            }

            @Override
            public CryptoCurrency fromString(String s) {
                return null;
            }
        });
    }


    private void initializeDisplayOptions() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 4, Res.get("setting.preferences.displayOptions"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 4);

        showOwnOffersInOfferBook = addLabelCheckBox(root, gridRow, Res.get("setting.preferences.showOwnOffers"), "", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        useAnimationsCheckBox = addLabelCheckBox(root, ++gridRow, Res.get("setting.preferences.useAnimations"), "").second;
        // useStickyMarketPriceCheckBox = addLabelCheckBox(root, ++gridRow, "Use sticky market price:", "").second;
        sortMarketCurrenciesNumericallyCheckBox = addLabelCheckBox(root, ++gridRow, Res.get("setting.preferences.sortWithNumOffers"), "").second;
        resetDontShowAgainButton = addLabelButton(root, ++gridRow, Res.get("setting.preferences.resetAllFlags"),
                Res.get("setting.preferences.reset"), 0).second;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Activate
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void activateGeneralOptions() {
        List<BaseCurrencyNetwork> baseCurrencyNetworks = Arrays.asList(BaseCurrencyNetwork.values());

        // We don't support DOGE anymore due lack of interest but leave it in the code in case it will get 
        // re-activated some day
        baseCurrencyNetworks = baseCurrencyNetworks.stream()
                .filter(e -> !e.isDoge())
                .collect(Collectors.toList());
        
        // show ony mainnet in production version
        if (!DevEnv.DEV_MODE)
            baseCurrencyNetworks = baseCurrencyNetworks.stream()
                    .filter(e -> e.isMainnet())
                    .collect(Collectors.toList());
        selectBaseCurrencyNetworkComboBox.setItems(FXCollections.observableArrayList(baseCurrencyNetworks));
        selectBaseCurrencyNetworkComboBox.setOnAction(e -> onSelectNetwork());
        selectBaseCurrencyNetworkComboBox.getSelectionModel().select(BisqEnvironment.getBaseCurrencyNetwork());

        boolean useCustomWithdrawalTxFee = preferences.isUseCustomWithdrawalTxFee();
        useCustomFeeCheckbox.setSelected(useCustomWithdrawalTxFee);

        transactionFeeInputTextField.setEditable(useCustomWithdrawalTxFee);
        if (!useCustomWithdrawalTxFee) {
            transactionFeeInputTextField.setText(String.valueOf(feeService.getTxFeePerByte().value));
            feeService.feeUpdateCounterProperty().addListener(transactionFeeChangeListener);
        }

        transactionFeeInputTextField.setText(getNonTradeTxFeePerBytes());
        ignoreTradersListInputTextField.setText(preferences.getIgnoreTradersList().stream().collect(Collectors.joining(", ")));

        userLanguageComboBox.setItems(languageCodes);
        userLanguageComboBox.getSelectionModel().select(preferences.getUserLanguage());
        userLanguageComboBox.setConverter(new StringConverter<String>() {
            @Override
            public String toString(String code) {
                return LanguageUtil.getDisplayName(code);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });

        userLanguageComboBox.setOnAction(e -> {
            String selectedItem = userLanguageComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                preferences.setUserLanguage(selectedItem);
                new Popup<>().information(Res.get("settings.preferences.languageChange"))
                        .closeButtonText(Res.get("shared.ok"))
                        .show();
            }
            // Should we apply the changed currency immediately to the language list?
            // If so and the user selects a unknown language he might get lost and it is hard to find
            // again the language he understands
           /* if (selectedItem != null && !selectedItem.equals(preferences.getUserLanguage())) {
                preferences.setUserLanguage(selectedItem);
                UserThread.execute(() -> {
                    languageCodes.clear();
                    languageCodes.addAll(LanguageUtil.getAllLanguageCodes());
                    userLanguageComboBox.getSelectionModel().select(preferences.getUserLanguage());
                });
            }*/
        });

        userCountryComboBox.setItems(countries);
        userCountryComboBox.getSelectionModel().select(preferences.getUserCountry());
        userCountryComboBox.setConverter(new StringConverter<Country>() {
            @Override
            public String toString(Country country) {
                return CountryUtil.getNameByCode(country.code);
            }

            @Override
            public Country fromString(String string) {
                return null;
            }
        });
        userCountryComboBox.setOnAction(e -> {
            Country country = userCountryComboBox.getSelectionModel().getSelectedItem();
            if (country != null) {
                preferences.setUserCountry(country);
            }
        });

        blockChainExplorerComboBox.setItems(blockExplorers);
        blockChainExplorerComboBox.getSelectionModel().select(preferences.getBlockChainExplorer());
        blockChainExplorerComboBox.setConverter(new StringConverter<BlockChainExplorer>() {
            @Override
            public String toString(BlockChainExplorer blockChainExplorer) {
                return blockChainExplorer.name;
            }

            @Override
            public BlockChainExplorer fromString(String string) {
                return null;
            }
        });
        blockChainExplorerComboBox.setOnAction(e -> preferences.setBlockChainExplorer(blockChainExplorerComboBox.getSelectionModel().getSelectedItem()));

        deviationInputTextField.setText(formatter.formatPercentagePrice(preferences.getMaxPriceDistanceInPercent()));
        deviationInputTextField.textProperty().addListener(deviationListener);
        deviationInputTextField.focusedProperty().addListener(deviationFocusedListener);

        transactionFeeInputTextField.focusedProperty().addListener(transactionFeeFocusedListener);
        ignoreTradersListInputTextField.textProperty().addListener(ignoreTradersListListener);
        useCustomFeeCheckbox.selectedProperty().addListener(useCustomFeeCheckboxListener);
    }

    private void activateDisplayCurrencies() {
        preferredTradeCurrencyComboBox.setItems(tradeCurrencies);
        preferredTradeCurrencyComboBox.getSelectionModel().select(preferences.getPreferredTradeCurrency());
        preferredTradeCurrencyComboBox.setVisibleRowCount(25);
        preferredTradeCurrencyComboBox.setOnAction(e -> {
            TradeCurrency selectedItem = preferredTradeCurrencyComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null)
                preferences.setPreferredTradeCurrency(selectedItem);
        });

        fiatCurrenciesComboBox.setItems(allFiatCurrencies);
        fiatCurrenciesListView.setItems(fiatCurrencies);
        fiatCurrenciesComboBox.setOnAction(e -> {
            FiatCurrency selectedItem = fiatCurrenciesComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                preferences.addFiatCurrency(selectedItem);
                if (allFiatCurrencies.contains(selectedItem)) {
                    UserThread.execute(() -> {
                        fiatCurrenciesComboBox.getSelectionModel().clearSelection();
                        allFiatCurrencies.remove(selectedItem);

                    });
                }
            }
        });
        cryptoCurrenciesComboBox.setItems(allCryptoCurrencies);
        cryptoCurrenciesListView.setItems(cryptoCurrencies);
        cryptoCurrenciesComboBox.setOnAction(e -> {
            CryptoCurrency selectedItem = cryptoCurrenciesComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                preferences.addCryptoCurrency(selectedItem);
                if (allCryptoCurrencies.contains(selectedItem)) {
                    UserThread.execute(() -> {
                        cryptoCurrenciesComboBox.getSelectionModel().clearSelection();
                        allCryptoCurrencies.remove(selectedItem);

                    });
                }
            }
        });
    }

    private void activateDisplayPreferences() {
        showOwnOffersInOfferBook.setSelected(preferences.isShowOwnOffersInOfferBook());
        showOwnOffersInOfferBook.setOnAction(e -> preferences.setShowOwnOffersInOfferBook(showOwnOffersInOfferBook.isSelected()));

        useAnimationsCheckBox.setSelected(preferences.isUseAnimations());
        useAnimationsCheckBox.setOnAction(e -> preferences.setUseAnimations(useAnimationsCheckBox.isSelected()));

        // useStickyMarketPriceCheckBox.setSelected(preferences.isUseStickyMarketPrice());
        // useStickyMarketPriceCheckBox.setOnAction(e -> preferences.setUseStickyMarketPrice(useStickyMarketPriceCheckBox.isSelected()));

        sortMarketCurrenciesNumericallyCheckBox.setSelected(preferences.isSortMarketCurrenciesNumerically());
        sortMarketCurrenciesNumericallyCheckBox.setOnAction(e -> preferences.setSortMarketCurrenciesNumerically(sortMarketCurrenciesNumericallyCheckBox.isSelected()));

        resetDontShowAgainButton.setOnAction(e -> preferences.resetDontShowAgain());

        autoSelectArbitratorsCheckBox.setSelected(preferences.isAutoSelectArbitrators());
        autoSelectArbitratorsCheckBox.setOnAction(e -> preferences.setAutoSelectArbitrators(autoSelectArbitratorsCheckBox.isSelected()));
    }

    private String getNonTradeTxFeePerBytes() {
        return preferences.isUseCustomWithdrawalTxFee() ?
                String.valueOf(preferences.getWithdrawalTxFeeInBytes()) :
                String.valueOf(feeService.getTxFeePerByte().value);
    }

    private void onSelectNetwork() {
        if (selectBaseCurrencyNetworkComboBox.getSelectionModel().getSelectedItem() != BisqEnvironment.getBaseCurrencyNetwork())
            selectNetwork();
    }

    private void selectNetwork() {
        new Popup().warning(Res.get("settings.net.needRestart"))
                .onAction(() -> {
                    bisqEnvironment.saveBaseCryptoNetwork(selectBaseCurrencyNetworkComboBox.getSelectionModel().getSelectedItem());
                    UserThread.runAfter(BisqApp.shutDownHandler::run, 500, TimeUnit.MILLISECONDS);
                })
                .actionButtonText(Res.get("shared.shutDown"))
                .closeButtonText(Res.get("shared.cancel"))
                .onClose(() -> selectBaseCurrencyNetworkComboBox.getSelectionModel().select(BisqEnvironment.getBaseCurrencyNetwork()))
                .show();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Deactivate
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void deactivateGeneralOptions() {
        selectBaseCurrencyNetworkComboBox.setOnAction(null);
        userLanguageComboBox.setOnAction(null);
        userCountryComboBox.setOnAction(null);
        blockChainExplorerComboBox.setOnAction(null);
        deviationInputTextField.textProperty().removeListener(deviationListener);
        deviationInputTextField.focusedProperty().removeListener(deviationFocusedListener);
        transactionFeeInputTextField.focusedProperty().removeListener(transactionFeeFocusedListener);
        if (transactionFeeChangeListener != null)
            feeService.feeUpdateCounterProperty().removeListener(transactionFeeChangeListener);
        ignoreTradersListInputTextField.textProperty().removeListener(ignoreTradersListListener);
        useCustomFeeCheckbox.selectedProperty().removeListener(useCustomFeeCheckboxListener);
    }

    private void deactivateDisplayCurrencies() {
        preferredTradeCurrencyComboBox.setOnAction(null);
    }

    private void deactivateDisplayPreferences() {
        useAnimationsCheckBox.setOnAction(null);
        // useStickyMarketPriceCheckBox.setOnAction(null);
        sortMarketCurrenciesNumericallyCheckBox.setOnAction(null);
        showOwnOffersInOfferBook.setOnAction(null);
        autoSelectArbitratorsCheckBox.setOnAction(null);
        resetDontShowAgainButton.setOnAction(null);
    }
}

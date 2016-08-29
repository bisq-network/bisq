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

package io.bitsquare.gui.main.settings.preferences;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.*;
import io.bitsquare.user.BlockChainExplorer;
import io.bitsquare.user.Preferences;
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
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class PreferencesView extends ActivatableViewAndModel<GridPane, Activatable> {

    // not supported yet
    //private ComboBox<String> btcDenominationComboBox; 
    private ComboBox<BlockChainExplorer> blockChainExplorerComboBox;
    //  private ComboBox<String> userLanguageComboBox;
    private ComboBox<TradeCurrency> preferredTradeCurrencyComboBox;

    private CheckBox useAnimationsCheckBox, autoSelectArbitratorsCheckBox, showOwnOffersInOfferBook, sortMarketCurrenciesNumericallyCheckBox/*, useStickyMarketPriceCheckBox*/;
    private int gridRow = 0;
    private InputTextField transactionFeeInputTextField, ignoreTradersListInputTextField;
    private ChangeListener<Boolean> transactionFeeFocusedListener;
    private final Preferences preferences;
    private BSFormatter formatter;

    private ListView<FiatCurrency> fiatCurrenciesListView;
    private ComboBox<FiatCurrency> fiatCurrenciesComboBox;
    private ListView<CryptoCurrency> cryptoCurrenciesListView;
    private ComboBox<CryptoCurrency> cryptoCurrenciesComboBox;
    private Button resetDontShowAgainButton;
    // private ListChangeListener<TradeCurrency> displayCurrenciesListChangeListener;
    final ObservableList<String> btcDenominations = FXCollections.observableArrayList(Preferences.getBtcDenominations());
    final ObservableList<BlockChainExplorer> blockExplorers;
    final ObservableList<String> languageCodes;
    public final ObservableList<FiatCurrency> fiatCurrencies;
    public final ObservableList<FiatCurrency> allFiatCurrencies;
    public final ObservableList<CryptoCurrency> cryptoCurrencies;
    public final ObservableList<CryptoCurrency> allCryptoCurrencies;
    public final ObservableList<TradeCurrency> tradeCurrencies;
    private InputTextField deviationInputTextField;
    private ChangeListener<String> deviationListener, ignoreTradersListListener;
    private ChangeListener<Boolean> deviationFocusedListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PreferencesView(Preferences preferences, BSFormatter formatter) {
        super();
        this.preferences = preferences;
        this.formatter = formatter;

        blockExplorers = FXCollections.observableArrayList(preferences.getBlockChainExplorers());
        languageCodes = FXCollections.observableArrayList(LanguageUtil.getAllLanguageCodes());
        fiatCurrencies = preferences.getFiatCurrenciesAsObservable();
        cryptoCurrencies = preferences.getCryptoCurrenciesAsObservable();
        tradeCurrencies = preferences.getTradeCurrenciesAsObservable();

        allFiatCurrencies = FXCollections.observableArrayList(CurrencyUtil.getAllSortedFiatCurrencies());
        allCryptoCurrencies = FXCollections.observableArrayList(CurrencyUtil.getAllSortedCryptoCurrencies());

        allFiatCurrencies.removeAll(fiatCurrencies);
        allCryptoCurrencies.removeAll(cryptoCurrencies);
    }

    @Override
    public void initialize() {
        initializeDisplayCurrencies();
        initializeOtherOptions();
        initializeDisplayOptions();
    }


    @Override
    protected void activate() {
        activateDisplayCurrencies();
        activateOtherOptions();
        activateDisplayPreferences();
    }

    @Override
    protected void deactivate() {
        deactivateDisplayCurrencies();
        deactivateOtherOptions();
        deactivateDisplayPreferences();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialize
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initializeDisplayCurrencies() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 3, "Currencies in market price feed list");
        GridPane.setColumnSpan(titledGroupBg, 4);

        preferredTradeCurrencyComboBox = addLabelComboBox(root, gridRow, "Preferred currency:", Layout.FIRST_ROW_DISTANCE).second;
        preferredTradeCurrencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                // http://boschista.deviantart.com/journal/Cool-ASCII-Symbols-214218618
                if (tradeCurrency instanceof FiatCurrency)
                    return "★ " + tradeCurrency.getNameAndCode();
                else if (tradeCurrency instanceof CryptoCurrency)
                    return "✦ " + tradeCurrency.getNameAndCode();
                else
                    return "-";
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        });

        Tuple2<Label, ListView> fiatTuple = addLabelListView(root, ++gridRow, "Display national currencies:");
        GridPane.setValignment(fiatTuple.first, VPos.TOP);
        fiatCurrenciesListView = fiatTuple.second;
        fiatCurrenciesListView.setMinHeight(2 * Layout.LIST_ROW_HEIGHT + 2);
        fiatCurrenciesListView.setMaxHeight(6 * Layout.LIST_ROW_HEIGHT + 2);
        Label placeholder = new Label("There are no national currencies selected");
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
                                    new Popup().warning("You cannot remove your selected preferred display currency").show();
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

        Tuple2<Label, ListView> cryptoCurrenciesTuple = addLabelListView(root, gridRow, "Display altcoins:");
        GridPane.setValignment(cryptoCurrenciesTuple.first, VPos.TOP);
        GridPane.setMargin(cryptoCurrenciesTuple.first, new Insets(0, 0, 0, 20));
        cryptoCurrenciesListView = cryptoCurrenciesTuple.second;
        GridPane.setColumnIndex(cryptoCurrenciesTuple.first, 2);
        GridPane.setColumnIndex(cryptoCurrenciesListView, 3);
        cryptoCurrenciesListView.setMinHeight(2 * Layout.LIST_ROW_HEIGHT + 2);
        cryptoCurrenciesListView.setMaxHeight(6 * Layout.LIST_ROW_HEIGHT + 2);
        placeholder = new Label("There are no altcoins selected");
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
                                    new Popup().warning("You cannot remove your selected preferred display currency").show();
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

        fiatCurrenciesComboBox = addLabelComboBox(root, ++gridRow).second;
        fiatCurrenciesComboBox.setPromptText("Add national currency");
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
        cryptoCurrenciesComboBox = labelComboBoxTuple2.second;
        GridPane.setColumnIndex(cryptoCurrenciesComboBox, 3);
        cryptoCurrenciesComboBox.setPromptText("Add altcoin");
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

    private void initializeOtherOptions() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 5, "General preferences", Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 4);
        // userLanguageComboBox = addLabelComboBox(root, gridRow, "Language:", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        // btcDenominationComboBox = addLabelComboBox(root, ++gridRow, "Bitcoin denomination:").second;
        blockChainExplorerComboBox = addLabelComboBox(root, gridRow, "Bitcoin block explorer:", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        deviationInputTextField = addLabelInputTextField(root, ++gridRow, "Max. deviation from market price:").second;
        autoSelectArbitratorsCheckBox = addLabelCheckBox(root, ++gridRow, "Auto select arbitrators:", "").second;

        deviationListener = (observable, oldValue, newValue) -> {
            try {
                double value = formatter.parsePercentStringToDouble(newValue);
                if (value <= 0.2) {
                    preferences.setMaxPriceDistanceInPercent(value);
                } else {
                    new Popup().warning("Amounts larger than 20 % are not allowed.").show();
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

        transactionFeeInputTextField = addLabelInputTextField(root, ++gridRow, "Withdrawal transaction fee (satoshi/byte):").second;
        transactionFeeFocusedListener = (o, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                try {
                    int val = Integer.parseInt(transactionFeeInputTextField.getText());
                    preferences.setNonTradeTxFeePerKB(val * 1000);
                } catch (NumberFormatException t) {
                    new Popup().warning("Please enter integer numbers only.").show();
                    transactionFeeInputTextField.setText(getNonTradeTxFeePerKB());
                } catch (Throwable t) {
                    new Popup().warning("Your input was not accepted.\n" + t.getMessage()).show();
                    transactionFeeInputTextField.setText(getNonTradeTxFeePerKB());
                }
            }
        };

        ignoreTradersListInputTextField = addLabelInputTextField(root, ++gridRow, "Ignore traders with onion address (comma sep.):").second;
        ignoreTradersListListener = (observable, oldValue, newValue) ->
                preferences.setIgnoreTradersList(Arrays.asList(newValue.replace(" ", "").replace(":9999", "").replace(".onion", "").split(",")));
    }

    private void initializeDisplayOptions() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, ++gridRow, 4, "Display options", Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 4);

        showOwnOffersInOfferBook = addLabelCheckBox(root, gridRow, "Show my own offers in offer book:", "", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        useAnimationsCheckBox = addLabelCheckBox(root, ++gridRow, "Use animations:", "").second;
        // useStickyMarketPriceCheckBox = addLabelCheckBox(root, ++gridRow, "Use sticky market price:", "").second;
        sortMarketCurrenciesNumericallyCheckBox = addLabelCheckBox(root, ++gridRow, "Sort market lists with nr. of offers/trades:", "").second;
        resetDontShowAgainButton = addLabelButton(root, ++gridRow, "Reset all don't show again flags:", "Reset", 0).second;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Activate
    ///////////////////////////////////////////////////////////////////////////////////////////


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

    private void activateOtherOptions() {
        transactionFeeInputTextField.setText(getNonTradeTxFeePerKB());
        ignoreTradersListInputTextField.setText(preferences.getIgnoreTradersList().stream().collect(Collectors.joining(", ")));
        
    /* btcDenominationComboBox.setDisable(true);
     btcDenominationComboBox.setItems(btcDenominations);
     btcDenominationComboBox.getSelectionModel().select(getBtcDenomination());
     btcDenominationComboBox.setOnAction(e -> onSelectBtcDenomination(btcDenominationComboBox.getSelectionModel().getSelectedItem()));*/

     /*   userLanguageComboBox.setItems(languageCodes);
        userLanguageComboBox.getSelectionModel().select(preferences.getPreferredLocale().getLanguage());
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
            String code = userLanguageComboBox.getSelectionModel().getSelectedItem();
            preferences.setPreferredLocale(new Locale(code, preferences.getPreferredLocale().getCountry()));
        });*/


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
    }

    @NotNull
    private String getNonTradeTxFeePerKB() {
        return String.valueOf(preferences.getNonTradeTxFeePerKB() / 1000);
    }

    private void activateDisplayPreferences() {
        showOwnOffersInOfferBook.setSelected(preferences.getShowOwnOffersInOfferBook());
        showOwnOffersInOfferBook.setOnAction(e -> preferences.setShowOwnOffersInOfferBook(showOwnOffersInOfferBook.isSelected()));

        useAnimationsCheckBox.setSelected(preferences.getUseAnimations());
        useAnimationsCheckBox.setOnAction(e -> preferences.setUseAnimations(useAnimationsCheckBox.isSelected()));

        // useStickyMarketPriceCheckBox.setSelected(preferences.getUseStickyMarketPrice());
        // useStickyMarketPriceCheckBox.setOnAction(e -> preferences.setUseStickyMarketPrice(useStickyMarketPriceCheckBox.isSelected()));

        sortMarketCurrenciesNumericallyCheckBox.setSelected(preferences.getSortMarketCurrenciesNumerically());
        sortMarketCurrenciesNumericallyCheckBox.setOnAction(e -> preferences.setSortMarketCurrenciesNumerically(sortMarketCurrenciesNumericallyCheckBox.isSelected()));

        resetDontShowAgainButton.setOnAction(e -> preferences.resetDontShowAgainForType());

        autoSelectArbitratorsCheckBox.setSelected(preferences.getAutoSelectArbitrators());
        autoSelectArbitratorsCheckBox.setOnAction(e -> preferences.setAutoSelectArbitrators(autoSelectArbitratorsCheckBox.isSelected()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Deactivate
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void deactivateDisplayCurrencies() {
        preferredTradeCurrencyComboBox.setOnAction(null);
    }

    private void deactivateOtherOptions() {
        //btcDenominationComboBox.setOnAction(null);
        // userLanguageComboBox.setOnAction(null);
        blockChainExplorerComboBox.setOnAction(null);
        deviationInputTextField.textProperty().removeListener(deviationListener);
        deviationInputTextField.focusedProperty().removeListener(deviationFocusedListener);
        transactionFeeInputTextField.focusedProperty().removeListener(transactionFeeFocusedListener);
        ignoreTradersListInputTextField.textProperty().removeListener(ignoreTradersListListener);
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

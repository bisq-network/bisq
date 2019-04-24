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

package bisq.desktop.main.settings.preferences;

import bisq.desktop.app.BisqApp;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.PasswordTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.ImageUtil;
import bisq.desktop.util.Layout;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.BaseCurrencyNetwork;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.LanguageUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.user.BlockChainExplorer;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.*;

@FxmlView
public class PreferencesView extends ActivatableViewAndModel<GridPane, PreferencesViewModel> {

    // not supported yet
    //private ComboBox<String> btcDenominationComboBox;
    private ComboBox<BlockChainExplorer> blockChainExplorerComboBox;
    private ComboBox<String> userLanguageComboBox;
    private ComboBox<Country> userCountryComboBox;
    private ComboBox<TradeCurrency> preferredTradeCurrencyComboBox;
    private ComboBox<BaseCurrencyNetwork> selectBaseCurrencyNetworkComboBox;

    private ToggleButton showOwnOffersInOfferBook, useAnimations, sortMarketCurrenciesNumerically, avoidStandbyMode,
            useCustomFee;
    private int gridRow = 0;
    private InputTextField transactionFeeInputTextField, ignoreTradersListInputTextField, referralIdInputTextField, rpcUserTextField;
    private ToggleButton isDaoFullNodeToggleButton;
    private PasswordTextField rpcPwTextField;
    private TitledGroupBg daoOptionsTitledGroupBg;

    private ChangeListener<Boolean> transactionFeeFocusedListener;
    private final Preferences preferences;
    private final FeeService feeService;
    private final ReferralIdService referralIdService;
    private final BisqEnvironment bisqEnvironment;
    private final AssetService assetService;
    private final FilterManager filterManager;
    private final DaoFacade daoFacade;
    private final BSFormatter formatter;

    private ListView<FiatCurrency> fiatCurrenciesListView;
    private ComboBox<FiatCurrency> fiatCurrenciesComboBox;
    private ListView<CryptoCurrency> cryptoCurrenciesListView;
    private ComboBox<CryptoCurrency> cryptoCurrenciesComboBox;
    private Button resetDontShowAgainButton, resyncDaoButton;
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
    private ChangeListener<String> deviationListener, ignoreTradersListListener, referralIdListener, rpcUserListener, rpcPwListener;
    private ChangeListener<Boolean> deviationFocusedListener;
    private ChangeListener<Boolean> useCustomFeeCheckboxListener;
    private ChangeListener<Number> transactionFeeChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PreferencesView(PreferencesViewModel model, Preferences preferences, FeeService feeService,
                           ReferralIdService referralIdService, BisqEnvironment bisqEnvironment,
                           AssetService assetService, FilterManager filterManager, DaoFacade daoFacade, BSFormatter formatter) {
        super(model);
        this.preferences = preferences;
        this.feeService = feeService;
        this.referralIdService = referralIdService;
        this.bisqEnvironment = bisqEnvironment;
        this.assetService = assetService;
        this.filterManager = filterManager;
        this.daoFacade = daoFacade;
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
        allFiatCurrencies.removeAll(fiatCurrencies);

        initializeGeneralOptions();
        initializeSeparator();
        initializeDisplayCurrencies();
        initializeDisplayOptions();
        if (DevEnv.isDaoActivated())
            initializeDaoOptions();
    }


    @Override
    protected void activate() {
        // We want to have it updated in case an asset got removed
        allCryptoCurrencies = FXCollections.observableArrayList(CurrencyUtil.getActiveSortedCryptoCurrencies(assetService, filterManager));
        allCryptoCurrencies.removeAll(cryptoCurrencies);

        activateGeneralOptions();
        activateDisplayCurrencies();
        activateDisplayPreferences();
        if (DevEnv.isDaoActivated())
            activateDaoPreferences();
    }

    @Override
    protected void deactivate() {
        deactivateGeneralOptions();
        deactivateDisplayCurrencies();
        deactivateDisplayPreferences();
        if (DevEnv.isDaoActivated())
            deactivateDaoPreferences();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialize
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void initializeGeneralOptions() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 9, Res.get("setting.preferences.general"));
        GridPane.setColumnSpan(titledGroupBg, 1);

        // selectBaseCurrencyNetwork
        selectBaseCurrencyNetworkComboBox = FormBuilder.addComboBox(root, gridRow,
                Res.get("settings.preferences.selectCurrencyNetwork"), Layout.FIRST_ROW_DISTANCE);

        selectBaseCurrencyNetworkComboBox.setButtonCell(GUIUtil.getComboBoxButtonCell(Res.get("settings.preferences.selectCurrencyNetwork"),
                selectBaseCurrencyNetworkComboBox, false));
        selectBaseCurrencyNetworkComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(BaseCurrencyNetwork baseCurrencyNetwork) {
                return baseCurrencyNetwork != null ?
                        Res.get(baseCurrencyNetwork.name()) :
                        Res.get("na");
            }

            @Override
            public BaseCurrencyNetwork fromString(String string) {
                return null;
            }
        });

        userLanguageComboBox = FormBuilder.addComboBox(root, ++gridRow,
                Res.get("shared.language"));
        userCountryComboBox = FormBuilder.addComboBox(root, ++gridRow,
                Res.get("shared.country"));
        userCountryComboBox.setButtonCell(GUIUtil.getComboBoxButtonCell(Res.get("shared.country"), userCountryComboBox,
                false));
        blockChainExplorerComboBox = FormBuilder.addComboBox(root, ++gridRow,
                Res.get("setting.preferences.explorer"));
        blockChainExplorerComboBox.setButtonCell(GUIUtil.getComboBoxButtonCell(Res.get("setting.preferences.explorer"),
                blockChainExplorerComboBox, false));

        Tuple3<Label, InputTextField, ToggleButton> tuple = addTopLabelInputTextFieldSlideToggleButton(root, ++gridRow,
                Res.get("setting.preferences.txFee"), Res.get("setting.preferences.useCustomValue"));
        transactionFeeInputTextField = tuple.second;
        useCustomFee = tuple.third;

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
                    int withdrawalTxFeePerByte = Integer.parseInt(transactionFeeInputTextField.getText());
                    final long minFeePerByte = BisqEnvironment.getBaseCurrencyNetwork().getDefaultMinFeePerByte();
                    if (withdrawalTxFeePerByte < minFeePerByte) {
                        new Popup<>().warning(Res.get("setting.preferences.txFeeMin", minFeePerByte)).show();
                        transactionFeeInputTextField.setText(estimatedFee);
                    } else if (withdrawalTxFeePerByte > 5000) {
                        new Popup<>().warning(Res.get("setting.preferences.txFeeTooLarge")).show();
                        transactionFeeInputTextField.setText(estimatedFee);
                    } else {
                        preferences.setWithdrawalTxFeeInBytes(withdrawalTxFeePerByte);
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
        deviationInputTextField = addInputTextField(root, ++gridRow,
                Res.get("setting.preferences.deviation"));

        deviationListener = (observable, oldValue, newValue) -> {
            try {
                double value = formatter.parsePercentStringToDouble(newValue);
                final double maxDeviation = 0.5;
                if (value <= maxDeviation) {
                    preferences.setMaxPriceDistanceInPercent(value);
                } else {
                    new Popup<>().warning(Res.get("setting.preferences.deviationToLarge", maxDeviation * 100)).show();
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

        // ignoreTraders
        ignoreTradersListInputTextField = addInputTextField(root, ++gridRow,
                Res.get("setting.preferences.ignorePeers"));
        ignoreTradersListListener = (observable, oldValue, newValue) ->
                preferences.setIgnoreTradersList(Arrays.asList(StringUtils.deleteWhitespace(newValue)
                        .replace(":9999", "").replace(".onion", "")
                        .split(",")));

        // referralId
        referralIdInputTextField = addInputTextField(root, ++gridRow, Res.get("setting.preferences.refererId"));
        referralIdListener = (observable, oldValue, newValue) -> {
            if (!newValue.equals(oldValue))
                referralIdService.setReferralId(newValue);
        };

        // AvoidStandbyModeService
        avoidStandbyMode = addSlideToggleButton(root, ++gridRow,
                Res.get("setting.preferences.avoidStandbyMode"));
    }

    private void initializeSeparator() {
        final Separator separator = new Separator(Orientation.VERTICAL);
        separator.setPadding(new Insets(0, 10, 0, 10));
        GridPane.setColumnIndex(separator, 1);
        GridPane.setHalignment(separator, HPos.CENTER);
        GridPane.setRowIndex(separator, 0);
        GridPane.setRowSpan(separator, GridPane.REMAINING);
        root.getChildren().add(separator);
    }

    private void initializeDisplayCurrencies() {
        int displayCurrenciesGridRowIndex = 0;
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, displayCurrenciesGridRowIndex, 9, Res.get("setting.preferences.currenciesInList"));
        GridPane.setColumnIndex(titledGroupBg, 2);
        GridPane.setColumnSpan(titledGroupBg, 2);


        preferredTradeCurrencyComboBox = FormBuilder.addComboBox(root, displayCurrenciesGridRowIndex++, Res.get("setting.preferences.prefCurrency"),
                Layout.FIRST_ROW_DISTANCE);
        GridPane.setColumnIndex(preferredTradeCurrencyComboBox, 2);

        preferredTradeCurrencyComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TradeCurrency object) {
                return object.getCode() + " - " + object.getName();
            }

            @Override
            public TradeCurrency fromString(String string) {
                return null;
            }
        });

        preferredTradeCurrencyComboBox.setButtonCell(GUIUtil.getTradeCurrencyButtonCell("", "",
                Collections.emptyMap()));
        preferredTradeCurrencyComboBox.setCellFactory(GUIUtil.getTradeCurrencyCellFactory("", "",
                Collections.emptyMap()));

        Tuple3<Label, ListView<FiatCurrency>, VBox> fiatTuple = FormBuilder.addTopLabelListView(root, displayCurrenciesGridRowIndex, Res.get("setting.preferences.displayFiat"));

        int listRowSpan = 6;
        GridPane.setColumnIndex(fiatTuple.third, 2);
        GridPane.setRowSpan(fiatTuple.third, listRowSpan);

        GridPane.setValignment(fiatTuple.third, VPos.TOP);
        GridPane.setMargin(fiatTuple.third, new Insets(10, 0, 0, 0));
        fiatCurrenciesListView = fiatTuple.second;
        fiatCurrenciesListView.setMinHeight(9 * Layout.LIST_ROW_HEIGHT + 2);
        fiatCurrenciesListView.setPrefHeight(10 * Layout.LIST_ROW_HEIGHT + 2);
        Label placeholder = new AutoTooltipLabel(Res.get("setting.preferences.noFiat"));
        placeholder.setWrapText(true);
        fiatCurrenciesListView.setPlaceholder(placeholder);
        fiatCurrenciesListView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<FiatCurrency> call(ListView<FiatCurrency> list) {
                return new ListCell<>() {
                    final Label label = new AutoTooltipLabel();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new AutoTooltipButton("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, -30d);
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
                                    if (!allFiatCurrencies.contains(item)) {
                                        allFiatCurrencies.add(item);
                                        allFiatCurrencies.sort(TradeCurrency::compareTo);
                                    }
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

        Tuple3<Label, ListView<CryptoCurrency>, VBox> cryptoCurrenciesTuple = FormBuilder.addTopLabelListView(root, displayCurrenciesGridRowIndex, Res.get("setting.preferences.displayAltcoins"));

        GridPane.setColumnIndex(cryptoCurrenciesTuple.third, 3);
        GridPane.setRowSpan(cryptoCurrenciesTuple.third, listRowSpan);

        GridPane.setValignment(cryptoCurrenciesTuple.third, VPos.TOP);
        GridPane.setMargin(cryptoCurrenciesTuple.third, new Insets(0, 0, 0, 20));
        cryptoCurrenciesListView = cryptoCurrenciesTuple.second;
        cryptoCurrenciesListView.setMinHeight(9 * Layout.LIST_ROW_HEIGHT + 2);
        cryptoCurrenciesListView.setPrefHeight(10 * Layout.LIST_ROW_HEIGHT + 2);
        placeholder = new AutoTooltipLabel(Res.get("setting.preferences.noAltcoins"));
        placeholder.setWrapText(true);
        cryptoCurrenciesListView.setPlaceholder(placeholder);
        cryptoCurrenciesListView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<CryptoCurrency> call(ListView<CryptoCurrency> list) {
                return new ListCell<>() {
                    final Label label = new AutoTooltipLabel();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new AutoTooltipButton("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, -30d);
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
                                    if (!allCryptoCurrencies.contains(item)) {
                                        allCryptoCurrencies.add(item);
                                        allCryptoCurrencies.sort(TradeCurrency::compareTo);
                                    }
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

        fiatCurrenciesComboBox = FormBuilder.addComboBox(root, displayCurrenciesGridRowIndex + listRowSpan);
        GridPane.setColumnIndex(fiatCurrenciesComboBox, 2);
        GridPane.setValignment(fiatCurrenciesComboBox, VPos.TOP);
        fiatCurrenciesComboBox.setPromptText(Res.get("setting.preferences.addFiat"));
        fiatCurrenciesComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(final FiatCurrency item, boolean empty) {
                super.updateItem(item, empty);
                this.setVisible(item != null || !empty);

                if (empty || item == null) {
                    setText(Res.get("setting.preferences.addFiat"));
                } else {
                    setText(item.getNameAndCode());
                }
            }
        });
        fiatCurrenciesComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(FiatCurrency tradeCurrency) {
                return tradeCurrency.getNameAndCode();
            }

            @Override
            public FiatCurrency fromString(String s) {
                return null;
            }
        });

        cryptoCurrenciesComboBox = FormBuilder.addComboBox(root, displayCurrenciesGridRowIndex + listRowSpan);
        GridPane.setColumnIndex(cryptoCurrenciesComboBox, 3);
        GridPane.setValignment(cryptoCurrenciesComboBox, VPos.TOP);
        cryptoCurrenciesComboBox.setPromptText(Res.get("setting.preferences.addAltcoin"));
        cryptoCurrenciesComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(final CryptoCurrency item, boolean empty) {
                super.updateItem(item, empty);
                this.setVisible(item != null || !empty);


                if (empty || item == null) {
                    setText(Res.get("setting.preferences.addAltcoin"));
                } else {
                    setText(item.getNameAndCode());
                }
            }
        });
        cryptoCurrenciesComboBox.setConverter(new StringConverter<>() {
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
        GridPane.setColumnSpan(titledGroupBg, 1);

//        showOwnOffersInOfferBook = addLabelCheckBox(root, gridRow, Res.get("setting.preferences.showOwnOffers"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        showOwnOffersInOfferBook = addSlideToggleButton(root, gridRow, Res.get("setting.preferences.showOwnOffers"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        useAnimations = addSlideToggleButton(root, ++gridRow, Res.get("setting.preferences.useAnimations"));
        // useStickyMarketPriceCheckBox = addLabelCheckBox(root, ++gridRow, "Use sticky market price:", "").second;
        sortMarketCurrenciesNumerically = addSlideToggleButton(root, ++gridRow, Res.get("setting.preferences.sortWithNumOffers"));
        resetDontShowAgainButton = addButton(root, ++gridRow, Res.get("setting.preferences.resetAllFlags"), 0);
        resetDontShowAgainButton.getStyleClass().add("compact-button");
        resetDontShowAgainButton.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(resetDontShowAgainButton, Priority.ALWAYS);
        GridPane.setColumnIndex(resetDontShowAgainButton, 0);
    }

    private void initializeDaoOptions() {
        daoOptionsTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, Res.get("setting.preferences.daoOptions"), Layout.GROUP_DISTANCE);
        resyncDaoButton = addButton(root, gridRow, Res.get("setting.preferences.dao.resync.label"), Layout.TWICE_FIRST_ROW_AND_GROUP_DISTANCE);
        resyncDaoButton.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(resyncDaoButton, Priority.ALWAYS);

        isDaoFullNodeToggleButton = addSlideToggleButton(root, ++gridRow, Res.get("setting.preferences.dao.isDaoFullNode"));
        rpcUserTextField = addInputTextField(root, ++gridRow, Res.getWithCol("setting.preferences.dao.rpcUser"));
        rpcUserTextField.setVisible(false);
        rpcUserTextField.setManaged(false);
        rpcPwTextField = addPasswordTextField(root, ++gridRow, Res.getWithCol("setting.preferences.dao.rpcPw"));
        rpcPwTextField.setVisible(false);
        rpcPwTextField.setManaged(false);

        rpcUserListener = (observable, oldValue, newValue) -> preferences.setRpcUser(rpcUserTextField.getText());
        rpcPwListener = (observable, oldValue, newValue) -> preferences.setRpcPw(rpcPwTextField.getText());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Activate
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void activateGeneralOptions() {
        List<BaseCurrencyNetwork> baseCurrencyNetworks = Arrays.asList(BaseCurrencyNetwork.values());

        // We allow switching to testnet to make it easier for users to test the testnet DAO version
        // We only show mainnet and dao testnet. Testnet is rather un-usable for application testing when asics
        // create 10000s of blocks per day.
        baseCurrencyNetworks = baseCurrencyNetworks.stream()
                .filter(e -> e.isMainnet() || e.isDaoTestNet())
                .collect(Collectors.toList());
        selectBaseCurrencyNetworkComboBox.setItems(FXCollections.observableArrayList(baseCurrencyNetworks));
        selectBaseCurrencyNetworkComboBox.setOnAction(e -> onSelectNetwork());
        selectBaseCurrencyNetworkComboBox.getSelectionModel().select(BisqEnvironment.getBaseCurrencyNetwork());

        boolean useCustomWithdrawalTxFee = preferences.isUseCustomWithdrawalTxFee();
        useCustomFee.setSelected(useCustomWithdrawalTxFee);

        transactionFeeInputTextField.setEditable(useCustomWithdrawalTxFee);
        if (!useCustomWithdrawalTxFee) {
            transactionFeeInputTextField.setText(String.valueOf(feeService.getTxFeePerByte().value));
            feeService.feeUpdateCounterProperty().addListener(transactionFeeChangeListener);
        }

        transactionFeeInputTextField.setText(String.valueOf(getTxFeeForWithdrawalPerByte()));
        ignoreTradersListInputTextField.setText(preferences.getIgnoreTradersList().stream().collect(Collectors.joining(", ")));
        referralIdService.getOptionalReferralId().ifPresent(referralId -> referralIdInputTextField.setText(referralId));
        referralIdInputTextField.setPromptText(Res.get("setting.preferences.refererId.prompt"));
        userLanguageComboBox.setItems(languageCodes);
        userLanguageComboBox.getSelectionModel().select(preferences.getUserLanguage());
        userLanguageComboBox.setConverter(new StringConverter<>() {
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

                if (model.needsArbitrationLanguageWarning()) {
                    new Popup<>().warning(Res.get("settings.preferences.arbitrationLanguageWarning",
                            model.getArbitrationLanguages()))
                            .closeButtonText(Res.get("shared.ok"))
                            .show();
                }
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
        userCountryComboBox.setConverter(new StringConverter<>() {
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
        blockChainExplorerComboBox.setConverter(new StringConverter<>() {
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
        useCustomFee.selectedProperty().addListener(useCustomFeeCheckboxListener);
        referralIdInputTextField.textProperty().addListener(referralIdListener);
    }

    private Coin getTxFeeForWithdrawalPerByte() {
        Coin fee = (preferences.isUseCustomWithdrawalTxFee()) ?
                Coin.valueOf(preferences.getWithdrawalTxFeeInBytes()) :
                feeService.getTxFeePerByte();
        log.info("tx fee = " + fee.toFriendlyString());
        return fee;
    }

    private void activateDisplayCurrencies() {
        preferredTradeCurrencyComboBox.setItems(tradeCurrencies);
        preferredTradeCurrencyComboBox.getSelectionModel().select(preferences.getPreferredTradeCurrency());
        preferredTradeCurrencyComboBox.setVisibleRowCount(12);
        preferredTradeCurrencyComboBox.setOnAction(e -> {
            TradeCurrency selectedItem = preferredTradeCurrencyComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null)
                preferences.setPreferredTradeCurrency(selectedItem);
        });

        fiatCurrenciesComboBox.setItems(allFiatCurrencies);
        fiatCurrenciesListView.setItems(fiatCurrencies);
        fiatCurrenciesComboBox.setOnHiding(e -> {
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
        cryptoCurrenciesComboBox.setOnHiding(e -> {
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

        useAnimations.setSelected(preferences.isUseAnimations());
        useAnimations.setOnAction(e -> preferences.setUseAnimations(useAnimations.isSelected()));

        // useStickyMarketPriceCheckBox.setSelected(preferences.isUseStickyMarketPrice());
        // useStickyMarketPriceCheckBox.setOnAction(e -> preferences.setUseStickyMarketPrice(useStickyMarketPriceCheckBox.isSelected()));

        sortMarketCurrenciesNumerically.setSelected(preferences.isSortMarketCurrenciesNumerically());
        sortMarketCurrenciesNumerically.setOnAction(e -> preferences.setSortMarketCurrenciesNumerically(sortMarketCurrenciesNumerically.isSelected()));

        resetDontShowAgainButton.setOnAction(e -> preferences.resetDontShowAgain());

        // We use opposite property (useStandbyMode) in preferences to have the default value (false) set as we want it,
        // so users who update gets set avoidStandbyMode=true (useStandbyMode=false)
        avoidStandbyMode.setSelected(!preferences.isUseStandbyMode());
        avoidStandbyMode.setOnAction(e -> preferences.setUseStandbyMode(!avoidStandbyMode.isSelected()));
    }

    private void activateDaoPreferences() {
        boolean daoFullNode = preferences.isDaoFullNode();
        isDaoFullNodeToggleButton.setSelected(daoFullNode);
        String rpcUser = preferences.getRpcUser();
        String rpcPw = preferences.getRpcPw();
        if (daoFullNode && (rpcUser == null || rpcUser.isEmpty() || rpcPw == null || rpcPw.isEmpty())) {
            log.warn("You have full DAO node selected but have not provided the rpc username and password. We reset daoFullNode to false");
            isDaoFullNodeToggleButton.setSelected(false);
        }
        rpcUserTextField.setText(rpcUser);
        rpcPwTextField.setText(rpcPw);
        updateDaoFields();

        resyncDaoButton.setOnAction(e -> daoFacade.resyncDao(() ->
                new Popup<>().attention(Res.get("setting.preferences.dao.resync.popup"))
                        .useShutDownButton()
                        .hideCloseButton()
                        .show()));

        isDaoFullNodeToggleButton.setOnAction(e -> {
            String key = "daoFullModeInfoShown";
            if (isDaoFullNodeToggleButton.isSelected() && preferences.showAgain(key)) {
                String url = "https://bisq.network/docs/dao-full-node";
                new Popup<>().backgroundInfo(Res.get("setting.preferences.dao.fullNodeInfo", url))
                        .onAction(() -> GUIUtil.openWebPage(url))
                        .actionButtonText(Res.get("setting.preferences.dao.fullNodeInfo.ok"))
                        .closeButtonText(Res.get("setting.preferences.dao.fullNodeInfo.cancel"))
                        .onClose(() -> UserThread.execute(() -> {
                            isDaoFullNodeToggleButton.setSelected(false);
                            updateDaoFields();
                        }))
                        .dontShowAgainId(key)
                        .width(800)
                        .show();
            }

            updateDaoFields();
        });

        rpcUserTextField.textProperty().addListener(rpcUserListener);
        rpcPwTextField.textProperty().addListener(rpcPwListener);
    }

    private void updateDaoFields() {
        boolean isDaoFullNode = isDaoFullNodeToggleButton.isSelected();
        GridPane.setRowSpan(daoOptionsTitledGroupBg, isDaoFullNode ? 4 : 2);
        rpcUserTextField.setVisible(isDaoFullNode);
        rpcUserTextField.setManaged(isDaoFullNode);
        rpcPwTextField.setVisible(isDaoFullNode);
        rpcPwTextField.setManaged(isDaoFullNode);
        preferences.setDaoFullNode(isDaoFullNode);
        if (!isDaoFullNode) {
            rpcPwTextField.clear();
            rpcUserTextField.clear();
        }
    }

    private void onSelectNetwork() {
        if (selectBaseCurrencyNetworkComboBox.getSelectionModel().getSelectedItem() != BisqEnvironment.getBaseCurrencyNetwork())
            selectNetwork();
    }

    private void selectNetwork() {
        new Popup().warning(Res.get("settings.net.needRestart"))
                .onAction(() -> {
                    bisqEnvironment.saveBaseCryptoNetwork(selectBaseCurrencyNetworkComboBox.getSelectionModel().getSelectedItem());
                    UserThread.runAfter(BisqApp.getShutDownHandler(), 500, TimeUnit.MILLISECONDS);
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
        useCustomFee.selectedProperty().removeListener(useCustomFeeCheckboxListener);
        referralIdInputTextField.textProperty().removeListener(referralIdListener);
    }

    private void deactivateDisplayCurrencies() {
        preferredTradeCurrencyComboBox.setOnAction(null);
    }

    private void deactivateDisplayPreferences() {
        useAnimations.setOnAction(null);
        // useStickyMarketPriceCheckBox.setOnAction(null);
        sortMarketCurrenciesNumerically.setOnAction(null);
        showOwnOffersInOfferBook.setOnAction(null);
        resetDontShowAgainButton.setOnAction(null);
        avoidStandbyMode.setOnAction(null);
    }

    private void deactivateDaoPreferences() {
        resyncDaoButton.setOnAction(null);
        isDaoFullNodeToggleButton.setOnAction(null);
        rpcUserTextField.textProperty().removeListener(rpcUserListener);
        rpcPwTextField.textProperty().removeListener(rpcUserListener);
    }
}

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

package bisq.desktop.main.account.content.notifications;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.WebCamWindow;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.AltcoinValidator;
import bisq.desktop.util.validation.FiatPriceValidator;
import bisq.desktop.util.validation.PercentageNumberValidator;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Altcoin;
import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.notifications.alerts.DisputeMsgEvents;
import bisq.core.notifications.alerts.MyOfferTakenEvents;
import bisq.core.notifications.alerts.TradeEvents;
import bisq.core.notifications.alerts.market.MarketAlertFilter;
import bisq.core.notifications.alerts.market.MarketAlerts;
import bisq.core.notifications.alerts.price.PriceAlert;
import bisq.core.notifications.alerts.price.PriceAlertFilter;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.UserThread;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@FxmlView
public class MobileNotificationsView extends ActivatableView<GridPane, Void> {
    private final Preferences preferences;
    private final User user;
    private final PriceFeedService priceFeedService;
    private final MarketAlerts marketAlerts;
    private final MobileNotificationService mobileNotificationService;
    private final BSFormatter formatter;

    private WebCamWindow webCamWindow;
    private QrCodeReader qrCodeReader;

    private TextField tokenInputTextField;
    private Label tokenInputLabel;
    private InputTextField priceAlertHighInputTextField, priceAlertLowInputTextField, marketAlertTriggerInputTextField;
    private CheckBox useSoundCheckBox, tradeCheckBox, marketCheckBox, priceCheckBox;
    private ComboBox<TradeCurrency> currencyComboBox;
    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private Button downloadButton, webCamButton, noWebCamButton, eraseButton, setPriceAlertButton,
            removePriceAlertButton, addMarketAlertButton, manageAlertsButton /*,testMsgButton*/;

    private ChangeListener<Boolean> useSoundCheckBoxListener, tradeCheckBoxListener, marketCheckBoxListener,
            priceCheckBoxListener, priceAlertHighFocusListener, priceAlertLowFocusListener, marketAlertTriggerFocusListener;
    private ChangeListener<String> tokenInputTextFieldListener, priceAlertHighListener, priceAlertLowListener, marketAlertTriggerListener;
    private ChangeListener<Number> priceFeedServiceListener;

    private int gridRow = 0;
    private int testMsgCounter = 0;
    private RadioButton buyOffersRadioButton, sellOffersRadioButton;
    private ToggleGroup offerTypeRadioButtonsToggleGroup;
    private ChangeListener<Toggle> offerTypeListener;
    private String selectedPriceAlertTradeCurrency;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private MobileNotificationsView(Preferences preferences,
                                    User user,
                                    PriceFeedService priceFeedService,
                                    MarketAlerts marketAlerts,
                                    MobileNotificationService mobileNotificationService,
                                    BSFormatter formatter) {
        super();
        this.preferences = preferences;
        this.user = user;
        this.priceFeedService = priceFeedService;
        this.marketAlerts = marketAlerts;
        this.mobileNotificationService = mobileNotificationService;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        createSetupFields();
        createSettingsFields();
        createMarketAlertFields();
        createPriceAlertFields();
    }

    @Override
    protected void activate() {
        // setup
        tokenInputTextField.textProperty().addListener(tokenInputTextFieldListener);
        downloadButton.setOnAction(e -> onDownload());
        webCamButton.setOnAction(e -> onOpenWebCam());
        noWebCamButton.setOnAction(e -> onNoWebCam());
        // testMsgButton.setOnAction(e -> onSendTestMsg());
        eraseButton.setOnAction(e -> onErase());

        // settings
        useSoundCheckBox.selectedProperty().addListener(useSoundCheckBoxListener);
        tradeCheckBox.selectedProperty().addListener(tradeCheckBoxListener);
        marketCheckBox.selectedProperty().addListener(marketCheckBoxListener);
        priceCheckBox.selectedProperty().addListener(priceCheckBoxListener);

        // market alert
        marketAlertTriggerInputTextField.textProperty().addListener(marketAlertTriggerListener);
        marketAlertTriggerInputTextField.focusedProperty().addListener(marketAlertTriggerFocusListener);
        offerTypeRadioButtonsToggleGroup.selectedToggleProperty().addListener(offerTypeListener);
        paymentAccountsComboBox.setOnAction(e -> onPaymentAccountSelected());
        addMarketAlertButton.setOnAction(e -> onAddMarketAlert());
        manageAlertsButton.setOnAction(e -> onManageMarketAlerts());

        paymentAccountsComboBox.setItems(FXCollections.observableArrayList(user.getPaymentAccountsAsObservable()));

        // price alert
        priceAlertHighInputTextField.textProperty().addListener(priceAlertHighListener);
        priceAlertLowInputTextField.textProperty().addListener(priceAlertLowListener);
        priceAlertHighInputTextField.focusedProperty().addListener(priceAlertHighFocusListener);
        priceAlertLowInputTextField.focusedProperty().addListener(priceAlertLowFocusListener);
        priceFeedService.updateCounterProperty().addListener(priceFeedServiceListener);
        currencyComboBox.setOnAction(e -> onSelectedTradeCurrency());
        setPriceAlertButton.setOnAction(e -> onSetPriceAlert());
        removePriceAlertButton.setOnAction(e -> onRemovePriceAlert());

        currencyComboBox.setItems(preferences.getTradeCurrenciesAsObservable());


        if (preferences.getPhoneKeyAndToken() != null) {
            tokenInputTextField.setText(preferences.getPhoneKeyAndToken());
            setPairingTokenFieldsVisible();
        } else {
            eraseButton.setDisable(true);
            //testMsgButton.setDisable(true);
        }
        setDisableForSetupFields(!mobileNotificationService.isSetupConfirmationSent());
        updateMarketAlertFields();
        fillPriceAlertFields();
        updatePriceAlertFields();
    }

    @Override
    protected void deactivate() {
        // setup
        tokenInputTextField.textProperty().removeListener(tokenInputTextFieldListener);
        downloadButton.setOnAction(null);
        webCamButton.setOnAction(null);
        noWebCamButton.setOnAction(null);
        //testMsgButton.setOnAction(null);
        eraseButton.setOnAction(null);

        // settings
        useSoundCheckBox.selectedProperty().removeListener(useSoundCheckBoxListener);
        tradeCheckBox.selectedProperty().removeListener(tradeCheckBoxListener);
        marketCheckBox.selectedProperty().removeListener(marketCheckBoxListener);
        priceCheckBox.selectedProperty().removeListener(priceCheckBoxListener);

        // market alert
        marketAlertTriggerInputTextField.textProperty().removeListener(marketAlertTriggerListener);
        marketAlertTriggerInputTextField.focusedProperty().removeListener(marketAlertTriggerFocusListener);
        offerTypeRadioButtonsToggleGroup.selectedToggleProperty().removeListener(offerTypeListener);
        paymentAccountsComboBox.setOnAction(null);
        addMarketAlertButton.setOnAction(null);
        manageAlertsButton.setOnAction(null);

        // price alert
        priceAlertHighInputTextField.textProperty().removeListener(priceAlertHighListener);
        priceAlertLowInputTextField.textProperty().removeListener(priceAlertLowListener);
        priceAlertHighInputTextField.focusedProperty().removeListener(priceAlertHighFocusListener);
        priceAlertLowInputTextField.focusedProperty().removeListener(priceAlertLowFocusListener);
        priceFeedService.updateCounterProperty().removeListener(priceFeedServiceListener);
        currencyComboBox.setOnAction(null);
        setPriceAlertButton.setOnAction(null);
        removePriceAlertButton.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI events
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Setup
    private void onDownload() {
        GUIUtil.openWebPage("https://bisq.network/downloads");
    }

    private void onOpenWebCam() {
        webCamButton.setDisable(true);
        log.info("Start WebCamLauncher");
        new WebCamLauncher(webCam -> {
            log.info("webCam available");
            webCamWindow = new WebCamWindow(webCam.getViewSize().width, webCam.getViewSize().height)
                    .onClose(() -> {
                        webCamButton.setDisable(false);
                        qrCodeReader.close();
                    });
            webCamWindow.show();

            qrCodeReader = new QrCodeReader(webCam, webCamWindow.getImageView(), qrCode -> {
                log.info("Qr code available");
                webCamWindow.hide();
                webCamButton.setDisable(false);
                reset();
                tokenInputTextField.setText(qrCode);
                updateMarketAlertFields();
                updatePriceAlertFields();
            });
        }, throwable -> {
            if (throwable instanceof NoWebCamFoundException) {
                new Popup<>().warning(Res.get("account.notifications.noWebCamFound.warning")).show();
                webCamButton.setDisable(false);
                onNoWebCam();
            } else {
                log.error(throwable.toString());
                new Popup<>().error(throwable.toString()).show();
            }
        });
    }

    private void onNoWebCam() {
        setPairingTokenFieldsVisible();
        noWebCamButton.setManaged(false);
        noWebCamButton.setVisible(false);
    }

    private void onErase() {
        try {
            boolean success = mobileNotificationService.sendEraseMessage();
            if (!success)
                log.warn("Erase message sending did not succeed");
            reset();
        } catch (Exception e) {
            new Popup<>().error(e.toString()).show();
        }
    }

    private void onSendTestMsg() {
        MobileMessage message = null;
        List<MobileMessage> messages = null;
        switch (testMsgCounter) {
            case 0:
                message = MyOfferTakenEvents.getTestMsg();
                break;
            case 1:
                messages = TradeEvents.getTestMessages();
                break;
            case 2:
                message = DisputeMsgEvents.getTestMsg();
                break;
            case 3:
                message = PriceAlert.getTestMsg();
                break;
            case 4:
            default:
                message = MarketAlerts.getTestMsg();
                break;
        }
        testMsgCounter++;
        if (testMsgCounter > 4)
            testMsgCounter = 0;

        try {
            if (message != null) {
                mobileNotificationService.sendMessage(message, useSoundCheckBox.isSelected());
            } else if (messages != null) {
                messages.forEach(msg -> {
                    try {
                        mobileNotificationService.sendMessage(msg, useSoundCheckBox.isSelected());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            new Popup<>().error(e.toString()).show();
        }
    }


    // Market alerts
    private void onPaymentAccountSelected() {
        marketAlertTriggerInputTextField.clear();
        marketAlertTriggerInputTextField.resetValidation();
        offerTypeRadioButtonsToggleGroup.selectToggle(null);
        updateMarketAlertFields();
    }

    private void onAddMarketAlert() {
        PaymentAccount paymentAccount = paymentAccountsComboBox.getSelectionModel().getSelectedItem();
        double percentAsDouble = formatter.parsePercentStringToDouble(marketAlertTriggerInputTextField.getText());
        int triggerValue = (int) Math.round(percentAsDouble * 10000);
        boolean isBuyOffer = offerTypeRadioButtonsToggleGroup.getSelectedToggle() == buyOffersRadioButton;
        MarketAlertFilter marketAlertFilter = new MarketAlertFilter(paymentAccount, triggerValue, isBuyOffer);
        marketAlerts.addMarketAlertFilter(marketAlertFilter);
        paymentAccountsComboBox.getSelectionModel().clearSelection();
    }

    private void onManageMarketAlerts() {
        new ManageMarketAlertsWindow(marketAlerts, formatter)
                .onClose(this::updateMarketAlertFields)
                .show();
    }

    // Price alerts
    private void onSelectedTradeCurrency() {
        TradeCurrency selectedItem = currencyComboBox.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            selectedPriceAlertTradeCurrency = selectedItem.getCode();
            boolean isCryptoCurrency = CurrencyUtil.isCryptoCurrency(selectedPriceAlertTradeCurrency);
            priceAlertHighInputTextField.setValidator(isCryptoCurrency ? new AltcoinValidator() : new FiatPriceValidator());
            priceAlertLowInputTextField.setValidator(isCryptoCurrency ? new AltcoinValidator() : new FiatPriceValidator());
        } else {
            selectedPriceAlertTradeCurrency = null;
        }
        updatePriceAlertFields();
    }

    private void onSetPriceAlert() {
        if (arePriceAlertInputsValid()) {
            String code = selectedPriceAlertTradeCurrency;
            long high = getPriceAsLong(priceAlertHighInputTextField);
            long low = getPriceAsLong(priceAlertLowInputTextField);
            if (high > 0 && low > 0)
                user.setPriceAlertFilter(new PriceAlertFilter(code, high, low));
            updatePriceAlertFields();
        }
    }

    private void onRemovePriceAlert() {
        user.removePriceAlertFilter();
        fillPriceAlertFields();
        updatePriceAlertFields();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createSetupFields() {
        FormBuilder.addTitledGroupBg(root, gridRow, 4, Res.get("account.notifications.setup.title"));
        downloadButton = FormBuilder.addLabelButton(root, gridRow,
                Res.getWithCol("account.notifications.download.label"), Res.get("account.notifications.download.button"),
                Layout.FIRST_ROW_DISTANCE).second;

        Tuple3<Label, Button, Button> tuple = FormBuilder.addLabel2Buttons(root, ++gridRow,
                Res.getWithCol("account.notifications.webcam.label"),
                Res.get("account.notifications.webcam.button"), Res.get("account.notifications.noWebcam.button"), 0);
        webCamButton = tuple.second;
        noWebCamButton = tuple.third;

        Tuple2<Label, InputTextField> tuple2 = FormBuilder.addLabelInputTextField(root, ++gridRow,
                Res.get("account.notifications.email.label"));
        tokenInputLabel = tuple2.first;
        tokenInputTextField = tuple2.second;
        tokenInputTextField.setPromptText(Res.get("account.notifications.email.prompt"));
        tokenInputTextFieldListener = (observable, oldValue, newValue) -> {
            applyKeyAndToken(newValue);
        };
        tokenInputLabel.setManaged(false);
        tokenInputLabel.setVisible(false);
        tokenInputTextField.setManaged(false);
        tokenInputTextField.setVisible(false);

        /*testMsgButton = FormBuilder.addLabelButton(root, ++gridRow, Res.get("account.notifications.testMsg.label"),
                Res.get("account.notifications.testMsg.title")).second;
        testMsgButton.setDefaultButton(false);*/

        eraseButton = FormBuilder.addLabelButton(root, ++gridRow,
                Res.get("account.notifications.erase.label"),
                Res.get("account.notifications.erase.title")).second;
        eraseButton.setId("notification-erase-button");
    }

    private void createSettingsFields() {
        FormBuilder.addTitledGroupBg(root, ++gridRow, 4,
                Res.get("account.notifications.settings.title"),
                Layout.GROUP_DISTANCE);

        useSoundCheckBox = FormBuilder.addLabelCheckBox(root, gridRow,
                Res.get("account.notifications.useSound.label"),
                "",
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        useSoundCheckBox.setSelected(preferences.isUseSoundForMobileNotifications());
        useSoundCheckBoxListener = (observable, oldValue, newValue) -> {
            mobileNotificationService.getUseSoundProperty().set(newValue);
            preferences.setUseSoundForMobileNotifications(newValue);
        };

        tradeCheckBox = FormBuilder.addLabelCheckBox(root, ++gridRow,
                Res.get("account.notifications.trade.label")).second;
        tradeCheckBox.setSelected(preferences.isUseTradeNotifications());
        tradeCheckBoxListener = (observable, oldValue, newValue) -> {
            mobileNotificationService.getUseTradeNotificationsProperty().set(newValue);
            preferences.setUseTradeNotifications(newValue);
        };

        marketCheckBox = FormBuilder.addLabelCheckBox(root, ++gridRow,
                Res.get("account.notifications.market.label")).second;
        marketCheckBox.setSelected(preferences.isUseMarketNotifications());
        marketCheckBoxListener = (observable, oldValue, newValue) -> {
            mobileNotificationService.getUseMarketNotificationsProperty().set(newValue);
            preferences.setUseMarketNotifications(newValue);
            updateMarketAlertFields();
        };
        priceCheckBox = FormBuilder.addLabelCheckBox(root, ++gridRow,
                Res.get("account.notifications.price.label")).second;
        priceCheckBox.setSelected(preferences.isUsePriceNotifications());
        priceCheckBoxListener = (observable, oldValue, newValue) -> {
            mobileNotificationService.getUsePriceNotificationsProperty().set(newValue);
            preferences.setUsePriceNotifications(newValue);
            updatePriceAlertFields();
        };
    }

    private void createMarketAlertFields() {
        FormBuilder.addTitledGroupBg(root, ++gridRow, 3, Res.get("account.notifications.marketAlert.title"),
                Layout.GROUP_DISTANCE);
        paymentAccountsComboBox = FormBuilder.<PaymentAccount>addLabelComboBox(root, gridRow,
                Res.getWithCol("account.notifications.marketAlert.selectPaymentAccount"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        paymentAccountsComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(PaymentAccount paymentAccount) {
                return paymentAccount.getAccountName();
            }

            @Override
            public PaymentAccount fromString(String string) {
                return null;
            }
        });

        offerTypeRadioButtonsToggleGroup = new ToggleGroup();
        Tuple3<Label, RadioButton, RadioButton> tuple = FormBuilder.addLabelRadioButtonRadioButton(root, ++gridRow,
                offerTypeRadioButtonsToggleGroup, Res.getWithCol("account.notifications.marketAlert.offerType.label"),
                Res.get("account.notifications.marketAlert.offerType.buy"),
                Res.get("account.notifications.marketAlert.offerType.sell"));
        buyOffersRadioButton = tuple.second;
        sellOffersRadioButton = tuple.third;
        offerTypeListener = (observable, oldValue, newValue) -> {
            marketAlertTriggerInputTextField.clear();
            marketAlertTriggerInputTextField.resetValidation();
            updateMarketAlertFields();
        };
        InfoInputTextField infoInputTextField = FormBuilder.addLabelInfoInputTextField(root, ++gridRow,
                Res.getWithCol("account.notifications.marketAlert.trigger")).second;
        marketAlertTriggerInputTextField = infoInputTextField.getInputTextField();
        marketAlertTriggerInputTextField.setPromptText(Res.get("account.notifications.marketAlert.trigger.prompt"));
        PercentageNumberValidator validator = new PercentageNumberValidator();
        validator.setMaxValue(50D);
        marketAlertTriggerInputTextField.setValidator(validator);
        infoInputTextField.setContentForInfoPopOver(createMarketAlertPriceInfoPopupLabel(Res.get("account.notifications.marketAlert.trigger.info")));
        infoInputTextField.setIconsRightAligned();

        marketAlertTriggerListener = (observable, oldValue, newValue) -> {
            updateMarketAlertFields();
        };
        marketAlertTriggerFocusListener = (observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                try {
                    double percentAsDouble = formatter.parsePercentStringToDouble(marketAlertTriggerInputTextField.getText()) * 100;
                    marketAlertTriggerInputTextField.setText(formatter.formatRoundedDoubleWithPrecision(percentAsDouble, 2) + "%");
                } catch (Throwable ignore) {
                }

                updateMarketAlertFields();
            }
        };

        Tuple2<Button, Button> buttonTuple = FormBuilder.add2ButtonsAfterGroup(root, ++gridRow,
                Res.get("account.notifications.marketAlert.addButton"),
                Res.get("account.notifications.marketAlert.manageAlertsButton"));
        addMarketAlertButton = buttonTuple.first;
        manageAlertsButton = buttonTuple.second;
    }

    private void createPriceAlertFields() {
        FormBuilder.addTitledGroupBg(root, ++gridRow, 3,
                Res.get("account.notifications.priceAlert.title"), 20);
        currencyComboBox = FormBuilder.<TradeCurrency>addLabelComboBox(root, gridRow,
                Res.getWithCol("list.currency.select"), 40).second;
        currencyComboBox.setPromptText(Res.get("list.currency.select"));
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency currency) {
                return currency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String string) {
                return null;
            }
        });

        priceAlertHighInputTextField = FormBuilder.addLabelInputTextField(root, ++gridRow,
                Res.getWithCol("account.notifications.priceAlert.high.label")).second;
        priceAlertHighListener = (observable, oldValue, newValue) -> {
            long priceAlertHighTextFieldValue = getPriceAsLong(priceAlertHighInputTextField);
            long priceAlertLowTextFieldValue = getPriceAsLong(priceAlertLowInputTextField);
            if (priceAlertLowTextFieldValue != 0 && priceAlertHighTextFieldValue != 0) {
                if (priceAlertHighTextFieldValue > priceAlertLowTextFieldValue)
                    updatePriceAlertFields();
            }
        };
        priceAlertHighFocusListener = (observable, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                applyPriceFormatting(priceAlertHighInputTextField);
                long priceAlertHighTextFieldValue = getPriceAsLong(priceAlertHighInputTextField);
                long priceAlertLowTextFieldValue = getPriceAsLong(priceAlertLowInputTextField);
                if (priceAlertLowTextFieldValue != 0 && priceAlertHighTextFieldValue != 0) {
                    if (priceAlertHighTextFieldValue <= priceAlertLowTextFieldValue) {
                        new Popup<>().warning(Res.get("account.notifications.priceAlert.warning.highPriceTooLow")).show();
                        UserThread.execute(() -> {
                            priceAlertHighInputTextField.clear();
                            updatePriceAlertFields();
                        });
                    }
                }
            }
        };
        priceAlertLowInputTextField = FormBuilder.addLabelInputTextField(root, ++gridRow,
                Res.getWithCol("account.notifications.priceAlert.low.label")).second;
        priceAlertLowListener = (observable, oldValue, newValue) -> {
            long priceAlertHighTextFieldValue = getPriceAsLong(priceAlertHighInputTextField);
            long priceAlertLowTextFieldValue = getPriceAsLong(priceAlertLowInputTextField);
            if (priceAlertLowTextFieldValue != 0 && priceAlertHighTextFieldValue != 0) {
                if (priceAlertLowTextFieldValue < priceAlertHighTextFieldValue)
                    updatePriceAlertFields();
            }
        };
        priceAlertLowFocusListener = (observable, oldValue, newValue) -> {
            applyPriceFormatting(priceAlertLowInputTextField);
            long priceAlertHighTextFieldValue = getPriceAsLong(priceAlertHighInputTextField);
            long priceAlertLowTextFieldValue = getPriceAsLong(priceAlertLowInputTextField);
            if (priceAlertLowTextFieldValue != 0 && priceAlertHighTextFieldValue != 0) {
                if (priceAlertLowTextFieldValue >= priceAlertHighTextFieldValue) {
                    new Popup<>().warning(Res.get("account.notifications.priceAlert.warning.lowerPriceTooHigh")).show();
                    UserThread.execute(() -> {
                        priceAlertLowInputTextField.clear();
                        updatePriceAlertFields();
                    });
                }
            }
        };

        Tuple2<Button, Button> tuple = FormBuilder.add2ButtonsAfterGroup(root, ++gridRow,
                Res.get("account.notifications.priceAlert.setButton"),
                Res.get("account.notifications.priceAlert.removeButton"));
        setPriceAlertButton = tuple.first;
        removePriceAlertButton = tuple.second;

        // When we get a price update an existing price alert might get removed.
        // We get updated the view at each price update so we get aware of the removed PriceAlertFilter in the
        // fillPriceAlertFields method. To be sure that we called after the PriceAlertFilter has been removed we delay
        // to the next frame. The priceFeedServiceListener in the  mobileNotificationService might get called before
        // our listener here.
        priceFeedServiceListener = (observable, oldValue, newValue) -> {
            UserThread.execute(() -> {
                fillPriceAlertFields();
                updatePriceAlertFields();
            });
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Setup/Settings
    private void applyKeyAndToken(String keyAndToken) {
        if (keyAndToken != null && !keyAndToken.isEmpty()) {
            boolean isValid = mobileNotificationService.applyKeyAndToken(keyAndToken);
            if (isValid) {
                setDisableForSetupFields(false);
                setPairingTokenFieldsVisible();
                updateMarketAlertFields();
                updatePriceAlertFields();
            }
        }
    }

    private void setDisableForSetupFields(boolean disable) {
        // testMsgButton.setDisable(disable);
        eraseButton.setDisable(disable);

        useSoundCheckBox.setDisable(disable);
        tradeCheckBox.setDisable(disable);
        marketCheckBox.setDisable(disable);
        priceCheckBox.setDisable(disable);
    }

    private void setPairingTokenFieldsVisible() {
        tokenInputLabel.setManaged(true);
        tokenInputLabel.setVisible(true);
        tokenInputTextField.setManaged(true);
        tokenInputTextField.setVisible(true);
    }

    private void reset() {
        mobileNotificationService.reset();
        tokenInputTextField.clear();
        setDisableForSetupFields(true);
        eraseButton.setDisable(true);
        //testMsgButton.setDisable(true);
        onRemovePriceAlert();
        new ArrayList<>(marketAlerts.getMarketAlertFilters()).forEach(marketAlerts::removeMarketAlertFilter);

    }


    // Market alerts
    private Label createMarketAlertPriceInfoPopupLabel(String text) {
        final Label label = new Label(text);
        label.setPrefWidth(300);
        label.setWrapText(true);
        label.setPadding(new Insets(10));
        return label;
    }

    private void updateMarketAlertFields() {
        boolean setupConfirmationSent = mobileNotificationService.isSetupConfirmationSent();
        boolean selected = marketCheckBox.isSelected();
        boolean disabled = !selected || !setupConfirmationSent;
        boolean isPaymentAccountSelected = paymentAccountsComboBox.getSelectionModel().getSelectedItem() != null;
        boolean isOfferTypeSelected = offerTypeRadioButtonsToggleGroup.getSelectedToggle() != null;
        boolean isTriggerValueValid = marketAlertTriggerInputTextField.getValidator() != null &&
                marketAlertTriggerInputTextField.getValidator().validate(marketAlertTriggerInputTextField.getText()).isValid;
        boolean allInputsValid = isPaymentAccountSelected && isOfferTypeSelected && isTriggerValueValid;

        paymentAccountsComboBox.setDisable(disabled);
        buyOffersRadioButton.setDisable(disabled);
        sellOffersRadioButton.setDisable(disabled);
        marketAlertTriggerInputTextField.setDisable(disabled);
        addMarketAlertButton.setDisable(disabled || !allInputsValid);
        manageAlertsButton.setDisable(disabled || marketAlerts.getMarketAlertFilters().isEmpty());
    }


    // PriceAlert
    private void fillPriceAlertFields() {
        PriceAlertFilter priceAlertFilter = user.getPriceAlertFilter();
        if (priceAlertFilter != null) {
            String currencyCode = priceAlertFilter.getCurrencyCode();
            Optional<TradeCurrency> optionalTradeCurrency = CurrencyUtil.getTradeCurrency(currencyCode);
            if (optionalTradeCurrency.isPresent()) {
                currencyComboBox.getSelectionModel().select(optionalTradeCurrency.get());
                onSelectedTradeCurrency();

                priceAlertHighInputTextField.setText(formatter.formatMarketPrice(priceAlertFilter.getHigh() / 10000d, currencyCode));
                priceAlertLowInputTextField.setText(formatter.formatMarketPrice(priceAlertFilter.getLow() / 10000d, currencyCode));
            } else {
                currencyComboBox.getSelectionModel().clearSelection();
            }
        } else {
            priceAlertHighInputTextField.clear();
            priceAlertLowInputTextField.clear();
            priceAlertHighInputTextField.resetValidation();
            priceAlertLowInputTextField.resetValidation();
            currencyComboBox.getSelectionModel().clearSelection();
        }
    }

    private void updatePriceAlertFields() {
        boolean setupConfirmationSent = mobileNotificationService.isSetupConfirmationSent();
        boolean selected = priceCheckBox.isSelected();
        boolean disable = !setupConfirmationSent ||
                !selected;
        priceAlertHighInputTextField.setDisable(selectedPriceAlertTradeCurrency == null || disable);
        priceAlertLowInputTextField.setDisable(selectedPriceAlertTradeCurrency == null || disable);
        PriceAlertFilter priceAlertFilter = user.getPriceAlertFilter();
        boolean valueSameAsFilter = false;
        if (priceAlertFilter != null &&
                selectedPriceAlertTradeCurrency != null) {
            valueSameAsFilter = priceAlertFilter.getHigh() == getPriceAsLong(priceAlertHighInputTextField) &&
                    priceAlertFilter.getLow() == getPriceAsLong(priceAlertLowInputTextField) &&
                    priceAlertFilter.getCurrencyCode().equals(selectedPriceAlertTradeCurrency);
        }
        setPriceAlertButton.setDisable(disable || !arePriceAlertInputsValid() || valueSameAsFilter);
        removePriceAlertButton.setDisable(disable || priceAlertFilter == null);
        currencyComboBox.setDisable(disable);
    }

    private boolean arePriceAlertInputsValid() {
        return selectedPriceAlertTradeCurrency != null &&
                isPriceInputValid(priceAlertHighInputTextField).isValid &&
                isPriceInputValid(priceAlertLowInputTextField).isValid;
    }

    private InputValidator.ValidationResult isPriceInputValid(InputTextField inputTextField) {
        InputValidator validator = inputTextField.getValidator();
        if (validator != null)
            return validator.validate(inputTextField.getText());
        else
            return new InputValidator.ValidationResult(false);
    }

    private long getPriceAsLong(InputTextField inputTextField) {
        try {
            String inputValue = inputTextField.getText();
            if (inputValue != null && !inputValue.isEmpty() && selectedPriceAlertTradeCurrency != null) {
                double priceAsDouble = formatter.parseNumberStringToDouble(inputValue);
                String currencyCode = selectedPriceAlertTradeCurrency;
                int precision = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                        Altcoin.SMALLEST_UNIT_EXPONENT : 2;
                // We want to use the converted value not the inout value as we apply the converted value at focus out.
                // E.g. if input is 5555.5555 it will be rounded to  5555.55 and we use that as the value for comparing
                // low and high price...
                String stringValue = formatter.formatRoundedDoubleWithPrecision(priceAsDouble, precision);
                return formatter.parsePriceStringToLong(currencyCode, stringValue, precision);
            } else {
                return 0;
            }
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private void applyPriceFormatting(InputTextField inputTextField) {
        try {
            String inputValue = inputTextField.getText();
            if (inputValue != null && !inputValue.isEmpty() && selectedPriceAlertTradeCurrency != null) {
                double priceAsDouble = formatter.parseNumberStringToDouble(inputValue);
                String currencyCode = selectedPriceAlertTradeCurrency;
                int precision = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                        Altcoin.SMALLEST_UNIT_EXPONENT : 2;
                String stringValue = formatter.formatRoundedDoubleWithPrecision(priceAsDouble, precision);
                inputTextField.setText(stringValue);
            }
        } catch (Throwable ignore) {
            updatePriceAlertFields();
        }
    }
}


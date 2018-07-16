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
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.WebCamWindow;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.AltcoinValidator;
import bisq.desktop.util.validation.FiatPriceValidator;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Price;
import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.notifications.MobileNotificationValidator;
import bisq.core.notifications.alerts.DisputeMsgEvents;
import bisq.core.notifications.alerts.MyOfferTakenEvents;
import bisq.core.notifications.alerts.TradeEvents;
import bisq.core.notifications.alerts.market.MarketAlerts;
import bisq.core.notifications.alerts.price.PriceAlert;
import bisq.core.notifications.alerts.price.PriceAlertFilter;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@FxmlView
public class NotificationsView extends ActivatableView<GridPane, Void> {
    private final Preferences preferences;
    private final User user;
    private final PriceFeedService priceFeedService;
    private final MobileNotificationValidator mobileNotificationValidator;
    private final MobileNotificationService mobileNotificationService;
    private final BSFormatter formatter;

    private WebCamWindow webCamWindow;
    private QrCodeReader qrCodeReader;

    private TextField tokenInputTextField;
    private Label tokenInputLabel;
    private InputTextField priceAlertHigh, priceAlertLow;
    private CheckBox useSoundCheckBox, tradeCheckBox, marketCheckBox, priceCheckBox;
    private ComboBox<TradeCurrency> currencyComboBox;
    private Button webCamButton, noWebCamButton, eraseButton, testMsgButton, setPriceAlertButton,
            removePriceAlertButton;

    private ChangeListener<Boolean> useSoundCheckBoxListener, tradeCheckBoxListener, marketCheckBoxListener,
            priceCheckBoxListener;
    private ChangeListener<String> tokenInputTextFieldListener, priceAlertHighListener, priceAlertLowListener;
    private ChangeListener<Number> priceFeedServiceListener;

    private TradeCurrency selectedPriceAlertTradeCurrency;
    private int gridRow = 0;
    private int testMsgCounter = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private NotificationsView(Preferences preferences,
                              User user,
                              PriceFeedService priceFeedService,
                              MobileNotificationValidator mobileNotificationValidator,
                              MobileNotificationService mobileNotificationService,
                              BSFormatter formatter) {
        super();
        this.preferences = preferences;
        this.user = user;
        this.priceFeedService = priceFeedService;
        this.mobileNotificationValidator = mobileNotificationValidator;
        this.mobileNotificationService = mobileNotificationService;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        createSetupFields();
        createSettingsFields();
        createPriceAlertFields();
    }

    @Override
    protected void activate() {
        tokenInputTextField.textProperty().addListener(tokenInputTextFieldListener);

        useSoundCheckBox.selectedProperty().addListener(useSoundCheckBoxListener);
        tradeCheckBox.selectedProperty().addListener(tradeCheckBoxListener);
        marketCheckBox.selectedProperty().addListener(marketCheckBoxListener);
        priceCheckBox.selectedProperty().addListener(priceCheckBoxListener);

        if (preferences.getPhoneKeyAndToken() != null) {
            tokenInputTextField.setText(preferences.getPhoneKeyAndToken());
            setPairingTokenFieldsVisible();
        } else {
            eraseButton.setDisable(true);
            testMsgButton.setDisable(true);
        }
        updateDisableState(!mobileNotificationService.isSetupConfirmationSent());

        // priceAlert
        priceAlertHigh.textProperty().addListener(priceAlertHighListener);
        priceAlertLow.textProperty().addListener(priceAlertLowListener);
        currencyComboBox.setOnAction(e -> setSelectedPriceAlertTradeCurrency());
        setPriceAlertButton.setOnAction(e -> {
            if (arePriceAlertInputsValid()) {
                String code = selectedPriceAlertTradeCurrency.getCode();
                long high = getPriceAlertHighTextFieldValue();
                long low = getPriceAlertLowTextFieldValue();
                user.setPriceAlertFilter(new PriceAlertFilter(code, high, low));
                updatePriceAlertInputs();
            }
        });
        removePriceAlertButton.setOnAction(e -> removeAlert());

        priceFeedService.updateCounterProperty().addListener(priceFeedServiceListener);

        currencyComboBox.setItems(preferences.getTradeCurrenciesAsObservable());
        fillPriceAlertFields();
        updatePriceAlertInputs();
    }

    @Override
    protected void deactivate() {
        tokenInputTextField.textProperty().removeListener(tokenInputTextFieldListener);

        useSoundCheckBox.selectedProperty().removeListener(useSoundCheckBoxListener);
        tradeCheckBox.selectedProperty().removeListener(tradeCheckBoxListener);
        marketCheckBox.selectedProperty().removeListener(marketCheckBoxListener);
        priceCheckBox.selectedProperty().removeListener(priceCheckBoxListener);

        priceAlertHigh.textProperty().removeListener(priceAlertHighListener);
        priceAlertLow.textProperty().removeListener(priceAlertLowListener);
        priceFeedService.updateCounterProperty().removeListener(priceFeedServiceListener);
        currencyComboBox.setOnAction(null);
        setPriceAlertButton.setOnAction(null);
        removePriceAlertButton.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create views
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createSetupFields() {
        FormBuilder.addTitledGroupBg(root, gridRow, 4, Res.get("account.notifications.setup.title"));
        Tuple3<Label, Button, Button> tuple = FormBuilder.addLabel2Buttons(root, gridRow,
                Res.get("account.notifications.webcam.label"),
                Res.get("account.notifications.webcam.button"), Res.get("account.notifications.noWebcam.button"),
                Layout.FIRST_ROW_DISTANCE);
        webCamButton = tuple.second;
        webCamButton.setDefaultButton(true);
        webCamButton.setOnAction((event) -> {
            webCamButton.setDisable(true);

            new WebCamLauncher(webCam -> {
                webCamWindow = new WebCamWindow(webCam.getViewSize().width, webCam.getViewSize().height)
                        .onClose(() -> {
                            webCamButton.setDisable(false);
                            qrCodeReader.close();
                        });
                webCamWindow.show();

                qrCodeReader = new QrCodeReader(webCam, webCamWindow.getImageView(), qrCode -> {
                    webCamWindow.hide();
                    webCamButton.setDisable(false);
                    reset();
                    tokenInputTextField.setText(qrCode);
                    updatePriceAlertInputs();

                    UserThread.runAfter(() -> {
                        if (!mobileNotificationService.getMobileModel().isContentAvailable())
                            new Popup<>()
                                    .warning(Res.get("account.notifications.isContentAvailable.warning",
                                            mobileNotificationService.getMobileModel().getDescriptor()))
                                    .show();
                    }, 600, TimeUnit.MILLISECONDS);
                });
            });
        });

        noWebCamButton = tuple.third;
        noWebCamButton.setOnAction(e -> {
            setPairingTokenFieldsVisible();
            noWebCamButton.setManaged(false);
            noWebCamButton.setVisible(false);
        });

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

        testMsgButton = FormBuilder.addLabelButton(root, ++gridRow, Res.get("account.notifications.testMsg.label"),
                Res.get("account.notifications.testMsg.title")).second;
        testMsgButton.setDefaultButton(false);
        testMsgButton.setOnAction(event -> {
            MobileMessage message = null;
            List<MobileMessage> messages = null;
            switch (testMsgCounter) {
                case 0:
                    message = MyOfferTakenEvents.getTestMsg();
                    break;
                case 1:
                    messages = TradeEvents.getTestMsgs();
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
        });

        eraseButton = FormBuilder.addLabelButton(root, ++gridRow,
                Res.get("account.notifications.erase.label"),
                Res.get("account.notifications.erase.title")).second;
        eraseButton.setId("notification-erase-button");
        eraseButton.setOnAction((event) -> {
            try {
                mobileNotificationService.sendEraseMessage();
                reset();
            } catch (Exception e) {
                new Popup<>().error(e.toString()).show();
            }
        });
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
        };
        priceCheckBox = FormBuilder.addLabelCheckBox(root, ++gridRow,
                Res.get("account.notifications.price.label")).second;
        priceCheckBox.setSelected(preferences.isUsePriceNotifications());
        priceCheckBoxListener = (observable, oldValue, newValue) -> {
            mobileNotificationService.getUsePriceNotificationsProperty().set(newValue);
            preferences.setUsePriceNotifications(newValue);
            updatePriceAlertInputs();
        };
    }

    private void createPriceAlertFields() {
        FormBuilder.addTitledGroupBg(root, ++gridRow, 3, Res.get("account.notifications.priceAlert.title"),
                Layout.GROUP_DISTANCE);
        currencyComboBox = FormBuilder.addLabelComboBox(root, gridRow, Res.get("list.currency.select"), Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
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

        priceAlertHigh = FormBuilder.addLabelInputTextField(root, ++gridRow,
                Res.get("account.notifications.priceAlert.high.label")).second;
        priceAlertHighListener = (observable, oldValue, newValue) -> updatePriceAlertInputs();

        priceAlertLow = FormBuilder.addLabelInputTextField(root, ++gridRow,
                Res.get("account.notifications.priceAlert.low.label")).second;
        priceAlertLowListener = (observable, oldValue, newValue) -> updatePriceAlertInputs();

        Tuple2<Button, Button> tuple = FormBuilder.add2ButtonsAfterGroup(root, ++gridRow,
                Res.get("account.notifications.priceAlert.setButton"),
                Res.get("account.notifications.priceAlert.clearButton"));
        setPriceAlertButton = tuple.first;
        removePriceAlertButton = tuple.second;

        priceFeedServiceListener = (observable, oldValue, newValue) -> {
            fillPriceAlertFields();
            updatePriceAlertInputs();
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setup/Settings
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyKeyAndToken(String keyAndToken) {
        mobileNotificationService.applyKeyAndToken(keyAndToken);
        if (mobileNotificationValidator.isValid(keyAndToken)) {
            updateDisableState(false);
            setPairingTokenFieldsVisible();
        }
    }

    private void updateDisableState(boolean disable) {
        testMsgButton.setDisable(disable);
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
        updateDisableState(true);
        eraseButton.setDisable(true);
        testMsgButton.setDisable(true);
        removeAlert();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PriceAlert
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillPriceAlertFields() {
        PriceAlertFilter priceAlertFilter = user.getPriceAlertFilter();
        if (priceAlertFilter != null) {
            String currencyCode = priceAlertFilter.getCurrencyCode();
            Optional<TradeCurrency> optionalTradeCurrency = CurrencyUtil.getTradeCurrency(currencyCode);
            if (optionalTradeCurrency.isPresent()) {
                currencyComboBox.getSelectionModel().select(optionalTradeCurrency.get());
                setSelectedPriceAlertTradeCurrency();

                priceAlertHigh.setText(formatter.formatMarketPrice(priceAlertFilter.getHigh() / 10000d, currencyCode));
                priceAlertLow.setText(formatter.formatMarketPrice(priceAlertFilter.getLow() / 10000d, currencyCode));
            } else {
                currencyComboBox.getSelectionModel().clearSelection();
            }
        } else {
            priceAlertHigh.clear();
            priceAlertLow.clear();
            currencyComboBox.getSelectionModel().clearSelection();
        }
    }

    private void updatePriceAlertInputs() {
        boolean setupConfirmationSent = mobileNotificationService.isSetupConfirmationSent();
        boolean selected = priceCheckBox.isSelected();
        boolean disable = !setupConfirmationSent ||
                !selected;
        priceAlertHigh.setDisable(selectedPriceAlertTradeCurrency == null || disable);
        priceAlertLow.setDisable(selectedPriceAlertTradeCurrency == null || disable);
        PriceAlertFilter priceAlertFilter = user.getPriceAlertFilter();
        boolean valueSameAsFilter = false;
        if (priceAlertFilter != null &&
                selectedPriceAlertTradeCurrency != null) {
            valueSameAsFilter = priceAlertFilter.getHigh() == getPriceAlertHighTextFieldValue() &&
                    priceAlertFilter.getLow() == getPriceAlertLowTextFieldValue() &&
                    priceAlertFilter.getCurrencyCode().equals(selectedPriceAlertTradeCurrency.getCode());
        }
        setPriceAlertButton.setDisable(disable || !arePriceAlertInputsValid() || valueSameAsFilter);
        removePriceAlertButton.setDisable(disable || priceAlertFilter == null);
        currencyComboBox.setDisable(disable);
    }

    private void removeAlert() {
        user.removePriceAlertFilter();
        fillPriceAlertFields();
        updatePriceAlertInputs();
    }

    private void setSelectedPriceAlertTradeCurrency() {
        selectedPriceAlertTradeCurrency = currencyComboBox.getSelectionModel().getSelectedItem();
        if (selectedPriceAlertTradeCurrency != null) {
            boolean isCryptoCurrency = CurrencyUtil.isCryptoCurrency(selectedPriceAlertTradeCurrency.getCode());
            priceAlertHigh.setValidator(isCryptoCurrency ? new AltcoinValidator() : new FiatPriceValidator());
            priceAlertLow.setValidator(isCryptoCurrency ? new AltcoinValidator() : new FiatPriceValidator());
        }
        updatePriceAlertInputs();
    }

    private boolean arePriceAlertInputsValid() {
        return selectedPriceAlertTradeCurrency != null &&
                isPriceInputValid(priceAlertHigh).isValid &&
                isPriceInputValid(priceAlertLow).isValid;
    }

    private InputValidator.ValidationResult isPriceInputValid(InputTextField inputTextField) {
        InputValidator validator = inputTextField.getValidator();
        if (validator != null)
            return validator.validate(inputTextField.getText());
        else
            return new InputValidator.ValidationResult(false);
    }

    private long getPriceAlertHighTextFieldValue() {
        String text = priceAlertHigh.getText();
        return text.isEmpty() ? 0 : Price.parse(selectedPriceAlertTradeCurrency.getCode(), text).getValue();
    }

    private long getPriceAlertLowTextFieldValue() {
        String text = priceAlertLow.getText();
        return text.isEmpty() ? 0 : Price.parse(selectedPriceAlertTradeCurrency.getCode(), text).getValue();
    }

}


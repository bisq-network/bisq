/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.offer.createoffer;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Tuple3;
import io.bisq.common.util.Utilities;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.user.Preferences;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.*;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.account.AccountView;
import io.bisq.gui.main.account.content.arbitratorselection.ArbitratorSelectionView;
import io.bisq.gui.main.account.content.fiataccounts.FiatAccountsView;
import io.bisq.gui.main.account.settings.AccountSettingsView;
import io.bisq.gui.main.funds.FundsView;
import io.bisq.gui.main.funds.withdrawal.WithdrawalView;
import io.bisq.gui.main.offer.OfferView;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.overlays.windows.OfferDetailsWindow;
import io.bisq.gui.main.overlays.windows.QRCodeWindow;
import io.bisq.gui.main.portfolio.PortfolioView;
import io.bisq.gui.main.portfolio.openoffer.OpenOffersView;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.gui.util.Layout;
import io.bisq.protobuffer.payload.payment.PaymentMethod;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Window;
import javafx.util.StringConverter;
import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;
import org.bitcoinj.uri.BitcoinURI;
import org.controlsfx.control.PopOver;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.bisq.gui.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createStringBinding;

@FxmlView
public class CreateOfferView extends ActivatableViewAndModel<AnchorPane, CreateOfferViewModel> {

    private final Navigation navigation;
    private final OfferDetailsWindow offerDetailsWindow;

    private ScrollPane scrollPane;
    private GridPane gridPane;
    private ImageView imageView;
    private AddressTextField addressTextField;
    private BalanceTextField balanceTextField;
    private TitledGroupBg payFundsPane;
    private BusyAnimation waitingForFundsBusyAnimation;
    private Button nextButton, cancelButton1, cancelButton2, fundFromSavingsWalletButton, fundFromExternalWalletButton, placeOfferButton;
    private InputTextField amountTextField, minAmountTextField, fixedPriceTextField, marketBasedPriceTextField, volumeTextField;
    private TextField currencyTextField;
    private Label directionLabel, amountDescriptionLabel, addressLabel, balanceLabel,
            totalToPayLabel, totalToPayInfoIconLabel, amountBtcLabel, priceCurrencyLabel,
            volumeCurrencyLabel, minAmountBtcLabel, priceDescriptionLabel,
            volumeDescriptionLabel, currencyTextFieldLabel,
            currencyComboBoxLabel, waitingForFundsLabel, marketBasedPriceLabel;
    private TextFieldWithCopyIcon totalToPayTextField;
    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private ComboBox<TradeCurrency> currencyComboBox;
    private PopOver totalToPayInfoPopover;
    private ToggleButton fixedPriceButton, useMarketBasedPriceButton;

    private OfferView.CloseHandler closeHandler;

    private ChangeListener<Boolean> amountFocusedListener;
    private ChangeListener<Boolean> minAmountFocusedListener;
    private ChangeListener<Boolean> priceFocusedListener, priceAsPercentageFocusedListener;
    private ChangeListener<Boolean> volumeFocusedListener;
    private ChangeListener<Boolean> buyerSecurityDepositFocusedListener;

    private ChangeListener<String> errorMessageListener;
    private ChangeListener<Boolean> placeOfferCompletedListener;
    // private ChangeListener<Coin> feeFromFundingTxListener;
    private EventHandler<ActionEvent> paymentAccountsComboBoxSelectionHandler;

    private EventHandler<ActionEvent> currencyComboBoxSelectionHandler;
    private int gridRow = 0;
    private final Preferences preferences;
    private final BSFormatter formatter;
    private ChangeListener<String> tradeCurrencyCodeListener;
    private ImageView qrCodeImageView;
    private HBox fundingHBox;
    private Subscription isWaitingForFundsSubscription;
    private Subscription cancelButton2StyleSubscription;
    private Subscription balanceSubscription;
    private final List<Node> editOfferElements = new ArrayList<>();
    private boolean isActivated;
    private Label xLabel;
    private VBox fixedPriceBox;
    private VBox percentagePriceBox;
    private HBox secondRowHBox;
    private HBox firstRowHBox;
    private HBox toggleButtonsHBox;
    private ChangeListener<Number> marketPriceAvailableListener;
    private InputTextField buyerSecurityDepositTextField;
    private TextField sellerSecurityDepositTextField;
    private Label buyerSecurityDepositBtcLabel, sellerSecurityDepositBtcLabel;
    private boolean clearXchangeWarningDisplayed;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CreateOfferView(CreateOfferViewModel model, Navigation navigation,
                            OfferDetailsWindow offerDetailsWindow, Preferences preferences, BSFormatter formatter) {
        super(model);

        this.navigation = navigation;
        this.offerDetailsWindow = offerDetailsWindow;
        this.preferences = preferences;
        this.formatter = formatter;
    }

    @Override
    protected void initialize() {
        addScrollPane();
        addGridPane();
        addPaymentGroup();
        addAmountPriceGroup();
        addFundingGroup();

        createListeners();

        balanceTextField.setFormatter(model.getFormatter());

        paymentAccountsComboBox.setConverter(new StringConverter<PaymentAccount>() {
            @Override
            public String toString(PaymentAccount paymentAccount) {
                TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
                String code = singleTradeCurrency != null ? singleTradeCurrency.getCode() : "";
                return paymentAccount.getAccountName() + " (" + code + ", " +
                        Res.get(paymentAccount.getPaymentMethod().getId()) + ")";
            }

            @Override
            public PaymentAccount fromString(String s) {
                return null;
            }
        });
    }

    @Override
    protected void activate() {
        if (model.dataModel.isTabSelected)
            doActivate();
    }

    private void doActivate() {
        if (!isActivated) {
            isActivated = true;
            currencyComboBox.setPrefWidth(250);
            paymentAccountsComboBox.setPrefWidth(250);

            addBindings();
            addListeners();
            addSubscriptions();

            sellerSecurityDepositTextField.setText(model.sellerSecurityDeposit);

            if (waitingForFundsBusyAnimation != null)
                waitingForFundsBusyAnimation.play();

            directionLabel.setText(model.getDirectionLabel());
            amountDescriptionLabel.setText(model.getAmountDescription());
            addressTextField.setAddress(model.getAddressAsString());
            addressTextField.setPaymentLabel(model.getPaymentLabel());

            paymentAccountsComboBox.setItems(model.dataModel.getPaymentAccounts());
            paymentAccountsComboBox.getSelectionModel().select(model.getPaymentAccount());

            onPaymentAccountsComboBoxSelected();

            balanceTextField.setTargetAmount(model.dataModel.getTotalToPayAsCoin().get());

            // if (DevFlags.STRESS_TEST_MODE)
            //     UserThread.runAfter(this::onShowPayFundsScreen, 200, TimeUnit.MILLISECONDS);

            updateMarketPriceAvailable();
        }
    }

    @Override
    protected void deactivate() {
        if (isActivated) {
            isActivated = false;
            removeBindings();
            removeListeners();
            removeSubscriptions();

            if (waitingForFundsBusyAnimation != null)
                waitingForFundsBusyAnimation.stop();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onTabSelected(boolean isSelected) {
        if (isSelected && !model.dataModel.isTabSelected)
            doActivate();
        else
            deactivate();

        isActivated = isSelected;
        model.dataModel.onTabSelected(isSelected);
    }

    public void initWithData(Offer.Direction direction, TradeCurrency tradeCurrency) {
        boolean result = model.initWithData(direction, tradeCurrency);

        if (!result) {
            new Popup().headLine(Res.get("popup.warning.noTradingAccountSetup.headline"))
                    .instruction(Res.get("popup.warning.noTradingAccountSetup.msg"))
                    .actionButtonTextWithGoTo("navigation.account")
                    .onAction(() -> {
                        navigation.setReturnPath(navigation.getCurrentPath());
                        navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, FiatAccountsView.class);
                    }).show();
        }

        if (direction == Offer.Direction.BUY) {
            imageView.setId("image-buy-large");

            placeOfferButton.setId("buy-button-big");
            placeOfferButton.setText(Res.get("createOffer.placeOfferButton", Res.get("shared.buy")));
            nextButton.setId("buy-button");
        } else {
            imageView.setId("image-sell-large");
            // only needed for sell
            totalToPayTextField.setPromptText(Res.get("createOffer.fundsBox.totalsNeeded.prompt"));

            placeOfferButton.setId("sell-button-big");
            placeOfferButton.setText(Res.get("createOffer.placeOfferButton", Res.get("shared.sell")));
            nextButton.setId("sell-button");
        }
    }

    // called form parent as the view does not get notified when the tab is closed
    public void onClose() {
        // we use model.placeOfferCompleted to not react on close which was triggered by a successful placeOffer
        if (model.dataModel.getBalance().get().isPositive() && !model.placeOfferCompleted.get()) {
            model.dataModel.swapTradeToSavings();
            new Popup().information(Res.get("createOffer.alreadyFunded"))
                    .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                    .show();
        }
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPlaceOffer() {
        if (model.isBootstrapped()) {
            if (model.hasAcceptedArbitrators()) {
                Offer offer = model.createAndGetOffer();
                //noinspection PointlessBooleanExpression
                if (!DevEnv.DEV_MODE)
                    offerDetailsWindow.onPlaceOffer(() ->
                            model.onPlaceOffer(offer, offerDetailsWindow::hide))
                            .show(offer);
                else
                    model.onPlaceOffer(offer, () -> {
                    });
            } else {
                new Popup().headLine(Res.get("popup.warning.noArbitratorSelected.headline"))
                        .instruction(Res.get("popup.warning.noArbitratorSelected.msg"))
                        .actionButtonTextWithGoTo("navigation.arbitratorSelection")
                        .onAction(() -> {
                            navigation.setReturnPath(navigation.getCurrentPath());
                            navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, ArbitratorSelectionView.class);
                        }).show();
            }
        } else {
            new Popup().information(Res.get("popup.warning.notFullyConnected")).show();
        }
    }

    private void onShowPayFundsScreen() {
        model.onShowPayFundsScreen();

        editOfferElements.stream().forEach(node -> {
            node.setMouseTransparent(true);
            node.setFocusTraversable(false);
        });

        balanceTextField.setTargetAmount(model.dataModel.getTotalToPayAsCoin().get());

        //noinspection PointlessBooleanExpression
        if (!DevEnv.DEV_MODE) {
            String key = "securityDepositInfo";
            new Popup().backgroundInfo(Res.get("popup.info.securityDepositInfo"))
                    .actionButtonText(Res.get("shared.faq"))
                    .onAction(() -> GUIUtil.openWebPage("https://bisq.io/faq#6"))
                    .useIUnderstandButton()
                    .dontShowAgainId(key, preferences)
                    .show();

            key = "createOfferFundWalletInfo";
            String tradeAmountText = model.isSellOffer() ? Res.get("createOffer.createOfferFundWalletInfo.tradeAmount", model.getTradeAmount()) : "";
            String message = Res.get("createOffer.createOfferFundWalletInfo.msg",
                    model.totalToPay.get(),
                    tradeAmountText,
                    model.getSecurityDepositInfo(),
                    model.getCreateOfferFee(),
                    model.getTxFee()
            );
            //TODO remove
            log.error(message);
            new Popup().headLine(Res.get("createOffer.createOfferFundWalletInfo.headline"))
                    .instruction(message)
                    .dontShowAgainId(key, preferences)
                    .show();
        }

        nextButton.setVisible(false);
        nextButton.setManaged(false);
        cancelButton1.setVisible(false);
        cancelButton1.setManaged(false);
        cancelButton1.setOnAction(null);

        waitingForFundsBusyAnimation.play();

        payFundsPane.setVisible(true);
        totalToPayLabel.setVisible(true);
        totalToPayInfoIconLabel.setVisible(true);
        totalToPayTextField.setVisible(true);
        addressLabel.setVisible(true);
        addressTextField.setVisible(true);
        qrCodeImageView.setVisible(true);
        balanceLabel.setVisible(true);
        balanceTextField.setVisible(true);
        cancelButton2.setVisible(true);
        //root.requestFocus();

        setupTotalToPayInfoIconLabel();

        final byte[] imageBytes = QRCode
                .from(getBitcoinURI())
                .withSize(98, 98) // code has 41 elements 8 px is border with 98 we get double scale and min. border
                .to(ImageType.PNG)
                .stream()
                .toByteArray();
        Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
        qrCodeImageView.setImage(qrImage);
    }

    private void maybeShowClearXchangeWarning(PaymentAccount paymentAccount) {
        if (paymentAccount.getPaymentMethod().getId().equals(PaymentMethod.CLEAR_X_CHANGE_ID) &&
                !clearXchangeWarningDisplayed) {
            clearXchangeWarningDisplayed = true;
            UserThread.runAfter(() -> GUIUtil.showClearXchangeWarning(preferences),
                    500, TimeUnit.MILLISECONDS);
        }
    }

    private void onPaymentAccountsComboBoxSelected() {
        PaymentAccount paymentAccount = paymentAccountsComboBox.getSelectionModel().getSelectedItem();
        maybeShowClearXchangeWarning(paymentAccount);

        if (paymentAccount != null) {
            currencyComboBox.setVisible(paymentAccount.hasMultipleCurrencies());
            if (paymentAccount.hasMultipleCurrencies()) {
                currencyComboBox.setItems(FXCollections.observableArrayList(paymentAccount.getTradeCurrencies()));

                // we select combobox following the user currency, if user currency not available in account, we select first
                TradeCurrency tradeCurrency = model.getTradeCurrency();
                if (paymentAccount.getTradeCurrencies().contains(tradeCurrency))
                    currencyComboBox.getSelectionModel().select(tradeCurrency);
                else
                    currencyComboBox.getSelectionModel().select(paymentAccount.getTradeCurrencies().get(0));

                model.onPaymentAccountSelected(paymentAccount);
            } else {
                TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
                if (singleTradeCurrency != null)
                    currencyTextField.setText(singleTradeCurrency.getNameAndCode());
                model.onPaymentAccountSelected(paymentAccount);
                model.onCurrencySelected(paymentAccount.getSingleTradeCurrency());
            }
        } else {
            currencyComboBox.setVisible(false);
            currencyTextField.setText("");
        }
    }

    private void onCurrencyComboBoxSelected() {
        model.onCurrencySelected(currencyComboBox.getSelectionModel().getSelectedItem());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void close() {
        if (closeHandler != null)
            closeHandler.close();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        amountBtcLabel.textProperty().bind(model.btcCode);
        buyerSecurityDepositBtcLabel.textProperty().bind(model.btcCode);
        sellerSecurityDepositBtcLabel.textProperty().bind(model.btcCode);
        priceCurrencyLabel.textProperty().bind(createStringBinding(() -> formatter.getCounterCurrency(model.tradeCurrencyCode.get()), model.btcCode, model.tradeCurrencyCode));

        marketBasedPriceLabel.prefWidthProperty().bind(priceCurrencyLabel.widthProperty());
        volumeCurrencyLabel.textProperty().bind(model.tradeCurrencyCode);
        minAmountBtcLabel.textProperty().bind(model.btcCode);
        priceDescriptionLabel.textProperty().bind(createStringBinding(() -> {
            // String currencyCode = model.tradeCurrencyCode.get();
            return formatter.getPriceWithCurrencyCode(model.tradeCurrencyCode.get());
            //BSResources.get("createOffer.amountPriceBox.priceDescriptionFiat", currencyCode);
           /* return CurrencyUtil.isCryptoCurrency(currencyCode) ?
                    BSResources.get("createOffer.amountPriceBox.priceDescriptionAltcoin", currencyCode) :
                    BSResources.get("createOffer.amountPriceBox.priceDescriptionFiat", currencyCode);*/

        }, model.tradeCurrencyCode));
        //xLabel.textProperty().bind(createStringBinding(() -> CurrencyUtil.isCryptoCurrency(model.tradeCurrencyCode.get()) ? "/" : "x", model.tradeCurrencyCode));
        xLabel.setText("x");
        volumeDescriptionLabel.textProperty().bind(createStringBinding(model.volumeDescriptionLabel::get, model.tradeCurrencyCode, model.volumeDescriptionLabel));
        amountTextField.textProperty().bindBidirectional(model.amount);
        minAmountTextField.textProperty().bindBidirectional(model.minAmount);
        fixedPriceTextField.textProperty().bindBidirectional(model.price);
        marketBasedPriceTextField.textProperty().bindBidirectional(model.marketPriceMargin);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        volumeTextField.promptTextProperty().bind(model.volumePromptLabel);
        totalToPayTextField.textProperty().bind(model.totalToPay);
        addressTextField.amountAsCoinProperty().bind(model.dataModel.getMissingCoin());
        buyerSecurityDepositTextField.textProperty().bindBidirectional(model.buyerSecurityDeposit);

        // Validation
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        minAmountTextField.validationResultProperty().bind(model.minAmountValidationResult);
        fixedPriceTextField.validationResultProperty().bind(model.priceValidationResult);
        volumeTextField.validationResultProperty().bind(model.volumeValidationResult);
        buyerSecurityDepositTextField.validationResultProperty().bind(model.buyerSecurityDepositValidationResult);

        // funding
        fundingHBox.visibleProperty().bind(model.dataModel.getIsWalletFunded().not().and(model.showPayFundsScreenDisplayed));
        fundingHBox.managedProperty().bind(model.dataModel.getIsWalletFunded().not().and(model.showPayFundsScreenDisplayed));
        waitingForFundsLabel.textProperty().bind(model.waitingForFundsText);
        placeOfferButton.visibleProperty().bind(model.dataModel.getIsWalletFunded().and(model.showPayFundsScreenDisplayed));
        placeOfferButton.managedProperty().bind(model.dataModel.getIsWalletFunded().and(model.showPayFundsScreenDisplayed));
        placeOfferButton.disableProperty().bind(model.isPlaceOfferButtonDisabled);
        cancelButton2.disableProperty().bind(model.cancelButtonDisabled);

        // trading account
        currencyComboBox.prefWidthProperty().bind(paymentAccountsComboBox.widthProperty());
        currencyComboBox.managedProperty().bind(currencyComboBox.visibleProperty());
        currencyComboBoxLabel.visibleProperty().bind(currencyComboBox.visibleProperty());
        currencyComboBoxLabel.managedProperty().bind(currencyComboBox.visibleProperty());
        currencyTextField.visibleProperty().bind(currencyComboBox.visibleProperty().not());
        currencyTextField.managedProperty().bind(currencyComboBox.visibleProperty().not());
        currencyTextFieldLabel.visibleProperty().bind(currencyComboBox.visibleProperty().not());
        currencyTextFieldLabel.managedProperty().bind(currencyComboBox.visibleProperty().not());
    }

    private void removeBindings() {
        amountBtcLabel.textProperty().unbind();
        buyerSecurityDepositBtcLabel.textProperty().unbind();
        sellerSecurityDepositBtcLabel.textProperty().unbind();
        priceCurrencyLabel.textProperty().unbind();
        fixedPriceTextField.disableProperty().unbind();
        priceCurrencyLabel.disableProperty().unbind();
        marketBasedPriceTextField.disableProperty().unbind();
        marketBasedPriceLabel.disableProperty().unbind();
        volumeCurrencyLabel.textProperty().unbind();
        minAmountBtcLabel.textProperty().unbind();
        priceDescriptionLabel.textProperty().unbind();
        xLabel.textProperty().unbind();
        volumeDescriptionLabel.textProperty().unbind();
        amountTextField.textProperty().unbindBidirectional(model.amount);
        minAmountTextField.textProperty().unbindBidirectional(model.minAmount);
        fixedPriceTextField.textProperty().unbindBidirectional(model.price);
        marketBasedPriceTextField.textProperty().unbindBidirectional(model.marketPriceMargin);
        marketBasedPriceLabel.prefWidthProperty().unbind();
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        volumeTextField.promptTextProperty().unbindBidirectional(model.volume);
        totalToPayTextField.textProperty().unbind();
        addressTextField.amountAsCoinProperty().unbind();
        buyerSecurityDepositTextField.textProperty().unbindBidirectional(model.buyerSecurityDeposit);

        // Validation
        amountTextField.validationResultProperty().unbind();
        minAmountTextField.validationResultProperty().unbind();
        fixedPriceTextField.validationResultProperty().unbind();
        volumeTextField.validationResultProperty().unbind();
        buyerSecurityDepositTextField.validationResultProperty().unbind();

        // funding
        fundingHBox.visibleProperty().unbind();
        fundingHBox.managedProperty().unbind();
        waitingForFundsLabel.textProperty().unbind();
        placeOfferButton.visibleProperty().unbind();
        placeOfferButton.managedProperty().unbind();
        placeOfferButton.disableProperty().unbind();
        cancelButton2.disableProperty().unbind();

        // trading account
        currencyComboBox.managedProperty().unbind();
        currencyComboBox.prefWidthProperty().unbind();
        currencyComboBoxLabel.visibleProperty().unbind();
        currencyComboBoxLabel.managedProperty().unbind();
        currencyTextField.visibleProperty().unbind();
        currencyTextField.managedProperty().unbind();
        currencyTextFieldLabel.visibleProperty().unbind();
        currencyTextFieldLabel.managedProperty().unbind();
    }

    private void addSubscriptions() {
        isWaitingForFundsSubscription = EasyBind.subscribe(model.isWaitingForFunds, isWaitingForFunds -> {
            waitingForFundsBusyAnimation.setIsRunning(isWaitingForFunds);
            waitingForFundsLabel.setVisible(isWaitingForFunds);
            waitingForFundsLabel.setManaged(isWaitingForFunds);
        });

        cancelButton2StyleSubscription = EasyBind.subscribe(placeOfferButton.visibleProperty(),
                isVisible -> cancelButton2.setId(isVisible ? "cancel-button" : null));

        balanceSubscription = EasyBind.subscribe(model.dataModel.getBalance(), balanceTextField::setBalance);
    }

    private void removeSubscriptions() {
        isWaitingForFundsSubscription.unsubscribe();
        cancelButton2StyleSubscription.unsubscribe();
        balanceSubscription.unsubscribe();
    }

    private void createListeners() {
        amountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue);
            amountTextField.setText(model.amount.get());
        };
        minAmountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutMinAmountTextField(oldValue, newValue);
            minAmountTextField.setText(model.minAmount.get());
        };
        priceFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutPriceTextField(oldValue, newValue);
            fixedPriceTextField.setText(model.price.get());
        };
        priceAsPercentageFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutPriceAsPercentageTextField(oldValue, newValue);
            marketBasedPriceTextField.setText(model.marketPriceMargin.get());
        };
        volumeFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutVolumeTextField(oldValue, newValue);
            volumeTextField.setText(model.volume.get());
        };
        buyerSecurityDepositFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutBuyerSecurityDepositTextField(oldValue, newValue);
            buyerSecurityDepositTextField.setText(model.buyerSecurityDeposit.get());
        };

        errorMessageListener = (o, oldValue, newValue) -> {
            if (newValue != null)
                UserThread.runAfter(() -> new Popup().error(Res.get("createOffer.amountPriceBox.error.message", model.errorMessage.get()))
                        .show(), 100, TimeUnit.MILLISECONDS);
        };

       /* feeFromFundingTxListener = (observable, oldValue, newValue) -> {
            log.debug("feeFromFundingTxListener " + newValue);
            if (!model.dataModel.isFeeFromFundingTxSufficient()) {
                new Popup().warning("The mining fee from your funding transaction is not sufficiently high.\n\n" +
                        "You need to use at least a mining fee of " +
                        model.formatter.formatCoinWithCode(FeePolicy.getMinRequiredFeeForFundingTx()) + ".\n\n" +
                        "The fee used in your funding transaction was only " +
                        model.formatter.formatCoinWithCode(newValue) + ".\n\n" +
                        "The trade transactions might take too much time to be included in " +
                        "a block if the fee is too low.\n" +
                        "Please check at your external wallet that you set the required fee and " +
                        "do a funding again with the correct fee.\n\n" +
                        "In the \"Funds/Open for withdrawal\" section you can withdraw those funds.")
                        .closeButtonText(Res.get("shared.close"))
                        .onClose(() -> {
                            close();
                            model.dataModel.swapTradeToSavings();
                            navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
                        })
                        .show();
            }
        };*/

        paymentAccountsComboBoxSelectionHandler = e -> onPaymentAccountsComboBoxSelected();
        currencyComboBoxSelectionHandler = e -> onCurrencyComboBoxSelected();

        tradeCurrencyCodeListener = (observable, oldValue, newValue) -> {
            fixedPriceTextField.clear();
            marketBasedPriceTextField.clear();
            volumeTextField.clear();
        };

        placeOfferCompletedListener = (o, oldValue, newValue) -> {
            if (DevEnv.DEV_MODE) {
                close();
            } else if (newValue) {
                // We need a bit of delay to avoid issues with fade out/fade in of 2 popups
                String key = "createOfferSuccessInfo";
                if (preferences.showAgain(key)) {
                    UserThread.runAfter(() -> new Popup().headLine(Res.get("createOffer.success.headline"))
                                    .feedback(Res.get("createOffer.success.info"))
                                    .dontShowAgainId(key, preferences)
                                    .actionButtonTextWithGoTo("navigation.portfolio.myOpenOffers")
                                    .onAction(() -> {
                                        UserThread.runAfter(() ->
                                                        navigation.navigateTo(MainView.class, PortfolioView.class,
                                                                OpenOffersView.class),
                                                100, TimeUnit.MILLISECONDS);
                                        close();
                                    })
                                    .onClose(this::close)
                                    .show(),
                            1);
                } else {
                    close();
                }
            }
        };


        marketPriceAvailableListener = (observable, oldValue, newValue) -> updateMarketPriceAvailable();
    }

    private void updateMarketPriceAvailable() {
        int marketPriceAvailableValue = model.marketPriceAvailableProperty.get();
        if (marketPriceAvailableValue > -1) {
            boolean isMarketPriceAvailable = marketPriceAvailableValue == 1;
            percentagePriceBox.setVisible(isMarketPriceAvailable);
            percentagePriceBox.setManaged(isMarketPriceAvailable);
            toggleButtonsHBox.setVisible(isMarketPriceAvailable);
            toggleButtonsHBox.setManaged(isMarketPriceAvailable);
            boolean fixedPriceSelected = !model.dataModel.getUseMarketBasedPrice().get() || !isMarketPriceAvailable;
            updateToggleButtons(fixedPriceSelected);
        }
    }

    private void addListeners() {
        model.tradeCurrencyCode.addListener(tradeCurrencyCodeListener);
        model.marketPriceAvailableProperty.addListener(marketPriceAvailableListener);

        // focus out
        amountTextField.focusedProperty().addListener(amountFocusedListener);
        minAmountTextField.focusedProperty().addListener(minAmountFocusedListener);
        fixedPriceTextField.focusedProperty().addListener(priceFocusedListener);
        marketBasedPriceTextField.focusedProperty().addListener(priceAsPercentageFocusedListener);
        volumeTextField.focusedProperty().addListener(volumeFocusedListener);
        buyerSecurityDepositTextField.focusedProperty().addListener(buyerSecurityDepositFocusedListener);

        // warnings
        model.errorMessage.addListener(errorMessageListener);
        // model.dataModel.feeFromFundingTxProperty.addListener(feeFromFundingTxListener);

        model.placeOfferCompleted.addListener(placeOfferCompletedListener);

        // UI actions
        paymentAccountsComboBox.setOnAction(paymentAccountsComboBoxSelectionHandler);
        currencyComboBox.setOnAction(currencyComboBoxSelectionHandler);
    }

    private void removeListeners() {
        model.tradeCurrencyCode.removeListener(tradeCurrencyCodeListener);
        model.marketPriceAvailableProperty.removeListener(marketPriceAvailableListener);

        // focus out
        amountTextField.focusedProperty().removeListener(amountFocusedListener);
        minAmountTextField.focusedProperty().removeListener(minAmountFocusedListener);
        fixedPriceTextField.focusedProperty().removeListener(priceFocusedListener);
        marketBasedPriceTextField.focusedProperty().removeListener(priceAsPercentageFocusedListener);
        volumeTextField.focusedProperty().removeListener(volumeFocusedListener);
        buyerSecurityDepositTextField.focusedProperty().removeListener(buyerSecurityDepositFocusedListener);

        // warnings
        model.errorMessage.removeListener(errorMessageListener);
        // model.dataModel.feeFromFundingTxProperty.removeListener(feeFromFundingTxListener);

        model.placeOfferCompleted.removeListener(placeOfferCompletedListener);

        // UI actions
        paymentAccountsComboBox.setOnAction(null);
        currencyComboBox.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build UI elements
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addScrollPane() {
        scrollPane = new ScrollPane();
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setOnScroll(e -> InputTextField.hideErrorMessageDisplay());
        AnchorPane.setLeftAnchor(scrollPane, 0d);
        AnchorPane.setTopAnchor(scrollPane, 0d);
        AnchorPane.setRightAnchor(scrollPane, 0d);
        AnchorPane.setBottomAnchor(scrollPane, 0d);
        root.getChildren().add(scrollPane);
    }

    private void addGridPane() {
        gridPane = new GridPane();
        gridPane.setPadding(new Insets(30, 25, -1, 25));
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.NEVER);
        columnConstraints1.setMinWidth(200);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        ColumnConstraints columnConstraints3 = new ColumnConstraints();
        columnConstraints3.setHgrow(Priority.NEVER);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2, columnConstraints3);
        scrollPane.setContent(gridPane);
    }

    private void addPaymentGroup() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, gridRow, 2, Res.get("shared.selectTradingAccount"));
        GridPane.setColumnSpan(titledGroupBg, 3);

        paymentAccountsComboBox = addLabelComboBox(gridPane, gridRow, Res.getWithCol("shared.tradingAccount"), Layout.FIRST_ROW_DISTANCE).second;
        paymentAccountsComboBox.setPromptText(Res.get("shared.selectTradingAccount"));
        paymentAccountsComboBox.setMinWidth(300);
        editOfferElements.add(paymentAccountsComboBox);

        // we display either currencyComboBox (multi currency account) or currencyTextField (single)
        Tuple2<Label, ComboBox> currencyComboBoxTuple = addLabelComboBox(gridPane, ++gridRow, Res.getWithCol("shared.currency"));
        currencyComboBoxLabel = currencyComboBoxTuple.first;
        editOfferElements.add(currencyComboBoxLabel);
        currencyComboBox = currencyComboBoxTuple.second;
        editOfferElements.add(currencyComboBox);
        currencyComboBox.setPromptText(Res.get("list.currency.select"));
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        });

        Tuple2<Label, TextField> currencyTextFieldTuple = addLabelTextField(gridPane, gridRow, Res.getWithCol("shared.currency"), "", 5);
        currencyTextFieldLabel = currencyTextFieldTuple.first;
        editOfferElements.add(currencyTextFieldLabel);
        currencyTextField = currencyTextFieldTuple.second;
        editOfferElements.add(currencyTextField);
    }

    private void addAmountPriceGroup() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 3, Res.get("createOffer.setAmountPrice"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 3);

        imageView = new ImageView();
        imageView.setPickOnBounds(true);
        directionLabel = new Label();
        directionLabel.setAlignment(Pos.CENTER);
        directionLabel.setPadding(new Insets(-5, 0, 0, 0));
        directionLabel.setId("direction-icon-label");
        VBox imageVBox = new VBox();
        imageVBox.setAlignment(Pos.CENTER);
        imageVBox.setSpacing(6);
        imageVBox.getChildren().addAll(imageView, directionLabel);
        GridPane.setRowIndex(imageVBox, gridRow);
        GridPane.setRowSpan(imageVBox, 2);
        GridPane.setMargin(imageVBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 10, 10, 10));
        gridPane.getChildren().add(imageVBox);

        addAmountPriceFields();
        addSecondRow();
        addThirdRow();

        Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane, ++gridRow,
                Res.get("shared.nextStep"), Res.get("shared.cancel"));
        nextButton = tuple.first;
        editOfferElements.add(nextButton);
        nextButton.disableProperty().bind(model.isNextButtonDisabled);
        //UserThread.runAfter(() -> nextButton.requestFocus(), 100, TimeUnit.MILLISECONDS);
        cancelButton1 = tuple.second;
        editOfferElements.add(cancelButton1);
        cancelButton1.setDefaultButton(false);
        cancelButton1.setOnAction(e -> {
            close();
            model.dataModel.swapTradeToSavings();
        });
        cancelButton1.setId("cancel-button");

        GridPane.setMargin(nextButton, new Insets(-35, 0, 0, 0));
        nextButton.setOnAction(e -> {
            if (model.isPriceInRange())
                onShowPayFundsScreen();
        });
    }

    private void addFundingGroup() {
        // don't increase gridRow as we removed button when this gets visible
        payFundsPane = addTitledGroupBg(gridPane, gridRow, 3, Res.get("createOffer.fundsBox.title"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(payFundsPane, 3);
        payFundsPane.setVisible(false);

        totalToPayLabel = new Label(Res.get("shared.totalsNeeded"));
        totalToPayLabel.setVisible(false);
        totalToPayInfoIconLabel = new Label();
        totalToPayInfoIconLabel.setVisible(false);
        HBox totalToPayBox = new HBox();
        totalToPayBox.setSpacing(4);
        totalToPayBox.setAlignment(Pos.CENTER_RIGHT);
        totalToPayBox.getChildren().addAll(totalToPayLabel, totalToPayInfoIconLabel);
        GridPane.setMargin(totalToPayBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        GridPane.setRowIndex(totalToPayBox, gridRow);
        gridPane.getChildren().add(totalToPayBox);
        totalToPayTextField = new TextFieldWithCopyIcon();
        totalToPayTextField.setCopyWithoutCurrencyPostFix(true);
        totalToPayTextField.setFocusTraversable(false);
        totalToPayTextField.setVisible(false);
        GridPane.setRowIndex(totalToPayTextField, gridRow);
        GridPane.setColumnIndex(totalToPayTextField, 1);
        GridPane.setMargin(totalToPayTextField, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(totalToPayTextField);

        qrCodeImageView = new ImageView();
        qrCodeImageView.setVisible(false);
        qrCodeImageView.setStyle("-fx-cursor: hand;");
        Tooltip.install(qrCodeImageView, new Tooltip(Res.get("shared.openLargeQRWindow")));
        qrCodeImageView.setOnMouseClicked(e -> GUIUtil.showFeeInfoBeforeExecute(
                () -> UserThread.runAfter(
                        () -> new QRCodeWindow(getBitcoinURI()).show(),
                        200, TimeUnit.MILLISECONDS)
        ));
        GridPane.setRowIndex(qrCodeImageView, gridRow);
        GridPane.setColumnIndex(qrCodeImageView, 2);
        GridPane.setRowSpan(qrCodeImageView, 3);
        GridPane.setMargin(qrCodeImageView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE - 9, 0, 0, 5));
        gridPane.getChildren().add(qrCodeImageView);

        Tuple2<Label, AddressTextField> addressTuple = addLabelAddressTextField(gridPane, ++gridRow,
                Res.get("shared.tradeWalletAddress"));
        addressLabel = addressTuple.first;
        addressLabel.setVisible(false);
        addressTextField = addressTuple.second;
        addressTextField.setVisible(false);

        Tuple2<Label, BalanceTextField> balanceTuple = addLabelBalanceTextField(gridPane, ++gridRow,
                Res.get("shared.tradeWalletBalance"));
        balanceLabel = balanceTuple.first;
        balanceLabel.setVisible(false);
        balanceTextField = balanceTuple.second;
        balanceTextField.setVisible(false);

        fundingHBox = new HBox();
        fundingHBox.setVisible(false);
        fundingHBox.setManaged(false);
        fundingHBox.setSpacing(10);
        fundFromSavingsWalletButton = new Button(Res.get("shared.fundFromSavingsWalletButton"));
        fundFromSavingsWalletButton.setDefaultButton(true);
        fundFromSavingsWalletButton.setDefaultButton(false);
        fundFromSavingsWalletButton.setOnAction(e -> model.fundFromSavingsWallet());
        Label label = new Label(Res.get("shared.OR"));
        label.setPadding(new Insets(5, 0, 0, 0));
        fundFromExternalWalletButton = new Button(Res.get("shared.fundFromExternalWalletButton"));
        fundFromExternalWalletButton.setDefaultButton(false);
        fundFromExternalWalletButton.setOnAction(e -> GUIUtil.showFeeInfoBeforeExecute(this::openWallet));
        waitingForFundsBusyAnimation = new BusyAnimation();
        waitingForFundsLabel = new Label();
        waitingForFundsLabel.setPadding(new Insets(5, 0, 0, 0));

        fundingHBox.getChildren().addAll(fundFromSavingsWalletButton, label, fundFromExternalWalletButton, waitingForFundsBusyAnimation, waitingForFundsLabel);
        GridPane.setRowIndex(fundingHBox, ++gridRow);
        GridPane.setColumnIndex(fundingHBox, 1);
        GridPane.setMargin(fundingHBox, new Insets(15, 10, 0, 0));
        gridPane.getChildren().add(fundingHBox);


        placeOfferButton = addButtonAfterGroup(gridPane, gridRow, "");
        placeOfferButton.setOnAction(e -> onPlaceOffer());
        placeOfferButton.setMinHeight(40);
        placeOfferButton.setPadding(new Insets(0, 20, 0, 20));

        cancelButton2 = addButton(gridPane, ++gridRow, Res.get("shared.cancel"));
        cancelButton2.setOnAction(e -> {
            if (model.dataModel.getIsWalletFunded().get()) {
                new Popup().warning(Res.get("createOffer.warnCancelOffer"))
                        .closeButtonText(Res.get("shared.no"))
                        .actionButtonText(Res.get("shared.yesCancel"))
                        .onAction(() -> {
                            close();
                            model.dataModel.swapTradeToSavings();
                        })
                        .show();
            } else {
                close();
                model.dataModel.swapTradeToSavings();
            }
        });
        cancelButton2.setDefaultButton(false);
        cancelButton2.setVisible(false);
    }

    private void openWallet() {
        try {
            Utilities.openURI(URI.create(getBitcoinURI()));
        } catch (Exception ex) {
            log.warn(ex.getMessage());
            new Popup().warning(Res.get("shared.openDefaultWalletFailed")).show();
        }
    }

    @NotNull
    private String getBitcoinURI() {
        return model.getAddressAsString() != null ? BitcoinURI.convertToBitcoinURI(model.getAddressAsString(), model.dataModel.getMissingCoin().get(),
                model.getPaymentLabel(), null) : "";
    }

    private void addAmountPriceFields() {
        // amountBox
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueCurrencyBox(Res.get("createOffer.amount.prompt"));
        HBox amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountTextField = amountValueCurrencyBoxTuple.second;
        editOfferElements.add(amountTextField);
        amountBtcLabel = amountValueCurrencyBoxTuple.third;
        editOfferElements.add(amountBtcLabel);
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, model.getAmountDescription());
        amountDescriptionLabel = amountInputBoxTuple.first;
        editOfferElements.add(amountDescriptionLabel);
        VBox amountBox = amountInputBoxTuple.second;

        // x
        xLabel = new Label();
        xLabel.setFont(Font.font("Helvetica-Bold", 20));
        xLabel.setPadding(new Insets(14, 3, 0, 3));
        xLabel.setMinWidth(14);
        xLabel.setMaxWidth(14);

        // price as percent
        Tuple3<HBox, InputTextField, Label> priceAsPercentageTuple = getEditableValueCurrencyBox(Res.get("createOffer.price.prompt"));
        HBox priceAsPercentageValueCurrencyBox = priceAsPercentageTuple.first;
        marketBasedPriceTextField = priceAsPercentageTuple.second;
        editOfferElements.add(marketBasedPriceTextField);
        marketBasedPriceLabel = priceAsPercentageTuple.third;
        editOfferElements.add(marketBasedPriceLabel);
        Tuple2<Label, VBox> priceAsPercentageInputBoxTuple = getTradeInputBox(priceAsPercentageValueCurrencyBox, Res.get("shared.distanceInPercent"));
        priceAsPercentageInputBoxTuple.first.setPrefWidth(200);
        percentagePriceBox = priceAsPercentageInputBoxTuple.second;

        // Fixed/Percentage toggle
        ToggleGroup toggleGroup = new ToggleGroup();
        fixedPriceButton = new ToggleButton(Res.get("createOffer.fixed"));
        editOfferElements.add(fixedPriceButton);
        fixedPriceButton.setId("toggle-price-left");
        fixedPriceButton.setToggleGroup(toggleGroup);
        fixedPriceButton.selectedProperty().addListener((ov, oldValue, newValue) -> {
            updateToggleButtons(newValue);
        });

        useMarketBasedPriceButton = new ToggleButton(Res.get("createOffer.percentage"));
        editOfferElements.add(useMarketBasedPriceButton);
        useMarketBasedPriceButton.setId("toggle-price-right");
        useMarketBasedPriceButton.setToggleGroup(toggleGroup);
        useMarketBasedPriceButton.selectedProperty().addListener((ov, oldValue, newValue) -> {
            updateToggleButtons(!newValue);
        });

        toggleButtonsHBox = new HBox();
        toggleButtonsHBox.setPadding(new Insets(18, 0, 0, 0));
        toggleButtonsHBox.getChildren().addAll(fixedPriceButton, useMarketBasedPriceButton);

        // =
        Label resultLabel = new Label("=");
        resultLabel.setFont(Font.font("Helvetica-Bold", 20));
        resultLabel.setPadding(new Insets(14, 2, 0, 2));

        // volume
        Tuple3<HBox, InputTextField, Label> volumeValueCurrencyBoxTuple = getEditableValueCurrencyBox(Res.get("createOffer.volume.prompt"));
        HBox volumeValueCurrencyBox = volumeValueCurrencyBoxTuple.first;
        volumeTextField = volumeValueCurrencyBoxTuple.second;
        editOfferElements.add(volumeTextField);
        volumeCurrencyLabel = volumeValueCurrencyBoxTuple.third;
        editOfferElements.add(volumeCurrencyLabel);
        Tuple2<Label, VBox> volumeInputBoxTuple = getTradeInputBox(volumeValueCurrencyBox, model.volumeDescriptionLabel.get());
        volumeDescriptionLabel = volumeInputBoxTuple.first;
        editOfferElements.add(volumeDescriptionLabel);
        VBox volumeBox = volumeInputBoxTuple.second;

        firstRowHBox = new HBox();
        firstRowHBox.setSpacing(5);
        firstRowHBox.setAlignment(Pos.CENTER_LEFT);
        firstRowHBox.getChildren().addAll(amountBox, xLabel, percentagePriceBox, toggleButtonsHBox, resultLabel, volumeBox);
        GridPane.setRowIndex(firstRowHBox, gridRow);
        GridPane.setColumnIndex(firstRowHBox, 1);
        GridPane.setMargin(firstRowHBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 10, 0, 0));
        GridPane.setColumnSpan(firstRowHBox, 2);
        gridPane.getChildren().add(firstRowHBox);
    }

    private void updateToggleButtons(boolean fixedPriceSelected) {
        int marketPriceAvailable = model.marketPriceAvailableProperty.get();
        fixedPriceSelected |= marketPriceAvailable == 0;

        if (marketPriceAvailable == 1) {
            model.dataModel.setUseMarketBasedPrice(!fixedPriceSelected);

            if (!fixedPriceButton.isSelected() && fixedPriceSelected)
                fixedPriceButton.setSelected(true);
            if (useMarketBasedPriceButton.isSelected() && !fixedPriceSelected)
                useMarketBasedPriceButton.setSelected(false);
        }

        fixedPriceButton.setMouseTransparent(fixedPriceSelected);
        useMarketBasedPriceButton.setMouseTransparent(!fixedPriceSelected);

        final boolean finalFixedPriceSelected = fixedPriceSelected;
        fixedPriceButton.setStyle(finalFixedPriceSelected ? "-fx-background-color: -bs-blue-transparent" : "-fx-background-color: -bs-very-light-grey");
        useMarketBasedPriceButton.setStyle(!finalFixedPriceSelected ? "-fx-background-color: -bs-blue-transparent" : "-fx-background-color: -bs-very-light-grey");

        if (fixedPriceSelected) {
            if (firstRowHBox.getChildren().contains(percentagePriceBox))
                firstRowHBox.getChildren().remove(percentagePriceBox);
            if (secondRowHBox.getChildren().contains(fixedPriceBox))
                secondRowHBox.getChildren().remove(fixedPriceBox);
            if (!firstRowHBox.getChildren().contains(fixedPriceBox))
                firstRowHBox.getChildren().add(2, fixedPriceBox);
            if (!secondRowHBox.getChildren().contains(percentagePriceBox))
                secondRowHBox.getChildren().add(2, percentagePriceBox);
        } else {
            if (firstRowHBox.getChildren().contains(fixedPriceBox))
                firstRowHBox.getChildren().remove(fixedPriceBox);
            if (secondRowHBox.getChildren().contains(percentagePriceBox))
                secondRowHBox.getChildren().remove(percentagePriceBox);
            if (!firstRowHBox.getChildren().contains(percentagePriceBox))
                firstRowHBox.getChildren().add(2, percentagePriceBox);
            if (!secondRowHBox.getChildren().contains(fixedPriceBox))
                secondRowHBox.getChildren().add(2, fixedPriceBox);
        }
    }

    private void addSecondRow() {
        // price as fiat
        Tuple3<HBox, InputTextField, Label> priceValueCurrencyBoxTuple = getEditableValueCurrencyBox(
                Res.get("createOffer.price.prompt"));
        HBox priceValueCurrencyBox = priceValueCurrencyBoxTuple.first;
        fixedPriceTextField = priceValueCurrencyBoxTuple.second;
        editOfferElements.add(fixedPriceTextField);
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        editOfferElements.add(priceCurrencyLabel);
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox, "");
        priceDescriptionLabel = priceInputBoxTuple.first;
        editOfferElements.add(priceDescriptionLabel);
        fixedPriceBox = priceInputBoxTuple.second;

        marketBasedPriceTextField.setPromptText(Res.get("shared.enterPercentageValue"));
        marketBasedPriceLabel.setText("%");
        marketBasedPriceLabel.setStyle("-fx-alignment: center;");

        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueCurrencyBox(
                Res.get("createOffer.amount.prompt"));
        HBox amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        minAmountTextField = amountValueCurrencyBoxTuple.second;
        editOfferElements.add(minAmountTextField);
        minAmountBtcLabel = amountValueCurrencyBoxTuple.third;
        editOfferElements.add(minAmountBtcLabel);

        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox,
                Res.get("createOffer.amountPriceBox.minAmountDescription"));

        Label xLabel = new Label("x");
        xLabel.setFont(Font.font("Helvetica-Bold", 20));
        xLabel.setPadding(new Insets(14, 3, 0, 3));
        xLabel.setVisible(false); // we just use it to get the same layout as the upper row

        secondRowHBox = new HBox();
        secondRowHBox.setSpacing(5);
        secondRowHBox.setAlignment(Pos.CENTER_LEFT);
        secondRowHBox.getChildren().addAll(amountInputBoxTuple.second, xLabel, fixedPriceBox);
        GridPane.setRowIndex(secondRowHBox, ++gridRow);
        GridPane.setColumnIndex(secondRowHBox, 1);
        GridPane.setMargin(secondRowHBox, new Insets(0, 10, 0, 0));
        GridPane.setColumnSpan(secondRowHBox, 2);
        gridPane.getChildren().add(secondRowHBox);
    }

    private void addThirdRow() {
        // security deposit for buyer
        Tuple3<HBox, InputTextField, Label> buyerSecurityDepositTuple = getEditableValueCurrencyBox(
                Res.get("createOffer.securityDeposit.prompt"));
        HBox buyerSecurityDepositValueCurrencyBox = buyerSecurityDepositTuple.first;
        buyerSecurityDepositTextField = buyerSecurityDepositTuple.second;
        editOfferElements.add(buyerSecurityDepositTextField);
        buyerSecurityDepositBtcLabel = buyerSecurityDepositTuple.third;
        editOfferElements.add(buyerSecurityDepositBtcLabel);
        Tuple2<Label, VBox> buyerSecurityDepositInputBoxTuple = getTradeInputBox(buyerSecurityDepositValueCurrencyBox,
                Res.get("shared.securityDepositBox.description", Res.get("shared.buyer")));
        editOfferElements.add(buyerSecurityDepositInputBoxTuple.first);
        VBox buyerSecurityDepositBox = buyerSecurityDepositInputBoxTuple.second;

        // security deposit for seller
        Tuple3<HBox, TextField, Label> sellerSecurityDepositTuple = getNotEditableValueCurrencyBox();
        HBox sellerSecurityDepositValueCurrencyBox = sellerSecurityDepositTuple.first;
        sellerSecurityDepositTextField = sellerSecurityDepositTuple.second;
        sellerSecurityDepositTextField.setPrefWidth(170);
        sellerSecurityDepositBtcLabel = sellerSecurityDepositTuple.third;
        Tuple2<Label, VBox> sellerSecurityDepositInputBoxTuple = getTradeInputBox(sellerSecurityDepositValueCurrencyBox,
                Res.get("shared.securityDepositBox.description", Res.get("shared.seller")));
        VBox sellerSecurityDepositBox = sellerSecurityDepositInputBoxTuple.second;

        Label xLabel = new Label("x");
        xLabel.setFont(Font.font("Helvetica-Bold", 20));
        xLabel.setPadding(new Insets(14, 3, 0, 3));
        xLabel.setVisible(false); // we just use it to get the same layout as the upper row

        HBox thirdRowHBox = new HBox();
        thirdRowHBox.setSpacing(5);
        thirdRowHBox.setAlignment(Pos.CENTER_LEFT);
        thirdRowHBox.getChildren().addAll(buyerSecurityDepositBox, xLabel, sellerSecurityDepositBox);
        GridPane.setRowIndex(thirdRowHBox, ++gridRow);
        GridPane.setColumnIndex(thirdRowHBox, 1);
        GridPane.setMargin(thirdRowHBox, new Insets(0, 10, 5, 0));
        GridPane.setColumnSpan(thirdRowHBox, 2);
        gridPane.getChildren().add(thirdRowHBox);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PayInfo
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupTotalToPayInfoIconLabel() {
        totalToPayInfoIconLabel.setId("clickable-icon");
        AwesomeDude.setIcon(totalToPayInfoIconLabel, AwesomeIcon.QUESTION_SIGN);

        totalToPayInfoIconLabel.setOnMouseEntered(e -> createInfoPopover());
        totalToPayInfoIconLabel.setOnMouseExited(e -> {
            if (totalToPayInfoPopover != null)
                totalToPayInfoPopover.hide();
        });
    }

    // As we don't use binding here we need to recreate it on mouse over to reflect the current state
    private void createInfoPopover() {
        GridPane infoGridPane = new GridPane();
        infoGridPane.setHgap(5);
        infoGridPane.setVgap(5);
        infoGridPane.setPadding(new Insets(10, 10, 10, 10));

        int i = 0;
        if (model.isSellOffer())
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.tradeAmount"), model.tradeAmount.get());

        addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.securityDeposit"), model.getSecurityDepositInfo());
        addPayInfoEntry(infoGridPane, i++, Res.get("createOffer.fundsBox.offerFee"), model.getCreateOfferFee());
        addPayInfoEntry(infoGridPane, i++, Res.get("createOffer.fundsBox.networkFee"), model.getTxFee());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #666;");
        GridPane.setConstraints(separator, 1, i++);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, i, Res.getWithCol("shared.total"), model.totalToPay.get());
        totalToPayInfoPopover = new PopOver(infoGridPane);
        if (totalToPayInfoIconLabel.getScene() != null) {
            totalToPayInfoPopover.setDetachable(false);
            totalToPayInfoPopover.setArrowIndent(5);
            totalToPayInfoPopover.show(totalToPayInfoIconLabel.getScene().getWindow(),
                    getPopupPosition().getX(),
                    getPopupPosition().getY());
        }
    }

    private void addPayInfoEntry(GridPane infoGridPane, int row, String labelText, String value) {
        Label label = new Label(labelText);
        TextField textField = new TextField(value);
        textField.setMinWidth(300);
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        textField.setId("payment-info");
        GridPane.setConstraints(label, 0, row, 1, 1, HPos.RIGHT, VPos.CENTER);
        GridPane.setConstraints(textField, 1, row);
        infoGridPane.getChildren().addAll(label, textField);
    }

    private Point2D getPopupPosition() {
        Window window = totalToPayInfoIconLabel.getScene().getWindow();
        Point2D point = totalToPayInfoIconLabel.localToScene(0, 0);
        double x = point.getX() + window.getX() + totalToPayInfoIconLabel.getWidth() + 2;
        double y = point.getY() + window.getY() + Math.floor(totalToPayInfoIconLabel.getHeight() / 2) - 9;
        return new Point2D(x, y);
    }
}


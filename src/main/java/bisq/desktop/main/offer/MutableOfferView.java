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

package bisq.desktop.main.offer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.components.AddressTextField;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.BalanceTextField;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.FundsTextField;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.arbitratorselection.ArbitratorSelectionView;
import bisq.desktop.main.account.content.fiataccounts.FiatAccountsView;
import bisq.desktop.main.account.settings.AccountSettingsView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.wallet.BsqWalletView;
import bisq.desktop.main.dao.wallet.receive.BsqReceiveView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.withdrawal.WithdrawalView;
import bisq.desktop.main.overlays.notifications.Notification;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.FeeOptionWindow;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.overlays.windows.QRCodeWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.openoffer.OpenOffersView;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.Transitions;

import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.value.ChangeListener;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.net.URI;

import java.io.ByteArrayInputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createStringBinding;

public abstract class MutableOfferView<M extends MutableOfferViewModel> extends ActivatableViewAndModel<AnchorPane, M> {
    protected final Navigation navigation;
    private final Preferences preferences;
    private final Transitions transitions;
    private final OfferDetailsWindow offerDetailsWindow;
    private final BSFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;

    private ScrollPane scrollPane;
    protected GridPane gridPane;
    private TitledGroupBg payFundsTitledGroupBg, setDepositTitledGroupBg, paymentTitledGroupBg;
    private BusyAnimation waitingForFundsBusyAnimation;
    private Button nextButton, cancelButton1, cancelButton2, placeOfferButton, priceTypeToggleButton;
    private InputTextField buyerSecurityDepositInputTextField, fixedPriceTextField, marketBasedPriceTextField;
    protected InputTextField amountTextField, minAmountTextField, volumeTextField;
    private TextField currencyTextField;
    private AddressTextField addressTextField;
    private BalanceTextField balanceTextField;
    private FundsTextField totalToPayTextField;
    private Label directionLabel, amountDescriptionLabel, addressLabel, balanceLabel, totalToPayLabel,
            priceCurrencyLabel, priceDescriptionLabel,
            volumeDescriptionLabel, currencyTextFieldLabel, buyerSecurityDepositLabel, currencyComboBoxLabel,
            waitingForFundsLabel, marketBasedPriceLabel, xLabel, percentagePriceDescription, resultLabel,
            buyerSecurityDepositBtcLabel, paymentAccountsLabel;
    protected Label amountBtcLabel, volumeCurrencyLabel, minAmountBtcLabel;
    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private ComboBox<TradeCurrency> currencyComboBox;
    private ImageView imageView, qrCodeImageView;
    private VBox fixedPriceBox, percentagePriceBox;
    private HBox fundingHBox, firstRowHBox, secondRowHBox, buyerSecurityDepositValueCurrencyBox;

    private Subscription isWaitingForFundsSubscription, balanceSubscription, cancelButton2StyleSubscription;
    private ChangeListener<Boolean> amountFocusedListener, minAmountFocusedListener, volumeFocusedListener,
            buyerSecurityDepositFocusedListener, priceFocusedListener, placeOfferCompletedListener,
            priceAsPercentageFocusedListener;
    private ChangeListener<String> tradeCurrencyCodeListener, errorMessageListener, marketPriceMarginListener;
    private ChangeListener<Number> marketPriceAvailableListener;
    private EventHandler<ActionEvent> currencyComboBoxSelectionHandler, paymentAccountsComboBoxSelectionHandler;
    private OfferView.CloseHandler closeHandler;

    protected int gridRow = 0;
    private final List<Node> editOfferElements = new ArrayList<>();
    private boolean clearXchangeWarningDisplayed, isActivated;
    private ChangeListener<Boolean> getShowWalletFundedNotificationListener;
    private InfoInputTextField marketBasedPriceInfoInputTextField;
    protected TitledGroupBg amountTitledGroupBg;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MutableOfferView(M model, Navigation navigation, Preferences preferences, Transitions transitions,
                            OfferDetailsWindow offerDetailsWindow, BSFormatter btcFormatter, BsqFormatter bsqFormatter) {
        super(model);

        this.navigation = navigation;
        this.preferences = preferences;
        this.transitions = transitions;
        this.offerDetailsWindow = offerDetailsWindow;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    protected void initialize() {
        addScrollPane();
        addGridPane();
        addPaymentGroup();
        addAmountPriceGroup();
        addOptionsGroup();
        addFundingGroup();

        createListeners();

        balanceTextField.setFormatter(model.getBtcFormatter());

        paymentAccountsComboBox.setConverter(GUIUtil.getPaymentAccountsComboBoxStringConverter());

        doSetFocus();
    }

    protected void doSetFocus() {
        GUIUtil.focusWhenAddedToScene(amountTextField);
    }

    @Override
    protected void activate() {
        if (model.getDataModel().isTabSelected)
            doActivate();
    }

    protected void doActivate() {
        if (!isActivated) {
            isActivated = true;
            currencyComboBox.setPrefWidth(250);
            paymentAccountsComboBox.setPrefWidth(250);

            addBindings();
            addListeners();
            addSubscriptions();

            if (waitingForFundsBusyAnimation != null)
                waitingForFundsBusyAnimation.play();

            directionLabel.setText(model.getDirectionLabel());
            amountDescriptionLabel.setText(model.getAmountDescription());
            addressTextField.setAddress(model.getAddressAsString());
            addressTextField.setPaymentLabel(model.getPaymentLabel());

            paymentAccountsComboBox.setItems(model.getDataModel().getPaymentAccounts());
            paymentAccountsComboBox.getSelectionModel().select(model.getPaymentAccount());

            onPaymentAccountsComboBoxSelected();

            balanceTextField.setTargetAmount(model.getDataModel().totalToPayAsCoinProperty().get());
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
        if (isSelected && !model.getDataModel().isTabSelected)
            doActivate();
        else
            deactivate();

        isActivated = isSelected;
        model.getDataModel().onTabSelected(isSelected);
    }

    public void initWithData(OfferPayload.Direction direction, TradeCurrency tradeCurrency) {
        boolean result = model.initWithData(direction, tradeCurrency);

        if (!result) {
            new Popup<>().headLine(Res.get("popup.warning.noTradingAccountSetup.headline"))
                    .instruction(Res.get("popup.warning.noTradingAccountSetup.msg"))
                    .actionButtonTextWithGoTo("navigation.account")
                    .onAction(() -> {
                        navigation.setReturnPath(navigation.getCurrentPath());
                        //noinspection unchecked
                        navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, FiatAccountsView.class);
                    }).show();
        }

        if (direction == OfferPayload.Direction.BUY) {
            imageView.setId("image-buy-large");

            placeOfferButton.setId("buy-button-big");
            placeOfferButton.setText(Res.get("createOffer.placeOfferButton", Res.get("shared.buy")));
            nextButton.setId("buy-button");
            percentagePriceDescription.setText(Res.get("shared.belowInPercent"));
        } else {
            imageView.setId("image-sell-large");
            placeOfferButton.setId("sell-button-big");
            placeOfferButton.setText(Res.get("createOffer.placeOfferButton", Res.get("shared.sell")));
            nextButton.setId("sell-button");
            percentagePriceDescription.setText(Res.get("shared.aboveInPercent"));
        }

        updateMarketPriceAvailable();

        if (!model.getDataModel().isMakerFeeValid() && model.getDataModel().getMakerFee() != null)
            showInsufficientBsqFundsForBtcFeePaymentPopup();
    }

    // called form parent as the view does not get notified when the tab is closed
    public void onClose() {
        // we use model.placeOfferCompleted to not react on close which was triggered by a successful placeOffer
        if (model.getDataModel().getBalance().get().isPositive() && !model.placeOfferCompleted.get()) {
            model.getDataModel().swapTradeToSavings();
            String key = "CreateOfferCancelAndFunded";
            if (preferences.showAgain(key)) {
                //noinspection unchecked
                new Popup<>().information(Res.get("createOffer.alreadyFunded"))
                        .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
                        .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                        .dontShowAgainId(key)
                        .show();
            }
        }
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPlaceOffer() {
        if (model.isReadyForTxBroadcast()) {
            if (model.getDataModel().isMakerFeeValid()) {
                if (model.hasAcceptedArbitrators()) {
                    Offer offer = model.createAndGetOffer();
                    //noinspection PointlessBooleanExpression
                    if (!DevEnv.isDevMode()) {
                        offerDetailsWindow.onPlaceOffer(() ->
                                model.onPlaceOffer(offer, offerDetailsWindow::hide))
                                .show(offer);
                    } else {
                        balanceSubscription.unsubscribe();
                        model.onPlaceOffer(offer, () -> {
                        });
                    }
                } else {
                    new Popup<>().headLine(Res.get("popup.warning.noArbitratorSelected.headline"))
                            .instruction(Res.get("popup.warning.noArbitratorSelected.msg"))
                            .actionButtonTextWithGoTo("navigation.arbitratorSelection")
                            .onAction(() -> {
                                navigation.setReturnPath(navigation.getCurrentPath());
                                //noinspection unchecked
                                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, ArbitratorSelectionView.class);
                            }).show();
                }
            } else {
                showInsufficientBsqFundsForBtcFeePaymentPopup();
            }
        } else {
            model.showNotReadyForTxBroadcastPopups();
        }
    }

    private void showInsufficientBsqFundsForBtcFeePaymentPopup() {
        Coin makerFee = model.getDataModel().getMakerFee(false);
        String message = null;
        if (makerFee != null) {
            message = Res.get("popup.warning.insufficientBsqFundsForBtcFeePayment",
                    bsqFormatter.formatCoinWithCode(makerFee.subtract(model.getDataModel().getBsqBalance())));

        } else if (model.getDataModel().getBsqBalance().isZero())
            message = Res.get("popup.warning.noBsqFundsForBtcFeePayment");

        if (message != null)
            //noinspection unchecked
            new Popup<>().warning(message)
                    .actionButtonTextWithGoTo("navigation.dao.wallet.receive")
                    .onAction(() -> navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, BsqReceiveView.class))
                    .show();
    }

    private void onShowPayFundsScreen() {
        nextButton.setVisible(false);
        nextButton.setManaged(false);
        nextButton.setOnAction(null);
        cancelButton1.setVisible(false);
        cancelButton1.setManaged(false);
        cancelButton1.setOnAction(null);

        int delay = 500;
        int diff = 100;

        transitions.fadeOutAndRemove(setDepositTitledGroupBg, delay, (event) -> {
        });
        delay -= diff;
        transitions.fadeOutAndRemove(buyerSecurityDepositLabel, delay);
        transitions.fadeOutAndRemove(buyerSecurityDepositValueCurrencyBox, delay);

        model.onShowPayFundsScreen();

        editOfferElements.stream().forEach(node -> {
            node.setMouseTransparent(true);
            node.setFocusTraversable(false);
        });

        balanceTextField.setTargetAmount(model.getDataModel().totalToPayAsCoinProperty().get());

        //noinspection PointlessBooleanExpression
        if (!DevEnv.isDevMode()) {
            String key = "securityDepositInfo";
            new Popup<>().backgroundInfo(Res.get("popup.info.securityDepositInfo"))
                    .actionButtonText(Res.get("shared.faq"))
                    .onAction(() -> GUIUtil.openWebPage("https://bisq.network/faq#6"))
                    .useIUnderstandButton()
                    .dontShowAgainId(key)
                    .show();

            key = "createOfferFundWalletInfo";
            String tradeAmountText = model.isSellOffer() ?
                    Res.get("createOffer.createOfferFundWalletInfo.tradeAmount", model.getTradeAmount()) : "";
            String message = Res.get("createOffer.createOfferFundWalletInfo.msg",
                    model.totalToPay.get(),
                    tradeAmountText,
                    model.getSecurityDepositInfo(),
                    model.getMakerFee(),
                    model.getTxFee()
            );
            new Popup<>().headLine(Res.get("createOffer.createOfferFundWalletInfo.headline"))
                    .instruction(message)
                    .dontShowAgainId(key)
                    .show();
        }

        waitingForFundsBusyAnimation.play();

        payFundsTitledGroupBg.setVisible(true);
        totalToPayLabel.setVisible(true);
        totalToPayTextField.setVisible(true);
        addressLabel.setVisible(true);
        addressTextField.setVisible(true);
        qrCodeImageView.setVisible(true);
        balanceLabel.setVisible(true);
        balanceTextField.setVisible(true);
        cancelButton2.setVisible(true);

        totalToPayTextField.setFundsStructure(Res.get("createOffer.fundsBox.fundsStructure",
                model.getSecurityDepositWithCode(), model.getMakerFeePercentage(), model.getTxFeePercentage()));
        totalToPayTextField.setContentForInfoPopOver(createInfoPopover());

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
            UserThread.runAfter(GUIUtil::showClearXchangeWarning,
                    500, TimeUnit.MILLISECONDS);
        }
    }

    protected void onPaymentAccountsComboBoxSelected() {
        // Temporary deactivate handler as the payment account change can populate a new currency list and causes
        // unwanted selection events (item 0)
        currencyComboBox.setOnAction(null);

        PaymentAccount paymentAccount = paymentAccountsComboBox.getSelectionModel().getSelectedItem();
        if (paymentAccount != null) {
            maybeShowClearXchangeWarning(paymentAccount);

            currencyComboBox.setVisible(paymentAccount.hasMultipleCurrencies());
            currencyTextField.setVisible(!paymentAccount.hasMultipleCurrencies());
            currencyTextFieldLabel.setVisible(!paymentAccount.hasMultipleCurrencies());
            if (paymentAccount.hasMultipleCurrencies()) {
                final List<TradeCurrency> tradeCurrencies = paymentAccount.getTradeCurrencies();
                currencyComboBox.setItems(FXCollections.observableArrayList(tradeCurrencies));
                if (paymentAccount.getSelectedTradeCurrency() != null)
                    currencyComboBox.getSelectionModel().select(paymentAccount.getSelectedTradeCurrency());
                else if (tradeCurrencies.contains(model.getTradeCurrency()))
                    currencyComboBox.getSelectionModel().select(model.getTradeCurrency());
                else
                    currencyComboBox.getSelectionModel().select(tradeCurrencies.get(0));

                model.onPaymentAccountSelected(paymentAccount);
            } else {
                TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
                if (singleTradeCurrency != null)
                    currencyTextField.setText(singleTradeCurrency.getNameAndCode());
                model.onPaymentAccountSelected(paymentAccount);
                model.onCurrencySelected(model.getDataModel().getTradeCurrency());
            }
        } else {
            currencyComboBox.setVisible(false);
            currencyTextField.setVisible(true);
            currencyTextFieldLabel.setVisible(true);

            currencyTextField.setText("");
        }

        currencyComboBox.setOnAction(currencyComboBoxSelectionHandler);
    }

    private void onCurrencyComboBoxSelected() {
        model.onCurrencySelected(currencyComboBox.getSelectionModel().getSelectedItem());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void close() {
        if (closeHandler != null)
            closeHandler.close();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        priceCurrencyLabel.textProperty().bind(createStringBinding(() -> btcFormatter.getCounterCurrency(model.tradeCurrencyCode.get()), model.tradeCurrencyCode));

        marketBasedPriceLabel.prefWidthProperty().bind(priceCurrencyLabel.widthProperty());
        volumeCurrencyLabel.textProperty().bind(model.tradeCurrencyCode);
        priceDescriptionLabel.textProperty().bind(createStringBinding(() -> btcFormatter.getPriceWithCurrencyCode(model.tradeCurrencyCode.get(), "shared.fixedPriceInCurForCur"), model.tradeCurrencyCode));
        xLabel.setText("x");
        volumeDescriptionLabel.textProperty().bind(createStringBinding(model.volumeDescriptionLabel::get, model.tradeCurrencyCode, model.volumeDescriptionLabel));
        amountTextField.textProperty().bindBidirectional(model.amount);
        minAmountTextField.textProperty().bindBidirectional(model.minAmount);
        fixedPriceTextField.textProperty().bindBidirectional(model.price);
        marketBasedPriceTextField.textProperty().bindBidirectional(model.marketPriceMargin);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        volumeTextField.promptTextProperty().bind(model.volumePromptLabel);
        totalToPayTextField.textProperty().bind(model.totalToPay);
        addressTextField.amountAsCoinProperty().bind(model.getDataModel().getMissingCoin());
        buyerSecurityDepositInputTextField.textProperty().bindBidirectional(model.buyerSecurityDeposit);

        // Validation
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        minAmountTextField.validationResultProperty().bind(model.minAmountValidationResult);
        fixedPriceTextField.validationResultProperty().bind(model.priceValidationResult);
        volumeTextField.validationResultProperty().bind(model.volumeValidationResult);
        buyerSecurityDepositInputTextField.validationResultProperty().bind(model.buyerSecurityDepositValidationResult);

        // funding
        fundingHBox.visibleProperty().bind(model.getDataModel().getIsBtcWalletFunded().not().and(model.showPayFundsScreenDisplayed));
        fundingHBox.managedProperty().bind(model.getDataModel().getIsBtcWalletFunded().not().and(model.showPayFundsScreenDisplayed));
        waitingForFundsLabel.textProperty().bind(model.waitingForFundsText);
        placeOfferButton.visibleProperty().bind(model.getDataModel().getIsBtcWalletFunded().and(model.showPayFundsScreenDisplayed));
        placeOfferButton.managedProperty().bind(model.getDataModel().getIsBtcWalletFunded().and(model.showPayFundsScreenDisplayed));
        placeOfferButton.disableProperty().bind(model.isPlaceOfferButtonDisabled);
        cancelButton2.disableProperty().bind(model.cancelButtonDisabled);

        // trading account
        paymentAccountsComboBox.managedProperty().bind(paymentAccountsComboBox.visibleProperty());
        paymentAccountsLabel.managedProperty().bind(paymentAccountsLabel.visibleProperty());
        paymentTitledGroupBg.managedProperty().bind(paymentTitledGroupBg.visibleProperty());
        currencyComboBox.prefWidthProperty().bind(paymentAccountsComboBox.widthProperty());
        currencyComboBox.managedProperty().bind(currencyComboBox.visibleProperty());
        currencyComboBoxLabel.visibleProperty().bind(currencyComboBox.visibleProperty());
        currencyComboBoxLabel.managedProperty().bind(currencyComboBoxLabel.visibleProperty());
        currencyTextField.managedProperty().bind(currencyTextField.visibleProperty());
        currencyTextFieldLabel.managedProperty().bind(currencyTextFieldLabel.visibleProperty());
    }

    private void removeBindings() {
        priceCurrencyLabel.textProperty().unbind();
        fixedPriceTextField.disableProperty().unbind();
        priceCurrencyLabel.disableProperty().unbind();
        marketBasedPriceTextField.disableProperty().unbind();
        marketBasedPriceLabel.disableProperty().unbind();
        volumeCurrencyLabel.textProperty().unbind();
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
        buyerSecurityDepositInputTextField.textProperty().unbindBidirectional(model.buyerSecurityDeposit);

        // Validation
        amountTextField.validationResultProperty().unbind();
        minAmountTextField.validationResultProperty().unbind();
        fixedPriceTextField.validationResultProperty().unbind();
        volumeTextField.validationResultProperty().unbind();
        buyerSecurityDepositInputTextField.validationResultProperty().unbind();

        // funding
        fundingHBox.visibleProperty().unbind();
        fundingHBox.managedProperty().unbind();
        waitingForFundsLabel.textProperty().unbind();
        placeOfferButton.visibleProperty().unbind();
        placeOfferButton.managedProperty().unbind();
        placeOfferButton.disableProperty().unbind();
        cancelButton2.disableProperty().unbind();

        // trading account
        paymentTitledGroupBg.managedProperty().unbind();
        paymentAccountsLabel.managedProperty().unbind();
        paymentAccountsComboBox.managedProperty().unbind();
        currencyComboBox.managedProperty().unbind();
        currencyComboBox.prefWidthProperty().unbind();
        currencyComboBoxLabel.visibleProperty().unbind();
        currencyComboBoxLabel.managedProperty().unbind();
        currencyTextField.managedProperty().unbind();
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

        balanceSubscription = EasyBind.subscribe(model.getDataModel().getBalance(), balanceTextField::setBalance);
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
            buyerSecurityDepositInputTextField.setText(model.buyerSecurityDeposit.get());
        };

        errorMessageListener = (o, oldValue, newValue) -> {
            if (newValue != null)
                UserThread.runAfter(() -> new Popup<>().error(Res.get("createOffer.amountPriceBox.error.message", model.errorMessage.get()))
                        .show(), 100, TimeUnit.MILLISECONDS);
        };

        paymentAccountsComboBoxSelectionHandler = e -> onPaymentAccountsComboBoxSelected();
        currencyComboBoxSelectionHandler = e -> onCurrencyComboBoxSelected();

        tradeCurrencyCodeListener = (observable, oldValue, newValue) -> {
            fixedPriceTextField.clear();
            marketBasedPriceTextField.clear();
            volumeTextField.clear();
        };

        placeOfferCompletedListener = (o, oldValue, newValue) -> {
            if (DevEnv.isDevMode()) {
                close();
            } else if (newValue) {
                // We need a bit of delay to avoid issues with fade out/fade in of 2 popups
                String key = "createOfferSuccessInfo";
                if (DontShowAgainLookup.showAgain(key)) {
                    UserThread.runAfter(() -> new Popup<>().headLine(Res.get("createOffer.success.headline"))
                                    .feedback(Res.get("createOffer.success.info"))
                                    .dontShowAgainId(key)
                                    .actionButtonTextWithGoTo("navigation.portfolio.myOpenOffers")
                                    .onAction(() -> {
                                        //noinspection unchecked
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

        getShowWalletFundedNotificationListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                Notification walletFundedNotification = new Notification()
                        .headLine(Res.get("notification.walletUpdate.headline"))
                        .notification(Res.get("notification.walletUpdate.msg", btcFormatter.formatCoinWithCode(model.getDataModel().getTotalToPayAsCoin().get())))
                        .autoClose();

                walletFundedNotification.show();
            }
        };

        marketPriceMarginListener = (observable, oldValue, newValue) -> {
            if (marketBasedPriceInfoInputTextField != null) {
                String tooltip;
                if (newValue.equals("0.00")) {
                    if (model.isSellOffer()) {
                        tooltip = Res.get("createOffer.info.sellAtMarketPrice");
                    } else {
                        tooltip = Res.get("createOffer.info.buyAtMarketPrice");
                    }
                    final Label atMarketPriceLabel = createPopoverLabel(tooltip);
                    marketBasedPriceInfoInputTextField.setContentForInfoPopOver(atMarketPriceLabel);
                } else if (newValue.contains("-")) {
                    if (model.isSellOffer()) {
                        tooltip = Res.get("createOffer.warning.sellBelowMarketPrice", newValue.substring(1));
                    } else {
                        tooltip = Res.get("createOffer.warning.buyAboveMarketPrice", newValue.substring(1));
                    }
                    final Label negativePercentageLabel = createPopoverLabel(tooltip);
                    marketBasedPriceInfoInputTextField.setContentForWarningPopOver(negativePercentageLabel);
                } else if (!newValue.equals("")) {
                    if (model.isSellOffer()) {
                        tooltip = Res.get("createOffer.info.sellAboveMarketPrice", newValue);
                    } else {
                        tooltip = Res.get("createOffer.info.buyBelowMarketPrice", newValue);
                    }
                    final Label positivePercentageLabel = createPopoverLabel(tooltip);
                    marketBasedPriceInfoInputTextField.setContentForInfoPopOver(positivePercentageLabel);
                }
            }
        };
    }

    private Label createPopoverLabel(String text) {
        final Label label = new Label(text);
        label.setPrefWidth(300);
        label.setWrapText(true);
        label.setPadding(new Insets(10));
        return label;
    }

    protected void updateMarketPriceAvailable() {
        int marketPriceAvailableValue = model.marketPriceAvailableProperty.get();
        if (marketPriceAvailableValue > -1) {
            boolean isMarketPriceAvailable = marketPriceAvailableValue == 1;
            percentagePriceBox.setVisible(isMarketPriceAvailable);
            percentagePriceBox.setManaged(isMarketPriceAvailable);
            priceTypeToggleButton.setVisible(isMarketPriceAvailable);
            priceTypeToggleButton.setManaged(isMarketPriceAvailable);
            boolean fixedPriceSelected = !model.getDataModel().getUseMarketBasedPrice().get() || !isMarketPriceAvailable;
            updatePriceToggleButtons(fixedPriceSelected);
        }
    }

    private void addListeners() {
        model.tradeCurrencyCode.addListener(tradeCurrencyCodeListener);
        model.marketPriceAvailableProperty.addListener(marketPriceAvailableListener);
        model.marketPriceMargin.addListener(marketPriceMarginListener);

        // focus out
        amountTextField.focusedProperty().addListener(amountFocusedListener);
        minAmountTextField.focusedProperty().addListener(minAmountFocusedListener);
        fixedPriceTextField.focusedProperty().addListener(priceFocusedListener);
        marketBasedPriceTextField.focusedProperty().addListener(priceAsPercentageFocusedListener);
        volumeTextField.focusedProperty().addListener(volumeFocusedListener);
        buyerSecurityDepositInputTextField.focusedProperty().addListener(buyerSecurityDepositFocusedListener);

        // notifications
        model.getDataModel().getShowWalletFundedNotification().addListener(getShowWalletFundedNotificationListener);

        // warnings
        model.errorMessage.addListener(errorMessageListener);
        // model.getDataModel().feeFromFundingTxProperty.addListener(feeFromFundingTxListener);

        model.placeOfferCompleted.addListener(placeOfferCompletedListener);

        // UI actions
        paymentAccountsComboBox.setOnAction(paymentAccountsComboBoxSelectionHandler);
        currencyComboBox.setOnAction(currencyComboBoxSelectionHandler);
    }

    private void removeListeners() {
        model.tradeCurrencyCode.removeListener(tradeCurrencyCodeListener);
        model.marketPriceAvailableProperty.removeListener(marketPriceAvailableListener);
        model.marketPriceMargin.removeListener(marketPriceMarginListener);

        // focus out
        amountTextField.focusedProperty().removeListener(amountFocusedListener);
        minAmountTextField.focusedProperty().removeListener(minAmountFocusedListener);
        fixedPriceTextField.focusedProperty().removeListener(priceFocusedListener);
        marketBasedPriceTextField.focusedProperty().removeListener(priceAsPercentageFocusedListener);
        volumeTextField.focusedProperty().removeListener(volumeFocusedListener);
        buyerSecurityDepositInputTextField.focusedProperty().removeListener(buyerSecurityDepositFocusedListener);

        // notifications
        model.getDataModel().getShowWalletFundedNotification().removeListener(getShowWalletFundedNotificationListener);

        // warnings
        model.errorMessage.removeListener(errorMessageListener);
        // model.getDataModel().feeFromFundingTxProperty.removeListener(feeFromFundingTxListener);

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
        paymentTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 2, Res.get("shared.selectTradingAccount"));
        GridPane.setColumnSpan(paymentTitledGroupBg, 3);

        //noinspection unchecked
        final Tuple2<Label, ComboBox> paymentAccountLabelComboBoxTuple = addLabelComboBox(gridPane, gridRow, Res.getWithCol("shared.tradingAccount"), Layout.FIRST_ROW_DISTANCE);
        paymentAccountsLabel = paymentAccountLabelComboBoxTuple.first;
        paymentAccountsComboBox = paymentAccountLabelComboBoxTuple.second;
        paymentAccountsComboBox.setPromptText(Res.get("shared.selectTradingAccount"));
        paymentAccountsComboBox.setMinWidth(300);
        editOfferElements.add(paymentAccountsComboBox);

        // we display either currencyComboBox (multi currency account) or currencyTextField (single)
        Tuple2<Label, ComboBox> currencyComboBoxTuple = addLabelComboBox(gridPane, ++gridRow, Res.getWithCol("shared.currency"));
        currencyComboBoxLabel = currencyComboBoxTuple.first;
        editOfferElements.add(currencyComboBoxLabel);
        //noinspection unchecked
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
        currencyTextFieldLabel.setVisible(false);
        editOfferElements.add(currencyTextFieldLabel);
        currencyTextField = currencyTextFieldTuple.second;
        currencyTextField.setVisible(false);
        editOfferElements.add(currencyTextField);
    }

    protected void hidePaymentGroup() {
        paymentTitledGroupBg.setVisible(false);
        paymentAccountsLabel.setVisible(false);
        paymentAccountsComboBox.setVisible(false);
        currencyComboBox.setVisible(false);
        currencyTextFieldLabel.setVisible(false);
        currencyTextField.setVisible(false);
    }

    private void addAmountPriceGroup() {
        amountTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2, Res.get("createOffer.setAmountPrice"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(amountTitledGroupBg, 3);

        imageView = new ImageView();
        imageView.setPickOnBounds(true);
        directionLabel = new AutoTooltipLabel();
        directionLabel.setAlignment(Pos.CENTER);
        directionLabel.setPadding(new Insets(-5, 0, 0, 0));
        directionLabel.setId("direction-icon-label");
        VBox imageVBox = new VBox();
        imageVBox.setAlignment(Pos.CENTER);
        imageVBox.setSpacing(12);
        imageVBox.getChildren().addAll(imageView, directionLabel);
        GridPane.setRowIndex(imageVBox, gridRow);
        GridPane.setRowSpan(imageVBox, 2);
        GridPane.setMargin(imageVBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 10, 10, 10));
        gridPane.getChildren().add(imageVBox);

        addAmountPriceFields();
        addSecondRow();
    }

    private void addOptionsGroup() {
        setDepositTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2, Res.get("createOffer.setDeposit"), Layout.GROUP_DISTANCE);

        addBuyerSecurityDepositRow();

        Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane, ++gridRow,
                Res.get("shared.nextStep"), Res.get("shared.cancel"));
        nextButton = tuple.first;
        editOfferElements.add(nextButton);
        nextButton.disableProperty().bind(model.isNextButtonDisabled);
        cancelButton1 = tuple.second;
        editOfferElements.add(cancelButton1);
        cancelButton1.setDefaultButton(false);
        cancelButton1.setOnAction(e -> {
            close();
            model.getDataModel().swapTradeToSavings();
        });
        cancelButton1.setId("cancel-button");

        GridPane.setMargin(nextButton, new Insets(-35, 0, 0, 0));
        nextButton.setOnAction(e -> {
            if (model.isPriceInRange()) {
                if (DevEnv.DAO_TRADING_ACTIVATED)
                    showFeeOption();
                else
                    onShowPayFundsScreen();
            }
        });
    }

    protected void hideOptionsGroup() {
        setDepositTitledGroupBg.setVisible(false);
        setDepositTitledGroupBg.setManaged(false);
        nextButton.setVisible(false);
        nextButton.setManaged(false);
        cancelButton1.setVisible(false);
        cancelButton1.setManaged(false);
        buyerSecurityDepositLabel.setVisible(false);
        buyerSecurityDepositLabel.setManaged(false);
        buyerSecurityDepositInputTextField.setVisible(false);
        buyerSecurityDepositInputTextField.setManaged(false);
        buyerSecurityDepositBtcLabel.setVisible(false);
        buyerSecurityDepositBtcLabel.setManaged(false);
    }

    private void showFeeOption() {
        Coin makerFee = model.getDataModel().getMakerFee(false);
        String missingBsq = null;
        if (makerFee != null) {
            missingBsq = Res.get("popup.warning.insufficientBsqFundsForBtcFeePayment",
                    bsqFormatter.formatCoinWithCode(makerFee.subtract(model.getDataModel().getBsqBalance())));

        } else if (model.getDataModel().getBsqBalance().isZero())
            missingBsq = Res.get("popup.warning.noBsqFundsForBtcFeePayment");

        new FeeOptionWindow(model.makerFeeWithCode,
                model.getDataModel().isCurrencyForMakerFeeBtc(),
                model.getDataModel().isBsqForFeeAvailable(),
                missingBsq,
                navigation,
                this::onShowPayFundsScreen)
                .onSelectionChangedHandler(model::setIsCurrencyForMakerFeeBtc)
                .onAction(this::onShowPayFundsScreen)
                .hideCloseButton()
                .show();
    }

    private void addBuyerSecurityDepositRow() {
        final double top = model.getDataModel().isBsqForFeeAvailable() ? 0 : Layout.FIRST_ROW_AND_GROUP_DISTANCE;
        buyerSecurityDepositLabel = addLabel(gridPane, ++gridRow,
                Res.getWithCol("shared.securityDepositBox.description", Res.get("shared.buyer")),
                top);

        Tuple3<HBox, InputTextField, Label> tuple = getEditableValueCurrencyBox(
                Res.get("createOffer.securityDeposit.prompt"));
        buyerSecurityDepositValueCurrencyBox = tuple.first;
        buyerSecurityDepositInputTextField = tuple.second;
        buyerSecurityDepositBtcLabel = tuple.third;

        editOfferElements.add(buyerSecurityDepositInputTextField);
        editOfferElements.add(buyerSecurityDepositBtcLabel);

        GridPane.setRowIndex(buyerSecurityDepositValueCurrencyBox, gridRow);
        GridPane.setColumnIndex(buyerSecurityDepositValueCurrencyBox, 1);
        GridPane.setColumnSpan(buyerSecurityDepositValueCurrencyBox, 2);
        GridPane.setMargin(buyerSecurityDepositValueCurrencyBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(buyerSecurityDepositValueCurrencyBox);
    }

    private void addFundingGroup() {
        // don't increase gridRow as we removed button when this gets visible
        payFundsTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 3,
                Res.get("createOffer.fundsBox.title"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(payFundsTitledGroupBg, 3);
        payFundsTitledGroupBg.setVisible(false);

        Tuple2<Label, FundsTextField> fundsTuple = addLabelFundsTextfield(gridPane, gridRow,
                Res.get("shared.totalsNeeded"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        totalToPayLabel = fundsTuple.first;
        totalToPayLabel.setVisible(false);
        totalToPayTextField = fundsTuple.second;
        totalToPayTextField.setVisible(false);

        qrCodeImageView = new ImageView();
        qrCodeImageView.setVisible(false);
        qrCodeImageView.getStyleClass().add("qr-code");
        Tooltip.install(qrCodeImageView, new Tooltip(Res.get("shared.openLargeQRWindow")));
        qrCodeImageView.setOnMouseClicked(e -> GUIUtil.showFeeInfoBeforeExecute(
                () -> UserThread.runAfter(
                        () -> new QRCodeWindow(getBitcoinURI()).show(),
                        200, TimeUnit.MILLISECONDS)));
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
        Button fundFromSavingsWalletButton = new AutoTooltipButton(Res.get("shared.fundFromSavingsWalletButton"));
        fundFromSavingsWalletButton.setDefaultButton(false);
        fundFromSavingsWalletButton.setOnAction(e -> model.fundFromSavingsWallet());
        Label label = new AutoTooltipLabel(Res.get("shared.OR"));
        label.setPadding(new Insets(5, 0, 0, 0));
        Button fundFromExternalWalletButton = new AutoTooltipButton(Res.get("shared.fundFromExternalWalletButton"));
        fundFromExternalWalletButton.setDefaultButton(false);
        fundFromExternalWalletButton.setOnAction(e -> GUIUtil.showFeeInfoBeforeExecute(this::openWallet));
        waitingForFundsBusyAnimation = new BusyAnimation();
        waitingForFundsLabel = new AutoTooltipLabel();
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
            if (model.getDataModel().getIsBtcWalletFunded().get()) {
                new Popup<>().warning(Res.get("createOffer.warnCancelOffer"))
                        .closeButtonText(Res.get("shared.no"))
                        .actionButtonText(Res.get("shared.yesCancel"))
                        .onAction(() -> {
                            close();
                            model.getDataModel().swapTradeToSavings();
                        })
                        .show();
            } else {
                close();
                model.getDataModel().swapTradeToSavings();
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
            new Popup<>().warning(Res.get("shared.openDefaultWalletFailed")).show();
        }
    }

    @NotNull
    private String getBitcoinURI() {
        return GUIUtil.getBitcoinURI(addressTextField.getAddress(), model.getDataModel().getMissingCoin().get(),
                model.getPaymentLabel());
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
        xLabel = new AutoTooltipLabel();
        xLabel.setFont(Font.font("Helvetica-Bold", 20));
        xLabel.setPadding(new Insets(14, 3, 0, 3));
        xLabel.setMinWidth(14);
        xLabel.setMaxWidth(14);

        // price as percent
        Tuple3<HBox, InfoInputTextField, Label> priceAsPercentageTuple = getEditableValueCurrencyBoxWithInfo(Res.get("createOffer.price.prompt"));

        HBox priceAsPercentageValueCurrencyBox = priceAsPercentageTuple.first;
        marketBasedPriceInfoInputTextField = priceAsPercentageTuple.second;
        marketBasedPriceTextField = marketBasedPriceInfoInputTextField.getTextField();
        marketBasedPriceTextField.setPrefWidth(200);
        editOfferElements.add(marketBasedPriceTextField);
        marketBasedPriceLabel = priceAsPercentageTuple.third;
        editOfferElements.add(marketBasedPriceLabel);
        Tuple2<Label, VBox> priceAsPercentageInputBoxTuple = getTradeInputBox(priceAsPercentageValueCurrencyBox,
                Res.get("shared.distanceInPercent"));
        percentagePriceDescription = priceAsPercentageInputBoxTuple.first;
        percentagePriceDescription.setPrefWidth(200);

        getSmallIconForLabel(MaterialDesignIcon.CHART_LINE, percentagePriceDescription);

        percentagePriceBox = priceAsPercentageInputBoxTuple.second;

        // Fixed/Percentage toggle
        priceTypeToggleButton = getIconButton(MaterialDesignIcon.SWAP_VERTICAL);
        editOfferElements.add(priceTypeToggleButton);

        priceTypeToggleButton.setOnAction((actionEvent) -> {
            updatePriceToggleButtons(model.getDataModel().getUseMarketBasedPrice().getValue());
        });

        // =
        resultLabel = new AutoTooltipLabel("=");
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
        firstRowHBox.getChildren().addAll(amountBox, xLabel, percentagePriceBox, priceTypeToggleButton, resultLabel, volumeBox);
        GridPane.setRowIndex(firstRowHBox, gridRow);
        GridPane.setColumnIndex(firstRowHBox, 1);
        GridPane.setMargin(firstRowHBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 10, 0, 0));
        GridPane.setColumnSpan(firstRowHBox, 2);
        gridPane.getChildren().add(firstRowHBox);
    }

    private void updatePriceToggleButtons(boolean fixedPriceSelected) {
        int marketPriceAvailable = model.marketPriceAvailableProperty.get();
        fixedPriceSelected = fixedPriceSelected || (marketPriceAvailable == 0);

        if (marketPriceAvailable == 1) {
            model.getDataModel().setUseMarketBasedPrice(!fixedPriceSelected);
        }

        percentagePriceBox.setDisable(fixedPriceSelected);
        fixedPriceBox.setDisable(!fixedPriceSelected);

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
        fixedPriceTextField.setPrefWidth(200);
        editOfferElements.add(fixedPriceTextField);
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        editOfferElements.add(priceCurrencyLabel);
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox, "");
        priceDescriptionLabel = priceInputBoxTuple.first;

        priceDescriptionLabel.setPrefWidth(200);

        getSmallIconForLabel(MaterialDesignIcon.LOCK, priceDescriptionLabel);

        editOfferElements.add(priceDescriptionLabel);
        fixedPriceBox = priceInputBoxTuple.second;

        marketBasedPriceTextField.setPromptText(Res.get("shared.enterPercentageValue"));
        marketBasedPriceLabel.setText("%");
        marketBasedPriceLabel.getStyleClass().add("percentage-label");

        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueCurrencyBox(
                Res.get("createOffer.amount.prompt"));
        HBox amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        minAmountTextField = amountValueCurrencyBoxTuple.second;
        editOfferElements.add(minAmountTextField);
        minAmountBtcLabel = amountValueCurrencyBoxTuple.third;
        editOfferElements.add(minAmountBtcLabel);

        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox,
                Res.get("createOffer.amountPriceBox.minAmountDescription"));

        Label xLabel = new AutoTooltipLabel("x");
        xLabel.setFont(Font.font("Helvetica-Bold", 20));
        xLabel.setPadding(new Insets(14, 3, 0, 3));
        xLabel.setVisible(false); // we just use it to get the same layout as the upper row

        secondRowHBox = new HBox();
        secondRowHBox.setSpacing(5);
        secondRowHBox.setAlignment(Pos.CENTER_LEFT);
        secondRowHBox.getChildren().addAll(amountInputBoxTuple.second, xLabel, fixedPriceBox, priceTypeToggleButton);
        GridPane.setRowIndex(secondRowHBox, ++gridRow);
        GridPane.setColumnIndex(secondRowHBox, 1);
        GridPane.setMargin(secondRowHBox, new Insets(0, 10, 0, 0));
        GridPane.setColumnSpan(secondRowHBox, 2);
        gridPane.getChildren().add(secondRowHBox);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PayInfo
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GridPane createInfoPopover() {
        GridPane infoGridPane = new GridPane();
        infoGridPane.setHgap(5);
        infoGridPane.setVgap(5);
        infoGridPane.setPadding(new Insets(10, 10, 10, 10));

        int i = 0;
        if (model.isSellOffer())
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.tradeAmount"), model.tradeAmount.get());

        addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.yourSecurityDeposit"), model.getSecurityDepositInfo());
        addPayInfoEntry(infoGridPane, i++, Res.get("createOffer.fundsBox.offerFee"), model.getMakerFee());
        addPayInfoEntry(infoGridPane, i++, Res.get("createOffer.fundsBox.networkFee"), model.getTxFee());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.getStyleClass().add("offer-separator");
        GridPane.setConstraints(separator, 1, i++);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, i, Res.getWithCol("shared.total"), model.getTotalToPayInfo());
        return infoGridPane;
    }

    private void addPayInfoEntry(GridPane infoGridPane, int row, String labelText, String value) {
        Label label = new AutoTooltipLabel(labelText);
        TextField textField = new TextField(value);
        textField.setMinWidth(500);
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        textField.setId("payment-info");
        GridPane.setConstraints(label, 0, row, 1, 1, HPos.RIGHT, VPos.CENTER);
        GridPane.setConstraints(textField, 1, row);
        infoGridPane.getChildren().addAll(label, textField);
    }
}

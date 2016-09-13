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

package io.bitsquare.gui.main.offer.takeoffer;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.app.DevFlags;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.account.content.arbitratorselection.ArbitratorSelectionView;
import io.bitsquare.gui.main.account.settings.AccountSettingsView;
import io.bitsquare.gui.main.funds.FundsView;
import io.bitsquare.gui.main.funds.withdrawal.WithdrawalView;
import io.bitsquare.gui.main.offer.OfferView;
import io.bitsquare.gui.main.overlays.notifications.Notification;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.OfferDetailsWindow;
import io.bitsquare.gui.main.overlays.windows.QRCodeWindow;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Window;
import javafx.util.StringConverter;
import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;
import org.bitcoinj.core.Coin;
import org.bitcoinj.uri.BitcoinURI;
import org.controlsfx.control.PopOver;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.bitsquare.gui.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createStringBinding;

// TODO Implement other positioning method in InoutTextField to display it over the field instead of right side
// priceAmountHBox is too large after redesign as to be used as layoutReference.
@FxmlView
public class TakeOfferView extends ActivatableViewAndModel<AnchorPane, TakeOfferViewModel> {
    private final Navigation navigation;
    private final BSFormatter formatter;
    private final OfferDetailsWindow offerDetailsWindow;
    private final Preferences preferences;
    private ScrollPane scrollPane;
    private GridPane gridPane;
    private ImageView imageView;
    private AddressTextField addressTextField;
    private BalanceTextField balanceTextField;
    private BusyAnimation waitingForFundsBusyAnimation, offerAvailabilityBusyAnimation;
    private TitledGroupBg payFundsPane;
    private Button nextButton, cancelButton1, cancelButton2, fundFromSavingsWalletButton, fundFromExternalWalletButton, takeOfferButton;
    private InputTextField amountTextField;
    private TextField paymentMethodTextField, currencyTextField, priceTextField, priceAsPercentageTextField, volumeTextField, amountRangeTextField;
    private Label directionLabel, amountDescriptionLabel, addressLabel, balanceLabel, totalToPayLabel, totalToPayInfoIconLabel,
            amountBtcLabel, priceCurrencyLabel, priceAsPercentageLabel,
            volumeCurrencyLabel, amountRangeBtcLabel, priceDescriptionLabel, volumeDescriptionLabel, waitingForFundsLabel, offerAvailabilityLabel;
    private TextFieldWithCopyIcon totalToPayTextField;
    private PopOver totalToPayInfoPopover;
    private OfferView.CloseHandler closeHandler;
    private ChangeListener<Boolean> amountFocusedListener;
    private int gridRow = 0;
    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private Label paymentAccountsLabel;
    private Label paymentMethodLabel;
    private Subscription offerWarningSubscription;
    private Subscription errorMessageSubscription, isOfferAvailableSubscription;
    private Subscription isWaitingForFundsSubscription;
    private Subscription showWarningInvalidBtcDecimalPlacesSubscription;
    private Subscription showTransactionPublishedScreenSubscription;
    private SimpleBooleanProperty errorPopupDisplayed;
    private boolean offerDetailsWindowDisplayed;
    private Notification walletFundedNotification;
    private ImageView qrCodeImageView;
    private HBox fundingHBox;
    private Subscription balanceSubscription;
    // private Subscription noSufficientFeeSubscription;
    //  private MonadicBinding<Boolean> noSufficientFeeBinding;
    private Subscription cancelButton2StyleSubscription;
    private VBox priceAsPercentageInputBox;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TakeOfferView(TakeOfferViewModel model, Navigation navigation, BSFormatter formatter,
                          OfferDetailsWindow offerDetailsWindow, Preferences preferences) {
        super(model);

        this.navigation = navigation;
        this.formatter = formatter;
        this.offerDetailsWindow = offerDetailsWindow;
        this.preferences = preferences;
    }

    @Override
    protected void initialize() {
        addScrollPane();
        addGridPane();
        addPaymentGroup();
        addAmountPriceGroup();
        addFundingGroup();

        balanceTextField.setFormatter(model.getFormatter());

        amountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue, amountTextField.getText());
            amountTextField.setText(model.amount.get());
        };
    }


    @Override
    protected void activate() {
        addBindings();
        addSubscriptions();
        amountTextField.focusedProperty().addListener(amountFocusedListener);

        if (offerAvailabilityBusyAnimation != null && !model.showPayFundsScreenDisplayed.get()) {
            offerAvailabilityBusyAnimation.play();
            offerAvailabilityLabel.setVisible(true);
            offerAvailabilityLabel.setManaged(true);
        } else {
            offerAvailabilityLabel.setVisible(false);
            offerAvailabilityLabel.setManaged(false);
        }

        if (waitingForFundsBusyAnimation != null && model.isWaitingForFunds.get()) {
            waitingForFundsBusyAnimation.play();
            waitingForFundsLabel.setVisible(true);
            waitingForFundsLabel.setManaged(true);
        } else {
            waitingForFundsLabel.setVisible(false);
            waitingForFundsLabel.setManaged(false);
        }

        volumeCurrencyLabel.setText(model.dataModel.getCurrencyCode());
        String currencyCode = model.dataModel.getCurrencyCode();
        priceDescriptionLabel.setText(BSResources.get("createOffer.amountPriceBox.priceDescriptionFiat", currencyCode));
       /* priceDescriptionLabel.setText(CurrencyUtil.isCryptoCurrency(currencyCode) ?
                BSResources.get("createOffer.amountPriceBox.priceDescriptionAltcoin", currencyCode) :
                BSResources.get("createOffer.amountPriceBox.priceDescriptionFiat", currencyCode));*/
        volumeDescriptionLabel.setText(model.volumeDescriptionLabel.get());

        if (model.getPossiblePaymentAccounts().size() > 1) {
            paymentAccountsComboBox.setItems(model.getPossiblePaymentAccounts());
            paymentAccountsComboBox.getSelectionModel().select(0);
        }

        balanceTextField.setTargetAmount(model.dataModel.totalToPayAsCoin.get());

       /* if (DevFlags.DEV_MODE)
            UserThread.runAfter(() -> onShowPayFundsScreen(), 200, TimeUnit.MILLISECONDS);*/
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeSubscriptions();
        amountTextField.focusedProperty().removeListener(amountFocusedListener);

        if (offerAvailabilityBusyAnimation != null)
            offerAvailabilityBusyAnimation.stop();

        if (waitingForFundsBusyAnimation != null)
            waitingForFundsBusyAnimation.stop();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer) {
        model.initWithData(offer);
        priceAsPercentageInputBox.setVisible(offer.getUseMarketBasedPrice());

        if (model.getOffer().getDirection() == Offer.Direction.SELL) {
            imageView.setId("image-buy-large");
            directionLabel.setId("direction-icon-label-buy");

            takeOfferButton.setId("buy-button-big");
            takeOfferButton.setText("Review offer for buying bitcoin");
            nextButton.setId("buy-button");
        } else {
            imageView.setId("image-sell-large");
            directionLabel.setId("direction-icon-label-sell");

            takeOfferButton.setId("sell-button-big");
            nextButton.setId("sell-button");
            takeOfferButton.setText("Review offer for selling bitcoin");
        }

        boolean showComboBox = model.getPossiblePaymentAccounts().size() > 1;
        paymentAccountsLabel.setVisible(showComboBox);
        paymentAccountsLabel.setManaged(showComboBox);
        paymentAccountsComboBox.setVisible(showComboBox);
        paymentAccountsComboBox.setManaged(showComboBox);
        paymentMethodTextField.setVisible(!showComboBox);
        paymentMethodTextField.setManaged(!showComboBox);
        paymentMethodLabel.setVisible(!showComboBox);
        paymentMethodLabel.setManaged(!showComboBox);
        if (!showComboBox)
            paymentMethodTextField.setText(BSResources.get(model.getPaymentMethod().getId()));
        currencyTextField.setText(model.dataModel.getCurrencyNameAndCode());
        directionLabel.setText(model.getDirectionLabel());
        amountDescriptionLabel.setText(model.getAmountDescription());
        amountRangeTextField.setText(model.getAmountRange());
        priceTextField.setText(model.getPrice());
        priceAsPercentageTextField.setText(model.marketPriceMargin);
        addressTextField.setPaymentLabel(model.getPaymentLabel());
        addressTextField.setAddress(model.dataModel.getAddressEntry().getAddressString());

        if (offer.getPrice() == null)
            new Popup().warning("You cannot take that offer as it uses a percentage price based on the " +
                    "market price but there is no price feed available.")
                    .onClose(this::close)
                    .show();
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    // called form parent as the view does not get notified when the tab is closed
    public void onClose() {
        Coin balance = model.dataModel.balance.get();
        if (balance != null && balance.isPositive() && !model.takeOfferCompleted.get() && !DevFlags.DEV_MODE) {
            model.dataModel.swapTradeToSavings();
            new Popup().information("You had already funded that offer.\n" +
                    "Your funds have been moved to your local Bitsquare wallet and are available for " +
                    "withdrawal in the \"Funds/Available for withdrawal\" screen.")
                    .actionButtonText("Go to \"Funds/Available for withdrawal\"")
                    .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                    .show();
        }

        // TODO need other implementation as it is displayed also if there are old funds in the wallet
        /*
        if (model.dataModel.isWalletFunded.get())
            new Popup().warning("You have already funds paid in.\nIn the <Funds/Open for withdrawal> section you can withdraw those funds.").show();*/
    }

    public void onTabSelected(boolean isSelected) {
        model.dataModel.onTabSelected(isSelected);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onTakeOffer() {
        if (model.hasAcceptedArbitrators()) {
            if (!DevFlags.DEV_MODE) {
                offerDetailsWindow.onTakeOffer(() ->
                                model.onTakeOffer(() -> {
                                    offerDetailsWindow.hide();
                                    offerDetailsWindowDisplayed = false;
                                })
                ).show(model.getOffer(), model.dataModel.amountAsCoin.get(), model.dataModel.tradePrice);
                offerDetailsWindowDisplayed = true;
            } else {
                model.onTakeOffer(() -> {
                });
            }
        } else {
            new Popup().warning("You have no arbitrator selected.\n" +
                    "You need to select at least one arbitrator.")
                    .actionButtonText("Go to \"Arbitrator selection\"")
                    .onAction(() -> navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, ArbitratorSelectionView.class))
                    .show();
        }
    }

    private void onShowPayFundsScreen() {
        model.onShowPayFundsScreen();

        amountTextField.setMouseTransparent(true);
        amountTextField.setFocusTraversable(false);
        priceTextField.setMouseTransparent(true);
        priceAsPercentageTextField.setMouseTransparent(true);
        volumeTextField.setMouseTransparent(true);

        balanceTextField.setTargetAmount(model.dataModel.totalToPayAsCoin.get());


        if (!DevFlags.DEV_MODE) {
            String key = "securityDepositInfo";
            new Popup().backgroundInfo("To ensure that both traders follow the trade protocol they need to pay a security deposit.\n\n" +
                    "The deposit will stay in your local trading wallet until the offer gets accepted by another trader.\n" +
                    "It will be refunded to you after the trade has successfully completed.")
                    .actionButtonText("Visit FAQ web page")
                    .onAction(() -> GUIUtil.openWebPage("https://bitsquare.io/faq#6"))
                    .closeButtonText("I understand")
                    .dontShowAgainId(key, preferences)
                    .show();

            key = "takeOfferFundWalletInfo";
            String tradeAmountText = model.isSeller() ? "- Trade amount: " + model.getAmount() + "\n" : "";
            new Popup().headLine("Fund your trade").instruction("You need to deposit " +
                    model.totalToPay.get() + " for taking this offer.\n\n" +

                    "The amount is the sum of:\n" +
                    tradeAmountText +
                    "- Security deposit: " + model.getSecurityDeposit() + "\n" +
                    "- Trading fee: " + model.getTakerFee() + "\n" +
                    "- Bitcoin mining fee: " + model.getNetworkFee() + "\n\n" +

                    "For funding you can choose between 2 options:\n" +
                    "- Transfer fund from your Bitsquare wallet OR\n" +
                    "- Transfer fund from any external wallet\n\n" +

                    "If you prefer a higher level of privacy you should use for each trade a distinct funding transaction using the external wallet option.\n" +
                    "If you prefer convenience using the Bitsquare wallet for several trades might be your preferred option.\n\n" +

                    "You can see all the details for funding when you close that popup.")
                    .dontShowAgainId(key, preferences)
                    .show();
        }

        nextButton.setVisible(false);
        offerAvailabilityBusyAnimation.stop();
        cancelButton1.setVisible(false);
        cancelButton1.setOnAction(null);
        cancelButton2.setVisible(true);

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

        setupTotalToPayInfoIconLabel();

        if (model.dataModel.isWalletFunded.get()) {
            if (walletFundedNotification == null) {
                walletFundedNotification = new Notification()
                        .headLine("Trading wallet update")
                        .notification("Your trading wallet was already sufficiently funded from an earlier take offer attempt.\n" +
                                "Amount: " + formatter.formatCoinWithCode(model.dataModel.totalToPayAsCoin.get()))
                        .autoClose();
                walletFundedNotification.show();
            }
        }

        final byte[] imageBytes = QRCode
                .from(getBitcoinURI())
                .withSize(98, 98) // code has 41 elements 8 px is border with 98 we get double scale and min. border
                .to(ImageType.PNG)
                .stream()
                .toByteArray();
        Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
        qrCodeImageView.setImage(qrImage);
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
        amountTextField.textProperty().bindBidirectional(model.amount);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        totalToPayTextField.textProperty().bind(model.totalToPay);
        addressTextField.amountAsCoinProperty().bind(model.dataModel.missingCoin);
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        priceCurrencyLabel.textProperty().bind(createStringBinding(() -> model.dataModel.getCurrencyCode() + "/" + model.btcCode.get(), model.btcCode));
        priceAsPercentageLabel.prefWidthProperty().bind(priceCurrencyLabel.widthProperty());
        amountRangeBtcLabel.textProperty().bind(model.btcCode);
        nextButton.disableProperty().bind(model.isNextButtonDisabled);


        // funding
        fundingHBox.visibleProperty().bind(model.dataModel.isWalletFunded.not().and(model.showPayFundsScreenDisplayed));
        fundingHBox.managedProperty().bind(model.dataModel.isWalletFunded.not().and(model.showPayFundsScreenDisplayed));
        waitingForFundsLabel.textProperty().bind(model.spinnerInfoText);
        takeOfferButton.disableProperty().bind(model.isTakeOfferButtonDisabled);
        takeOfferButton.visibleProperty().bind(model.dataModel.isWalletFunded.and(model.showPayFundsScreenDisplayed));
        takeOfferButton.managedProperty().bind(model.dataModel.isWalletFunded.and(model.showPayFundsScreenDisplayed));
    }

    private void removeBindings() {
        amountBtcLabel.textProperty().unbind();
        amountTextField.textProperty().unbindBidirectional(model.amount);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        totalToPayTextField.textProperty().unbind();
        addressTextField.amountAsCoinProperty().unbind();
        amountTextField.validationResultProperty().unbind();
        priceCurrencyLabel.textProperty().unbind();
        priceAsPercentageLabel.prefWidthProperty().unbind();
        amountRangeBtcLabel.textProperty().unbind();
        nextButton.disableProperty().unbind();

        // funding
        fundingHBox.visibleProperty().unbind();
        fundingHBox.managedProperty().unbind();
        waitingForFundsLabel.textProperty().unbind();
        takeOfferButton.visibleProperty().unbind();
        takeOfferButton.managedProperty().unbind();
        takeOfferButton.disableProperty().unbind();
    }

    private void addSubscriptions() {
        errorPopupDisplayed = new SimpleBooleanProperty();
        offerWarningSubscription = EasyBind.subscribe(model.offerWarning, newValue -> {
            if (newValue != null) {
                if (offerDetailsWindowDisplayed)
                    offerDetailsWindow.hide();

                UserThread.runAfter(() -> new Popup().warning(newValue + "\n\n" +
                        "If you have already paid in funds you can withdraw it in the " +
                        "\"Funds/Available for withdrawal\" screen.")
                        .actionButtonText("Go to \"Available for withdrawal\"")
                        .onAction(() -> {
                            errorPopupDisplayed.set(true);
                            model.resetOfferWarning();
                            close();
                            navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
                        })
                        .onClose(() -> {
                            errorPopupDisplayed.set(true);
                            model.resetOfferWarning();
                            close();
                        })
                        .show(), 100, TimeUnit.MILLISECONDS);
            }
        });

        errorMessageSubscription = EasyBind.subscribe(model.errorMessage, newValue -> {
            if (newValue != null) {
                new Popup().error(BSResources.get("takeOffer.error.message", model.errorMessage.get()) +
                        "Please try to restart you application and check your network connection to see if you can resolve the issue.")
                        .onClose(() -> {
                            errorPopupDisplayed.set(true);
                            model.resetErrorMessage();
                            close();
                        })
                        .show();
            }
        });

        isOfferAvailableSubscription = EasyBind.subscribe(model.isOfferAvailable, isOfferAvailable -> {
            if (isOfferAvailable)
                offerAvailabilityBusyAnimation.stop();

            offerAvailabilityLabel.setVisible(!isOfferAvailable);
            offerAvailabilityLabel.setManaged(!isOfferAvailable);
        });

        isWaitingForFundsSubscription = EasyBind.subscribe(model.isWaitingForFunds, isWaitingForFunds -> {
            waitingForFundsBusyAnimation.setIsRunning(isWaitingForFunds);
            waitingForFundsLabel.setVisible(isWaitingForFunds);
            waitingForFundsLabel.setManaged(isWaitingForFunds);
        });

        showWarningInvalidBtcDecimalPlacesSubscription = EasyBind.subscribe(model.showWarningInvalidBtcDecimalPlaces, newValue -> {
            if (newValue) {
                new Popup().warning(BSResources.get("takeOffer.amountPriceBox.warning.invalidBtcDecimalPlaces")).show();
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        showTransactionPublishedScreenSubscription = EasyBind.subscribe(model.showTransactionPublishedScreen, newValue -> {
            if (newValue && DevFlags.DEV_MODE) {
                close();
            } else if (newValue && model.getTrade() != null && model.getTrade().errorMessageProperty().get() == null) {
                String key = "takeOfferSuccessInfo";
                if (preferences.showAgain(key)) {
                    UserThread.runAfter(() -> new Popup().headLine(BSResources.get("takeOffer.success.headline"))
                            .feedback(BSResources.get("takeOffer.success.info"))
                            .actionButtonText("Go to \"Open trades\"")
                            .dontShowAgainId(key, preferences)
                            .onAction(() -> {
                                UserThread.runAfter(
                                        () -> navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class)
                                        , 100, TimeUnit.MILLISECONDS);
                                close();
                            })
                            .onClose(this::close)
                            .show(), 1);
                } else {
                    close();
                }
            }
        });

 /*       noSufficientFeeBinding = EasyBind.combine(model.dataModel.isWalletFunded, model.dataModel.isMainNet, model.dataModel.isFeeFromFundingTxSufficient,
                (isWalletFunded, isMainNet, isFeeSufficient) -> isWalletFunded && isMainNet && !isFeeSufficient);
        noSufficientFeeSubscription = noSufficientFeeBinding.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                new Popup().warning("The mining fee from your funding transaction is not sufficiently high.\n\n" +
                        "You need to use at least a mining fee of " +
                        model.formatter.formatCoinWithCode(FeePolicy.getMinRequiredFeeForFundingTx()) + ".\n\n" +
                        "The fee used in your funding transaction was only " +
                        model.formatter.formatCoinWithCode(model.dataModel.feeFromFundingTx) + ".\n\n" +
                        "The trade transactions might take too much time to be included in " +
                        "a block if the fee is too low.\n" +
                        "Please check at your external wallet that you set the required fee and " +
                        "do a funding again with the correct fee.\n\n" +
                        "In the \"Funds/Open for withdrawal\" section you can withdraw those funds.")
                        .closeButtonText("Close")
                        .onClose(() -> {
                            close();
                            navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
                        })
                        .show();
        });*/

        balanceSubscription = EasyBind.subscribe(model.dataModel.balance, newValue -> balanceTextField.setBalance(newValue));
        cancelButton2StyleSubscription = EasyBind.subscribe(takeOfferButton.visibleProperty(),
                isVisible -> cancelButton2.setId(isVisible ? "cancel-button" : null));
    }

    private void removeSubscriptions() {
        offerWarningSubscription.unsubscribe();
        errorMessageSubscription.unsubscribe();
        isOfferAvailableSubscription.unsubscribe();
        isWaitingForFundsSubscription.unsubscribe();
        showWarningInvalidBtcDecimalPlacesSubscription.unsubscribe();
        showTransactionPublishedScreenSubscription.unsubscribe();
        // noSufficientFeeSubscription.unsubscribe();
        balanceSubscription.unsubscribe();
        cancelButton2StyleSubscription.unsubscribe();
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
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, gridRow, 2, "Payment info");
        GridPane.setColumnSpan(titledGroupBg, 3);

        Tuple2<Label, ComboBox> tuple = addLabelComboBox(gridPane, gridRow, "Payment account:", Layout.FIRST_ROW_DISTANCE);
        paymentAccountsLabel = tuple.first;
        paymentAccountsLabel.setVisible(false);
        paymentAccountsLabel.setManaged(false);
        paymentAccountsComboBox = tuple.second;
        paymentAccountsComboBox.setPromptText("Select payment account");
        paymentAccountsComboBox.setConverter(new StringConverter<PaymentAccount>() {
            @Override
            public String toString(PaymentAccount paymentAccount) {
                return paymentAccount.getAccountName() + " (" + paymentAccount.getSingleTradeCurrency().getCode() + ", " +
                        BSResources.get(paymentAccount.getPaymentMethod().getId()) + ")";
            }

            @Override
            public PaymentAccount fromString(String s) {
                return null;
            }
        });
        paymentAccountsComboBox.setVisible(false);
        paymentAccountsComboBox.setManaged(false);
        paymentAccountsComboBox.setOnAction(e -> model.onPaymentAccountSelected(paymentAccountsComboBox.getSelectionModel().getSelectedItem()));

        Tuple2<Label, TextField> tuple2 = addLabelTextField(gridPane, gridRow, "Payment method:", "", Layout.FIRST_ROW_DISTANCE);
        paymentMethodLabel = tuple2.first;
        paymentMethodTextField = tuple2.second;
        currencyTextField = addLabelTextField(gridPane, ++gridRow, "Trade currency:", "").second;
    }

    private void addAmountPriceGroup() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2, "Set amount and price", Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 3);

        imageView = new ImageView();
        imageView.setPickOnBounds(true);
        directionLabel = new Label();
        directionLabel.setAlignment(Pos.CENTER);
        directionLabel.setPadding(new Insets(-5, 0, 0, 0));
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

        HBox hBox = new HBox();
        hBox.setSpacing(10);

        nextButton = new Button(BSResources.get("takeOffer.amountPriceBox.next"));
        nextButton.setDefaultButton(true);
        nextButton.setOnAction(e -> onShowPayFundsScreen());

        //UserThread.runAfter(() -> nextButton.requestFocus(), 100, TimeUnit.MILLISECONDS);

        cancelButton1 = new Button(BSResources.get("shared.cancel"));
        cancelButton1.setDefaultButton(false);
        cancelButton1.setId("cancel-button");
        cancelButton1.setOnAction(e -> {
            model.dataModel.swapTradeToSavings();
            close();
        });

        offerAvailabilityBusyAnimation = new BusyAnimation();
        offerAvailabilityLabel = new Label(BSResources.get("takeOffer.fundsBox.isOfferAvailable"));

        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(nextButton, cancelButton1, offerAvailabilityBusyAnimation, offerAvailabilityLabel);

        GridPane.setRowIndex(hBox, ++gridRow);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(-30, 0, 0, 0));
        gridPane.getChildren().add(hBox);
    }

    private void addFundingGroup() {
        // don't increase gridRow as we removed button when this gets visible
        payFundsPane = addTitledGroupBg(gridPane, gridRow, 3, BSResources.get("takeOffer.fundsBox.title"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(payFundsPane, 3);
        payFundsPane.setVisible(false);

        totalToPayLabel = new Label(BSResources.get("takeOffer.fundsBox.totalsNeeded"));
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
        totalToPayTextField.setFocusTraversable(false);
        totalToPayTextField.setVisible(false);
        totalToPayTextField.setPromptText(BSResources.get("createOffer.fundsBox.totalsNeeded.prompt"));
        totalToPayTextField.setCopyWithoutCurrencyPostFix(true);
        GridPane.setRowIndex(totalToPayTextField, gridRow);
        GridPane.setColumnIndex(totalToPayTextField, 1);
        GridPane.setMargin(totalToPayTextField, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(totalToPayTextField);

        qrCodeImageView = new ImageView();
        qrCodeImageView.setVisible(false);
        qrCodeImageView.setStyle("-fx-cursor: hand;");
        Tooltip.install(qrCodeImageView, new Tooltip("Open large QR-Code window"));
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

        Tuple2<Label, AddressTextField> addressTuple = addLabelAddressTextField(gridPane, ++gridRow, BSResources.get("takeOffer.fundsBox.address"));
        addressLabel = addressTuple.first;
        addressLabel.setVisible(false);
        addressTextField = addressTuple.second;
        addressTextField.setVisible(false);

        Tuple2<Label, BalanceTextField> balanceTuple = addLabelBalanceTextField(gridPane, ++gridRow, BSResources.get("takeOffer.fundsBox.balance"));
        balanceLabel = balanceTuple.first;
        balanceLabel.setVisible(false);
        balanceTextField = balanceTuple.second;
        balanceTextField.setVisible(false);

        fundingHBox = new HBox();
        fundingHBox.setVisible(false);
        fundingHBox.setManaged(false);
        fundingHBox.setSpacing(10);
        fundFromSavingsWalletButton = new Button("Transfer funds from Bitsquare wallet");
        fundFromSavingsWalletButton.setDefaultButton(true);
        fundFromSavingsWalletButton.setDefaultButton(false);
        fundFromSavingsWalletButton.setOnAction(e -> model.fundFromSavingsWallet());
        Label label = new Label("OR");
        label.setPadding(new Insets(5, 0, 0, 0));
        fundFromExternalWalletButton = new Button("Open your external wallet for funding");
        fundFromExternalWalletButton.setDefaultButton(false);
        fundFromExternalWalletButton.setOnAction(e -> GUIUtil.showFeeInfoBeforeExecute(this::openWallet));
        waitingForFundsBusyAnimation = new BusyAnimation(false);
        waitingForFundsLabel = new Label();
        waitingForFundsLabel.setPadding(new Insets(5, 0, 0, 0));
        fundingHBox.getChildren().addAll(fundFromSavingsWalletButton, label, fundFromExternalWalletButton, waitingForFundsBusyAnimation, waitingForFundsLabel);
        GridPane.setRowIndex(fundingHBox, ++gridRow);
        GridPane.setColumnIndex(fundingHBox, 1);
        GridPane.setMargin(fundingHBox, new Insets(15, 10, 0, 0));
        gridPane.getChildren().add(fundingHBox);

        takeOfferButton = addButtonAfterGroup(gridPane, gridRow, "");
        takeOfferButton.setVisible(false);
        takeOfferButton.setManaged(false);
        takeOfferButton.setMinHeight(40);
        takeOfferButton.setPadding(new Insets(0, 20, 0, 20));
        takeOfferButton.setOnAction(e -> onTakeOffer());

        cancelButton2 = addButton(gridPane, ++gridRow, BSResources.get("shared.cancel"));
        cancelButton2.setOnAction(e -> {
            if (model.dataModel.isWalletFunded.get()) {
                new Popup().warning("You have already funded that offer.\n" +
                        "If you cancel now, your funds will be moved to your local Bitsquare wallet and are available " +
                        "for withdrawal in the \"Funds/Available for withdrawal\" screen.\n" +
                        "Are you sure you want to cancel?")
                        .closeButtonText("No")
                        .actionButtonText("Yes, cancel")
                        .onAction(() -> {
                            model.dataModel.swapTradeToSavings();
                            close();
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
            new Popup().warning("Opening a default bitcoin wallet application has failed. " +
                    "Perhaps you don't have one installed?").show();
        }
    }

    @NotNull
    private String getBitcoinURI() {
        String addressString = model.dataModel.getAddressEntry().getAddressString();
        return addressString != null ? BitcoinURI.convertToBitcoinURI(addressString, model.dataModel.missingCoin.get(),
                model.getPaymentLabel(), null) : "";
    }

    private void addAmountPriceFields() {
        // amountBox
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getAmountCurrencyBox(BSResources.get("takeOffer.amount.prompt"));
        HBox amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountTextField = amountValueCurrencyBoxTuple.second;
        amountBtcLabel = amountValueCurrencyBoxTuple.third;
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, model.getAmountDescription());
        amountDescriptionLabel = amountInputBoxTuple.first;
        VBox amountBox = amountInputBoxTuple.second;

        // x
        Label xLabel = new Label("x");
        xLabel.setFont(Font.font("Helvetica-Bold", 20));
        xLabel.setPadding(new Insets(14, 3, 0, 3));

        // price
        Tuple3<HBox, TextField, Label> priceValueCurrencyBoxTuple = getValueCurrencyBox();
        HBox priceValueCurrencyBox = priceValueCurrencyBoxTuple.first;
        priceTextField = priceValueCurrencyBoxTuple.second;
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox, BSResources.get("takeOffer.amountPriceBox.priceDescription"));
        priceDescriptionLabel = priceInputBoxTuple.first;
        VBox priceBox = priceInputBoxTuple.second;

        // =
        Label resultLabel = new Label("=");
        resultLabel.setFont(Font.font("Helvetica-Bold", 20));
        resultLabel.setPadding(new Insets(14, 2, 0, 2));

        // volume
        Tuple3<HBox, TextField, Label> volumeValueCurrencyBoxTuple = getValueCurrencyBox();
        HBox volumeValueCurrencyBox = volumeValueCurrencyBoxTuple.first;
        volumeTextField = volumeValueCurrencyBoxTuple.second;
        volumeCurrencyLabel = volumeValueCurrencyBoxTuple.third;
        Tuple2<Label, VBox> volumeInputBoxTuple = getTradeInputBox(volumeValueCurrencyBox, model.volumeDescriptionLabel.get());
        volumeDescriptionLabel = volumeInputBoxTuple.first;
        VBox volumeBox = volumeInputBoxTuple.second;

        HBox hBox = new HBox();
        hBox.setSpacing(5);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(amountBox, xLabel, priceBox, resultLabel, volumeBox);
        GridPane.setRowIndex(hBox, gridRow);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 10, 0, 0));
        GridPane.setColumnSpan(hBox, 2);
        gridPane.getChildren().add(hBox);
    }

    private void addSecondRow() {
        Tuple3<HBox, TextField, Label> priceAsPercentageTuple = getValueCurrencyBox();
        HBox priceAsPercentageValueCurrencyBox = priceAsPercentageTuple.first;
        priceAsPercentageTextField = priceAsPercentageTuple.second;
        priceAsPercentageLabel = priceAsPercentageTuple.third;

        Tuple2<Label, VBox> priceAsPercentageInputBoxTuple = getTradeInputBox(priceAsPercentageValueCurrencyBox, "Distance in % from market price");
        priceAsPercentageInputBoxTuple.first.setPrefWidth(220);
        priceAsPercentageInputBox = priceAsPercentageInputBoxTuple.second;

        priceAsPercentageTextField.setPromptText("Enter % value");
        priceAsPercentageLabel.setText("%");
        priceAsPercentageLabel.setStyle("-fx-alignment: center;");


        Tuple3<HBox, TextField, Label> amountValueCurrencyBoxTuple = getValueCurrencyBox();
        HBox amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountRangeTextField = amountValueCurrencyBoxTuple.second;
        amountRangeBtcLabel = amountValueCurrencyBoxTuple.third;

        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, BSResources.get("takeOffer.amountPriceBox.amountRangeDescription"));

        Label xLabel = new Label("x");
        xLabel.setFont(Font.font("Helvetica-Bold", 20));
        xLabel.setPadding(new Insets(14, 3, 0, 3));
        xLabel.setVisible(false); // we just use it to get the same layout as the upper row

        HBox hBox = new HBox();
        hBox.setSpacing(5);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(amountInputBoxTuple.second, xLabel, priceAsPercentageInputBox);

        GridPane.setRowIndex(hBox, ++gridRow);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(5, 10, 5, 0));
        GridPane.setColumnSpan(hBox, 2);
        gridPane.getChildren().add(hBox);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Tuple2<Label, VBox> getTradeInputBox(HBox amountValueBox, String promptText) {
        Label descriptionLabel = new Label(promptText);
        descriptionLabel.setId("input-description-label");
        descriptionLabel.setPrefWidth(190);

        VBox box = new VBox();
        box.setSpacing(4);
        box.getChildren().addAll(descriptionLabel, amountValueBox);
        return new Tuple2<>(descriptionLabel, box);
    }

    private Tuple3<HBox, InputTextField, Label> getAmountCurrencyBox(String promptText) {
        InputTextField input = new InputTextField();
        input.setPrefWidth(190);
        input.setAlignment(Pos.CENTER_RIGHT);
        input.setId("text-input-with-currency-text-field");
        input.setPromptText(promptText);

        Label currency = new Label();
        currency.setId("currency-info-label");

        HBox box = new HBox();
        box.getChildren().addAll(input, currency);
        return new Tuple3<>(box, input, currency);
    }

    private Tuple3<HBox, TextField, Label> getValueCurrencyBox() {
        TextField textField = new InputTextField();
        textField.setPrefWidth(190);
        textField.setAlignment(Pos.CENTER_RIGHT);
        textField.setId("text-input-with-currency-text-field");
        textField.setMouseTransparent(true);
        textField.setEditable(false);
        textField.setFocusTraversable(false);

        Label currency = new Label();
        currency.setId("currency-info-label-disabled");

        HBox box = new HBox();
        box.getChildren().addAll(textField, currency);
        return new Tuple3<>(box, textField, currency);
    }

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
        if (model.isSeller())
            addPayInfoEntry(infoGridPane, i++, BSResources.get("takeOffer.fundsBox.tradeAmount"), model.getAmount());

        addPayInfoEntry(infoGridPane, i++, BSResources.get("takeOffer.fundsBox.securityDeposit"), model.getSecurityDeposit());
        addPayInfoEntry(infoGridPane, i++, BSResources.get("takeOffer.fundsBox.offerFee"), model.getTakerFee());
        addPayInfoEntry(infoGridPane, i++, BSResources.get("takeOffer.fundsBox.networkFee"), model.getNetworkFee());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #666;");
        GridPane.setConstraints(separator, 1, i++);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, i, BSResources.get("takeOffer.fundsBox.total"),
                model.totalToPay.get());
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


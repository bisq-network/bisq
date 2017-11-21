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

package io.bisq.gui.main.offer.takeoffer;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Tuple3;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferPayload;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.user.DontShowAgainLookup;
import io.bisq.gui.Navigation;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.*;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.account.AccountView;
import io.bisq.gui.main.account.content.arbitratorselection.ArbitratorSelectionView;
import io.bisq.gui.main.account.settings.AccountSettingsView;
import io.bisq.gui.main.dao.DaoView;
import io.bisq.gui.main.dao.wallet.BsqWalletView;
import io.bisq.gui.main.dao.wallet.receive.BsqReceiveView;
import io.bisq.gui.main.funds.FundsView;
import io.bisq.gui.main.funds.withdrawal.WithdrawalView;
import io.bisq.gui.main.offer.OfferView;
import io.bisq.gui.main.overlays.notifications.Notification;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.overlays.windows.OfferDetailsWindow;
import io.bisq.gui.main.overlays.windows.QRCodeWindow;
import io.bisq.gui.main.portfolio.PortfolioView;
import io.bisq.gui.main.portfolio.pendingtrades.PendingTradesView;
import io.bisq.gui.util.*;
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
import org.controlsfx.control.PopOver;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.bisq.gui.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createStringBinding;

@FxmlView
public class TakeOfferView extends ActivatableViewAndModel<AnchorPane, TakeOfferViewModel> {
    private final Navigation navigation;
    private final BSFormatter formatter;
    private final BsqFormatter bsqFormatter;
    private final Transitions transitions;
    private final OfferDetailsWindow offerDetailsWindow;

    private ScrollPane scrollPane;
    private GridPane gridPane;
    private TitledGroupBg payFundsPane, paymentAccountTitledGroupBg, feeCurrencyTitledGroupBg;
    private VBox priceAsPercentageInputBox;
    private HBox fundingHBox, takerFeeRowHBox, buyerSecurityDepositValueCurrencyBox,
        sellerSecurityDepositValueCurrencyBox;
    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private Label directionLabel, amountDescriptionLabel, addressLabel, balanceLabel, totalToPayLabel,
        totalToPayInfoIconLabel, paymentAccountsLabel, paymentMethodLabel,
        amountBtcLabel, priceCurrencyLabel, priceAsPercentageLabel,
        volumeCurrencyLabel, amountRangeBtcLabel, priceDescriptionLabel, volumeDescriptionLabel,
        waitingForFundsLabel, offerAvailabilityLabel, buyerSecurityDepositBtcLabel, sellerSecurityDepositBtcLabel,
        takerFeeCurrencyLabel, takerFeeTextLabel, buyerSecurityDepositLabel, sellerSecurityDepositLabel;
    private InputTextField amountTextField;
    private TextField paymentMethodTextField, currencyTextField, priceTextField, priceAsPercentageTextField,
        volumeTextField, amountRangeTextField, buyerSecurityDepositTextField, takerFeeTextField,
        sellerSecurityDepositTextField;
    private TextFieldWithCopyIcon totalToPayTextField;
    private AddressTextField addressTextField;
    private BalanceTextField balanceTextField;
    private Button nextButton, cancelButton1, cancelButton2, takeOfferButton;
    private ToggleButton payFeeInBsqButton, payFeeInBtcButton;
    private ImageView imageView, qrCodeImageView;
    private BusyAnimation waitingForFundsBusyAnimation, offerAvailabilityBusyAnimation;
    private Notification walletFundedNotification;
    private PopOver totalToPayInfoPopover;
    private OfferView.CloseHandler closeHandler;
    private ChangeListener<Boolean> amountFocusedListener;
    private Subscription cancelButton2StyleSubscription, balanceSubscription,
        showTransactionPublishedScreenSubscription, showWarningInvalidBtcDecimalPlacesSubscription,
        isWaitingForFundsSubscription, offerWarningSubscription, errorMessageSubscription,
        isOfferAvailableSubscription;

    private int gridRow = 0;
    private boolean offerDetailsWindowDisplayed, clearXchangeWarningDisplayed;
    private SimpleBooleanProperty errorPopupDisplayed;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TakeOfferView(TakeOfferViewModel model,
                          Navigation navigation,
                          BSFormatter formatter,
                          BsqFormatter bsqFormatter,
                          Transitions transitions,
                          OfferDetailsWindow offerDetailsWindow) {
        super(model);

        this.navigation = navigation;
        this.formatter = formatter;
        this.bsqFormatter = bsqFormatter;
        this.transitions = transitions;
        this.offerDetailsWindow = offerDetailsWindow;
    }

    @Override
    protected void initialize() {
        addScrollPane();
        addGridPane();
        addPaymentGroup();
        addAmountPriceGroup();
        if (model.dataModel.isBsqForFeeAvailable())
            addFeeCurrencySelection();

        addButtons();
        addOfferAvailabilityLabel();
        addFundingGroup();

        balanceTextField.setFormatter(model.getBtcFormatter());

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

        String currencyCode = model.dataModel.getCurrencyCode();
        volumeCurrencyLabel.setText(currencyCode);
        priceDescriptionLabel.setText(formatter.getPriceWithCurrencyCode(currencyCode));
        volumeDescriptionLabel.setText(model.volumeDescriptionLabel.get());

        if (model.getPossiblePaymentAccounts().size() > 1) {
            paymentAccountsComboBox.setItems(model.getPossiblePaymentAccounts());
            paymentAccountsComboBox.getSelectionModel().select(0);

            paymentAccountTitledGroupBg.setText(Res.get("shared.selectTradingAccount"));
        }

        balanceTextField.setTargetAmount(model.dataModel.totalToPayAsCoin.get());

       /* if (DevFlags.DEV_MODE)
            UserThread.runAfter(() -> onShowPayFundsScreen(), 200, TimeUnit.MILLISECONDS);*/

        maybeShowClearXchangeWarning();
    }

    private void showInsufficientBsqFundsForBtcFeePaymentPopup() {
        Coin takerFee = model.dataModel.getTakerFee(false);
        String message = null;
        if (takerFee != null)
            message = Res.get("popup.warning.insufficientBsqFundsForBtcFeePayment",
                bsqFormatter.formatCoinWithCode(takerFee.subtract(model.dataModel.getBsqBalance())));

        else if (model.dataModel.getBsqBalance().isZero())
            message = Res.get("popup.warning.noBsqFundsForBtcFeePayment");

        if (message != null)
            //noinspection unchecked
            new Popup<>().warning(message)
                .actionButtonTextWithGoTo("navigation.dao.wallet.receive")
                .onAction(() -> navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, BsqReceiveView.class))
                .show();
    }

    private void maybeShowClearXchangeWarning() {
        if (model.getPaymentMethod().getId().equals(PaymentMethod.CLEAR_X_CHANGE_ID) &&
            !clearXchangeWarningDisplayed) {
            clearXchangeWarningDisplayed = true;
            UserThread.runAfter(GUIUtil::showClearXchangeWarning,
                500, TimeUnit.MILLISECONDS);
        }
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
        priceAsPercentageInputBox.setVisible(offer.isUseMarketBasedPrice());

        if (model.getOffer().getDirection() == OfferPayload.Direction.SELL) {
            imageView.setId("image-buy-large");
            directionLabel.setId("direction-icon-label-buy");

            takeOfferButton.setId("buy-button-big");
            takeOfferButton.setText(Res.get("takeOffer.takeOfferButton", Res.get("shared.buy")));
            nextButton.setId("buy-button");
        } else {
            imageView.setId("image-sell-large");
            directionLabel.setId("direction-icon-label-sell");

            takeOfferButton.setId("sell-button-big");
            nextButton.setId("sell-button");
            takeOfferButton.setText(Res.get("takeOffer.takeOfferButton", Res.get("shared.sell")));
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
            paymentMethodTextField.setText(Res.get(model.getPaymentMethod().getId()));
        currencyTextField.setText(model.dataModel.getCurrencyNameAndCode());
        directionLabel.setText(model.getDirectionLabel());
        amountDescriptionLabel.setText(model.getAmountDescription());
        amountRangeTextField.setText(model.getAmountRange());
        priceTextField.setText(model.getPrice());
        priceAsPercentageTextField.setText(model.marketPriceMargin);
        addressTextField.setPaymentLabel(model.getPaymentLabel());
        addressTextField.setAddress(model.dataModel.getAddressEntry().getAddressString());

        if (model.dataModel.isBsqForFeeAvailable()) {
            buyerSecurityDepositTextField.setText(model.getBuyerSecurityDeposit());
            sellerSecurityDepositTextField.setText(model.getSellerSecurityDeposit());
        }

        if (offer.getPrice() == null)
            new Popup<>().warning(Res.get("takeOffer.noPriceFeedAvailable"))
                .onClose(this::close)
                .show();

        if (model.dataModel.isTakerFeeValid()) {
            updateFeeToggleButtons(model.dataModel.isCurrencyForTakerFeeBtc());
        } else {
            if (model.dataModel.getTakerFee() != null)
                showInsufficientBsqFundsForBtcFeePaymentPopup();
            updateFeeToggleButtons(true);
        }
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    // called form parent as the view does not get notified when the tab is closed
    @SuppressWarnings("PointlessBooleanExpression")
    public void onClose() {
        Coin balance = model.dataModel.balance.get();
        //noinspection ConstantConditions,ConstantConditions
        if (balance != null && balance.isPositive() && !model.takeOfferCompleted.get() && !DevEnv.DEV_MODE) {
            model.dataModel.swapTradeToSavings();
            //noinspection unchecked
            new Popup<>().information(Res.get("takeOffer.alreadyFunded.movedFunds"))
                .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
                .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                .show();
        }

        // TODO need other implementation as it is displayed also if there are old funds in the wallet
        /*
        if (model.dataModel.isWalletFunded.get())
            new Popup<>().warning("You have already funds paid in.\nIn the <Funds/Open for withdrawal> section you can withdraw those funds.").show();*/
    }

    public void onTabSelected(boolean isSelected) {
        model.dataModel.onTabSelected(isSelected);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("PointlessBooleanExpression")
    private void onTakeOffer() {
        if (model.dataModel.isTakerFeeValid()) {
            if (model.hasAcceptedArbitrators()) {
                if (!DevEnv.DEV_MODE) {
                    offerDetailsWindow.onTakeOffer(() ->
                            model.onTakeOffer(() -> {
                                offerDetailsWindow.hide();
                                offerDetailsWindowDisplayed = false;
                            })
                    ).show(model.getOffer(), model.dataModel.getAmount().get(), model.dataModel.tradePrice);
                    offerDetailsWindowDisplayed = true;
                } else {
                    balanceSubscription.unsubscribe();
                    model.onTakeOffer(() -> {
                    });
                }
            } else {
                new Popup<>().headLine(Res.get("popup.warning.noArbitratorSelected.headline"))
                    .instruction(Res.get("popup.warning.noArbitratorSelected.msg"))
                    .actionButtonTextWithGoTo("navigation.arbitratorSelection")
                    .onAction(() -> {
                        navigation.setReturnPath(navigation.getCurrentPath());
                        //noinspection unchecked
                        navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class,
                            ArbitratorSelectionView.class);
                    }).show();
            }
        } else {
            showInsufficientBsqFundsForBtcFeePaymentPopup();
        }
    }

    @SuppressWarnings("PointlessBooleanExpression")
    private void onShowPayFundsScreen() {
        model.onShowPayFundsScreen();

        amountTextField.setMouseTransparent(true);
        amountTextField.setFocusTraversable(false);
        priceTextField.setMouseTransparent(true);
        priceAsPercentageTextField.setMouseTransparent(true);
        volumeTextField.setMouseTransparent(true);

        if (model.dataModel.isBsqForFeeAvailable()) {
            payFeeInBtcButton.setMouseTransparent(true);
            payFeeInBsqButton.setMouseTransparent(true);
        }

        balanceTextField.setTargetAmount(model.dataModel.totalToPayAsCoin.get());


        if (!DevEnv.DEV_MODE) {
            String key = "securityDepositInfo";
            new Popup<>().backgroundInfo(Res.get("popup.info.securityDepositInfo"))
                .actionButtonText(Res.get("shared.faq"))
                .onAction(() -> GUIUtil.openWebPage("https://bisq.network/faq#6"))
                .useIUnderstandButton()
                .dontShowAgainId(key)
                .show();


            String tradeAmountText = model.isSeller() ? Res.get("takeOffer.takeOfferFundWalletInfo.tradeAmount", model.getTradeAmount()) : "";
            String message = Res.get("takeOffer.takeOfferFundWalletInfo.msg",
                model.totalToPay.get(),
                tradeAmountText,
                model.getSecurityDepositInfo(),
                model.getTakerFee(),
                model.getTxFee()
            );
            key = "takeOfferFundWalletInfo";
            new Popup<>().headLine(Res.get("takeOffer.takeOfferFundWalletInfo.headline"))
                .instruction(message)
                .dontShowAgainId(key)
                .show();
        }

        offerAvailabilityBusyAnimation.stop();
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
                    .headLine(Res.get("notification.walletUpdate.headline"))
                    .notification(Res.get("notification.takeOffer.walletUpdate.msg", formatter.formatCoinWithCode(model.dataModel.totalToPayAsCoin.get())))
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
        model.dataModel.onClose();
        if (closeHandler != null)
            closeHandler.close();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        amountTextField.textProperty().bindBidirectional(model.amount);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        totalToPayTextField.textProperty().bind(model.totalToPay);
        addressTextField.amountAsCoinProperty().bind(model.dataModel.missingCoin);
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        priceCurrencyLabel.textProperty().bind(createStringBinding(() -> formatter.getCurrencyPair(model.dataModel.getCurrencyCode())));
        priceAsPercentageLabel.prefWidthProperty().bind(priceCurrencyLabel.widthProperty());
        nextButton.disableProperty().bind(model.isNextButtonDisabled);
        if (model.dataModel.isBsqForFeeAvailable()) {
            takerFeeTextField.textProperty().bind(model.takerFee);
            takerFeeCurrencyLabel.textProperty().bind(model.takerFeeCurrencyCode);
        }
        // funding
        fundingHBox.visibleProperty().bind(model.dataModel.isWalletFunded.not().and(model.showPayFundsScreenDisplayed));
        fundingHBox.managedProperty().bind(model.dataModel.isWalletFunded.not().and(model.showPayFundsScreenDisplayed));
        waitingForFundsLabel.textProperty().bind(model.spinnerInfoText);
        takeOfferButton.disableProperty().bind(model.isTakeOfferButtonDisabled);
        takeOfferButton.visibleProperty().bind(model.dataModel.isWalletFunded.and(model.showPayFundsScreenDisplayed));
        takeOfferButton.managedProperty().bind(model.dataModel.isWalletFunded.and(model.showPayFundsScreenDisplayed));
    }

    private void removeBindings() {
        amountTextField.textProperty().unbindBidirectional(model.amount);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        totalToPayTextField.textProperty().unbind();
        addressTextField.amountAsCoinProperty().unbind();
        amountTextField.validationResultProperty().unbind();
        priceCurrencyLabel.textProperty().unbind();
        priceAsPercentageLabel.prefWidthProperty().unbind();
        nextButton.disableProperty().unbind();
        if (model.dataModel.isBsqForFeeAvailable()) {
            takerFeeTextField.textProperty().unbind();
            takerFeeCurrencyLabel.textProperty().unbind();
        }

        // funding
        fundingHBox.visibleProperty().unbind();
        fundingHBox.managedProperty().unbind();
        waitingForFundsLabel.textProperty().unbind();
        takeOfferButton.visibleProperty().unbind();
        takeOfferButton.managedProperty().unbind();
        takeOfferButton.disableProperty().unbind();
    }

    @SuppressWarnings("PointlessBooleanExpression")
    private void addSubscriptions() {
        errorPopupDisplayed = new SimpleBooleanProperty();
        offerWarningSubscription = EasyBind.subscribe(model.offerWarning, newValue -> {
            if (newValue != null) {
                if (offerDetailsWindowDisplayed)
                    offerDetailsWindow.hide();

                UserThread.runAfter(() -> new Popup<>().warning(newValue + "\n\n" +
                    Res.get("takeOffer.alreadyPaidInFunds"))
                    .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
                    .onAction(() -> {
                        errorPopupDisplayed.set(true);
                        model.resetOfferWarning();
                        close();
                        //noinspection unchecked
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
                new Popup<>().error(Res.get("takeOffer.error.message", model.errorMessage.get()) +
                    Res.get("popup.error.tryRestart"))
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
                new Popup<>().warning(Res.get("takeOffer.amountPriceBox.warning.invalidBtcDecimalPlaces")).show();
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        showTransactionPublishedScreenSubscription = EasyBind.subscribe(model.showTransactionPublishedScreen, newValue -> {
            //noinspection ConstantConditions
            if (newValue && DevEnv.DEV_MODE) {
                close();
            } else //noinspection ConstantConditions,ConstantConditions
                if (newValue && model.getTrade() != null && !model.getTrade().hasFailed()) {
                    String key = "takeOfferSuccessInfo";
                    if (DontShowAgainLookup.showAgain(key)) {
                        UserThread.runAfter(() -> new Popup<>().headLine(Res.get("takeOffer.success.headline"))
                            .feedback(Res.get("takeOffer.success.info"))
                            .actionButtonTextWithGoTo("navigation.portfolio.pending")
                            .dontShowAgainId(key)
                            .onAction(() -> {
                                //noinspection unchecked
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
                new Popup<>().warning("The mining fee from your funding transaction is not sufficiently high.\n\n" +
                        "You need to use at least a mining fee of " +
                        model.formatter.formatCoinWithCode(FeePolicy.getMinRequiredFeeForFundingTx()) + ".\n\n" +
                        "The fee used in your funding transaction was only " +
                        model.formatter.formatCoinWithCode(model.dataModel.feeFromFundingTx) + ".\n\n" +
                        "The trade transactions might take too much time to be included in " +
                        "a block if the fee is too low.\n" +
                        "Please check at your external wallet that you set the required fee and " +
                        "do a funding again with the correct fee.\n\n" +
                        "In the \"Funds/Open for withdrawal\" section you can withdraw those funds.")
                        .closeButtonText(Res.get("shared.close"))
                        .onClose(() -> {
                            close();
                            navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
                        })
                        .show();
        });*/

        balanceSubscription = EasyBind.subscribe(model.dataModel.balance, balanceTextField::setBalance);
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
        paymentAccountTitledGroupBg = FormBuilder.addTitledGroupBg(gridPane, gridRow, 2, Res.get("takeOffer.paymentInfo"));
        GridPane.setColumnSpan(paymentAccountTitledGroupBg, 3);

        Tuple2<Label, ComboBox> tuple = FormBuilder.addLabelComboBox(gridPane, gridRow, Res.getWithCol("shared.tradingAccount"), Layout.FIRST_ROW_DISTANCE);
        paymentAccountsLabel = tuple.first;
        paymentAccountsLabel.setVisible(false);
        paymentAccountsLabel.setManaged(false);
        //noinspection unchecked
        paymentAccountsComboBox = tuple.second;
        paymentAccountsComboBox.setPromptText(Res.get("shared.selectTradingAccount"));
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
        paymentAccountsComboBox.setVisible(false);
        paymentAccountsComboBox.setManaged(false);
        paymentAccountsComboBox.setOnAction(e -> {
            maybeShowClearXchangeWarning();
            model.onPaymentAccountSelected(paymentAccountsComboBox.getSelectionModel().getSelectedItem());
        });

        Tuple2<Label, TextField> tuple2 = FormBuilder.addLabelTextField(gridPane, gridRow, Res.getWithCol("shared.paymentMethod"), "", Layout.FIRST_ROW_DISTANCE);
        paymentMethodLabel = tuple2.first;
        paymentMethodTextField = tuple2.second;
        currencyTextField = FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.tradeCurrency"), "").second;
    }

    private void addAmountPriceGroup() {
        TitledGroupBg titledGroupBg = FormBuilder.addTitledGroupBg(gridPane, ++gridRow, 2,
            Res.get("takeOffer.setAmountPrice"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 3);

        imageView = new ImageView();
        imageView.setPickOnBounds(true);
        directionLabel = new Label();
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

    private void addFeeCurrencySelection() {
        feeCurrencyTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 3, Res.get("takeOffer.feeCurrency"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(feeCurrencyTitledGroupBg, 3);

        addMakerFeeRow();
        addBuyerSecurityDepositRow();
        addSellerSecurityDepositRow();
    }

    private void addButtons() {
        nextButton = new Button(Res.get("shared.nextStep"));
        nextButton.setDefaultButton(true);
        nextButton.setOnAction(e -> {
            nextButton.setVisible(false);
            nextButton.setManaged(false);
            nextButton.setOnAction(null);
            cancelButton1.setVisible(false);
            cancelButton1.setManaged(false);
            cancelButton1.setOnAction(null);

            int delay = 500;
            int diff = 100;
            if (model.dataModel.isBsqForFeeAvailable()) {
                transitions.fadeOutAndRemove(feeCurrencyTitledGroupBg, delay, (event) -> onShowPayFundsScreen());
                delay -= diff;
                transitions.fadeOutAndRemove(takerFeeTextLabel, delay);
                transitions.fadeOutAndRemove(takerFeeRowHBox, delay);
                delay -= diff;
                transitions.fadeOutAndRemove(buyerSecurityDepositLabel, delay);
                transitions.fadeOutAndRemove(buyerSecurityDepositValueCurrencyBox, delay);
                delay -= diff;
                transitions.fadeOutAndRemove(sellerSecurityDepositLabel, delay);
                transitions.fadeOutAndRemove(sellerSecurityDepositValueCurrencyBox, delay);
            } else {
                onShowPayFundsScreen();
            }
        });

        cancelButton1 = new Button(Res.get("shared.cancel"));
        cancelButton1.setDefaultButton(false);
        cancelButton1.setId("cancel-button");
        cancelButton1.setOnAction(e -> {
            model.dataModel.swapTradeToSavings();
            close();
        });
    }

    private void addOfferAvailabilityLabel() {
        offerAvailabilityBusyAnimation = new BusyAnimation();
        offerAvailabilityLabel = new Label(Res.get("takeOffer.fundsBox.isOfferAvailable"));

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(nextButton, cancelButton1, offerAvailabilityBusyAnimation, offerAvailabilityLabel);

        GridPane.setRowIndex(hBox, ++gridRow);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(-30, 0, 0, 0));
        gridPane.getChildren().add(hBox);
    }

    private void addMakerFeeRow() {
        takerFeeTextLabel = addLabel(gridPane, gridRow, Res.getWithCol("takeOffer.currencyForFee"),
            Layout.FIRST_ROW_AND_GROUP_DISTANCE);

        Tuple3<HBox, TextField, Label> makerFeeValueCurrencyBoxTuple = getNonEditableValueCurrencyBox();
        HBox makerFeeValueCurrencyBox = makerFeeValueCurrencyBoxTuple.first;
        takerFeeTextField = makerFeeValueCurrencyBoxTuple.second;
        takerFeeCurrencyLabel = makerFeeValueCurrencyBoxTuple.third;

        ToggleGroup feeToggleGroup = new ToggleGroup();
        payFeeInBsqButton = new ToggleButton("BSQ");
        payFeeInBsqButton.setPrefWidth(70);
        payFeeInBsqButton.setId("toggle-price-left");
        payFeeInBsqButton.setToggleGroup(feeToggleGroup);
        payFeeInBsqButton.selectedProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue && model.dataModel.isBsqForFeeAvailable())
                updateFeeToggleButtons(false);
            else
                updateFeeToggleButtons(true);
        });
        payFeeInBsqButton.setOnAction(ev -> {
            if (model.dataModel.isBsqForFeeAvailable()) {
                updateFeeToggleButtons(false);
            } else {
                showInsufficientBsqFundsForBtcFeePaymentPopup();
                updateFeeToggleButtons(true);
            }
        });

        payFeeInBtcButton = new ToggleButton("BTC");
        payFeeInBtcButton.setPrefWidth(payFeeInBsqButton.getPrefWidth());
        payFeeInBtcButton.setId("toggle-price-right");
        payFeeInBtcButton.setToggleGroup(feeToggleGroup);
        payFeeInBtcButton.selectedProperty().addListener((ov, oldValue, newValue) -> {
            updateFeeToggleButtons(newValue);
        });

        HBox payFeeInBtcToggleButtonsHBox = new HBox();
        payFeeInBtcToggleButtonsHBox.getChildren().addAll(payFeeInBsqButton, payFeeInBtcButton);

        takerFeeRowHBox = new HBox();
        takerFeeRowHBox.setSpacing(5);
        takerFeeRowHBox.setAlignment(Pos.CENTER_LEFT);

        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq())
            takerFeeRowHBox.getChildren().addAll(makerFeeValueCurrencyBox, payFeeInBtcToggleButtonsHBox);
        else
            takerFeeRowHBox.getChildren().addAll(makerFeeValueCurrencyBox);

        GridPane.setRowIndex(takerFeeRowHBox, gridRow);
        GridPane.setColumnIndex(takerFeeRowHBox, 1);
        GridPane.setMargin(takerFeeRowHBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        GridPane.setColumnSpan(takerFeeRowHBox, 2);
        gridPane.getChildren().add(takerFeeRowHBox);
    }

    private void addBuyerSecurityDepositRow() {
        buyerSecurityDepositLabel = addLabel(gridPane, ++gridRow,
            Res.getWithCol("shared.securityDepositBox.description", Res.get("shared.buyer")),
            0);

        Tuple3<HBox, TextField, Label> tuple = getNonEditableValueCurrencyBox();
        buyerSecurityDepositValueCurrencyBox = tuple.first;
        buyerSecurityDepositTextField = tuple.second;
        buyerSecurityDepositBtcLabel = tuple.third;
        buyerSecurityDepositBtcLabel.setMinWidth(takerFeeCurrencyLabel.getMinWidth());
        buyerSecurityDepositBtcLabel.setMaxWidth(takerFeeCurrencyLabel.getMaxWidth());

        GridPane.setRowIndex(buyerSecurityDepositValueCurrencyBox, gridRow);
        GridPane.setColumnIndex(buyerSecurityDepositValueCurrencyBox, 1);
        GridPane.setColumnSpan(buyerSecurityDepositValueCurrencyBox, 2);
        gridPane.getChildren().add(buyerSecurityDepositValueCurrencyBox);
    }

    private void addSellerSecurityDepositRow() {
        sellerSecurityDepositLabel = addLabel(gridPane, ++gridRow,
            Res.getWithCol("shared.securityDepositBox.description", Res.get("shared.seller")),
            0);

        Tuple3<HBox, TextField, Label> tuple = getNonEditableValueCurrencyBox();
        sellerSecurityDepositValueCurrencyBox = tuple.first;
        sellerSecurityDepositTextField = tuple.second;
        sellerSecurityDepositBtcLabel = tuple.third;
        sellerSecurityDepositBtcLabel.setMinWidth(takerFeeCurrencyLabel.getMinWidth());
        sellerSecurityDepositBtcLabel.setMaxWidth(takerFeeCurrencyLabel.getMaxWidth());

        GridPane.setRowIndex(sellerSecurityDepositValueCurrencyBox, gridRow);
        GridPane.setColumnIndex(sellerSecurityDepositValueCurrencyBox, 1);
        GridPane.setColumnSpan(sellerSecurityDepositValueCurrencyBox, 2);
        gridPane.getChildren().add(sellerSecurityDepositValueCurrencyBox);
    }

    private void addFundingGroup() {
        // don't increase gridRow as we removed button when this gets visible
        payFundsPane = FormBuilder.addTitledGroupBg(gridPane, gridRow, 3, Res.get("takeOffer.fundsBox.title"), Layout.GROUP_DISTANCE);
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
        totalToPayTextField.setFocusTraversable(false);
        totalToPayTextField.setVisible(false);
        totalToPayTextField.setPromptText(Res.get("createOffer.fundsBox.totalsNeeded.prompt"));
        totalToPayTextField.setCopyWithoutCurrencyPostFix(true);
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
                200, TimeUnit.MILLISECONDS)));
        GridPane.setRowIndex(qrCodeImageView, gridRow);
        GridPane.setColumnIndex(qrCodeImageView, 2);
        GridPane.setRowSpan(qrCodeImageView, 3);
        GridPane.setMargin(qrCodeImageView, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE - 9, 0, 0, 5));
        gridPane.getChildren().add(qrCodeImageView);

        Tuple2<Label, AddressTextField> addressTuple = FormBuilder.addLabelAddressTextField(gridPane, ++gridRow, Res.get("shared.tradeWalletAddress"));
        addressLabel = addressTuple.first;
        addressLabel.setVisible(false);
        addressTextField = addressTuple.second;
        addressTextField.setVisible(false);

        Tuple2<Label, BalanceTextField> balanceTuple = FormBuilder.addLabelBalanceTextField(gridPane, ++gridRow, Res.get("shared.tradeWalletBalance"));
        balanceLabel = balanceTuple.first;
        balanceLabel.setVisible(false);
        balanceTextField = balanceTuple.second;
        balanceTextField.setVisible(false);

        fundingHBox = new HBox();
        fundingHBox.setVisible(false);
        fundingHBox.setManaged(false);
        fundingHBox.setSpacing(10);
        Button fundFromSavingsWalletButton = new Button(Res.get("shared.fundFromSavingsWalletButton"));
        fundFromSavingsWalletButton.setDefaultButton(true);
        fundFromSavingsWalletButton.setDefaultButton(false);
        fundFromSavingsWalletButton.setOnAction(e -> model.fundFromSavingsWallet());
        Label label = new Label(Res.get("shared.OR"));
        label.setPadding(new Insets(5, 0, 0, 0));
        Button fundFromExternalWalletButton = new Button(Res.get("shared.fundFromExternalWalletButton"));
        fundFromExternalWalletButton.setDefaultButton(false);
        fundFromExternalWalletButton.setOnAction(e -> GUIUtil.showFeeInfoBeforeExecute(this::openWallet));
        waitingForFundsBusyAnimation = new BusyAnimation(false);
        waitingForFundsLabel = new Label();
        waitingForFundsLabel.setPadding(new Insets(5, 0, 0, 0));
        fundingHBox.getChildren().addAll(fundFromSavingsWalletButton,
            label,
            fundFromExternalWalletButton,
            waitingForFundsBusyAnimation,
            waitingForFundsLabel);

        GridPane.setRowIndex(fundingHBox, ++gridRow);
        GridPane.setColumnIndex(fundingHBox, 1);
        GridPane.setMargin(fundingHBox, new Insets(15, 10, 0, 0));
        gridPane.getChildren().add(fundingHBox);

        takeOfferButton = FormBuilder.addButtonAfterGroup(gridPane, gridRow, "");
        takeOfferButton.setVisible(false);
        takeOfferButton.setManaged(false);
        takeOfferButton.setMinHeight(40);
        takeOfferButton.setPadding(new Insets(0, 20, 0, 20));
        takeOfferButton.setOnAction(e -> onTakeOffer());

        cancelButton2 = FormBuilder.addButton(gridPane, ++gridRow, Res.get("shared.cancel"));
        cancelButton2.setOnAction(e -> {
            if (model.dataModel.isWalletFunded.get()) {
                new Popup<>().warning(Res.get("takeOffer.alreadyFunded.askCancel"))
                    .closeButtonText(Res.get("shared.no"))
                    .actionButtonText(Res.get("shared.yesCancel"))
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
            new Popup<>().warning(Res.get("shared.openDefaultWalletFailed")).show();
        }
    }

    @NotNull
    private String getBitcoinURI() {
        return GUIUtil.getBitcoinURI(model.dataModel.getAddressEntry().getAddressString(),
            model.dataModel.missingCoin.get(),
            model.getPaymentLabel());
    }

    private void addAmountPriceFields() {
        // amountBox
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getAmountCurrencyBox(Res.get("takeOffer.amount.prompt"));
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
        Tuple3<HBox, TextField, Label> priceValueCurrencyBoxTuple = getNonEditableValueCurrencyBox();
        HBox priceValueCurrencyBox = priceValueCurrencyBoxTuple.first;
        priceTextField = priceValueCurrencyBoxTuple.second;
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox,
            Res.get("takeOffer.amountPriceBox.priceDescription"));
        priceDescriptionLabel = priceInputBoxTuple.first;
        VBox priceBox = priceInputBoxTuple.second;

        // =
        Label resultLabel = new Label("=");
        resultLabel.setFont(Font.font("Helvetica-Bold", 20));
        resultLabel.setPadding(new Insets(14, 2, 0, 2));

        // volume
        Tuple3<HBox, TextField, Label> volumeValueCurrencyBoxTuple = getNonEditableValueCurrencyBox();
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
        Tuple3<HBox, TextField, Label> priceAsPercentageTuple = getNonEditableValueCurrencyBox();
        HBox priceAsPercentageValueCurrencyBox = priceAsPercentageTuple.first;
        priceAsPercentageTextField = priceAsPercentageTuple.second;
        priceAsPercentageLabel = priceAsPercentageTuple.third;

        Tuple2<Label, VBox> priceAsPercentageInputBoxTuple = getTradeInputBox(priceAsPercentageValueCurrencyBox,
            Res.get("shared.distanceInPercent"));
        priceAsPercentageInputBoxTuple.first.setPrefWidth(220);
        priceAsPercentageInputBox = priceAsPercentageInputBoxTuple.second;

        priceAsPercentageLabel.setText("%");
        priceAsPercentageLabel.setStyle("-fx-alignment: center;");


        Tuple3<HBox, TextField, Label> amountValueCurrencyBoxTuple = getNonEditableValueCurrencyBox();
        HBox amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountRangeTextField = amountValueCurrencyBoxTuple.second;
        amountRangeBtcLabel = amountValueCurrencyBoxTuple.third;

        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox,
            Res.get("takeOffer.amountPriceBox.amountRangeDescription"));

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

    private void updateFeeToggleButtons(boolean btcSelected) {
        if (model.dataModel.isBsqForFeeAvailable()) {
            model.setCurrencyForTakerFeeBtc(btcSelected);
            if (btcSelected || model.dataModel.isBsqForFeeAvailable()) {
                if (!payFeeInBtcButton.isSelected() && btcSelected)
                    payFeeInBtcButton.setSelected(true);
                if (payFeeInBsqButton.isSelected() && !btcSelected)
                    payFeeInBsqButton.setSelected(false);

                payFeeInBtcButton.setMouseTransparent(btcSelected);
                payFeeInBsqButton.setMouseTransparent(!btcSelected);

                payFeeInBtcButton.setStyle(btcSelected ?
                    "-fx-background-color: -bs-blue-transparent" : "-fx-background-color: -bs-very-light-grey");
                payFeeInBsqButton.setStyle(!btcSelected ?
                    "-fx-background-color: -bs-blue-transparent" : "-fx-background-color: -bs-very-light-grey");
            }
        }
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
            addPayInfoEntry(infoGridPane, i++, Res.get("takeOffer.fundsBox.tradeAmount"), model.getTradeAmount());

        addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.yourSecurityDeposit"), model.getSecurityDepositInfo());
        addPayInfoEntry(infoGridPane, i++, Res.get("takeOffer.fundsBox.offerFee"), model.getTakerFee());
        addPayInfoEntry(infoGridPane, i++, Res.get("takeOffer.fundsBox.networkFee"), model.getTxFee());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setStyle("-fx-background: #666;");
        GridPane.setConstraints(separator, 1, i++);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, i, Res.getWithCol("shared.total"),
            model.getTotalToPayInfo());
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
        textField.setMinWidth(500);
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


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
import io.bitsquare.app.BitsquareApp;
import io.bitsquare.btc.FeePolicy;
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
    private ProgressIndicator spinner, offerAvailabilitySpinner;
    private TitledGroupBg payFundsPane;
    private Button nextButton, takeOfferButton, cancelButton1, cancelButton2;
    private InputTextField amountTextField;
    private TextField paymentMethodTextField, currencyTextField, priceTextField, volumeTextField, amountRangeTextField;
    private Label directionLabel, amountDescriptionLabel, addressLabel, balanceLabel, totalToPayLabel, totalToPayInfoIconLabel,
            amountBtcLabel, priceCurrencyLabel,
            volumeCurrencyLabel, amountRangeBtcLabel, priceDescriptionLabel, volumeDescriptionLabel, spinnerInfoLabel, offerAvailabilitySpinnerLabel;
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
    private Subscription isSpinnerVisibleSubscription;
    private Subscription showWarningInvalidBtcDecimalPlacesSubscription;
    private Subscription showTransactionPublishedScreenSubscription;
    private SimpleBooleanProperty errorPopupDisplayed;
    private ChangeListener<Coin> feeFromFundingTxListener;
    private boolean offerDetailsWindowDisplayed;
    private Notification walletFundedNotification;
    private Subscription isWalletFundedSubscription;
    private ImageView qrCodeImageView;


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

        amountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue, amountTextField.getText());
            amountTextField.setText(model.amount.get());
        };
    }

    @Override
    protected void activate() {
        paymentAccountsComboBox.setOnAction(e -> onPaymentAccountsComboBoxSelected());

        amountTextField.focusedProperty().addListener(amountFocusedListener);

        amountBtcLabel.textProperty().bind(model.btcCode);
        amountTextField.textProperty().bindBidirectional(model.amount);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        totalToPayTextField.textProperty().bind(model.totalToPay);
        addressTextField.amountAsCoinProperty().bind(model.totalToPayAsCoin);
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        takeOfferButton.disableProperty().bind(model.isTakeOfferButtonDisabled);

        spinner.visibleProperty().bind(model.isSpinnerVisible);
        spinnerInfoLabel.visibleProperty().bind(model.isSpinnerVisible);
        spinnerInfoLabel.textProperty().bind(model.spinnerInfoText);

        priceCurrencyLabel.textProperty().bind(createStringBinding(() ->
                model.dataModel.getCurrencyCode() + "/" + model.btcCode.get(), model.btcCode));

        volumeCurrencyLabel.setText(model.dataModel.getCurrencyCode());
        amountRangeBtcLabel.textProperty().bind(model.btcCode);

        priceDescriptionLabel.setText(BSResources.get("createOffer.amountPriceBox.priceDescription", model.dataModel.getCurrencyCode()));
        volumeDescriptionLabel.setText(model.volumeDescriptionLabel.get());

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
        isOfferAvailableSubscription = EasyBind.subscribe(model.isOfferAvailable, newValue -> {
            if (newValue) {
                offerAvailabilitySpinner.setProgress(0);
                offerAvailabilitySpinner.setVisible(false);
                offerAvailabilitySpinnerLabel.setVisible(false);
            }
        });

        isSpinnerVisibleSubscription = EasyBind.subscribe(model.isSpinnerVisible,
                isSpinnerVisible -> spinner.setProgress(isSpinnerVisible ? -1 : 0));

        showWarningInvalidBtcDecimalPlacesSubscription = EasyBind.subscribe(model.showWarningInvalidBtcDecimalPlaces, newValue -> {
            if (newValue) {
                new Popup().warning(BSResources.get("takeOffer.amountPriceBox.warning.invalidBtcDecimalPlaces")).show();
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });
        showTransactionPublishedScreenSubscription = EasyBind.subscribe(model.showTransactionPublishedScreen, newValue -> {
            if (newValue && BitsquareApp.DEV_MODE) {
                close();
                navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);
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

        if (model.getPossiblePaymentAccounts().size() > 1) {
            paymentAccountsComboBox.setItems(model.getPossiblePaymentAccounts());
            paymentAccountsComboBox.getSelectionModel().select(0);
        }

        feeFromFundingTxListener = (observable, oldValue, newValue) -> {
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
                        .closeButtonText("Close")
                        .onClose(() -> {
                            close();
                            navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
                        })
                        .show();
            }
        };
        model.dataModel.feeFromFundingTxProperty.addListener(feeFromFundingTxListener);

        if (offerAvailabilitySpinner != null && offerAvailabilitySpinner.isVisible())
            offerAvailabilitySpinner.setProgress(-1);

        if (spinner != null && spinner.isVisible())
            spinner.setProgress(-1);
    }

    @Override
    protected void deactivate() {
        paymentAccountsComboBox.setOnAction(null);

        amountTextField.focusedProperty().removeListener(amountFocusedListener);

        amountBtcLabel.textProperty().unbind();
        amountTextField.textProperty().unbindBidirectional(model.amount);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        totalToPayTextField.textProperty().unbind();
        addressTextField.amountAsCoinProperty().unbind();
        amountTextField.validationResultProperty().unbind();
        takeOfferButton.disableProperty().unbind();
        spinner.visibleProperty().unbind();
        spinnerInfoLabel.visibleProperty().unbind();
        spinnerInfoLabel.textProperty().unbind();
        priceCurrencyLabel.textProperty().unbind();
        volumeCurrencyLabel.textProperty().unbind();
        amountRangeBtcLabel.textProperty().unbind();
        priceDescriptionLabel.textProperty().unbind();
        volumeDescriptionLabel.textProperty().unbind();

        offerWarningSubscription.unsubscribe();
        errorMessageSubscription.unsubscribe();
        isOfferAvailableSubscription.unsubscribe();
        isSpinnerVisibleSubscription.unsubscribe();
        showWarningInvalidBtcDecimalPlacesSubscription.unsubscribe();
        showTransactionPublishedScreenSubscription.unsubscribe();

        model.dataModel.feeFromFundingTxProperty.removeListener(feeFromFundingTxListener);
        if (balanceTextField != null)
            balanceTextField.cleanup();

        if (offerAvailabilitySpinner != null)
            offerAvailabilitySpinner.setProgress(0);

        if (spinner != null)
            spinner.setProgress(0);

        if (isWalletFundedSubscription != null)
            isWalletFundedSubscription.unsubscribe();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer) {
        model.initWithData(offer);

        if (model.getOffer().getDirection() == Offer.Direction.SELL) {
            imageView.setId("image-buy-large");
            directionLabel.setId("direction-icon-label-buy");

            takeOfferButton.setId("buy-button-big");
            takeOfferButton.setText("Review take offer for buying bitcoin");
            nextButton.setId("buy-button");
        } else {
            imageView.setId("image-sell-large");
            directionLabel.setId("direction-icon-label-sell");

            takeOfferButton.setId("sell-button-big");
            nextButton.setId("sell-button");
            takeOfferButton.setText("Review take offer for selling bitcoin");
        }

        balanceTextField.setFormatter(model.getFormatter());
        balanceTextField.setupBalanceListener(model.address.get());

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
        addressTextField.setPaymentLabel(model.getPaymentLabel());
        addressTextField.setAddress(model.getAddressAsString());
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    // called form parent as the view does not get notified when the tab is closed
    public void onClose() {
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
            offerDetailsWindow.onTakeOffer(() ->
                            model.onTakeOffer(() -> {
                                offerDetailsWindow.hide();
                                offerDetailsWindowDisplayed = false;
                            })
            ).show(model.getOffer(), model.dataModel.amountAsCoin.get());
            offerDetailsWindowDisplayed = true;
        } else {
            new Popup().warning("You have no arbitrator selected.\n" +
                    "You need to select at least one arbitrator.")
                    .actionButtonText("Go to \"Arbitrator selection\"")
                    .onAction(() -> navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, ArbitratorSelectionView.class))
                    .show();
        }
    }

    private void onPaymentAccountsComboBoxSelected() {
        model.onPaymentAccountSelected(paymentAccountsComboBox.getSelectionModel().getSelectedItem());
    }

    private void onShowPayFundsScreen() {
        model.onShowPayFundsScreen();

        amountTextField.setMouseTransparent(true);
        priceTextField.setMouseTransparent(true);
        volumeTextField.setMouseTransparent(true);

        if (!BitsquareApp.DEV_MODE) {
            String key = "securityDepositInfo";
            new Popup().backgroundInfo("To ensure that both traders follow the trade protocol they need to pay a security deposit.\n\n" +
                    "The deposit will stay in your local trading wallet until the offer gets accepted by another trader.\n" +
                    "It will be refunded to you after the trade has successfully completed.")
                    .actionButtonText("Visit FAQ web page")
                    .onAction(() -> Utilities.openWebPage("https://bitsquare.io/faq#6"))
                    .closeButtonText("I understand")
                    .dontShowAgainId(key, preferences)
                    .show();

            key = "takeOfferFundWalletInfo";
            String tradeAmountText = model.isSeller() ? "the trade amount, " : "";
            new Popup().headLine("Fund your trading wallet")
                    .instruction("You need to pay in " +
                            model.totalToPay.get() + " to your local Bitsquare trading wallet.\n" +
                            "The amount is the sum of " + tradeAmountText + "the security deposit, the trading fee and " +
                            "the bitcoin mining fee.\n\n" +
                            "Please send from your external Bitcoin wallet the exact amount to the address: " +
                            model.getAddressAsString() + "\n(you can copy the address in the screen below after closing that popup)\n\n" +
                            "Make sure you use a sufficiently high mining fee of at least " +
                            model.formatter.formatCoinWithCode(FeePolicy.getMinRequiredFeeForFundingTx()) +
                            " to avoid problems that your transaction does not get confirmed in the blockchain.\n" +
                            "Transactions with a lower fee will not be accepted.\n\n" +
                            "You can see the status of your incoming payment and all the details in the screen below.")
                    .dontShowAgainId(key, preferences)
                    .show();
        }

        nextButton.setVisible(false);
        offerAvailabilitySpinner.setProgress(0);
        offerAvailabilitySpinner.setVisible(false);
        offerAvailabilitySpinnerLabel.setVisible(false);
        cancelButton1.setVisible(false);
        cancelButton1.setOnAction(null);
        takeOfferButton.setVisible(true);
        cancelButton2.setVisible(true);

        spinner.setProgress(-1);

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
        } else {
            isWalletFundedSubscription = EasyBind.subscribe(model.dataModel.isWalletFunded, isFunded -> {
                if (isFunded) {
                    if (walletFundedNotification == null) {
                        walletFundedNotification = new Notification()
                                .headLine("Trading wallet update")
                                .notification("Your trading wallet is sufficiently funded.\n" +
                                        "Amount: " + formatter.formatCoinWithCode(model.dataModel.totalToPayAsCoin.get()))
                                .autoClose();
                        walletFundedNotification.show();
                    }
                }
            });
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

        addAmountRangeBox();

        HBox hBox = new HBox();
        hBox.setSpacing(10);

        nextButton = new Button(BSResources.get("takeOffer.amountPriceBox.next"));
        nextButton.setDefaultButton(true);
        nextButton.disableProperty().bind(model.isNextButtonDisabled);
        nextButton.setOnAction(e -> onShowPayFundsScreen());
        //UserThread.runAfter(() -> nextButton.requestFocus(), 100, TimeUnit.MILLISECONDS);

        cancelButton1 = new Button(BSResources.get("shared.cancel"));
        cancelButton1.setOnAction(e -> close());
        cancelButton1.setDefaultButton(false);
        cancelButton1.setId("cancel-button");

        offerAvailabilitySpinner = new ProgressIndicator(0);
        offerAvailabilitySpinner.setPrefSize(18, 18);
        offerAvailabilitySpinner.setProgress(-1);

        offerAvailabilitySpinnerLabel = new Label(BSResources.get("takeOffer.fundsBox.isOfferAvailable"));

        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(nextButton, cancelButton1, offerAvailabilitySpinner, offerAvailabilitySpinnerLabel);

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
        qrCodeImageView.setOnMouseClicked(e -> new QRCodeWindow(getBitcoinURI()).show());
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

        Tuple3<Button, ProgressIndicator, Label> takeOfferTuple = addButtonWithStatusAfterGroup(gridPane, ++gridRow, "");
        takeOfferButton = takeOfferTuple.first;
        takeOfferButton.setVisible(false);
        takeOfferButton.setMinHeight(40);
        takeOfferButton.setPadding(new Insets(0, 20, 0, 20));
        takeOfferButton.setOnAction(e -> {
            onTakeOffer();
            balanceTextField.cleanup();
        });

        spinner = takeOfferTuple.second;
        spinnerInfoLabel = takeOfferTuple.third;

        cancelButton2 = addButton(gridPane, ++gridRow, BSResources.get("shared.cancel"));
        cancelButton2.setOnAction(e -> {
            if (model.dataModel.isWalletFunded.get())
                new Popup().warning("You have already paid in the funds.\n" +
                        "Are you sure you want to cancel.")
                        .actionButtonText("Yes, cancel")
                        .onAction(() -> close())
                        .closeButtonText("No")
                        .show();
            else
                close();
        });
        cancelButton2.setDefaultButton(false);
        cancelButton2.setVisible(false);
        cancelButton2.setId("cancel-button");
    }

    @NotNull
    private String getBitcoinURI() {
        return model.getAddressAsString() != null ? BitcoinURI.convertToBitcoinURI(model.getAddressAsString(), model.totalToPayAsCoin.get(),
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

    private void addAmountRangeBox() {
        Tuple3<HBox, TextField, Label> amountValueCurrencyBoxTuple = getValueCurrencyBox();
        HBox amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountRangeTextField = amountValueCurrencyBoxTuple.second;
        amountRangeBtcLabel = amountValueCurrencyBoxTuple.third;

        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, BSResources.get("takeOffer.amountPriceBox.amountRangeDescription"));
        VBox box = amountInputBoxTuple.second;
        GridPane.setRowIndex(box, ++gridRow);
        GridPane.setColumnIndex(box, 1);
        GridPane.setMargin(box, new Insets(5, 10, 5, 0));
        gridPane.getChildren().add(box);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Tuple2<Label, VBox> getTradeInputBox(HBox amountValueBox, String promptText) {
        Label descriptionLabel = new Label(promptText);
        descriptionLabel.setId("input-description-label");
        descriptionLabel.setPrefWidth(170);

        VBox box = new VBox();
        box.setSpacing(4);
        box.getChildren().addAll(descriptionLabel, amountValueBox);
        return new Tuple2<>(descriptionLabel, box);
    }

    private Tuple3<HBox, InputTextField, Label> getAmountCurrencyBox(String promptText) {
        InputTextField input = new InputTextField();
        input.setPrefWidth(170);
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
        textField.setPrefWidth(170);
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


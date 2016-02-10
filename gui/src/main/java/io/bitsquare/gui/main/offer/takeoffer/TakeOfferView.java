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
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.*;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.account.content.arbitratorselection.ArbitratorSelectionView;
import io.bitsquare.gui.main.account.settings.AccountSettingsView;
import io.bitsquare.gui.main.offer.OfferView;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesView;
import io.bitsquare.gui.popups.OfferDetailsPopup;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.trade.offer.Offer;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.controlsfx.control.PopOver;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.reactfx.util.FxTimer;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.bitsquare.gui.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createStringBinding;

// TODO Implement other positioning method in InoutTextField to display it over the field instead of right side
// priceAmountHBox is too large after redesign as to be used as layoutReference.
@FxmlView
public class TakeOfferView extends ActivatableViewAndModel<AnchorPane, TakeOfferViewModel> {
    private final Navigation navigation;
    private final BSFormatter formatter;
    private final OfferDetailsPopup offerDetailsPopup;
    private ScrollPane scrollPane;
    private GridPane gridPane;
    private ImageView imageView;
    private AddressTextField addressTextField;
    private BalanceTextField balanceTextField;
    private ProgressIndicator takeOfferSpinner;
    private TitledGroupBg payFundsPane;
    private Button nextButton, takeOfferButton, cancelButton1, cancelButton2;
    private InputTextField amountTextField;
    private TextField paymentMethodTextField, currencyTextField, priceTextField, volumeTextField, amountRangeTextField;
    private Label buyLabel, amountDescriptionLabel, addressLabel, balanceLabel, totalToPayLabel, totalToPayInfoIconLabel,
            amountBtcLabel, priceCurrencyLabel,
            volumeCurrencyLabel, amountRangeBtcLabel, priceDescriptionLabel, volumeDescriptionLabel, takeOfferSpinnerInfoLabel;
    private TextFieldWithCopyIcon totalToPayTextField;
    private PopOver totalToPayInfoPopover;
    private OfferView.CloseHandler closeHandler;
    private ChangeListener<Boolean> amountFocusedListener;
    private int gridRow = 0;
    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private Label paymentAccountsLabel;
    private Label paymentMethodLabel;
    private Subscription offerWarningSubscription;
    private Subscription errorMessageSubscription;
    private Subscription isTakeOfferSpinnerVisibleSubscription;
    private Subscription showWarningInvalidBtcDecimalPlacesSubscription;
    private Subscription showTransactionPublishedScreenSubscription;
    private Subscription showCheckAvailabilityPopupSubscription;
    private SimpleBooleanProperty errorPopupDisplayed;
    private Popup isOfferAvailablePopup;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TakeOfferView(TakeOfferViewModel model, Navigation navigation, BSFormatter formatter, OfferDetailsPopup offerDetailsPopup) {
        super(model);

        this.navigation = navigation;
        this.formatter = formatter;
        this.offerDetailsPopup = offerDetailsPopup;
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
        takeOfferSpinnerInfoLabel.visibleProperty().bind(model.isTakeOfferSpinnerVisible);

        priceCurrencyLabel.textProperty().bind(createStringBinding(() ->
                model.getTradeCurrency().getCode() + "/" + model.btcCode.get(), model.btcCode));

        volumeCurrencyLabel.setText(model.getTradeCurrency().getCode());
        amountRangeBtcLabel.textProperty().bind(model.btcCode);

        priceDescriptionLabel.setText(BSResources.get("createOffer.amountPriceBox.priceDescription", model.getTradeCurrency().getCode()));
        volumeDescriptionLabel.setText(model.volumeDescriptionLabel.get());

        errorPopupDisplayed = new SimpleBooleanProperty();
        offerWarningSubscription = EasyBind.subscribe(model.offerWarning, newValue -> {
            if (newValue != null) {
                if (isOfferAvailablePopup != null) {
                    isOfferAvailablePopup.hide();
                    isOfferAvailablePopup = null;
                }
                new Popup().warning(newValue).onClose(() -> {
                    errorPopupDisplayed.set(true);
                    model.resetOfferWarning();
                    close();
                }).show();
            }
        });

        errorMessageSubscription = EasyBind.subscribe(model.errorMessage, newValue -> {
            if (newValue != null) {
                new Popup().error(BSResources.get("takeOffer.error.message", newValue)).onClose(() -> {
                    errorPopupDisplayed.set(true);
                    model.resetErrorMessage();
                    close();
                }).show();
            }
        });

        showCheckAvailabilityPopupSubscription = EasyBind.combine(model.offerWarning,
                model.errorMessage,
                errorPopupDisplayed,
                model.isOfferAvailable,
                (a, b, c, d) -> a == null && b == null && !c && !d)
                .subscribe((observable, oldValue, newValue) -> {
                    if (!oldValue && newValue) {
                        isOfferAvailablePopup = new Popup().information(BSResources.get("takeOffer.fundsBox.isOfferAvailable"))
                                .onClose(() -> {
                                    model.resetErrorMessage();
                                    close();
                                });
                        isOfferAvailablePopup.show();
                    }
                });

        isTakeOfferSpinnerVisibleSubscription = EasyBind.subscribe(model.isTakeOfferSpinnerVisible, newValue -> {
            takeOfferSpinner.setProgress(newValue ? -1 : 0);
            takeOfferSpinner.setVisible(newValue);
        });

        showWarningInvalidBtcDecimalPlacesSubscription = EasyBind.subscribe(model.showWarningInvalidBtcDecimalPlaces, newValue -> {
            if (newValue) {
                new Popup().warning(BSResources.get("takeOffer.amountPriceBox.warning.invalidBtcDecimalPlaces")).show();
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });
        showTransactionPublishedScreenSubscription = EasyBind.subscribe(model.showTransactionPublishedScreen, newValue -> {
            if (newValue && BitsquareApp.DEV_MODE) {
                newValue = false;
                close();
                navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class);
            }

            if (newValue && model.getTrade() != null && model.getTrade().errorMessageProperty().get() == null) {
                FxTimer.runLater(Duration.ofMillis(100),
                        () -> {
                            new Popup().information(BSResources.get("takeOffer.success.info"))
                                    .actionButtonText("Go to \"Portfolio/Open trades\"")
                                    .onAction(() -> {
                                        close();
                                        FxTimer.runLater(Duration.ofMillis(100),
                                                () -> navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class)
                                        );
                                    })
                                    .onClose(() -> close())
                                    .show();
                        }
                );
            }
        });

        if (model.getPossiblePaymentAccounts().size() > 1) {
            paymentAccountsComboBox.setItems(model.getPossiblePaymentAccounts());
            paymentAccountsComboBox.getSelectionModel().select(0);
        }
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
        takeOfferSpinnerInfoLabel.visibleProperty().unbind();
        priceCurrencyLabel.textProperty().unbind();
        volumeCurrencyLabel.textProperty().unbind();
        amountRangeBtcLabel.textProperty().unbind();
        priceDescriptionLabel.textProperty().unbind();
        volumeDescriptionLabel.textProperty().unbind();

        offerWarningSubscription.unsubscribe();
        errorMessageSubscription.unsubscribe();
        isTakeOfferSpinnerVisibleSubscription.unsubscribe();
        showWarningInvalidBtcDecimalPlacesSubscription.unsubscribe();
        showTransactionPublishedScreenSubscription.unsubscribe();
        showCheckAvailabilityPopupSubscription.unsubscribe();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer) {
        model.initWithData(offer);

        if (model.getOffer().getDirection() == Offer.Direction.SELL)
            imageView.setId("image-buy-large");
        else
            imageView.setId("image-sell-large");

        balanceTextField.setup(model.address.get(), model.getFormatter());

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
        currencyTextField.setText(model.getTradeCurrency().getCodeAndName());
        buyLabel.setText(model.getDirectionLabel());
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
        // TODO need other implementation as it is displayed laso if there are old funds in the wallet
        /*
        if (model.dataModel.isWalletFunded.get())
            new Popup().warning("You have already funds paid in.\nIn the <Funds/Open for withdrawal> section you can withdraw those funds.").show();*/
    }

    public void onTabSelected() {
        model.dataModel.onTabSelected();
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onTakeOffer() {
        Offer offer = model.getOffer();
        if (model.getShowTakeOfferConfirmation()) {
            offerDetailsPopup.onTakeOffer(() -> model.onTakeOffer()).show(offer, model.dataModel.amountAsCoin.get());
        } else {
            if (model.hasAcceptedArbitrators()) {
                model.onTakeOffer();
            } else {
                new Popup().warning("You have no arbitrator selected.\n" +
                        "Please select at least one arbitrator.").show();

                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, ArbitratorSelectionView.class);
            }
        }
    }

    private void onPaymentAccountsComboBoxSelected() {
        model.onPaymentAccountSelected(paymentAccountsComboBox.getSelectionModel().getSelectedItem());
    }

    private void onShowPayFundsScreen() {
        if (!BitsquareApp.DEV_MODE) {
            String id = "tradeWalletInfoPopup";
            if (model.dataModel.getPreferences().showAgain(id)) {
                new Popup().information("To ensure that both traders follow the trade protocol they need to pay a security deposit.\n\n" +
                        "The deposit will be refunded to you after the trade has successfully completed.\n\n" +
                        "You need to pay in the exact amount displayed to you from your external bitcoin wallet into the " +
                        "Bitsquare trade wallet.\n" +
                        "The amount is the sum of the trade amount, the security deposit, the trading fee and " +
                        "the bitcoin mining fee.\n" +
                        "You can see the details when you move the mouse over the question mark.\n\n" +
                        "Important notice!\n" +
                        "Please take care that you use a mining fee of at least " +
                        model.formatter.formatCoinWithCode(FeePolicy.getMinRequiredFeeForFundingTx()) + " when you transfer bitcoin from your external " +
                        "wallet to ensure the trade transactions will get into the blockchain.\n" +
                        "A too low mining fee might result in a delayed trade and will be rejected!")
                        .closeButtonText("I understand")
                        .show();
                model.dataModel.getPreferences().dontShowAgain(id);
            }
        }

        nextButton.setVisible(false);
        cancelButton1.setVisible(false);
        cancelButton1.setOnAction(null);
        takeOfferButton.setVisible(true);
        cancelButton2.setVisible(true);

        payFundsPane.setVisible(true);
        totalToPayLabel.setVisible(true);
        totalToPayInfoIconLabel.setVisible(true);
        totalToPayTextField.setVisible(true);
        addressLabel.setVisible(true);
        addressTextField.setVisible(true);
        balanceLabel.setVisible(true);
        balanceTextField.setVisible(true);

        setupTotalToPayInfoIconLabel();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void close() {
        if (closeHandler != null)
            closeHandler.close();

        isOfferAvailablePopup = null;
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
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        columnConstraints1.setMinWidth(200);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
        scrollPane.setContent(gridPane);
    }

    private void addPaymentGroup() {
        addTitledGroupBg(gridPane, gridRow, 2, "Payment info");
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
        addTitledGroupBg(gridPane, ++gridRow, 2, "Set amount and price", Layout.GROUP_DISTANCE);

        imageView = new ImageView();
        imageView.setPickOnBounds(true);
        buyLabel = new Label();
        buyLabel.setId("direction-icon-label");
        buyLabel.setAlignment(Pos.CENTER);
        buyLabel.setPadding(new Insets(-5, 0, 0, 0));
        VBox imageVBox = new VBox();
        imageVBox.setAlignment(Pos.CENTER);
        imageVBox.setSpacing(6);
        imageVBox.getChildren().addAll(imageView, buyLabel);
        GridPane.setRowIndex(imageVBox, gridRow);
        GridPane.setRowSpan(imageVBox, 2);
        GridPane.setMargin(imageVBox, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 10, 10, 10));
        gridPane.getChildren().add(imageVBox);

        addAmountPriceFields();

        addAmountRangeBox();

        Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane, ++gridRow, BSResources.get("takeOffer.amountPriceBox.next"), BSResources.get("shared.cancel"));
        nextButton = tuple.first;
        UserThread.runAfter(() -> nextButton.requestFocus(), 100, TimeUnit.MILLISECONDS);
        cancelButton1 = tuple.second;
        cancelButton1.setDefaultButton(false);
        cancelButton1.setOnAction(e -> close());

        GridPane.setMargin(nextButton, new Insets(-35, 0, 0, 0));
        nextButton.setId("show-details-button");
        nextButton.setOnAction(e -> onShowPayFundsScreen());
    }

    private void addFundingGroup() {
        // don't increase gridRow as we removed button when this gets visible
        payFundsPane = addTitledGroupBg(gridPane, gridRow, 3, BSResources.get("takeOffer.fundsBox.title"), Layout.GROUP_DISTANCE);
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

        Tuple3<Button, ProgressIndicator, Label> takeOfferTuple = addButtonWithStatus(gridPane, ++gridRow, BSResources.get("takeOffer.fundsBox.takeOffer"));
        takeOfferButton = takeOfferTuple.first;
        takeOfferButton.setVisible(false);
        takeOfferButton.setOnAction(e -> onTakeOffer());
        takeOfferSpinner = takeOfferTuple.second;
        takeOfferSpinnerInfoLabel = takeOfferTuple.third;
        takeOfferSpinnerInfoLabel.setText(BSResources.get("takeOffer.fundsBox.takeOfferSpinnerInfo"));
        takeOfferSpinnerInfoLabel.setVisible(false);

        cancelButton2 = addButton(gridPane, ++gridRow, BSResources.get("shared.cancel"));
        cancelButton2.setOnAction(e -> close());
        cancelButton2.setDefaultButton(false);
        cancelButton2.setVisible(false);
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
        addPayInfoEntry(infoGridPane, i++, BSResources.get("takeOffer.fundsBox.offerFee"), model.getOfferFee());
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


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

package bisq.desktop.main.offer.atomictakeoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipSlideToggleButton;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.wallet.BsqWalletView;
import bisq.desktop.main.dao.wallet.receive.BsqReceiveView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.withdrawal.WithdrawalView;
import bisq.desktop.main.offer.OfferView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesView;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayloadI;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;

import java.util.concurrent.TimeUnit;

import static bisq.desktop.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createStringBinding;

@FxmlView
public class AtomicTakeOfferView extends ActivatableViewAndModel<AnchorPane, AtomicTakeOfferViewModel> {
    private final Navigation navigation;
    private final BsqFormatter bsqFormatter;
    private final OfferDetailsWindow offerDetailsWindow;

    private ScrollPane scrollPane;
    private GridPane gridPane;
    private TitledGroupBg
            paymentAccountTitledGroupBg;
    private VBox
            amountRangeBox;
    private HBox
            amountValueCurrencyBox,
            priceValueCurrencyBox,
            volumeValueCurrencyBox,
            minAmountValueCurrencyBox,
            advancedOptionsBox,
            buttonBox,
            firstRowHBox;
    private Label amountDescriptionLabel,
            priceCurrencyLabel,
            volumeCurrencyLabel,
            priceDescriptionLabel,
            volumeDescriptionLabel,
            offerAvailabilityLabel,
            tradeFeeDescriptionLabel,
            resultLabel,
            tradeFeeInBtcLabel,
            tradeFeeInBsqLabel,
            xLabel,
            fakeXLabel;
    private InputTextField amountTextField;
    private TextField
            paymentMethodTextField,
            currencyTextField,
            priceTextField,
            volumeTextField,
            amountRangeTextField;
    private Text xIcon, fakeXIcon;
    private Button takeAtomicOfferButton, cancelButton1;
    private BusyAnimation offerAvailabilityBusyAnimation;
    private OfferView.CloseHandler closeHandler;
    private Subscription
            showTransactionPublishedScreenSubscription,
            showWarningInvalidBtcDecimalPlacesSubscription,
            offerWarningSubscription,
            errorMessageSubscription,
            isOfferAvailableSubscription;

    private int gridRow = 0;
    private boolean offerDetailsWindowDisplayed;
    private SimpleBooleanProperty errorPopupDisplayed;
    private ChangeListener<Boolean> amountFocusedListener;
    private AutoTooltipSlideToggleButton tradeFeeInBtcToggle,
            tradeFeeInBsqToggle;
    private ChangeListener<Boolean> tradeFeeInBtcToggleListener,
            tradeFeeInBsqToggleListener,
            tradeFeeVisibleListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private AtomicTakeOfferView(AtomicTakeOfferViewModel model,
                                Navigation navigation,
                                BsqFormatter bsqFormatter,
                                OfferDetailsWindow offerDetailsWindow
    ) {
        super(model);
        this.navigation = navigation;
        this.bsqFormatter = bsqFormatter;
        this.offerDetailsWindow = offerDetailsWindow;
    }

    @Override
    protected void initialize() {
        addScrollPane();
        addGridPane();
        addPaymentGroup();
        addAmountPriceGroup();
        addOptionsGroup();
        addButtons();
        addOfferAvailabilityLabel();

        amountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue, amountTextField.getText());
            amountTextField.setText(model.amount.get());
        };

        tradeFeeInBtcToggleListener = (observable, oldValue, newValue) -> {
            if (newValue && tradeFeeInBsqToggle.isSelected())
                tradeFeeInBsqToggle.setSelected(false);

            if (!newValue && !tradeFeeInBsqToggle.isSelected())
                tradeFeeInBsqToggle.setSelected(true);

            setIsCurrencyForMakerFeeBtc(newValue);
        };
        tradeFeeInBsqToggleListener = (observable, oldValue, newValue) -> {
            if (newValue && tradeFeeInBtcToggle.isSelected())
                tradeFeeInBtcToggle.setSelected(false);

            if (!newValue && !tradeFeeInBtcToggle.isSelected())
                tradeFeeInBtcToggle.setSelected(true);

            setIsCurrencyForMakerFeeBtc(!newValue);
        };

        tradeFeeVisibleListener = (observable, oldValue, newValue) -> {
            if (DevEnv.isDaoActivated()) {
                tradeFeeInBtcToggle.setVisible(newValue);
                tradeFeeInBsqToggle.setVisible(newValue);
            }
        };

        GUIUtil.focusWhenAddedToScene(amountTextField);
    }

    private void setIsCurrencyForMakerFeeBtc(boolean isCurrencyForMakerFeeBtc) {
        model.setIsCurrencyForTakerFeeBtc(isCurrencyForMakerFeeBtc);
        if (DevEnv.isDaoActivated()) {
            tradeFeeInBtcLabel.setOpacity(isCurrencyForMakerFeeBtc ? 1 : 0.3);
            tradeFeeInBsqLabel.setOpacity(isCurrencyForMakerFeeBtc ? 0.3 : 1);
        }
    }

    @Override
    protected void activate() {
        addBindings();
        addSubscriptions();
        addListeners();

        if (offerAvailabilityBusyAnimation != null) {
            offerAvailabilityLabel.setVisible(true);
            offerAvailabilityLabel.setManaged(true);
        } else {
            offerAvailabilityLabel.setVisible(false);
            offerAvailabilityLabel.setManaged(false);
        }

        String currencyCode = model.dataModel.getCurrencyCode();
        volumeCurrencyLabel.setText(currencyCode);
        priceDescriptionLabel.setText(CurrencyUtil.getPriceWithCurrencyCode(currencyCode));
        volumeDescriptionLabel.setText(model.volumeDescriptionLabel.get());

        boolean currencyForMakerFeeBtc = model.dataModel.isCurrencyForTakerFeeBtc();
        tradeFeeInBtcToggle.setSelected(currencyForMakerFeeBtc);
        tradeFeeInBsqToggle.setSelected(!currencyForMakerFeeBtc);
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeSubscriptions();
        removeListeners();

        if (offerAvailabilityBusyAnimation != null)
            offerAvailabilityBusyAnimation.stop();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer) {
        model.initWithData(offer);

        if (model.getOffer().getDirection() == OfferPayloadI.Direction.SELL) {
            takeAtomicOfferButton.setId("buy-button-big");
        } else {
            takeAtomicOfferButton.setId("sell-button-big");
        }
        paymentMethodTextField.setVisible(true);
        paymentMethodTextField.setManaged(true);
        paymentMethodTextField.setText(model.getPaymentAccount().getAccountName());

        currencyTextField.setText(model.dataModel.getCurrencyNameAndCode());
        amountDescriptionLabel.setText(model.getAmountDescription());

        if (model.isRange()) {
            amountRangeTextField.setText(model.getAmountRange());
            amountRangeBox.setVisible(true);
        } else {
            amountTextField.setDisable(true);
        }

        priceTextField.setText(model.getPrice());
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    public void onTabSelected(boolean isSelected) {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onTakeOffer() {
        if (!model.dataModel.isTakerFeeValid()) {
            showInsufficientBsqFundsForBtcFeePaymentPopup();
            return;
        }

        offerDetailsWindow.onTakeOffer(() ->
                model.onTakeOffer(() -> {
                    offerDetailsWindow.hide();
                    offerDetailsWindowDisplayed = false;
                })
        ).show(model.getOffer(),
                model.dataModel.getAmount().get(),
                model.dataModel.getTradePrice());

        offerDetailsWindowDisplayed = true;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void close() {
        close(true);
    }

    private void close(boolean removeOffer) {
        model.dataModel.onClose(removeOffer);
        if (closeHandler != null)
            closeHandler.close();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        amountTextField.textProperty().bindBidirectional(model.amount);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        priceCurrencyLabel.textProperty().bind(createStringBinding(() -> CurrencyUtil.getCounterCurrency(model.dataModel.getCurrencyCode())));
        takeAtomicOfferButton.disableProperty().bind(model.isAtomicTakeOfferButtonDisabled);
        tradeFeeInBtcLabel.textProperty().bind(model.tradeFeeInBtcWithFiat);
        tradeFeeInBsqLabel.textProperty().bind(model.tradeFeeInBsqWithFiat);
        tradeFeeDescriptionLabel.textProperty().bind(model.tradeFeeDescription);
        tradeFeeInBtcLabel.visibleProperty().bind(model.isTradeFeeVisible);
        tradeFeeInBsqLabel.visibleProperty().bind(model.isTradeFeeVisible);
        tradeFeeDescriptionLabel.visibleProperty().bind(model.isTradeFeeVisible);
        tradeFeeDescriptionLabel.managedProperty().bind(tradeFeeDescriptionLabel.visibleProperty());
    }

    private void removeBindings() {
        amountTextField.textProperty().unbindBidirectional(model.amount);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        amountTextField.validationResultProperty().unbind();
        priceCurrencyLabel.textProperty().unbind();
        takeAtomicOfferButton.disableProperty().unbind();
        tradeFeeInBtcLabel.textProperty().unbind();
        tradeFeeInBsqLabel.textProperty().unbind();
        tradeFeeDescriptionLabel.textProperty().unbind();
        tradeFeeInBtcLabel.visibleProperty().unbind();
        tradeFeeInBsqLabel.visibleProperty().unbind();
        tradeFeeDescriptionLabel.visibleProperty().unbind();
        tradeFeeDescriptionLabel.managedProperty().unbind();
    }

    private void addSubscriptions() {
        errorPopupDisplayed = new SimpleBooleanProperty();
        offerWarningSubscription = EasyBind.subscribe(model.offerWarning, newValue -> {
            if (newValue != null) {
                if (offerDetailsWindowDisplayed)
                    offerDetailsWindow.hide();

                UserThread.runAfter(() -> new Popup().warning(newValue)
                        .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
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
                new Popup().error(Res.get("takeOffer.error.message", model.errorMessage.get()) +
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
            if (isOfferAvailable) {
                offerAvailabilityBusyAnimation.stop();
                offerAvailabilityBusyAnimation.setVisible(false);
            }

            offerAvailabilityLabel.setVisible(!isOfferAvailable);
            offerAvailabilityLabel.setManaged(!isOfferAvailable);
        });

        showWarningInvalidBtcDecimalPlacesSubscription = EasyBind.subscribe(model.showWarningInvalidBtcDecimalPlaces, newValue -> {
            if (newValue) {
                new Popup().warning(Res.get("takeOffer.amountPriceBox.warning.invalidBtcDecimalPlaces")).show();
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        showTransactionPublishedScreenSubscription = EasyBind.subscribe(model.showTransactionPublishedScreen, newValue -> {
            if (newValue && DevEnv.isDevMode()) {
                close();
            } else if (newValue && model.getAtomicTrade() != null && !model.getAtomicTrade().hasFailed()) {
                String key = "takeOfferSuccessInfo";
                if (DontShowAgainLookup.showAgain(key)) {
                    UserThread.runAfter(() -> new Popup().headLine(Res.get("takeOffer.success.headline"))
                            .feedback(Res.get("takeOffer.success.info"))
                            .actionButtonTextWithGoTo("navigation.portfolio.pending")
                            .dontShowAgainId(key)
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
    }

    private void removeSubscriptions() {
        offerWarningSubscription.unsubscribe();
        errorMessageSubscription.unsubscribe();
        isOfferAvailableSubscription.unsubscribe();
        showWarningInvalidBtcDecimalPlacesSubscription.unsubscribe();
        showTransactionPublishedScreenSubscription.unsubscribe();
    }

    private void addListeners() {
        amountTextField.focusedProperty().addListener(amountFocusedListener);
        model.isTradeFeeVisible.addListener(tradeFeeVisibleListener);
        tradeFeeInBtcToggle.selectedProperty().addListener(tradeFeeInBtcToggleListener);
        tradeFeeInBsqToggle.selectedProperty().addListener(tradeFeeInBsqToggleListener);
    }

    private void removeListeners() {
        amountTextField.focusedProperty().removeListener(amountFocusedListener);
        model.isTradeFeeVisible.removeListener(tradeFeeVisibleListener);
        tradeFeeInBtcToggle.selectedProperty().removeListener(tradeFeeInBtcToggleListener);
        tradeFeeInBsqToggle.selectedProperty().removeListener(tradeFeeInBsqToggleListener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build UI elements
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addScrollPane() {
        scrollPane = new ScrollPane();
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        AnchorPane.setLeftAnchor(scrollPane, 0d);
        AnchorPane.setTopAnchor(scrollPane, 0d);
        AnchorPane.setRightAnchor(scrollPane, 0d);
        AnchorPane.setBottomAnchor(scrollPane, 0d);
        root.getChildren().add(scrollPane);
    }

    private void addGridPane() {
        gridPane = new GridPane();
        gridPane.getStyleClass().add("content-pane");
        gridPane.setPadding(new Insets(15, 15, -1, 15));
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.NEVER);
        columnConstraints1.setMinWidth(200);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
        scrollPane.setContent(gridPane);
    }

    private void addPaymentGroup() {
        paymentAccountTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 1, Res.get("takeOffer.paymentInfo"));
        GridPane.setColumnSpan(paymentAccountTitledGroupBg, 2);

        final var paymentAccountTuple = addTopLabelTextFieldWithHbox(gridPane,
                gridRow, Res.get("shared.paymentMethod"), Layout.FIRST_ROW_DISTANCE);

        paymentMethodTextField = paymentAccountTuple.second;
        paymentMethodTextField.setMinWidth(250);
        paymentMethodTextField.setEditable(false);
        paymentMethodTextField.setMouseTransparent(true);
        paymentMethodTextField.setFocusTraversable(false);

        currencyTextField = new JFXTextField();
        currencyTextField.setMinWidth(250);
        currencyTextField.setEditable(false);
        currencyTextField.setMouseTransparent(true);
        currencyTextField.setFocusTraversable(false);

        final Tuple2<Label, VBox> tradeCurrencyTuple = getTopLabelWithVBox(Res.get("shared.tradeCurrency"), currencyTextField);
        HBox.setMargin(tradeCurrencyTuple.second, new Insets(5, 0, 0, 0));

        final HBox hBox = paymentAccountTuple.third;
        hBox.setSpacing(30);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.setPadding(new Insets(10, 0, 18, 0));

        hBox.getChildren().add(tradeCurrencyTuple.second);
    }

    private void addAmountPriceGroup() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("takeOffer.setAmountPrice"), Layout.COMPACT_GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 2);

        addAmountPriceFields();
        addSecondRow();
    }

    private void addOptionsGroup() {
        addTitledGroupBg(gridPane, ++gridRow, 1, Res.get("shared.advancedOptions"), Layout.COMPACT_GROUP_DISTANCE);

        advancedOptionsBox = new HBox();
        advancedOptionsBox.setSpacing(40);

        GridPane.setRowIndex(advancedOptionsBox, gridRow);
        GridPane.setColumnIndex(advancedOptionsBox, 0);
        GridPane.setHalignment(advancedOptionsBox, HPos.LEFT);
        GridPane.setMargin(advancedOptionsBox, new Insets(Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(advancedOptionsBox);

        advancedOptionsBox.getChildren().addAll(getTradeFeeFieldsBox());
    }

    private void addButtons() {
        Tuple3<Button, Button, HBox> tuple = add2ButtonsWithBox(gridPane, ++gridRow,
                "ATOMIC!!", Res.get("shared.cancel"), 15, true);

        buttonBox = tuple.third;

        takeAtomicOfferButton = tuple.first;
        takeAtomicOfferButton.setMaxWidth(200);
        takeAtomicOfferButton.setDefaultButton(true);
        takeAtomicOfferButton.setOnAction(e -> onTakeOffer());

        cancelButton1 = tuple.second;
        cancelButton1.setMaxWidth(200);
        cancelButton1.setDefaultButton(false);
        cancelButton1.setOnAction(e -> close(false));
    }

    private void addOfferAvailabilityLabel() {
        offerAvailabilityBusyAnimation = new BusyAnimation(false);
        offerAvailabilityLabel = new AutoTooltipLabel(Res.get("takeOffer.fundsBox.isOfferAvailable"));

        buttonBox.getChildren().addAll(offerAvailabilityBusyAnimation, offerAvailabilityLabel);
    }

    private void addAmountPriceFields() {
        // amountBox
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueBox(Res.get("takeOffer.amount.prompt"));
        amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountTextField = amountValueCurrencyBoxTuple.second;
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, model.getAmountDescription());
        amountDescriptionLabel = amountInputBoxTuple.first;
        VBox amountBox = amountInputBoxTuple.second;

        // x
        xLabel = new Label();
        xIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", xLabel);
        xIcon.getStyleClass().add("opaque-icon");
        xLabel.getStyleClass().addAll("opaque-icon-character");

        // price
        Tuple3<HBox, TextField, Label> priceValueCurrencyBoxTuple = getNonEditableValueBox();
        priceValueCurrencyBox = priceValueCurrencyBoxTuple.first;
        priceTextField = priceValueCurrencyBoxTuple.second;
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox,
                Res.get("takeOffer.amountPriceBox.priceDescription"));
        priceDescriptionLabel = priceInputBoxTuple.first;

        getSmallIconForLabel(MaterialDesignIcon.LOCK, priceDescriptionLabel, "small-icon-label");

        VBox priceBox = priceInputBoxTuple.second;

        // =
        resultLabel = new AutoTooltipLabel("=");
        resultLabel.getStyleClass().addAll("opaque-icon-character");

        // volume
        Tuple3<HBox, InfoInputTextField, Label> volumeValueCurrencyBoxTuple = getNonEditableValueBoxWithInfo();
        volumeValueCurrencyBox = volumeValueCurrencyBoxTuple.first;

        InfoInputTextField volumeInfoTextField = volumeValueCurrencyBoxTuple.second;
        volumeTextField = volumeInfoTextField.getInputTextField();
        volumeCurrencyLabel = volumeValueCurrencyBoxTuple.third;
        Tuple2<Label, VBox> volumeInputBoxTuple = getTradeInputBox(volumeValueCurrencyBox, model.volumeDescriptionLabel.get());
        volumeDescriptionLabel = volumeInputBoxTuple.first;
        VBox volumeBox = volumeInputBoxTuple.second;

        firstRowHBox = new HBox();
        firstRowHBox.setSpacing(5);
        firstRowHBox.setAlignment(Pos.CENTER_LEFT);
        firstRowHBox.getChildren().addAll(amountBox, xLabel, priceBox, resultLabel, volumeBox);
        GridPane.setColumnSpan(firstRowHBox, 2);
        GridPane.setRowIndex(firstRowHBox, gridRow);
        GridPane.setMargin(firstRowHBox, new Insets(Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE, 10, 0, 0));
        gridPane.getChildren().add(firstRowHBox);
    }

    private void addSecondRow() {
        Tuple3<HBox, TextField, Label> amountValueCurrencyBoxTuple = getNonEditableValueBox();
        amountRangeTextField = amountValueCurrencyBoxTuple.second;

        minAmountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(minAmountValueCurrencyBox,
                Res.get("takeOffer.amountPriceBox.amountRangeDescription"));

        amountRangeBox = amountInputBoxTuple.second;
        amountRangeBox.setVisible(false);

        fakeXLabel = new Label();
        fakeXIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", fakeXLabel);
        fakeXLabel.setVisible(false); // we just use it to get the same layout as the upper row
        fakeXLabel.getStyleClass().add("opaque-icon-character");

        HBox hBox = new HBox();
        hBox.setSpacing(5);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(amountRangeBox, fakeXLabel/*, priceAsPercentageInputBox*/);

        GridPane.setRowIndex(hBox, ++gridRow);
        GridPane.setMargin(hBox, new Insets(0, 10, 10, 0));
        gridPane.getChildren().add(hBox);
    }

    private VBox getTradeFeeFieldsBox() {
        tradeFeeInBtcLabel = new Label();
        tradeFeeInBtcLabel.setMouseTransparent(true);
        tradeFeeInBtcLabel.setId("trade-fee-textfield");

        tradeFeeInBsqLabel = new Label();
        tradeFeeInBsqLabel.setMouseTransparent(true);
        tradeFeeInBsqLabel.setId("trade-fee-textfield");

        VBox vBox = new VBox();
        vBox.setSpacing(6);
        vBox.setMaxWidth(300);
        vBox.setAlignment(DevEnv.isDaoActivated() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        vBox.getChildren().addAll(tradeFeeInBtcLabel, tradeFeeInBsqLabel);

        tradeFeeInBtcToggle = new AutoTooltipSlideToggleButton();
        tradeFeeInBtcToggle.setText("BTC");
        tradeFeeInBtcToggle.setPadding(new Insets(-8, 5, -10, 5));

        tradeFeeInBsqToggle = new AutoTooltipSlideToggleButton();
        tradeFeeInBsqToggle.setText("BSQ");
        tradeFeeInBsqToggle.setPadding(new Insets(-9, 5, -9, 5));

        VBox tradeFeeToggleButtonBox = new VBox();
        tradeFeeToggleButtonBox.getChildren().addAll(tradeFeeInBtcToggle, tradeFeeInBsqToggle);

        HBox hBox = new HBox();
        hBox.getChildren().addAll(vBox, tradeFeeToggleButtonBox);
        hBox.setMinHeight(47);
        hBox.setMaxHeight(hBox.getMinHeight());
        HBox.setHgrow(vBox, Priority.ALWAYS);
        HBox.setHgrow(tradeFeeToggleButtonBox, Priority.NEVER);

        final Tuple2<Label, VBox> tradeInputBox = getTradeInputBox(hBox, Res.get("createOffer.tradeFee.descriptionBSQEnabled"));

        tradeFeeDescriptionLabel = tradeInputBox.first;

        return tradeInputBox.second;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void showInsufficientBsqFundsForBtcFeePaymentPopup() {
        Coin takerFee = model.dataModel.getTakerFee();
        String message = null;
        if (takerFee != null)
            message = Res.get("popup.warning.insufficientBsqFundsForBtcFeePayment",
                    bsqFormatter.formatCoinWithCode(takerFee.subtract(model.dataModel.getUsableBsqBalance())));

        else if (model.dataModel.getUsableBsqBalance().isZero())
            message = Res.get("popup.warning.noBsqFundsForBtcFeePayment");

        if (message != null)
            new Popup().warning(message)
                    .actionButtonTextWithGoTo("navigation.dao.wallet.receive")
                    .onAction(() -> navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, BsqReceiveView.class))
                    .show();
    }

    private Tuple2<Label, VBox> getTradeInputBox(HBox amountValueBox, String promptText) {
        Label descriptionLabel = new AutoTooltipLabel(promptText);
        descriptionLabel.setId("input-description-label");
        descriptionLabel.setPrefWidth(170);

        VBox box = new VBox();
        box.setPadding(new Insets(10, 0, 0, 0));
        box.setSpacing(2);
        box.getChildren().addAll(descriptionLabel, amountValueBox);
        return new Tuple2<>(descriptionLabel, box);
    }
}


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

package bisq.desktop.main.offer.takeoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
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
import bisq.core.offer.OfferDirection;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

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
public class TakeBsqSwapOfferView extends ActivatableViewAndModel<AnchorPane, TakeBsqSwapOfferViewModel> {
    private final Navigation navigation;
    private final OfferDetailsWindow offerDetailsWindow;

    private ScrollPane scrollPane;
    private GridPane gridPane;
    private VBox
            amountRangeBox;
    private HBox buttonBox;
    private Label amountDescriptionLabel;
    private Label priceCurrencyLabel;
    private Label volumeCurrencyLabel;
    private Label priceDescriptionLabel;
    private Label volumeDescriptionLabel;
    private Label offerAvailabilityLabel;
    private InputTextField amountTextField;
    private TextField
            paymentMethodTextField,
            currencyTextField,
            priceTextField,
            volumeTextField,
            amountRangeTextField;
    private Button takeBsqSwapOfferButton;
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TakeBsqSwapOfferView(TakeBsqSwapOfferViewModel model,
                                 Navigation navigation,
                                 OfferDetailsWindow offerDetailsWindow
    ) {
        super(model);
        this.navigation = navigation;
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

        GUIUtil.focusWhenAddedToScene(amountTextField);
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

        if (model.getOffer().getDirection() == OfferDirection.SELL) {
            takeBsqSwapOfferButton.setId("buy-button-big");
        } else {
            takeBsqSwapOfferButton.setId("sell-button-big");
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
        takeBsqSwapOfferButton.disableProperty().bind(model.isTakeBsqSwapOfferButtonDisabled);
    }

    private void removeBindings() {
        amountTextField.textProperty().unbindBidirectional(model.amount);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        amountTextField.validationResultProperty().unbind();
        priceCurrencyLabel.textProperty().unbind();
        takeBsqSwapOfferButton.disableProperty().unbind();
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
            } else if (newValue && model.getBsqSwapTrade() != null && !model.getBsqSwapTrade().hasFailed()) {
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
    }

    private void removeListeners() {
        amountTextField.focusedProperty().removeListener(amountFocusedListener);
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
        TitledGroupBg paymentAccountTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 1, Res.get("takeOffer.paymentInfo"));
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
        HBox advancedOptionsBox = new HBox();
        advancedOptionsBox.setSpacing(40);

        GridPane.setRowIndex(advancedOptionsBox, gridRow);
        GridPane.setColumnIndex(advancedOptionsBox, 0);
        GridPane.setHalignment(advancedOptionsBox, HPos.LEFT);
        GridPane.setMargin(advancedOptionsBox, new Insets(Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(advancedOptionsBox);

        // TODO(sq): Show tx fee and trade fee
        advancedOptionsBox.getChildren().addAll(getTradeFeeFieldsBox());
    }

    private void addButtons() {
        Tuple3<Button, Button, HBox> tuple = add2ButtonsWithBox(gridPane, ++gridRow,
                Res.get("takeOffer.swap"), Res.get("shared.cancel"), 15, true);

        buttonBox = tuple.third;

        takeBsqSwapOfferButton = tuple.first;
        takeBsqSwapOfferButton.setMaxWidth(200);
        takeBsqSwapOfferButton.setDefaultButton(true);
        takeBsqSwapOfferButton.setOnAction(e -> onTakeOffer());

        Button cancelButton1 = tuple.second;
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
        HBox amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountTextField = amountValueCurrencyBoxTuple.second;
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, model.getAmountDescription());
        amountDescriptionLabel = amountInputBoxTuple.first;
        VBox amountBox = amountInputBoxTuple.second;

        // x
        Label xLabel = new Label();
        Text xIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", xLabel);
        xIcon.getStyleClass().add("opaque-icon");
        xLabel.getStyleClass().addAll("opaque-icon-character");

        // price
        Tuple3<HBox, TextField, Label> priceValueCurrencyBoxTuple = getNonEditableValueBox();
        HBox priceValueCurrencyBox = priceValueCurrencyBoxTuple.first;
        priceTextField = priceValueCurrencyBoxTuple.second;
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox,
                Res.get("takeOffer.amountPriceBox.priceDescription"));
        priceDescriptionLabel = priceInputBoxTuple.first;

        getSmallIconForLabel(MaterialDesignIcon.LOCK, priceDescriptionLabel, "small-icon-label");

        VBox priceBox = priceInputBoxTuple.second;

        // =
        Label resultLabel = new AutoTooltipLabel("=");
        resultLabel.getStyleClass().addAll("opaque-icon-character");

        // volume
        Tuple3<HBox, InfoInputTextField, Label> volumeValueCurrencyBoxTuple = getNonEditableValueBoxWithInfo();
        HBox volumeValueCurrencyBox = volumeValueCurrencyBoxTuple.first;

        InfoInputTextField volumeInfoTextField = volumeValueCurrencyBoxTuple.second;
        volumeTextField = volumeInfoTextField.getInputTextField();
        volumeCurrencyLabel = volumeValueCurrencyBoxTuple.third;
        Tuple2<Label, VBox> volumeInputBoxTuple = getTradeInputBox(volumeValueCurrencyBox, model.volumeDescriptionLabel.get());
        volumeDescriptionLabel = volumeInputBoxTuple.first;
        VBox volumeBox = volumeInputBoxTuple.second;

        HBox firstRowHBox = new HBox();
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

        HBox minAmountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(minAmountValueCurrencyBox,
                Res.get("takeOffer.amountPriceBox.amountRangeDescription"));

        amountRangeBox = amountInputBoxTuple.second;
        amountRangeBox.setVisible(false);

        Label fakeXLabel = new Label();
        Text fakeXIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", fakeXLabel);
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
        return new VBox();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////


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


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
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.fiataccounts.FiatAccountsView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.openoffer.OpenOffersView;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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

import javafx.beans.value.ChangeListener;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.Setter;

import static bisq.desktop.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createStringBinding;

public abstract class AtomicOfferView<M extends AtomicOfferViewModel<?>> extends ActivatableViewAndModel<AnchorPane, M> {
    protected final Navigation navigation;
    private final OfferDetailsWindow offerDetailsWindow;

    private ScrollPane scrollPane;
    protected GridPane gridPane;
    private TitledGroupBg paymentTitledGroupBg;
    protected TitledGroupBg amountTitledGroupBg;
    private AutoTooltipButton placeOfferButton;
    private InputTextField fixedPriceTextField;
    protected InputTextField amountTextField, minAmountTextField, volumeTextField;
    private TextField currencyTextField;
    private Label amountDescriptionLabel;
    private Label priceCurrencyLabel;
    private Label priceDescriptionLabel;
    private Label volumeDescriptionLabel;
    protected Label amountBtcLabel, volumeCurrencyLabel, minAmountBtcLabel;
    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private VBox currencyTextFieldBox;

    private ChangeListener<Boolean> amountFocusedListener, minAmountFocusedListener, volumeFocusedListener,
            priceFocusedListener, placeOfferCompletedListener;
    private ChangeListener<String> tradeCurrencyCodeListener, errorMessageListener, volumeListener;
    private EventHandler<ActionEvent> paymentAccountsComboBoxSelectionHandler;
    private OfferView.CloseHandler closeHandler;

    protected int gridRow = 0;
    private final List<Node> editOfferElements = new ArrayList<>();
    private boolean isActivated;
    private InfoInputTextField volumeInfoInputTextField;

    @Setter
    private OfferView.OfferActionHandler offerActionHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AtomicOfferView(M model,
                           Navigation navigation,
                           OfferDetailsWindow offerDetailsWindow) {
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

        createListeners();

        paymentAccountsComboBox.setConverter(GUIUtil.getPaymentAccountsComboBoxStringConverter());
        paymentAccountsComboBox.setButtonCell(GUIUtil.getComboBoxButtonCell(Res.get("shared.selectTradingAccount"),
                paymentAccountsComboBox, false));
        paymentAccountsComboBox.setCellFactory(model.getPaymentAccountListCellFactory(paymentAccountsComboBox));

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
        if (isActivated) {
            return;
        }
        isActivated = true;
        paymentAccountsComboBox.setPrefWidth(250);

        addBindings();
        addListeners();

        amountDescriptionLabel.setText(model.getAmountDescription());

        paymentAccountsComboBox.setItems(model.getDataModel().getPaymentAccounts());
        paymentAccountsComboBox.getSelectionModel().select(model.getPaymentAccount());
    }

    @Override
    protected void deactivate() {
        if (!isActivated) {
            return;
        }
        isActivated = false;
        removeBindings();
        removeListeners();
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

    public void initWithData(OfferPayload.Direction direction, TradeCurrency tradeCurrency,
                             OfferView.OfferActionHandler offerActionHandler) {
        this.offerActionHandler = offerActionHandler;

        boolean result = model.initWithData(direction, tradeCurrency);

        if (!result) {
            new Popup().headLine(Res.get("popup.warning.noTradingAccountSetup.headline"))
                    .instruction(Res.get("popup.warning.noTradingAccountSetup.msg"))
                    .actionButtonTextWithGoTo("navigation.account")
                    .onAction(() -> {
                        navigation.setReturnPath(navigation.getCurrentPath());
                        navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
                    }).show();
        }

        if (direction == OfferPayload.Direction.BUY) {
            placeOfferButton.setId("buy-button-big");
            placeOfferButton.updateText(Res.get("createOffer.placeOfferButton", Res.get("shared.buy")));
        } else {
            placeOfferButton.setId("sell-button-big");
            placeOfferButton.updateText(Res.get("createOffer.placeOfferButton", Res.get("shared.sell")));
        }
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPlaceOffer() {
        if (!model.getDataModel().canPlaceOffer()) {
            return;
        }
        Offer offer = model.createAndGetOffer();
        if (!DevEnv.isDevMode()) {
            offerDetailsWindow.onPlaceOffer(() ->
                    model.onPlaceOffer(offer, offerDetailsWindow::hide))
                    .show(offer);
        } else {
            model.onPlaceOffer(offer, () -> {
            });
        }
    }

    protected void onPaymentAccountsComboBoxSelected() {
        // Changing payment account from atomic BSQ closes the atomic offer tab and opens the normal create offer tab
        PaymentAccount paymentAccount = paymentAccountsComboBox.getSelectionModel().getSelectedItem();
        if (paymentAccount == null) {
            currencyTextFieldBox.setVisible(true);

            currencyTextField.setText("");
            return;
        }

        if (paymentAccount.getPaymentMethod() != PaymentMethod.ATOMIC) {
            if (offerActionHandler != null)
                offerActionHandler.onCreateOffer(paymentAccount.getSelectedTradeCurrency());
            return;
        }
        currencyTextFieldBox.setVisible(true);
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        if (singleTradeCurrency != null)
            currencyTextField.setText(singleTradeCurrency.getNameAndCode());
        model.onPaymentAccountSelected(paymentAccount);
        model.onCurrencySelected(model.getDataModel().getTradeCurrency());
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
        priceCurrencyLabel.textProperty().bind(createStringBinding(() -> CurrencyUtil.getCounterCurrency(model.tradeCurrencyCode.get()), model.tradeCurrencyCode));
        volumeCurrencyLabel.textProperty().bind(model.tradeCurrencyCode);
        priceDescriptionLabel.textProperty().bind(createStringBinding(() -> CurrencyUtil.getPriceWithCurrencyCode(model.tradeCurrencyCode.get(), "shared.fixedPriceInCurForCur"), model.tradeCurrencyCode));
        volumeDescriptionLabel.textProperty().bind(createStringBinding(model.volumeDescriptionLabel::get, model.tradeCurrencyCode, model.volumeDescriptionLabel));
        amountTextField.textProperty().bindBidirectional(model.amount);
        minAmountTextField.textProperty().bindBidirectional(model.minAmount);
        fixedPriceTextField.textProperty().bindBidirectional(model.price);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        volumeTextField.promptTextProperty().bind(model.volumePromptLabel);

        // Validation
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        minAmountTextField.validationResultProperty().bind(model.minAmountValidationResult);
        fixedPriceTextField.validationResultProperty().bind(model.priceValidationResult);
        volumeTextField.validationResultProperty().bind(model.volumeValidationResult);

        // funding
        placeOfferButton.disableProperty().bind(model.isPlaceOfferButtonDisabled);

        // trading account
        paymentAccountsComboBox.managedProperty().bind(paymentAccountsComboBox.visibleProperty());
        paymentTitledGroupBg.managedProperty().bind(paymentTitledGroupBg.visibleProperty());
        currencyTextFieldBox.managedProperty().bind(currencyTextFieldBox.visibleProperty());
    }

    private void removeBindings() {
        priceCurrencyLabel.textProperty().unbind();
        volumeCurrencyLabel.textProperty().unbind();
        priceDescriptionLabel.textProperty().unbind();
        volumeDescriptionLabel.textProperty().unbind();
        amountTextField.textProperty().unbindBidirectional(model.amount);
        minAmountTextField.textProperty().unbindBidirectional(model.minAmount);
        fixedPriceTextField.textProperty().unbindBidirectional(model.price);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        volumeTextField.promptTextProperty().unbindBidirectional(model.volume);

        // Validation
        amountTextField.validationResultProperty().unbind();
        minAmountTextField.validationResultProperty().unbind();
        fixedPriceTextField.validationResultProperty().unbind();
        volumeTextField.validationResultProperty().unbind();

        // funding
        placeOfferButton.disableProperty().unbind();

        // trading account
        paymentTitledGroupBg.managedProperty().unbind();
        paymentAccountsComboBox.managedProperty().unbind();
        currencyTextFieldBox.managedProperty().unbind();
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
        volumeFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutVolumeTextField(oldValue, newValue);
            volumeTextField.setText(model.volume.get());
        };

        errorMessageListener = (o, oldValue, newValue) -> {
            if (newValue != null)
                UserThread.runAfter(() -> new Popup().error(Res.get("createOffer.amountPriceBox.error.message", model.errorMessage.get()))
                        .show(), 100, TimeUnit.MILLISECONDS);
        };

        paymentAccountsComboBoxSelectionHandler = e -> onPaymentAccountsComboBoxSelected();

        tradeCurrencyCodeListener = (observable, oldValue, newValue) -> {
            fixedPriceTextField.clear();
            volumeTextField.clear();
        };

        placeOfferCompletedListener = (o, oldValue, newValue) -> {
            if (DevEnv.isDevMode()) {
                close();
            } else if (newValue) {
                // We need a bit of delay to avoid issues with fade out/fade in of 2 popups
                String key = "createOfferSuccessInfo";
                if (DontShowAgainLookup.showAgain(key)) {
                    UserThread.runAfter(() -> new Popup().headLine(Res.get("createOffer.success.headline"))
                                    .feedback(Res.get("createOffer.success.info"))
                                    .dontShowAgainId(key)
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

        volumeListener = (observable, oldValue, newValue) -> {
            if (!newValue.equals("") && CurrencyUtil.isFiatCurrency(model.tradeCurrencyCode.get())) {
                Label popOverLabel = OfferViewUtil.createPopOverLabel(Res.get("offerbook.info.roundedFiatVolume"));
                volumeInfoInputTextField.setContentForPrivacyPopOver(popOverLabel);
            } else {
                volumeInfoInputTextField.hideIcon();
            }
        };
    }

    private void addListeners() {
        model.tradeCurrencyCode.addListener(tradeCurrencyCodeListener);
        model.volume.addListener(volumeListener);

        // focus out
        amountTextField.focusedProperty().addListener(amountFocusedListener);
        minAmountTextField.focusedProperty().addListener(minAmountFocusedListener);
        fixedPriceTextField.focusedProperty().addListener(priceFocusedListener);
        volumeTextField.focusedProperty().addListener(volumeFocusedListener);

        // warnings
        model.errorMessage.addListener(errorMessageListener);

        model.placeOfferCompleted.addListener(placeOfferCompletedListener);

        // UI actions
        paymentAccountsComboBox.setOnAction(paymentAccountsComboBoxSelectionHandler);
    }

    private void removeListeners() {
        model.tradeCurrencyCode.removeListener(tradeCurrencyCodeListener);
        model.volume.removeListener(volumeListener);

        // focus out
        amountTextField.focusedProperty().removeListener(amountFocusedListener);
        minAmountTextField.focusedProperty().removeListener(minAmountFocusedListener);
        fixedPriceTextField.focusedProperty().removeListener(priceFocusedListener);
        volumeTextField.focusedProperty().removeListener(volumeFocusedListener);

        // warnings
        model.errorMessage.removeListener(errorMessageListener);

        model.placeOfferCompleted.removeListener(placeOfferCompletedListener);

        // UI actions
        paymentAccountsComboBox.setOnAction(null);
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
        gridPane.setPadding(new Insets(30, 25, -1, 25));
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
        paymentTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 1, Res.get("shared.selectTradingAccount"));
        GridPane.setColumnSpan(paymentTitledGroupBg, 2);

        HBox paymentGroupBox = new HBox();
        paymentGroupBox.setAlignment(Pos.CENTER_LEFT);
        paymentGroupBox.setSpacing(12);
        paymentGroupBox.setPadding(new Insets(10, 0, 18, 0));

        final Tuple3<VBox, Label, ComboBox<PaymentAccount>> tradingAccountBoxTuple = addTopLabelComboBox(
                Res.get("shared.tradingAccount"), Res.get("shared.selectTradingAccount"));

        paymentGroupBox.getChildren().addAll(tradingAccountBoxTuple.first);

        GridPane.setRowIndex(paymentGroupBox, gridRow);
        GridPane.setColumnSpan(paymentGroupBox, 2);
        GridPane.setMargin(paymentGroupBox, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(paymentGroupBox);

        tradingAccountBoxTuple.first.setMinWidth(800);
        paymentAccountsComboBox = tradingAccountBoxTuple.third;
        paymentAccountsComboBox.setMinWidth(tradingAccountBoxTuple.first.getMinWidth());
        paymentAccountsComboBox.setPrefWidth(tradingAccountBoxTuple.first.getMinWidth());
        editOfferElements.add(tradingAccountBoxTuple.first);

        final Tuple3<Label, TextField, VBox> currencyTextFieldTuple =
                addTopLabelTextField(gridPane, gridRow, Res.get("shared.currency"), "", 5d);
        currencyTextField = currencyTextFieldTuple.second;
        currencyTextFieldBox = currencyTextFieldTuple.third;
        currencyTextFieldBox.setVisible(false);
        editOfferElements.add(currencyTextFieldBox);

        paymentGroupBox.getChildren().add(currencyTextFieldBox);
    }

    private void addAmountPriceGroup() {
        amountTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("createOffer.setAmountPrice"), Layout.COMPACT_GROUP_DISTANCE);
        GridPane.setColumnSpan(amountTitledGroupBg, 2);

        addFirstRow();
        addSecondRow();
    }

    private void addOptionsGroup() {
        // TODO(sq): Add option for max tx fee
//        TitledGroupBg optionsTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1,
//                Res.get("shared.advancedOptions"), Layout.COMPACT_GROUP_DISTANCE);

        HBox advancedOptionsBox = new HBox();
        advancedOptionsBox.setSpacing(40);

        GridPane.setRowIndex(advancedOptionsBox, gridRow);
        GridPane.setColumnIndex(advancedOptionsBox, 0);
        GridPane.setHalignment(advancedOptionsBox, HPos.LEFT);
        GridPane.setMargin(advancedOptionsBox, new Insets(Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(advancedOptionsBox);

        advancedOptionsBox.getChildren().addAll(getTradeFeeFieldsBox());

        Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane, ++gridRow,
                Res.get("shared.nextStep"), Res.get("shared.cancel"));
        placeOfferButton = (AutoTooltipButton) tuple.first;
        editOfferElements.add(placeOfferButton);
        placeOfferButton.disableProperty().bind(model.isPlaceOfferButtonDisabled);
        AutoTooltipButton cancelButton1 = (AutoTooltipButton) tuple.second;
        cancelButton1.setMaxWidth(200);
        editOfferElements.add(cancelButton1);
        cancelButton1.setDefaultButton(false);
        cancelButton1.setOnAction(e -> {
            close();
        });

        placeOfferButton.setOnAction(e -> onPlaceOffer());
    }

    private void addFirstRow() {
        // amountBox
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueBox(Res.get("createOffer.amount.prompt"));
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
        Label xLabel = new Label();
        Text xIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", xLabel);
        xIcon.getStyleClass().add("opaque-icon");
        xLabel.getStyleClass().add("opaque-icon-character");

        // Fixed price
        Tuple3<HBox, InputTextField, Label> priceValueCurrencyBoxTuple = getEditableValueBox(
                Res.get("createOffer.price.prompt"));
        HBox priceValueCurrencyBox = priceValueCurrencyBoxTuple.first;
        fixedPriceTextField = priceValueCurrencyBoxTuple.second;
        editOfferElements.add(fixedPriceTextField);
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        editOfferElements.add(priceCurrencyLabel);
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox, "");
        priceDescriptionLabel = priceInputBoxTuple.first;

        getSmallIconForLabel(MaterialDesignIcon.LOCK, priceDescriptionLabel, "small-icon-label");

        editOfferElements.add(priceDescriptionLabel);
        VBox fixedPriceBox = priceInputBoxTuple.second;

        // =
        Label resultLabel = new AutoTooltipLabel("=");
        resultLabel.getStyleClass().add("opaque-icon-character");

        // volume
        Tuple3<HBox, InfoInputTextField, Label> volumeValueCurrencyBoxTuple = getEditableValueBoxWithInfo(Res.get("createOffer.volume.prompt"));
        HBox volumeValueCurrencyBox = volumeValueCurrencyBoxTuple.first;
        volumeInfoInputTextField = volumeValueCurrencyBoxTuple.second;
        volumeTextField = volumeInfoInputTextField.getInputTextField();
        editOfferElements.add(volumeTextField);
        volumeCurrencyLabel = volumeValueCurrencyBoxTuple.third;
        editOfferElements.add(volumeCurrencyLabel);
        Tuple2<Label, VBox> volumeInputBoxTuple = getTradeInputBox(volumeValueCurrencyBox, model.volumeDescriptionLabel.get());
        volumeDescriptionLabel = volumeInputBoxTuple.first;
        editOfferElements.add(volumeDescriptionLabel);
        VBox volumeBox = volumeInputBoxTuple.second;

        HBox firstRowHBox = new HBox();
        firstRowHBox.setSpacing(5);
        firstRowHBox.setAlignment(Pos.CENTER_LEFT);
        firstRowHBox.getChildren().addAll(amountBox, xLabel, fixedPriceBox, resultLabel, volumeBox);
        GridPane.setColumnSpan(firstRowHBox, 2);
        GridPane.setRowIndex(firstRowHBox, gridRow);
        GridPane.setMargin(firstRowHBox, new Insets(Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE, 10, 0, 0));
        gridPane.getChildren().add(firstRowHBox);
    }

    private void addSecondRow() {
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueBox(Res.get("createOffer.amount.prompt"));
        HBox minAmountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        minAmountTextField = amountValueCurrencyBoxTuple.second;
        editOfferElements.add(minAmountTextField);
        minAmountBtcLabel = amountValueCurrencyBoxTuple.third;
        editOfferElements.add(minAmountBtcLabel);

        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(minAmountValueCurrencyBox, Res.get("createOffer.amountPriceBox.minAmountDescription"));

        Label fakeXLabel = new Label();
        Text fakeXIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", fakeXLabel);
        fakeXLabel.getStyleClass().add("opaque-icon-character");
        fakeXLabel.setVisible(false); // we just use it to get the same layout as the upper row

        HBox secondRowHBox = new HBox();
        secondRowHBox.setSpacing(5);
        secondRowHBox.setAlignment(Pos.CENTER_LEFT);
        secondRowHBox.getChildren().addAll(amountInputBoxTuple.second, fakeXLabel);
        GridPane.setColumnSpan(secondRowHBox, 2);
        GridPane.setRowIndex(secondRowHBox, ++gridRow);
        GridPane.setColumnIndex(secondRowHBox, 0);
        GridPane.setMargin(secondRowHBox, new Insets(0, 10, 10, 0));
        gridPane.getChildren().add(secondRowHBox);
    }

    private VBox getTradeFeeFieldsBox() {
        return new VBox();
    }
}

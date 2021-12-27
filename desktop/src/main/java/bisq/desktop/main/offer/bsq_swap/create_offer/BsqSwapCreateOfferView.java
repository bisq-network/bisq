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

package bisq.desktop.main.offer.bsq_swap.create_offer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.OfferView;
import bisq.desktop.main.offer.bsq_swap.BsqSwapOfferView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.BsqSwapOfferDetailsWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.openoffer.OpenOffersView;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.beans.value.ChangeListener;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.offer.bsq_swap.BsqSwapOfferModel.BSQ;
import static bisq.desktop.util.FormBuilder.*;

@FxmlView
@Slf4j
public class BsqSwapCreateOfferView extends BsqSwapOfferView<BsqSwapCreateOfferViewModel> {
    private InputTextField minAmountTextField, priceTextField, volumeTextField;
    private Label miningPowLabel;
    private BusyAnimation miningPowBusyAnimation;
    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private ChangeListener<Boolean> minAmountFocusedListener, volumeFocusedListener,
            priceFocusedListener, placeOfferCompletedListener;
    private ChangeListener<String> errorMessageListener;
    private EventHandler<ActionEvent> paymentAccountsComboBoxSelectionHandler;
    private final List<Node> editOfferElements = new ArrayList<>();
    private boolean isActivated;

    @Setter
    private OfferView.OfferActionHandler offerActionHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqSwapCreateOfferView(BsqSwapCreateOfferViewModel model,
                                  Navigation navigation,
                                  BsqSwapOfferDetailsWindow bsqSwapOfferDetailsWindow) {
        super(model, navigation, bsqSwapOfferDetailsWindow);
    }

    @Override
    protected void initialize() {
        super.initialize();
    }

    @Override
    protected void activate() {
        super.activate();

        if (model.dataModel.isTabSelected()) {
            doActivate();
        }
    }

    private void doActivate() {
        if (!isActivated) {
            isActivated = true;
            paymentAccountsComboBox.setPrefWidth(250);

            addListeners();
            addBindings();

            paymentAccountsComboBox.setItems(model.dataModel.getPaymentAccounts());
            paymentAccountsComboBox.getSelectionModel().select(model.dataModel.getPaymentAccount());
            onPaymentAccountsComboBoxSelected();

            String key = "BsqSwapMakerInfo";
            if (DontShowAgainLookup.showAgain(key)) {
                new Popup().information(Res.get("createOffer.bsqSwap.offerVisibility") + "\n\n" + Res.get("bsqSwapOffer.feeHandling"))
                        .width(1000)
                        .closeButtonText(Res.get("shared.iUnderstand"))
                        .dontShowAgainId(key)
                        .show();
            }
        }
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        if (isActivated) {
            isActivated = false;
            removeListeners();
            removeBindings();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(OfferDirection direction,
                             OfferView.OfferActionHandler offerActionHandler,
                             @Nullable BsqSwapOfferPayload offerPayload) {
        this.offerActionHandler = offerActionHandler;

        model.initWithData(offerPayload != null ? offerPayload.getDirection() : direction, offerPayload);

        if (model.dataModel.isBuyOffer()) {
            actionButton.setId("buy-button-big");
            actionButton.updateText(Res.get("createOffer.placeOfferButton", Res.get("shared.buy")));
            volumeDescriptionLabel.setText(Res.get("createOffer.amountPriceBox.buy.volumeDescription", BSQ));
        } else {
            actionButton.setId("sell-button-big");
            actionButton.updateText(Res.get("createOffer.placeOfferButton", Res.get("shared.sell")));
            volumeDescriptionLabel.setText(Res.get("createOffer.amountPriceBox.sell.volumeDescription", BSQ));
        }

        String amountDescription = Res.get("createOffer.amountPriceBox.amountDescription",
                model.dataModel.isBuyOffer() ? Res.get("shared.buy") : Res.get("shared.sell"));
        amountDescriptionLabel.setText(amountDescription);

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onTabSelected(boolean isSelected) {
        if (isSelected && !model.dataModel.isTabSelected()) {
            doActivate();
        }

        isActivated = isSelected;
        model.dataModel.onTabSelected(isSelected);
    }

    @Override
    protected void onCancel1() {
        close();
    }

    @Override
    protected void onShowFeeInfoScreen() {
        super.onShowFeeInfoScreen();

        paymentAccountsComboBox.setDisable(true);
        paymentAccountsComboBox.setMouseTransparent(true);

        editOfferElements.forEach(node -> {
            node.setMouseTransparent(true);
            node.setFocusTraversable(false);
        });

        inputAmountTextField.setFundsStructure(model.getInputAmountDetails());
        inputAmountTextField.setContentForInfoPopOver(createInputAmountDetailsPopover());
        payoutAmountTextField.setFundsStructure(model.getPayoutAmountDetails());
        payoutAmountTextField.setContentForInfoPopOver(createPayoutAmountDetailsPopover());

        model.dataModel.getMissingFunds().addListener(missingFundsListener);
        checkForMissingFunds(model.dataModel.getMissingFunds().get());

        // We create the offer and start do the pow.
        // As the pow could take some time we do it already now and not at offer confirm.
        // We have already all data to create the offer, so no reason to delay it to later.
        model.requestNewOffer();
    }

    @Override
    protected void onAction() {
        if (!model.dataModel.canPlaceOrTakeOffer()) {
            return;
        }

        if (DevEnv.isDevMode()) {
            model.onPlaceOffer();
            requestFocus();
            return;
        }

        bsqSwapOfferDetailsWindow.onPlaceOffer(model::onPlaceOffer).show(model.dataModel.offer);
        requestFocus();
    }

    @Override
    protected void onCancel2() {
        close();
    }

    private void onPaymentAccountsComboBoxSelected() {
        PaymentAccount paymentAccount = paymentAccountsComboBox.getSelectionModel().getSelectedItem();
        // We have represented BSQ swaps as payment method and switch to a new view if a non BSQ swap account is selected
        if (paymentAccount != null && !paymentAccount.getPaymentMethod().isBsqSwap()) {
            close();

            if (offerActionHandler != null) {
                offerActionHandler.onCreateOffer(paymentAccount.getSelectedTradeCurrency(),
                        paymentAccount.getPaymentMethod());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createListeners() {
        super.createListeners();

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
            priceTextField.setText(model.price.get());
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

        placeOfferCompletedListener = (o, oldValue, newValue) -> {
            if (DevEnv.isDevMode()) {
                close();
            } else if (newValue) {
                // We need a bit of delay to avoid issues with fade out/fade in of 2 popups
                String key = "createBsqOfferSuccessInfo";
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
                            100, TimeUnit.MILLISECONDS);
                } else {
                    close();
                }
            }
        };
    }

    @Override
    protected void addListeners() {
        // focus out
        amountTextField.focusedProperty().addListener(amountFocusedListener);
        minAmountTextField.focusedProperty().addListener(minAmountFocusedListener);
        priceTextField.focusedProperty().addListener(priceFocusedListener);
        volumeTextField.focusedProperty().addListener(volumeFocusedListener);

        // warnings
        model.errorMessage.addListener(errorMessageListener);

        model.placeOfferCompleted.addListener(placeOfferCompletedListener);

        // UI actions
        paymentAccountsComboBox.setOnAction(paymentAccountsComboBoxSelectionHandler);
    }

    @Override
    protected void removeListeners() {
        super.removeListeners();

        // focus out
        amountTextField.focusedProperty().removeListener(amountFocusedListener);
        minAmountTextField.focusedProperty().removeListener(minAmountFocusedListener);
        priceTextField.focusedProperty().removeListener(priceFocusedListener);
        volumeTextField.focusedProperty().removeListener(volumeFocusedListener);

        // warnings
        model.errorMessage.removeListener(errorMessageListener);

        model.placeOfferCompleted.removeListener(placeOfferCompletedListener);

        // UI actions
        paymentAccountsComboBox.setOnAction(null);
    }

    @Override
    protected void addBindings() {
        amountTextField.textProperty().bindBidirectional(model.amount);
        minAmountTextField.textProperty().bindBidirectional(model.minAmount);
        priceTextField.textProperty().bindBidirectional(model.price);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        volumeTextField.promptTextProperty().bind(model.volumePromptLabel);
        inputAmountTextField.textProperty().bind(model.getInputAmount());
        payoutAmountTextField.textProperty().bind(model.getPayoutAmount());
        // Validation
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        minAmountTextField.validationResultProperty().bind(model.minAmountValidationResult);
        priceTextField.validationResultProperty().bind(model.priceValidationResult);
        volumeTextField.validationResultProperty().bind(model.volumeValidationResult);

        nextButton.disableProperty().bind(model.isNextButtonDisabled);
        actionButton.disableProperty().bind(model.isPlaceOfferButtonDisabled);
        cancelButton2.disableProperty().bind(model.cancelButtonDisabled);

        // trading account
        paymentAccountTitledGroupBg.managedProperty().bind(paymentAccountTitledGroupBg.visibleProperty());
        currencyTextFieldBox.managedProperty().bind(currencyTextFieldBox.visibleProperty());

        miningPowLabel.visibleProperty().bind(model.miningPoW);
        miningPowLabel.managedProperty().bind(model.miningPoW);
        miningPowBusyAnimation.visibleProperty().bind(model.miningPoW);
        miningPowBusyAnimation.managedProperty().bind(model.miningPoW);
        miningPowBusyAnimation.isRunningProperty().bind(model.miningPoW);
    }

    @Override
    protected void removeBindings() {
        amountTextField.textProperty().unbindBidirectional(model.amount);
        minAmountTextField.textProperty().unbindBidirectional(model.minAmount);
        priceTextField.textProperty().unbindBidirectional(model.price);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        volumeTextField.promptTextProperty().unbindBidirectional(model.volume);
        inputAmountTextField.textProperty().unbind();
        payoutAmountTextField.textProperty().unbind();

        // Validation
        amountTextField.validationResultProperty().unbind();
        minAmountTextField.validationResultProperty().unbind();
        priceTextField.validationResultProperty().unbind();
        volumeTextField.validationResultProperty().unbind();

        nextButton.disableProperty().unbind();
        actionButton.disableProperty().unbind();
        cancelButton2.disableProperty().unbind();

        // trading account
        paymentAccountTitledGroupBg.managedProperty().unbind();
        currencyTextFieldBox.managedProperty().unbind();

        miningPowLabel.visibleProperty().unbind();
        miningPowLabel.managedProperty().unbind();
        miningPowBusyAnimation.visibleProperty().unbind();
        miningPowBusyAnimation.managedProperty().unbind();
        miningPowBusyAnimation.isRunningProperty().unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build UI elements
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addPaymentAccountGroup() {
        paymentAccountTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 1, Res.get("shared.selectTradingAccount"));
        GridPane.setColumnSpan(paymentAccountTitledGroupBg, 2);

        HBox paymentGroupBox = new HBox();
        paymentGroupBox.setAlignment(Pos.CENTER_LEFT);
        paymentGroupBox.setSpacing(12);
        paymentGroupBox.setPadding(new Insets(10, 0, 18, 0));

        Tuple3<VBox, Label, ComboBox<PaymentAccount>> paymentAccountBoxTuple = addTopLabelComboBox(
                Res.get("shared.tradingAccount"), Res.get("shared.selectTradingAccount"));

        Tuple3<Label, TextField, VBox> currencyTextFieldTuple = addTopLabelTextField(gridPane, gridRow,
                Res.get("shared.currency"), BSQ, 5d);
        currencyTextFieldBox = currencyTextFieldTuple.third;

        paymentAccountVBox = paymentAccountBoxTuple.first;
        paymentGroupBox.getChildren().addAll(paymentAccountVBox, currencyTextFieldBox);

        GridPane.setRowIndex(paymentGroupBox, gridRow);
        GridPane.setColumnSpan(paymentGroupBox, 2);
        GridPane.setMargin(paymentGroupBox, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(paymentGroupBox);

        paymentAccountVBox.setMinWidth(800);
        paymentAccountsComboBox = paymentAccountBoxTuple.third;
        paymentAccountsComboBox.setMinWidth(paymentAccountVBox.getMinWidth());
        paymentAccountsComboBox.setPrefWidth(paymentAccountVBox.getMinWidth());
        paymentAccountsComboBox.setConverter(GUIUtil.getPaymentAccountsComboBoxStringConverter());
        paymentAccountsComboBox.setButtonCell(GUIUtil.getComboBoxButtonCell(Res.get("shared.selectTradingAccount"),
                paymentAccountsComboBox, false));
        paymentAccountsComboBox.setCellFactory(getPaymentAccountListCellFactory(paymentAccountsComboBox));

        editOfferElements.add(paymentAccountVBox);
    }

    @Override
    protected void addAmountPriceGroup() {
        TitledGroupBg amountTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("createOffer.setAmountPrice"), Layout.COMPACT_GROUP_DISTANCE);
        GridPane.setColumnSpan(amountTitledGroupBg, 2);

        addFirstRow();
        addSecondRow();
    }

    private void addFirstRow() {
        // amountBox
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueBox(Res.get("createOffer.amount.prompt"));
        amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountTextField = amountValueCurrencyBoxTuple.second;
        editOfferElements.add(amountTextField);
        Label amountBtcLabel = amountValueCurrencyBoxTuple.third;
        editOfferElements.add(amountBtcLabel);
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, "");
        amountDescriptionLabel = amountInputBoxTuple.first;
        editOfferElements.add(amountDescriptionLabel);
        VBox amountBox = amountInputBoxTuple.second;

        // x
        xLabel = new Label();
        xIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", xLabel);
        xIcon.getStyleClass().add("opaque-icon");
        xLabel.getStyleClass().add("opaque-icon-character");

        // price
        Tuple3<HBox, InputTextField, Label> priceValueCurrencyBoxTuple = getEditableValueBox(
                Res.get("createOffer.price.prompt"));
        priceValueCurrencyBox = priceValueCurrencyBoxTuple.first;
        priceTextField = priceValueCurrencyBoxTuple.second;
        editOfferElements.add(priceTextField);
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        priceCurrencyLabel.setText("BTC");
        editOfferElements.add(priceCurrencyLabel);
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox, "");
        priceDescriptionLabel = priceInputBoxTuple.first;
        priceDescriptionLabel.setText(CurrencyUtil.getPriceWithCurrencyCode(BSQ, "shared.fixedPriceInCurForCur"));


        getSmallIconForLabel(MaterialDesignIcon.LOCK, priceDescriptionLabel, "small-icon-label");

        editOfferElements.add(priceDescriptionLabel);
        VBox fixedPriceBox = priceInputBoxTuple.second;

        // =
        resultLabel = new AutoTooltipLabel("=");
        resultLabel.getStyleClass().add("opaque-icon-character");

        // volume
        Tuple3<HBox, InfoInputTextField, Label> volumeValueCurrencyBoxTuple = getEditableValueBoxWithInfo(Res.get("createOffer.volume.prompt"));
        volumeValueCurrencyBox = volumeValueCurrencyBoxTuple.first;
        InfoInputTextField volumeInfoInputTextField = volumeValueCurrencyBoxTuple.second;
        volumeTextField = volumeInfoInputTextField.getInputTextField();
        editOfferElements.add(volumeTextField);
        volumeCurrencyLabel = volumeValueCurrencyBoxTuple.third;
        volumeCurrencyLabel.setText(BSQ);
        editOfferElements.add(volumeCurrencyLabel);
        Tuple2<Label, VBox> volumeInputBoxTuple = getTradeInputBox(volumeValueCurrencyBox, "");
        volumeDescriptionLabel = volumeInputBoxTuple.first;
        editOfferElements.add(volumeDescriptionLabel);
        VBox volumeBox = volumeInputBoxTuple.second;

        firstRowHBox = new HBox();
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
        minAmountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        minAmountTextField = amountValueCurrencyBoxTuple.second;
        editOfferElements.add(minAmountTextField);
        Label minAmountBtcLabel = amountValueCurrencyBoxTuple.third;
        editOfferElements.add(minAmountBtcLabel);

        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(minAmountValueCurrencyBox, Res.get("createOffer.amountPriceBox.minAmountDescription"));

        secondRowHBox = new HBox();
        secondRowHBox.setSpacing(5);
        secondRowHBox.setAlignment(Pos.CENTER_LEFT);
        secondRowHBox.getChildren().add(amountInputBoxTuple.second);
        GridPane.setColumnSpan(secondRowHBox, 2);
        GridPane.setRowIndex(secondRowHBox, ++gridRow);
        GridPane.setColumnIndex(secondRowHBox, 0);
        GridPane.setMargin(secondRowHBox, new Insets(0, 10, 10, 0));
        gridPane.getChildren().add(secondRowHBox);
    }

    @Override
    protected void addNextAndCancelButtons() {
        super.addNextAndCancelButtons();

        editOfferElements.add(nextButton);
        editOfferElements.add(cancelButton1);
    }

    @Override
    protected void addFeeInfoGroup() {
        super.addFeeInfoGroup();

        miningPowBusyAnimation = new BusyAnimation(false);
        miningPowLabel = new AutoTooltipLabel(Res.get("createOffer.bsqSwap.mintingPow"));
        HBox.setMargin(miningPowLabel, new Insets(6, 0, 0, 0));
        actionButtonBar.getChildren().addAll(miningPowBusyAnimation, miningPowLabel);
    }

    @Override
    protected void updateOfferElementsStyle() {
        super.updateOfferElementsStyle();

        GridPane.setColumnSpan(firstRowHBox, 2);
        GridPane.setColumnSpan(secondRowHBox, 1);
    }

    @Override
    protected void checkForMissingFunds(Coin missing) {
        if (missing.isPositive() && !isMissingFundsPopupOpen) {
            isMissingFundsPopupOpen = true;
            String wallet = model.dataModel.isBuyer() ? "BSQ" : "BTC";
            String warning = Res.get("createOffer.bsqSwap.missingFunds.maker",
                    wallet, model.getMissingFunds(missing));
            new Popup().warning(warning)
                    .onClose(() -> {
                        isMissingFundsPopupOpen = false;
                    })
                    .show();
        }
    }
}

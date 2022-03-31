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

package bisq.desktop.main.offer.bsq_swap.edit_offer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.offer.bsq_swap.BsqSwapOfferView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.Layout;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.DontShowAgainLookup;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Tuple4;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.beans.value.ChangeListener;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.offer.bsq_swap.BsqSwapOfferModel.BSQ;
import static bisq.desktop.util.FormBuilder.*;

@FxmlView
@Slf4j
public class BsqSwapEditOfferView extends BsqSwapOfferView<BsqSwapEditOfferViewModel> {
    private TextField paymentMethodTextField, currencyTextField;
    private InputTextField minAmountTextField, priceTextField, volumeTextField;
    private ChangeListener<Boolean> priceFocusedListener;
    private boolean isActivated;
    private BusyAnimation busyAnimation;
    private Label spinnerInfoLabel;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqSwapEditOfferView(BsqSwapEditOfferViewModel model,
                                Navigation navigation) {
        super(model, navigation, null);
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

            addListeners();
            addBindings();
        }
    }

    public void onClose() {
        model.onCancelEditOffer(() -> {}, errorMessage -> {
            log.error(errorMessage);
            new Popup().warning(Res.get("editOffer.failed", errorMessage)).show();
        });
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

    public void applyOpenOffer(OpenOffer openOffer) {
        model.applyOpenOffer(openOffer);

        model.onStartEditOffer(() -> {}, errorMessage -> {
            log.error(errorMessage);
            new Popup().warning(Res.get("editOffer.failed", errorMessage))
                    .onClose(this::close)
                    .show();
        });

        updateElementsWithDirection();
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
    protected void onCancel2() {
        close();
    }

    @Override
    protected void onAction() {
        if (!model.dataModel.canPlaceOrTakeOffer()) {
            return;
        }

        if (!isMissingFundsPopupOpen && model.dataModel.hasMissingFunds()) {
            checkForMissingFunds(model.dataModel.getMissingFunds().get());
            return;
        }

        isMissingFundsPopupOpen = false;
        model.isNextButtonDisabled.setValue(true);
        model.isCancelButtonDisabled.setValue(true);
        spinnerInfoLabel.setText(Res.get("editOffer.publishOffer"));
        busyAnimation.play();

        model.onPublishOffer(() -> {
            String key = "editOfferSuccess";
            if (DontShowAgainLookup.showAgain(key)) {
                new Popup()
                        .feedback(Res.get("editOffer.success"))
                        .dontShowAgainId(key)
                        .show();
            }
            spinnerInfoLabel.setText("");
            busyAnimation.stop();
            close();
        }, (message) -> {
            log.error(message);
            spinnerInfoLabel.setText("");
            busyAnimation.stop();
            model.isNextButtonDisabled.setValue(false);
            model.isCancelButtonDisabled.setValue(false);
            new Popup().warning(Res.get("editOffer.failed", message)).show();
        });

        requestFocus();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createListeners() {
        super.createListeners();

        priceFocusedListener = (o, oldValue, newValue) -> {
            if (oldValue && !newValue) model.onFocusOutPriceTextField();
            priceTextField.setText(model.price.get());
        };
    }

    @Override
    protected void addListeners() {
        // focus out
        priceTextField.focusedProperty().addListener(priceFocusedListener);
    }

    @Override
    protected void removeListeners() {
        super.removeListeners();

        // focus out
        priceTextField.focusedProperty().removeListener(priceFocusedListener);
    }

    @Override
    protected void addBindings() {
        amountTextField.textProperty().bindBidirectional(model.amount);
        minAmountTextField.textProperty().bindBidirectional(model.minAmount);
        priceTextField.textProperty().bindBidirectional(model.price);
        volumeTextField.textProperty().bindBidirectional(model.volume);

        // validation
        priceTextField.validationResultProperty().bind(model.priceValidationResult);

        nextButton.disableProperty().bind(model.isNextButtonDisabled);
        cancelButton1.disableProperty().bind(model.isCancelButtonDisabled);

        // trading account
        paymentAccountTitledGroupBg.managedProperty().bind(paymentAccountTitledGroupBg.visibleProperty());
    }

    @Override
    protected void removeBindings() {
        amountTextField.textProperty().unbindBidirectional(model.amount);
        minAmountTextField.textProperty().unbindBidirectional(model.minAmount);
        priceTextField.textProperty().unbindBidirectional(model.price);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        volumeTextField.promptTextProperty().unbindBidirectional(model.volume);

        // Validation
        priceTextField.validationResultProperty().unbind();

        nextButton.disableProperty().unbind();
        cancelButton1.disableProperty().unbind();

        // trading account
        paymentAccountTitledGroupBg.managedProperty().unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build UI elements
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addPaymentAccountGroup() {
        paymentAccountTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 1, Res.get("shared.editOffer"));
        GridPane.setColumnSpan(paymentAccountTitledGroupBg, 2);

        // We use the addComboBoxTopLabelTextField only for convenience for having the expected layout
        Tuple4<ComboBox<PaymentAccount>, Label, TextField, HBox> paymentAccountTuple = addComboBoxTopLabelTextField(gridPane,
                gridRow, "", Res.get("shared.paymentMethod"), Layout.FIRST_ROW_DISTANCE);
        HBox hBox = paymentAccountTuple.fourth;
        hBox.getChildren().remove(paymentAccountTuple.first);

        paymentMethodTextField = paymentAccountTuple.third;
        paymentMethodTextField.setMinWidth(300);
        paymentMethodTextField.setEditable(false);
        paymentMethodTextField.setMouseTransparent(true);
        paymentMethodTextField.setFocusTraversable(false);
        paymentMethodTextField.setText(PaymentMethod.BSQ_SWAP.getDisplayString());

        currencyTextField = new JFXTextField(CurrencyUtil.getNameByCode(BSQ));
        currencyTextField.setMinWidth(300);
        currencyTextField.setEditable(false);
        currencyTextField.setMouseTransparent(true);
        currencyTextField.setFocusTraversable(false);

        Tuple2<Label, VBox> tradeCurrencyTuple = getTopLabelWithVBox(Res.get("shared.tradeCurrency"), currencyTextField);
        VBox vBox = tradeCurrencyTuple.second;
        HBox.setMargin(vBox, new Insets(5, 0, 0, 0));

        hBox.setSpacing(30);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.setPadding(new Insets(10, 0, 18, 0));
        hBox.getChildren().add(vBox);
    }

    @Override
    protected void addAmountPriceGroup() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("createOffer.setAmountPrice"), Layout.COMPACT_GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 2);

        addFirstRow();
        addSecondRow();
    }

    private void addFirstRow() {
        // amountBox
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getNonEditableValueBox();
        amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountTextField = amountValueCurrencyBoxTuple.second;
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, "");
        amountDescriptionLabel = amountInputBoxTuple.first;
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
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        priceCurrencyLabel.setText("BTC");
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox, "");
        priceDescriptionLabel = priceInputBoxTuple.first;
        priceDescriptionLabel.setText(CurrencyUtil.getPriceWithCurrencyCode(BSQ, "shared.fixedPriceInCurForCur"));


        getSmallIconForLabel(MaterialDesignIcon.LOCK, priceDescriptionLabel, "small-icon-label");

        VBox fixedPriceBox = priceInputBoxTuple.second;

        // =
        resultLabel = new AutoTooltipLabel("=");
        resultLabel.getStyleClass().add("opaque-icon-character");

        // volume
        Tuple3<HBox, InfoInputTextField, Label> volumeValueCurrencyBoxTuple = getNonEditableValueBoxWithInfo();
        volumeValueCurrencyBox = volumeValueCurrencyBoxTuple.first;
        InfoInputTextField volumeInfoInputTextField = volumeValueCurrencyBoxTuple.second;
        volumeTextField = volumeInfoInputTextField.getInputTextField();
        volumeTextField.setPromptText(Res.get("createOffer.volume.prompt", BSQ));
        volumeCurrencyLabel = volumeValueCurrencyBoxTuple.third;
        volumeCurrencyLabel.setText(BSQ);
        Tuple2<Label, VBox> volumeInputBoxTuple = getTradeInputBox(volumeValueCurrencyBox, "");
        volumeDescriptionLabel = volumeInputBoxTuple.first;
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
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getNonEditableValueBox();
        minAmountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        minAmountTextField = amountValueCurrencyBoxTuple.second;
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
        Tuple4<Button, BusyAnimation, Label, HBox> tuple = addButtonBusyAnimationLabelAfterGroup(gridPane,
                ++gridRow, Res.get("editOffer.confirmEdit"));

        nextButtonBar = tuple.fourth;

        nextButton = tuple.first;
        nextButton.setMaxWidth(250);
        nextButton.setOnAction(e -> onAction());

        busyAnimation = tuple.second;
        spinnerInfoLabel = tuple.third;

        cancelButton1 = new AutoTooltipButton(Res.get("shared.cancel"));
        cancelButton1.setDefaultButton(false);
        cancelButton1.setMaxWidth(250);
        cancelButton1.setOnAction(e -> close());
        nextButtonBar.getChildren().add(cancelButton1);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateElementsWithDirection() {
        boolean isBuyOffer = model.dataModel.isBuyOffer();

        String volumeDescription = isBuyOffer
                ? Res.get("createOffer.amountPriceBox.buy.volumeDescription", BSQ)
                : Res.get("createOffer.amountPriceBox.sell.volumeDescription", BSQ);

        volumeDescriptionLabel.setText(volumeDescription);

        String amountDescription = isBuyOffer
                ? Res.get("createOffer.amountPriceBox.amountDescription", Res.get("shared.buy"))
                : Res.get("createOffer.amountPriceBox.amountDescription", Res.get("shared.sell"));

        amountDescriptionLabel.setText(amountDescription);

        ImageView iconView = new ImageView();
        iconView.setId(isBuyOffer ? "image-buy-white" : "image-sell-white");
        nextButton.setGraphic(iconView);
        nextButton.setId(isBuyOffer ? "sell-button-big" : "buy-button-big");
    }

    @Override
    protected void checkForMissingFunds(Coin missing) {
        if (missing.isPositive() && !isMissingFundsPopupOpen) {
            isMissingFundsPopupOpen = true;
            String wallet = model.dataModel.isBuyer() ? "BSQ" : "BTC";
            String warning = Res.get("createOffer.bsqSwap.missingFunds.maker",
                    wallet, model.getMissingFunds(missing));
            new Popup().warning(warning)
                    .actionButtonText(Res.get("shared.continueAnyway"))
                    .onAction(() -> onAction())
                    .onClose(() -> {
                        isMissingFundsPopupOpen = false;
                    })
                    .show();
        }
    }
}

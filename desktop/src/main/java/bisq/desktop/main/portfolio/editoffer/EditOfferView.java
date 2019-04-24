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

package bisq.desktop.main.portfolio.editoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.main.offer.MutableOfferView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.util.Transitions;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.user.Preferences;
import bisq.core.util.BSFormatter;
import bisq.core.util.BsqFormatter;

import bisq.common.util.Tuple4;

import com.google.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import static bisq.desktop.util.FormBuilder.addButtonBusyAnimationLabelAfterGroup;

@FxmlView
public class EditOfferView extends MutableOfferView<EditOfferViewModel> {

    private BusyAnimation busyAnimation;
    private Button confirmButton;
    private Button cancelButton;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private EditOfferView(EditOfferViewModel model, Navigation navigation, Preferences preferences, Transitions transitions, OfferDetailsWindow offerDetailsWindow, BSFormatter btcFormatter, BsqFormatter bsqFormatter) {
        super(model, navigation, preferences, transitions, offerDetailsWindow, btcFormatter, bsqFormatter);
    }

    @Override
    protected void initialize() {
        super.initialize();

        addConfirmEditGroup();
        renameAmountGroup();
    }

    private void renameAmountGroup() {
        amountTitledGroupBg.setText(Res.get("editOffer.setPrice"));
    }

    @Override
    protected void doSetFocus() {
        // Don't focus in any field before data was set
    }

    @Override
    protected void doActivate() {
        super.doActivate();

        addBindings();

        hideOptionsGroup();

        // Lock amount field as it would require bigger changes to support increased amount values.
        amountTextField.setDisable(true);
        amountBtcLabel.setDisable(true);
        minAmountTextField.setDisable(true);
        minAmountBtcLabel.setDisable(true);
        volumeTextField.setDisable(true);
        volumeCurrencyLabel.setDisable(true);

        // Workaround to fix margin on top of amount group
        gridPane.setPadding(new Insets(-20, 25, -1, 25));

        updatePriceToggle();
        updateElementsWithDirection();

        model.isNextButtonDisabled.setValue(false);
        cancelButton.setDisable(false);

        model.onInvalidateMarketPriceMargin();
        model.onInvalidatePrice();

        // To force re-validation of payment account validation
        onPaymentAccountsComboBoxSelected();
        hidePaymentGroup();
    }

    @Override
    public void onClose() {
        model.onCancelEditOffer(errorMessage -> {
            log.error(errorMessage);
            new Popup<>().warning(Res.get("editOffer.failed", errorMessage)).show();
        });
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        removeBindings();
    }

    @Override
    protected void showFiatRoundingInfoPopup() {
        // don't show it again as it was already shown when creating the offer in the first place
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyOpenOffer(OpenOffer openOffer) {
        model.applyOpenOffer(openOffer);

        initWithData(openOffer.getOffer().getDirection(),
                CurrencyUtil.getTradeCurrency(openOffer.getOffer().getCurrencyCode()).get());

        model.onStartEditOffer(errorMessage -> {
            log.error(errorMessage);
            new Popup<>().warning(Res.get("editOffer.failed", errorMessage))
                    .onClose(this::close)
                    .show();
        });

        if (!model.isSecurityDepositValid()) {
            new Popup<>().warning(Res.get("editOffer.invalidDeposit"))
                    .onClose(this::close)
                    .show();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        confirmButton.disableProperty().bind(model.isNextButtonDisabled);
    }

    private void removeBindings() {
        confirmButton.disableProperty().unbind();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build UI elements
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addConfirmEditGroup() {

        int tmpGridRow = 4;
        final Tuple4<Button, BusyAnimation, Label, HBox> editOfferTuple = addButtonBusyAnimationLabelAfterGroup(gridPane, tmpGridRow++, Res.get("editOffer.confirmEdit"));

        final HBox editOfferConfirmationBox = editOfferTuple.forth;
        editOfferConfirmationBox.setAlignment(Pos.CENTER_LEFT);
        GridPane.setHalignment(editOfferConfirmationBox, HPos.LEFT);

        confirmButton = editOfferTuple.first;
        confirmButton.setMinHeight(40);
        confirmButton.setPadding(new Insets(0, 20, 0, 20));
        confirmButton.setGraphicTextGap(10);

        busyAnimation = editOfferTuple.second;
        Label spinnerInfoLabel = editOfferTuple.third;

        cancelButton = new AutoTooltipButton(Res.get("shared.cancel"));
        cancelButton.setDefaultButton(false);
        cancelButton.setId("cancel-button");
        cancelButton.setOnAction(event -> close());
        editOfferConfirmationBox.getChildren().add(cancelButton);

        confirmButton.setOnAction(e -> {
            if (model.isPriceInRange()) {
                model.isNextButtonDisabled.setValue(true);
                cancelButton.setDisable(true);
                busyAnimation.play();
                spinnerInfoLabel.setText(Res.get("editOffer.publishOffer"));
                //edit offer
                model.onPublishOffer(() -> {
                    log.debug("Edit offer was successful");
                    new Popup<>().feedback(Res.get("editOffer.success")).show();
                    spinnerInfoLabel.setText("");
                    busyAnimation.stop();
                    close();
                }, (message) -> {
                    log.error(message);
                    spinnerInfoLabel.setText("");
                    busyAnimation.stop();
                    model.isNextButtonDisabled.setValue(false);
                    cancelButton.setDisable(false);
                    new Popup<>().warning(Res.get("editOffer.failed", message)).show();
                });
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateElementsWithDirection() {
        ImageView iconView = new ImageView();
        iconView.setId(model.isSellOffer() ? "image-sell-white" : "image-buy-white");
        confirmButton.setGraphic(iconView);
        confirmButton.setId(model.isSellOffer() ? "sell-button-big" : "buy-button-big");
    }
}

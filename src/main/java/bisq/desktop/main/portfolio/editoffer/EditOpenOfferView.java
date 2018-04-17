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
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.EditableOfferView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.openoffer.OpenOffersView;
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.BsqFormatter;
import bisq.desktop.util.Transitions;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;

import bisq.common.util.Tuple3;

import com.google.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

import javafx.geometry.Insets;

import static bisq.desktop.util.FormBuilder.addButton;
import static bisq.desktop.util.FormBuilder.addButtonBusyAnimationLabelAfterGroup;

@FxmlView
public class EditOpenOfferView extends EditableOfferView<EditOpenOfferViewModel> {

    private BusyAnimation busyAnimation;
    private Button confirmButton;
    private Button cancelButton;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private EditOpenOfferView(EditOpenOfferViewModel model, Navigation navigation, Preferences preferences, Transitions transitions, OfferDetailsWindow offerDetailsWindow, BSFormatter btcFormatter, BsqFormatter bsqFormatter) {
        super(model, navigation, preferences, transitions, offerDetailsWindow, btcFormatter, bsqFormatter);
    }

    @Override
    protected void initialize() {
        super.initialize();

        addConfirmEditGroup();
    }

    @Override
    protected void doActivate() {
        super.doActivate();

        addBindings();

        hidePaymentGroup();
        hideOptionsGroup();

        //workaround to fix margin on top of amount group
        gridPane.setPadding(new Insets(-20, 25, -1, 25));

        updateMarketPriceAvailable();
        updateElementsWithDirection();

        model.onStartEditOffer(errorMessage -> {
            log.error(errorMessage);
            new Popup<>().warning(Res.get("editOffer.failed", errorMessage))
                    .onClose(() -> {
                        close();
                    })
                    .show();
        });

        model.isNextButtonDisabled.setValue(false);
        cancelButton.setDisable(false);

        model.onInvalidateMarketPriceMargin();
        model.onInvalidatePrice();
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(OpenOffer openOffer) {
        super.initWithData(openOffer.getOffer().getDirection(), CurrencyUtil.getTradeCurrency(openOffer.getOffer().getCurrencyCode()).get());
        model.initWithData(openOffer);
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
        final Tuple3<Button, BusyAnimation, Label> editOfferTuple = addButtonBusyAnimationLabelAfterGroup(gridPane, tmpGridRow++, Res.get("editOffer.confirmEdit"));

        confirmButton = editOfferTuple.first;
        confirmButton.setMinHeight(40);
        confirmButton.setPadding(new Insets(0, 20, 0, 20));
        confirmButton.setGraphicTextGap(10);


        busyAnimation = editOfferTuple.second;
        Label spinnerInfoLabel = editOfferTuple.third;


        cancelButton = addButton(gridPane, tmpGridRow, Res.get("shared.cancel"));
        cancelButton.setDefaultButton(false);
        cancelButton.setId("cancel-button");
        cancelButton.setOnAction(event -> close());

        confirmButton.setOnAction(e -> {

            if (model.isPriceInRange()) {

                model.isNextButtonDisabled.setValue(true);
                cancelButton.setDisable(true);
                busyAnimation.play();
                spinnerInfoLabel.setText(Res.get("editOffer.publishOffer"));
                //edit offer
                model.onPublishOffer(() -> {
                    log.debug("Edit offer was successful");

                    String key = "ShowOpenOffersAfterEditing";

                    if (DontShowAgainLookup.showAgain(key))
                        //noinspection unchecked
                        new Popup<>().feedback(Res.get("editOffer.success"))
                                .actionButtonTextWithGoTo("navigation.portfolio.myOpenOffers")
                                .onAction(() -> navigation.navigateTo(MainView.class, PortfolioView.class, OpenOffersView.class))
                                .dontShowAgainId(key)
                                .show();
                    spinnerInfoLabel.setText("");
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

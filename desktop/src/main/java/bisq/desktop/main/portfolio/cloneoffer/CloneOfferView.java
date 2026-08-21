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

package bisq.desktop.main.portfolio.cloneoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.main.offer.bisq_v1.MutableOfferView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.util.GUIUtil;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.payment.PaymentAccount;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.util.Tuple4;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.stream.Collectors;

import static bisq.desktop.util.FormBuilder.addButtonBusyAnimationLabelAfterGroup;

@FxmlView
public class CloneOfferView extends MutableOfferView<CloneOfferViewModel> {

    private BusyAnimation busyAnimation;
    private Button cloneButton;
    private Button cancelButton;
    private Label spinnerInfoLabel;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private CloneOfferView(CloneOfferViewModel model,
                           Navigation navigation,
                           Preferences preferences,
                           OfferDetailsWindow offerDetailsWindow,
                           @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                           BsqFormatter bsqFormatter) {
        super(model, navigation, preferences, offerDetailsWindow, btcFormatter, bsqFormatter);
    }

    @Override
    protected void initialize() {
        super.initialize();

        addCloneGroup();
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
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        removeBindings();
    }

    @Override
    public void onClose() {
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyOpenOffer(OpenOffer openOffer) {
        model.applyOpenOffer(openOffer);

        initWithData(openOffer.getOffer().getDirection(),
                CurrencyUtil.getTradeCurrency(openOffer.getOffer().getCurrencyCode()).get(),
                null);

        if (!model.isSecurityDepositValid()) {
            new Popup().warning(Res.get("editOffer.invalidDeposit"))
                    .onClose(this::close)
                    .show();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        cloneButton.disableProperty().bind(model.isNextButtonDisabled);
    }

    private void removeBindings() {
        cloneButton.disableProperty().unbind();
    }

    @Override
    protected ObservableList<PaymentAccount> filterPaymentAccounts(ObservableList<PaymentAccount> paymentAccounts) {
        // We do not allow cloning or BSQ as there is no maker fee and requirement for reserved funds.
        // Do not create a new ObservableList as that would cause bugs with the selected account.
        List<PaymentAccount> toRemove = paymentAccounts.stream()
                .filter(paymentAccount -> GUIUtil.BSQ.equals(paymentAccount.getSingleTradeCurrency()))
                .collect(Collectors.toList());
        toRemove.forEach(paymentAccounts::remove);
        return paymentAccounts;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build UI elements
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addCloneGroup() {
        Tuple4<Button, BusyAnimation, Label, HBox> tuple4 = addButtonBusyAnimationLabelAfterGroup(gridPane, 4, Res.get("cloneOffer.clone"));

        HBox hBox = tuple4.fourth;
        hBox.setAlignment(Pos.CENTER_LEFT);
        GridPane.setHalignment(hBox, HPos.LEFT);

        cloneButton = tuple4.first;
        cloneButton.setMinHeight(40);
        cloneButton.setPadding(new Insets(0, 20, 0, 20));
        cloneButton.setGraphicTextGap(10);

        busyAnimation = tuple4.second;
        spinnerInfoLabel = tuple4.third;

        cancelButton = new AutoTooltipButton(Res.get("shared.cancel"));
        cancelButton.setDefaultButton(false);
        cancelButton.setOnAction(event -> close());
        hBox.getChildren().add(cancelButton);

        cloneButton.setOnAction(e -> {
            cloneButton.requestFocus();   // fix issue #5460 (when enter key used, focus is wrong)
            onClone();
        });
    }

    private void onClone() {
        if (model.dataModel.cannotActivateOffer()) {
            new Popup().warning(Res.get("cloneOffer.cannotActivateOffer"))
                    .actionButtonText(Res.get("shared.yes"))
                    .onAction(this::doClone)
                    .closeButtonText(Res.get("shared.no"))
                    .show();
        } else {
            doClone();
        }
    }

    private void doClone() {
        if (model.isPriceInRange()) {
            model.isNextButtonDisabled.setValue(true);
            cancelButton.setDisable(true);
            busyAnimation.play();
            spinnerInfoLabel.setText(Res.get("cloneOffer.publishOffer"));
            model.onCloneOffer(() -> {
                        String key = "cloneOfferSuccess";
                        if (DontShowAgainLookup.showAgain(key)) {
                            new Popup()
                                    .feedback(Res.get("cloneOffer.success"))
                                    .dontShowAgainId(key)
                                    .show();
                        }
                        spinnerInfoLabel.setText("");
                        busyAnimation.stop();
                        close();
                    },
                    errorMessage -> {
                        log.error(errorMessage);
                        spinnerInfoLabel.setText("");
                        busyAnimation.stop();
                        model.isNextButtonDisabled.setValue(false);
                        cancelButton.setDisable(false);
                        new Popup().warning(errorMessage).show();
                    });
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateElementsWithDirection() {
        ImageView iconView = new ImageView();
        iconView.setId(model.isShownAsSellOffer() ? "image-sell-white" : "image-buy-white");
        cloneButton.setGraphic(iconView);
        cloneButton.setId(model.isShownAsSellOffer() ? "sell-button-big" : "buy-button-big");
    }
}

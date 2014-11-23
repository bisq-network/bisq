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

package io.bitsquare.gui.main.account.content.registration;

import io.bitsquare.gui.InitializableView;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.Wizard;
import io.bitsquare.gui.components.AddressTextField;
import io.bitsquare.gui.components.BalanceTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.locale.BSResources;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

public class RegistrationView extends InitializableView<GridPane, RegistrationViewModel> implements Wizard.Step {

    @FXML TextField feeTextField;
    @FXML AddressTextField addressTextField;
    @FXML BalanceTextField balanceTextField;
    @FXML Button payButton;
    @FXML Label paymentSpinnerInfoLabel;
    @FXML ProgressIndicator paymentSpinner;

    private Wizard parent;

    private final OverlayManager overlayManager;

    @Inject
    private RegistrationView(RegistrationViewModel model, OverlayManager overlayManager) {
        super(model);
        this.overlayManager = overlayManager;
    }

    @Override
    public void initialize() {
        feeTextField.setText(model.getFeeAsString());
        addressTextField.setAmountAsCoin(model.getFeeAsCoin());
        addressTextField.setPaymentLabel(model.getPaymentLabel());
        addressTextField.setAddress(model.getAddressAsString());

        // TODO find better solution
        addressTextField.setOverlayManager(overlayManager);

        balanceTextField.setup(model.getWalletService(), model.address.get(),
                model.getFormatter());

        payButton.disableProperty().bind(model.isPayButtonDisabled);

        model.requestPlaceOfferErrorMessage.addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                Popups.openErrorPopup(BSResources.get("shared.error"),
                        BSResources.get("An error occurred when paying the registration fee"),
                        newValue);
            }
        });

        paymentSpinnerInfoLabel.visibleProperty().bind(model.isPaymentSpinnerVisible);

        model.isPaymentSpinnerVisible.addListener((ov, oldValue, newValue) -> {
            paymentSpinner.setProgress(newValue ? -1 : 0);
            paymentSpinner.setVisible(newValue);
        });

        model.showTransactionPublishedScreen.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                overlayManager.blurContent();

                List<Action> actions = new ArrayList<>();
               /* actions.add(new AbstractAction(BSResources.get("shared.copyTxId")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        getProperties().put("type", "COPY");
                         Utilities.copyToClipboard(presentationModel.getTransactionId());
                    }
                });*/
                actions.add(new AbstractAction(BSResources.get("shared.close")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        getProperties().put("type", "CLOSE");
                        try {
                            parent.nextStep(RegistrationView.this);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Dialog.Actions.CLOSE.handle(actionEvent);
                        overlayManager.removeBlurContent();
                    }
                });

                Popups.openInfoPopup(BSResources.get("You have been successfully registered."),
                        BSResources.get("Congratulation you have been successfully registered.\n\n" +
                                " You can now start trading."),
                        actions);
            }
        });
    }

    @Override
    public void setParent(Wizard parent) {
        this.parent = parent;
    }

    @Override
    public void hideWizardNavigation() {
    }

    @FXML
    private void onPayFee() {
        model.payFee();
    }

    @FXML
    private void onOpenHelp() {
        Help.openWindow(HelpId.PAY_ACCOUNT_FEE);
    }
}


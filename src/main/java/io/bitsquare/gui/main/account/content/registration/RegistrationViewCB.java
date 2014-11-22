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

import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.ViewCB;
import io.bitsquare.gui.components.AddressTextField;
import io.bitsquare.gui.components.BalanceTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.account.MultiStepNavigation;
import io.bitsquare.gui.main.account.content.ContextAware;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.locale.BSResources;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RegistrationViewCB extends ViewCB implements ContextAware {

    private static final Logger log = LoggerFactory.getLogger(RegistrationViewCB.class);

    private final OverlayManager overlayManager;
    private final RegistrationPM model;

    @FXML TextField feeTextField;
    @FXML AddressTextField addressTextField;
    @FXML BalanceTextField balanceTextField;
    @FXML Button payButton;
    @FXML Label paymentSpinnerInfoLabel;
    @FXML ProgressIndicator paymentSpinner;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private RegistrationViewCB(RegistrationPM model, OverlayManager overlayManager) {
        this.model = model;
        this.overlayManager = overlayManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

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
                            if (parent instanceof MultiStepNavigation)
                                ((MultiStepNavigation) parent).nextStep(RegistrationViewCB.this);
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ContextAware implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void useSettingsContext(boolean useSettingsContext) {
        if (useSettingsContext) {
            // TODO not impl. yet
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    private void onPayFee() {
        model.payFee();
    }

    @FXML
    private void onOpenHelp() {
        Help.openWindow(HelpId.PAY_ACCOUNT_FEE);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}


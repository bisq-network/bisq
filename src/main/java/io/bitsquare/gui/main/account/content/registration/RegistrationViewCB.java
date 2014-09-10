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

import io.bitsquare.gui.CachedCodeBehind;
import io.bitsquare.gui.OverlayController;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.btc.AddressTextField;
import io.bitsquare.gui.components.btc.BalanceTextField;
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
import javafx.scene.input.*;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistrationViewCB extends CachedCodeBehind<RegistrationPM> implements ContextAware {

    private static final Logger log = LoggerFactory.getLogger(RegistrationViewCB.class);

    private OverlayController overlayController;

    @FXML private TextField feeTextField;
    @FXML private AddressTextField addressTextField;
    @FXML private BalanceTextField balanceTextField;
    @FXML private Button payButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private RegistrationViewCB(RegistrationPM presentationModel, OverlayController overlayController) {
        super(presentationModel);
        this.overlayController = overlayController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        feeTextField.setText(presentationModel.getFeeAsString());
        addressTextField.setAmountAsCoin(presentationModel.getFeeAsCoin());
        addressTextField.setPaymentLabel(presentationModel.getPaymentLabel());
        addressTextField.setAddress(presentationModel.getAddressAsString());

        // TODO find better solution
        addressTextField.setOverlayController(overlayController);

        balanceTextField.setup(presentationModel.getWalletFacade(), presentationModel.address.get());

        payButton.disableProperty().bind(presentationModel.isPayButtonDisabled);

        presentationModel.requestPlaceOfferErrorMessage.addListener((o, oldValue, newValue) -> {
            if (newValue != null) {
                Popups.openErrorPopup(BSResources.get("shared.error"),
                        BSResources.get("createOffer.amountPriceBox.error.message",
                                presentationModel.requestPlaceOfferErrorMessage.get()));
            }
        });

        presentationModel.showTransactionPublishedScreen.addListener((o, oldValue, newValue) -> {
            if (newValue) {
                overlayController.blurContent();

                List<Action> actions = new ArrayList<>();
                actions.add(new AbstractAction(BSResources.get("shared.copyTxId")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                        ClipboardContent content = new ClipboardContent();
                        content.putString(presentationModel.getTransactionId());
                        clipboard.setContent(content);
                    }
                });
                actions.add(new AbstractAction(BSResources.get("shared.close")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        try {
                            if (parentController instanceof MultiStepNavigation)
                                ((MultiStepNavigation) parentController).nextStep(RegistrationViewCB.this);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Dialog.Actions.CLOSE.handle(actionEvent);
                        overlayController.removeBlurContent();
                    }
                });

                Popups.openInfo(BSResources.get("The Transaction ID for the offer payment is:\n" +
                                presentationModel.getTransactionId() +
                                "\n\n You can now start trading."),
                        BSResources.get("You have been successfully registered."),
                        actions);
            }
        });
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();

    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Override 
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void useSettingsContext(boolean useSettingsContext) {
        if (useSettingsContext) {
            // TODO
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    private void onPayFee() {
        presentationModel.payFee();
    }

    @FXML
    private void onOpenHelp() {
        Help.openWindow(HelpId.PAY_ACCOUNT_FEE);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}


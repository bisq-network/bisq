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

package io.bitsquare.gui.account.registration;

import io.bitsquare.gui.CachedCodeBehind;
import io.bitsquare.gui.MainController;
import io.bitsquare.gui.account.settings.AccountSettingsCB;
import io.bitsquare.gui.account.setup.SetupCB;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.components.btc.AddressTextField;
import io.bitsquare.gui.components.btc.BalanceTextField;
import io.bitsquare.gui.help.Help;
import io.bitsquare.gui.help.HelpId;
import io.bitsquare.locale.BSResources;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.*;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegistrationCB extends CachedCodeBehind<RegistrationPM> {

    private static final Logger log = LoggerFactory.getLogger(RegistrationCB.class);


    @FXML private TextField feeTextField;
    @FXML private AddressTextField addressTextField;
    @FXML private BalanceTextField balanceTextField;
    @FXML private Button payButton;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public RegistrationCB(RegistrationPM presentationModel) {
        super(presentationModel);
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
                MainController.GET_INSTANCE().blurContentScreen();

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
                            if (parentController instanceof SetupCB)
                                ((SetupCB) parentController).onCompleted(RegistrationCB.this);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Dialog.Actions.CLOSE.handle(actionEvent);
                        MainController.GET_INSTANCE().removeContentScreenBlur();
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

    @Override
    public void activate() {
        super.activate();

    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Override from CodeBehind
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setParentController(Initializable parentController) {
        super.setParentController(parentController);
        if (parentController instanceof AccountSettingsCB) {
            //TODO
            // ((GridPane) root).getChildren().remove(completedButton);
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


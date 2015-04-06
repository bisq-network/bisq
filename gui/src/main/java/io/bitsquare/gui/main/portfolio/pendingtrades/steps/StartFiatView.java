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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps;

import io.bitsquare.gui.components.InfoDisplay;
import io.bitsquare.gui.components.TextFieldWithCopyIcon;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;

import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.*;
import javafx.scene.control.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.ComponentBuilder.*;

public class StartFiatView extends TradeStepDetailsView {
    private static final Logger log = LoggerFactory.getLogger(WaitTxInBlockchainView.class);

    private TxIdTextField txIdTextField;
    private TextFieldWithCopyIcon fiatAmountTextField;
    private TextField paymentMethodTextField;
    private TextFieldWithCopyIcon holderNameTextField;
    private TextFieldWithCopyIcon primaryIdTextField;
    private TextFieldWithCopyIcon secondaryIdTextField;
    private InfoDisplay paymentsInfoDisplay;
    private Button paymentStartedButton;
    private Label statusLabel;

    private final ChangeListener<String> txIdChangeListener;
    private ProgressIndicator statusProgressIndicator;
    private Parent root;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public StartFiatView(PendingTradesViewModel model) {
        super(model);

        txIdChangeListener = (ov, oldValue, newValue) -> txIdTextField.setup(model.getWalletService(), newValue);

        paymentMethodTextField.setText(model.getPaymentMethod());
        fiatAmountTextField.setText(model.getFiatAmount());
        holderNameTextField.setText(model.getHolderName());
        primaryIdTextField.setText(model.getPrimaryId());
        secondaryIdTextField.setText(model.getSecondaryId());
        paymentsInfoDisplay.setText(BSResources.get("Copy and paste the payment account data to your " +
                        "internet banking web page and transfer the {0} amount to the other traders " +
                        "payment account. When the transfer is completed inform the other trader by " +
                        "clicking the button below.",
                model.getCurrencyCode()));
        
        /*
              statusTextField.setText("Deposit transaction has at least one block chain confirmation. " +
                            "Please start now the payment.");
                    infoDisplay.setText("You are now safe to start the payment. You can wait for up to 6 block chain " +
                            "confirmations if you want more security.");

         */
    }

    @Override
    public void activate() {
        log.debug("activate ##");
        super.activate();

        model.getTxId().addListener(txIdChangeListener);
        txIdTextField.setup(model.getWalletService(), model.getTxId().get());
    }

    @Override
    public void deactivate() {
        super.deactivate();

        model.getTxId().removeListener(txIdChangeListener);
        txIdTextField.cleanup();
        if (root != null)
            root.setMouseTransparent(false);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPaymentStarted(ActionEvent actionEvent) {
        log.debug("onPaymentStarted");
        model.fiatPaymentStarted();
        paymentStartedButton.setDisable(true);
        statusProgressIndicator.setVisible(true);
        statusProgressIndicator.setProgress(-1);
        statusLabel.setText("Sending message to trading partner...");
        root = statusProgressIndicator.getScene().getRoot();
        // We deactivate mouse interaction to avoid that user leaves screen
        root.setMouseTransparent(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build view
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void buildGridEntries() {
        getAndAddTitledGroupBg(gridPane, gridRow, 1, "Blockchain confirmation");
        txIdTextField = getAndAddLabelTxIdTextFieldPair(gridPane, gridRow++, "Deposit transaction ID:", Layout.FIRST_ROW_DISTANCE).txIdTextField;

        getAndAddTitledGroupBg(gridPane, gridRow, 6, "Payments details", Layout.GROUP_DISTANCE);
        fiatAmountTextField = getAndAddLabelTextFieldWithCopyIconPair(gridPane, gridRow++, "Amount to transfer:", Layout.FIRST_ROW_AND_GROUP_DISTANCE)
                .textFieldWithCopyIcon;
        paymentMethodTextField = getAndAddLabelTextFieldPair(gridPane, gridRow++, "Payment method:").textField;
        holderNameTextField = getAndAddLabelTextFieldWithCopyIconPair(gridPane, gridRow++, "Receiver:").textFieldWithCopyIcon;
        primaryIdTextField = getAndAddLabelTextFieldWithCopyIconPair(gridPane, gridRow++, "IBAN:").textFieldWithCopyIcon;
        secondaryIdTextField = getAndAddLabelTextFieldWithCopyIconPair(gridPane, gridRow++, "BIC:").textFieldWithCopyIcon;
        paymentsInfoDisplay = getAndAddInfoDisplay(gridPane, gridRow++, "infoDisplay", this::onOpenHelp);
        ButtonWithProgressIndicatorAndLabelBucket bucket = getAndAddButtonWithStatus(gridPane, gridRow++, "Payment started", this::onPaymentStarted);
        paymentStartedButton = bucket.button;
        statusProgressIndicator = bucket.progressIndicator;
        statusLabel = bucket.label;
    }
}

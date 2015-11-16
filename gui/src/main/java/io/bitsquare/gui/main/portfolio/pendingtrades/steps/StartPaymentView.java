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

import io.bitsquare.app.BitsquareApp;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.components.paymentmethods.*;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.user.PopupId;
import io.bitsquare.user.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;

import static io.bitsquare.gui.util.FormBuilder.*;

public class StartPaymentView extends TradeStepDetailsView {
    private final Preferences preferences;
    private TxIdTextField txIdTextField;

    private Button paymentStartedButton;
    private Label statusLabel;

    private final ChangeListener<String> txIdChangeListener;
    private ProgressIndicator statusProgressIndicator;

    private TitledGroupBg txConfirmationGroup;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public StartPaymentView(PendingTradesViewModel model) {
        super(model);
        txIdChangeListener = (ov, oldValue, newValue) -> txIdTextField.setup(newValue);
        preferences = model.dataModel.getPreferences();
    }

    @Override
    public void doActivate() {
        super.doActivate();

        model.getTxId().addListener(txIdChangeListener);
        txIdTextField.setup(model.getTxId().get());

        String key = PopupId.SEND_PAYMENT_INFO;
        if (preferences.showAgain(key) && !BitsquareApp.DEV_MODE) {
            new Popup().information("You need to transfer now the agreed amount to your trading partner.\n" +
                    "Please take care that you use the exact data presented here, including the reference text\n" +
                    "Please do not click the \"Payment started\" button before you have completed the transfer.\n" +
                    "Take care that you make the transfer soon to not exceed the trading period.")
                    .onClose(() -> preferences.dontShowAgain(key))
                    .show();
        }
    }

    @Override
    public void doDeactivate() {
        super.doDeactivate();

        model.getTxId().removeListener(txIdChangeListener);
        txIdTextField.cleanup();
        statusProgressIndicator.setProgress(0);
    }


    @Override
    protected void displayRequestCheckPayment() {
        addDisputeInfoLabel();
        infoLabel.setText("You still have not done your payment!\n" +
                        "If the seller does not receive your payment until " +
                        model.getDateFromBlocks(openDisputeTimeInBlocks) +
                        " the trade will be investigated by the arbitrator."
        );
    }

    @Override
    protected void displayOpenForDisputeForm() {
        addDisputeInfoLabel();
        infoLabel.setText("You have not completed your payment!\n" +
                "The max. period for the trade has elapsed (" +
                model.getDateFromBlocks(openDisputeTimeInBlocks) + ")." +
                "\nPlease contact now the arbitrator for opening a dispute.");

        addOpenDisputeButton();
        GridPane.setMargin(openDisputeButton, new Insets(0, 0, 0, 0));
        GridPane.setColumnIndex(openDisputeButton, 1);
    }

    @Override
    protected void disputeInProgress() {
        super.disputeInProgress();

        paymentStartedButton.setDisable(true);
    }

    @Override
    protected void addDisputeInfoLabel() {
        if (infoLabel == null) {
            // we replace tx id field as there is not enough space
            gridPane.getChildren().removeAll(txConfirmationGroup, txIdTextField);

            infoTitledGroupBg = addTitledGroupBg(gridPane, 0, 1, "Information");
            infoLabel = addMultilineLabel(gridPane, 0, Layout.FIRST_ROW_DISTANCE);
            infoLabel.setStyle(" -fx-text-fill: -bs-error-red;");
            // grid does not auto update layout correctly
            infoLabel.setMinHeight(70);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPaymentStarted(ActionEvent actionEvent) {
        log.debug("onPaymentStarted");
        if (model.isAuthenticated()) {
            String key = PopupId.PAYMENT_SENT;
            if (preferences.showAgain(key) && !BitsquareApp.DEV_MODE) {
                new Popup().information("You are confirming that you have transferred the payment to your trading partner.\n" +
                        "Please click the \"Payment started\" button only if you have completed the transfer.")
                        .onClose(() -> preferences.dontShowAgain(key))
                        .show();
            } else {
                paymentStartedButton.setDisable(true);

                statusProgressIndicator.setVisible(true);
                statusProgressIndicator.setProgress(-1);
                statusLabel.setText("Sending message to trading partner...");

                model.fiatPaymentStarted();
            }
        } else {
            new Popup().warning("You need to wait until your client is authenticated in the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build view
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void buildGridEntries() {
        txConfirmationGroup = addTitledGroupBg(gridPane, gridRow, 1, "Blockchain confirmation");
        txIdTextField = addLabelTxIdTextField(gridPane, gridRow, "Deposit transaction ID:", Layout.FIRST_ROW_DISTANCE).second;

        TitledGroupBg accountTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1, "Payments details", Layout.GROUP_DISTANCE);
        addLabelTextFieldWithCopyIcon(gridPane, gridRow, "Amount to transfer:", model.getFiatAmount(),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        PaymentAccountContractData paymentAccountContractData = model.dataModel.getSellersPaymentAccountContractData();

        String paymentMethodName = paymentAccountContractData.getPaymentMethodName();
        switch (paymentMethodName) {
            case PaymentMethod.OK_PAY_ID:
                gridRow = OKPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.PERFECT_MONEY_ID:
                gridRow = PerfectMoneyForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.SEPA_ID:
                gridRow = SepaForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.SWISH_ID:
                gridRow = SwishForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.ALI_PAY_ID:
                gridRow = AliPayForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            case PaymentMethod.BLOCK_CHAINS_ID:
                gridRow = BlockChainForm.addFormForBuyer(gridPane, gridRow, paymentAccountContractData);
                break;
            default:
                log.error("Not supported PaymentMethod: " + paymentMethodName);
        }


        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Reference:", model.getReference());

        Tuple3<Button, ProgressIndicator, Label> tuple3 = addButtonWithStatus(gridPane, ++gridRow, "Payment started");
        paymentStartedButton = tuple3.first;
        paymentStartedButton.setOnAction(this::onPaymentStarted);
        statusProgressIndicator = tuple3.second;
        statusLabel = tuple3.third;

        GridPane.setRowSpan(accountTitledGroupBg, gridRow - 1);
    }
}

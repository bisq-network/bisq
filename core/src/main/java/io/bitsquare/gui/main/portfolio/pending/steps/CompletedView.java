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

package io.bitsquare.gui.main.portfolio.pending.steps;

import io.bitsquare.gui.components.InfoDisplay;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.main.portfolio.pending.PendingTradesViewModel;
import io.bitsquare.gui.util.Layout;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.ComponentBuilder.*;

public class CompletedView extends TradeStepDetailsView {
    private static final Logger log = LoggerFactory.getLogger(WaitTxInBlockchainView.class);

    private Label btcTradeAmountLabel;
    private TextField btcTradeAmountTextField;
    private Label fiatTradeAmountLabel;
    private TextField fiatTradeAmountTextField;
    private Label feesLabel;
    private TextField feesTextField;
    private Label securityDepositLabel;
    private TextField securityDepositTextField;
    private InfoDisplay summaryInfoDisplay;

    private InputTextField withdrawAddressTextField;
    private TextField withdrawAmountTextField;
    private Button withdrawButton;

    private ChangeListener<Boolean> focusedPropertyListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CompletedView(PendingTradesViewModel model) {
        super(model);

        focusedPropertyListener = (ov, oldValue, newValue) -> {
            if (oldValue && !newValue)
                model.withdrawAddressFocusOut(withdrawAddressTextField.getText());
        };

      /*  statusTextField.setText("Congratulations! Trade has successfully completed.");
        infoDisplay.setText("The trade is now completed and you can withdraw your Bitcoin to any external" +
                "wallet. To protect your privacy you should take care that your trades are not merged " +
                "in " +
                "that external wallet. For more information about privacy see our help pages.");*/

      /*  btcTradeAmountLabel.setText("You have bought:");
        fiatTradeAmountLabel.setText("You have paid:");
        btcTradeAmountTextField.setText(model.getTradeVolume());
        fiatTradeAmountTextField.setText(model.getFiatVolume());
        feesTextField.setText(model.getTotalFees());
        securityDepositTextField.setText(model.getSecurityDeposit());
        summaryInfoDisplay.setText("Your security deposit has been refunded to you. " +
                "You can review the details to that trade any time in the closed trades screen.");*/

        //   withdrawAmountTextField.setText(model.getAmountToWithdraw());
    }

    @Override
    public void activate() {
        super.activate();
        withdrawAddressTextField.focusedProperty().addListener(focusedPropertyListener);
        withdrawAddressTextField.setValidator(model.getBtcAddressValidator());
        withdrawButton.disableProperty().bind(model.withdrawalButtonDisable);

        // We need to handle both cases: Address not set and address already set (when returning from other view)
        // We get address validation after focus out, so first make sure we loose focus and then set it again as hint for user to put address in
        Platform.runLater(() -> {
            withdrawAddressTextField.requestFocus();
            Platform.runLater(() -> {
                this.requestFocus();
                Platform.runLater(() -> {
                    withdrawAddressTextField.requestFocus();
                });
            });
        });
    }

    @Override
    public void deactivate() {
        super.deactivate();
        withdrawAddressTextField.focusedProperty().removeListener(focusedPropertyListener);
        withdrawButton.disableProperty().unbind();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onWithdraw(ActionEvent actionEvent) {
        log.debug("onWithdraw");
        model.withdraw(withdrawAddressTextField.getText());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build view
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void buildGridEntries() {
        getAndAddTitledGroupBg(gridPane, gridRow, 5, "Summary");
        LabelTextFieldPair btcTradeAmountPair = getAndAddLabelTextFieldPair(gridPane, gridRow++, "You have bought:", Layout.FIRST_ROW_DISTANCE);
        btcTradeAmountLabel = btcTradeAmountPair.label;
        btcTradeAmountTextField = btcTradeAmountPair.textField;

        LabelTextFieldPair fiatTradeAmountPair = getAndAddLabelTextFieldPair(gridPane, gridRow++, "You have paid:");
        fiatTradeAmountLabel = fiatTradeAmountPair.label;
        fiatTradeAmountTextField = fiatTradeAmountPair.textField;

        LabelTextFieldPair feesPair = getAndAddLabelTextFieldPair(gridPane, gridRow++, "Total fees paid:");
        feesLabel = feesPair.label;
        feesTextField = feesPair.textField;

        LabelTextFieldPair securityDepositPair = getAndAddLabelTextFieldPair(gridPane, gridRow++, "Refunded security deposit:");
        securityDepositLabel = securityDepositPair.label;
        securityDepositTextField = securityDepositPair.textField;

        summaryInfoDisplay = getAndAddInfoDisplay(gridPane, gridRow++, "infoDisplay", this::onOpenHelp);

        getAndAddTitledGroupBg(gridPane, gridRow, 2, "Withdraw your bitcoins", Layout.GROUP_DISTANCE);
        withdrawAmountTextField = getAndAddLabelTextFieldPair(gridPane, gridRow++, "Amount to withdraw:", Layout.FIRST_ROW_AND_GROUP_DISTANCE).textField;
        withdrawAddressTextField = getAndAddLabelInputTextFieldPair(gridPane, gridRow++, "Withdraw to address:").inputTextField;
        withdrawButton = getAndAddButton(gridPane, gridRow++, "Withdraw to external wallet", this::onWithdraw);


        //TODO just temp for testing
        withdrawAddressTextField.setText("mxmKZruv9x9JLcEj6rZx6Hnm4LLAcQHtcr");
    }


   /* fiatTradeAmountLabel.setText("You have received:");
    btcTradeAmountTextField.setText(model.getTradeVolume());
    fiatTradeAmountTextField.setText(model.getFiatVolume());
    feesTextField.setText(model.getTotalFees());
    securityDepositTextField.setText(model.getSecurityDeposit());
    summaryInfoDisplay.setText("Your security deposit has been refunded to you. "+
            "You can review the details to that trade any time in the closed trades screen.");

    withdrawAmountTextField.setText(model.getAmountToWithdraw());*/

    public void setBtcTradeAmountLabelText(String text) {
        btcTradeAmountLabel.setText(text);
    }

    public void setFiatTradeAmountLabelText(String text) {
        fiatTradeAmountLabel.setText(text);
    }

    public void setBtcTradeAmountTextFieldText(String text) {
        btcTradeAmountTextField.setText(text);
    }

    public void setFiatTradeAmountTextFieldText(String text) {
        fiatTradeAmountTextField.setText(text);
    }

    public void setFeesTextFieldText(String text) {
        feesTextField.setText(text);
    }

    public void setSecurityDepositTextFieldText(String text) {
        securityDepositTextField.setText(text);
    }

    public void setSummaryInfoDisplayText(String text) {
        summaryInfoDisplay.setText(text);
    }

    public void setWithdrawAmountTextFieldText(String text) {
        withdrawAmountTextField.setText(text);
    }

   /* completedView.setBtcTradeAmountLabelText("You have sold:");
    completedView.setFiatTradeAmountLabelText("You have received:");
    completedView.setBtcTradeAmountTextFieldText(model.getTradeVolume());
    completedView.setFiatTradeAmountTextFieldText(model.getFiatVolume());
    completedView.setFeesTextFieldText(model.getTotalFees());
    completedView.setSecurityDepositTextFieldText(model.getSecurityDeposit());
    completedView.setSummaryInfoDisplayText("Your security deposit has been refunded to you. "+
            "You can review the details to that trade any time in the closed trades screen.");

    completedView.setWithdrawAmountTextFieldText(model.getAmountToWithdraw());*/
}

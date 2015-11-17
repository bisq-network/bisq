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
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.user.PopupId;
import io.bitsquare.user.Preferences;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import static io.bitsquare.gui.util.FormBuilder.*;

public class ConfirmPaymentReceivedView extends TradeStepDetailsView {
    private final ChangeListener<String> txIdChangeListener;

    private TxIdTextField txIdTextField;
    private Button confirmFiatReceivedButton;
    private Label statusLabel;
    private ProgressIndicator statusProgressIndicator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ConfirmPaymentReceivedView(PendingTradesViewModel model) {
        super(model);

        txIdChangeListener = (ov, oldValue, newValue) -> txIdTextField.setup(newValue);
    }

    @Override
    public void doActivate() {
        super.doActivate();

        model.getTxId().addListener(txIdChangeListener);
        txIdTextField.setup(model.getTxId().get());
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
        infoLabel.setStyle(" -fx-text-fill: -bs-error-red;");
        infoLabel.setText("You still have not confirmed the receipt of the payment!\n" +
                "Please check you payment processor or bank account to see if the payment has arrived.\n" +
                "If you do not confirm receipt until " +
                model.getDateFromBlocks(openDisputeTimeInBlocks) +
                " the trade will be investigated by the arbitrator.");
    }

    @Override
    protected void displayOpenForDisputeForm() {
        infoLabel.setStyle(" -fx-text-fill: -bs-error-red;");
        infoLabel.setText("You have not confirmed the receipt of the payment!\n" +
                "The max. period for the trade has elapsed (" +
                model.getDateFromBlocks(openDisputeTimeInBlocks) + ")." +
                "\nPlease contact now the arbitrator for opening a dispute.");

        addOpenDisputeButton();
        GridPane.setMargin(openDisputeButton, new Insets(0, 0, 0, 0));
    }

    @Override
    protected void disputeInProgress() {
        super.disputeInProgress();

        confirmFiatReceivedButton.setDisable(true);
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPaymentReceived(ActionEvent actionEvent) {
        log.debug("onPaymentReceived");
        if (model.isAuthenticated()) {
            Preferences preferences = model.dataModel.getPreferences();
            String key = PopupId.PAYMENT_RECEIVED;
            if (preferences.showAgain(key) && !BitsquareApp.DEV_MODE) {
                new Popup().headLine("Confirmation")
                        .message("Do you have received the payment from your trading partner?\n\n" +
                                "Please note that as soon you have confirmed the locked Bitcoin will be released.\n" +
                                "There is no way to reverse a Bitcoin payment.")
                        .dontShowAgainId(key, preferences)
                        .actionButtonText("Yes I have received the payment")
                        .closeButtonText("No")
                        .onAction(() -> confirmPaymentReceived())
                        .show();
            } else {
                confirmPaymentReceived();
            }
        } else {
            new Popup().warning("You need to wait until your client is authenticated in the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
        }
    }

    private void confirmPaymentReceived() {
        confirmFiatReceivedButton.setDisable(true);

        statusProgressIndicator.setVisible(true);
        statusProgressIndicator.setProgress(-1);
        statusLabel.setText("Sending message to trading partner...");

        model.fiatPaymentReceived();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setInfoLabelText(String text) {
        if (infoLabel != null)
            infoLabel.setText(text);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build view
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void buildGridEntries() {
        addTitledGroupBg(gridPane, gridRow, 1, "Blockchain confirmation");
        txIdTextField = addLabelTxIdTextField(gridPane, gridRow, "Deposit transaction ID:", Layout.FIRST_ROW_DISTANCE).second;

        infoTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1, "Information", Layout.GROUP_DISTANCE);
        infoLabel = addMultilineLabel(gridPane, gridRow, Layout.FIRST_ROW_AND_GROUP_DISTANCE);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        confirmFiatReceivedButton = new Button("Confirm payment receipt");
        confirmFiatReceivedButton.setDefaultButton(true);
        confirmFiatReceivedButton.setOnAction(this::onPaymentReceived);

        statusProgressIndicator = new ProgressIndicator(0);
        statusProgressIndicator.setPrefHeight(24);
        statusProgressIndicator.setPrefWidth(24);
        statusProgressIndicator.setVisible(false);

        statusLabel = new Label();
        statusLabel.setPadding(new Insets(5, 0, 0, 0));

        hBox.getChildren().addAll(confirmFiatReceivedButton, statusProgressIndicator, statusLabel);
        GridPane.setRowIndex(hBox, ++gridRow);
        GridPane.setColumnIndex(hBox, 0);
        GridPane.setHalignment(hBox, HPos.LEFT);
        GridPane.setMargin(hBox, new Insets(15, 0, 0, 0));
        gridPane.getChildren().add(hBox);
    }
}



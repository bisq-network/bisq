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

package io.bitsquare.gui.main.portfolio.pendingtrades.steps.seller;

import io.bitsquare.app.BitsquareApp;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesViewModel;
import io.bitsquare.gui.main.portfolio.pendingtrades.steps.TradeStepView;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.locale.BSResources;
import io.bitsquare.user.PopupId;
import io.bitsquare.user.Preferences;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class SellerStep3View extends TradeStepView {

    private Button confirmFiatReceivedButton;
    private Label statusLabel;
    private ProgressIndicator statusProgressIndicator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SellerStep3View(PendingTradesViewModel model) {
        super(model);

    }

    @Override
    public void doActivate() {
        super.doActivate();
    }

    @Override
    public void doDeactivate() {
        super.doDeactivate();

        statusProgressIndicator.setProgress(0);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Content
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void addContent() {
        super.addContent();

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        confirmFiatReceivedButton = new Button("Confirm payment receipt");
        confirmFiatReceivedButton.setDefaultButton(true);
        confirmFiatReceivedButton.setOnAction(e -> onPaymentReceived());

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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Info
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getInfoBlockTitle() {
        return "Confirm payment receipt";
    }

    @Override
    protected String getInfoText() {
        if (model.isBlockChainMethod()) {
            return BSResources.get("The bitcoin buyer has started the {0} payment.\n" +
                            "Check for blockchain confirmations at your Altcoin wallet or block explorer and " +
                            "confirm the payment when you have sufficient blockchain confirmations.",
                    model.getCurrencyCode());
        } else {
            return BSResources.get("The bitcoin buyer has started the {0} payment.\n" +
                            "Check at your payment account (e.g. bank account) and confirm when you have " +
                            "received the payment.",
                    model.getCurrencyCode());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Warning
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getWarningText() {
        setWarningState();
        String substitute = model.isBlockChainMethod() ?
                "on the " + model.getCurrencyCode() + "blockchain" :
                "at your payment provider (e.g. bank)";
        return "You still have not confirmed the receipt of the payment!\n" +
                "Please check " + substitute + " if you have received the payment.\n" +
                "If you do not confirm receipt until " +
                model.getOpenDisputeTimeAsFormattedDate() +
                " the trade will be investigated by the arbitrator.";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispute
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected String getOpenForDisputeText() {
        return "You have not confirmed the receipt of the payment!\n" +
                "The max. period for the trade has elapsed (" +
                model.getOpenDisputeTimeAsFormattedDate() + ")." +
                "\nPlease contact now the arbitrator for opening a dispute.";
    }

    @Override
    protected void applyOnDisputeOpened() {
        confirmFiatReceivedButton.setDisable(true);
    }


    ////////////////////////////////////////////////////////////////////////////////////////
    // UI Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPaymentReceived() {
        log.debug("onPaymentReceived");
        if (model.isBootstrapped()) {
            Preferences preferences = model.dataModel.getPreferences();
            String key = PopupId.PAYMENT_RECEIVED;
            if (preferences.showAgain(key) && !BitsquareApp.DEV_MODE) {
                new Popup().headLine("Confirmation")
                        .message("Did you receive the payment from your trading partner?\n\n" +
                                "Please note that as soon you have confirmed the locked bitcoin will be released.\n" +
                                "There is no way to reverse a bitcoin payment.")
                        .dontShowAgainId(key, preferences)
                        .actionButtonText("Yes I have received the payment")
                        .closeButtonText("No")
                        .onAction(this::confirmPaymentReceived)
                        .show();
            } else {
                confirmPaymentReceived();
            }
        } else {
            new Popup().warning("You need to wait until your client is bootstrapped in the network.\n" +
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
}



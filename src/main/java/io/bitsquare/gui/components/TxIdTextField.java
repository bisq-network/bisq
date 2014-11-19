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

package io.bitsquare.gui.components;

import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.TxConfidenceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.util.Utilities;

import org.bitcoinj.core.TransactionConfidence;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TxIdTextField extends AnchorPane {
    private static final Logger log = LoggerFactory.getLogger(TxIdTextField.class);

    private final TextField textField;
    private final Tooltip progressIndicatorTooltip;
    private final ConfidenceProgressIndicator progressIndicator;
    private final Label copyIcon;
    private TxConfidenceListener txConfidenceListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxIdTextField() {
        progressIndicator = new ConfidenceProgressIndicator();
        progressIndicator.setFocusTraversable(false);
        progressIndicator.setPrefSize(24, 24);
        progressIndicator.setId("funds-confidence");
        progressIndicator.setLayoutY(1);
        progressIndicator.setProgress(0);
        progressIndicator.setVisible(false);
        AnchorPane.setRightAnchor(progressIndicator, 0.0);
        progressIndicatorTooltip = new Tooltip("-");
        Tooltip.install(progressIndicator, progressIndicatorTooltip);

        copyIcon = new Label();
        copyIcon.setLayoutY(3);
        copyIcon.getStyleClass().add("copy-icon");
        Tooltip.install(copyIcon, new Tooltip("Copy transaction ID to clipboard"));
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        AnchorPane.setRightAnchor(copyIcon, 30.0);

        textField = new TextField();
        textField.setId("address-text-field");
        textField.setEditable(false);
        Tooltip.install(textField, new Tooltip("Open a blockchain explorer with that transactions ID"));
        AnchorPane.setRightAnchor(textField, 55.0);
        AnchorPane.setLeftAnchor(textField, 0.0);
        textField.focusTraversableProperty().set(focusTraversableProperty().get());
        focusedProperty().addListener((ov, oldValue, newValue) -> textField.requestFocus());

        getChildren().addAll(textField, copyIcon, progressIndicator);
    }

    public void setup(WalletService walletService, String txID) {
        if (txConfidenceListener != null)
            walletService.removeTxConfidenceListener(txConfidenceListener);

        txConfidenceListener = new TxConfidenceListener(txID) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence);
            }
        };
        walletService.addTxConfidenceListener(txConfidenceListener);
        updateConfidence(walletService.getConfidenceForTxId(txID));

        textField.setText(txID);
        textField.setOnMouseClicked(mouseEvent -> {
            try {
                // TODO get the url form the app preferences
                Utilities.openWebPage("https://www.biteasy.com/testnet/transactions/" + txID);
                //Utilities.openURL("https://blockchain.info/tx/" + txID);
            } catch (Exception e) {
                log.error(e.getMessage());
                Popups.openWarningPopup("Warning", "Opening browser failed. Please check your internet " +
                        "connection.");
            }
        });

        copyIcon.setOnMouseClicked(e -> Utilities.copyToClipboard(txID));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters/Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateConfidence(TransactionConfidence confidence) {
        if (confidence != null) {
            switch (confidence.getConfidenceType()) {
                case UNKNOWN:
                    progressIndicatorTooltip.setText("Unknown transaction status");
                    progressIndicator.setProgress(0);
                    break;
                case PENDING:
                    progressIndicatorTooltip.setText(
                            "Seen by " + confidence.numBroadcastPeers() + " peer(s) / 0 " + "confirmations");
                    progressIndicator.setProgress(-1.0);
                    break;
                case BUILDING:
                    progressIndicatorTooltip.setText("Confirmed in " + confidence.getDepthInBlocks() + " block(s)");
                    progressIndicator.setProgress(Math.min(1, (double) confidence.getDepthInBlocks() / 6.0));
                    break;
                case DEAD:
                    progressIndicatorTooltip.setText("Transaction is invalid.");
                    progressIndicator.setProgress(0);
                    break;
            }

            if (progressIndicator.getProgress() != 0) {
                progressIndicator.setVisible(true);
                AnchorPane.setRightAnchor(progressIndicator, 0.0);
            }
        }
    }
}

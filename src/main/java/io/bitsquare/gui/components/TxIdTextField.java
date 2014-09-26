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

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.btc.listeners.TxConfidenceListener;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;

import com.google.bitcoin.core.TransactionConfidence;

import java.awt.*;

import java.io.IOException;

import java.net.URI;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.input.*;
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

    public void setup(WalletFacade walletFacade, String txID) {
        textField.setText(txID);
        textField.setOnMouseClicked(mouseEvent -> {
            try {
                Desktop.getDesktop().browse(URI.create("https://blockchain.info/address/" + txID));
            } catch (IOException e) {
                log.warn(e.getMessage());
                Popups.openWarningPopup("Warning", "Opening blockchain.info failed. Please check your internet " +
                        "connection.");
            }
        });

        copyIcon.setOnMouseClicked(e -> {
            if (txID != null && txID.length() > 0) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(txID);
                clipboard.setContent(content);
            }
        });

        walletFacade.addTxConfidenceListener(new TxConfidenceListener(txID) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence);
            }
        });
        updateConfidence(walletFacade.getConfidenceForTxId(txID));
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

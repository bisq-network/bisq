/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.components;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.listeners.TxConfidenceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.user.Preferences;
import io.bisq.gui.components.indicator.TxConfidenceIndicator;
import io.bisq.gui.util.GUIUtil;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import org.bitcoinj.core.TransactionConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TxIdTextField extends AnchorPane {
    private static final Logger log = LoggerFactory.getLogger(TxIdTextField.class);

    private static Preferences preferences;

    public static void setPreferences(Preferences preferences) {
        TxIdTextField.preferences = preferences;
    }

    private static BtcWalletService walletService;

    public static void setWalletService(BtcWalletService walletService) {
        TxIdTextField.walletService = walletService;
    }

    private final TextField textField;
    private final Tooltip progressIndicatorTooltip;
    private final TxConfidenceIndicator txConfidenceIndicator;
    private final Label copyIcon;
    private final Label blockExplorerIcon;
    private TxConfidenceListener txConfidenceListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxIdTextField() {
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setFocusTraversable(false);
        txConfidenceIndicator.setPrefSize(24, 24);
        txConfidenceIndicator.setId("funds-confidence");
        txConfidenceIndicator.setLayoutY(1);
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setVisible(false);
        AnchorPane.setRightAnchor(txConfidenceIndicator, 0.0);
        progressIndicatorTooltip = new Tooltip("-");
        txConfidenceIndicator.setTooltip(progressIndicatorTooltip);

        copyIcon = new Label();
        copyIcon.setLayoutY(3);
        copyIcon.getStyleClass().add("copy-icon");
        copyIcon.setTooltip(new Tooltip(Res.get("txIdTextField.copyIcon.tooltip")));
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        AnchorPane.setRightAnchor(copyIcon, 30.0);

        Tooltip tooltip = new Tooltip(Res.get("txIdTextField.blockExplorerIcon.tooltip"));

        blockExplorerIcon = new Label();
        blockExplorerIcon.getStyleClass().add("external-link-icon");
        blockExplorerIcon.setTooltip(tooltip);
        AwesomeDude.setIcon(blockExplorerIcon, AwesomeIcon.EXTERNAL_LINK);
        blockExplorerIcon.setMinWidth(20);
        AnchorPane.setRightAnchor(blockExplorerIcon, 52.0);
        AnchorPane.setTopAnchor(blockExplorerIcon, 4.0);

        textField = new TextField();
        textField.setId("address-text-field");
        textField.setEditable(false);
        textField.setTooltip(tooltip);
        AnchorPane.setRightAnchor(textField, 80.0);
        AnchorPane.setLeftAnchor(textField, 0.0);
        textField.focusTraversableProperty().set(focusTraversableProperty().get());
        getChildren().addAll(textField, copyIcon, blockExplorerIcon, txConfidenceIndicator);
    }

    public void setup(String txID) {
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
        textField.setOnMouseClicked(mouseEvent -> openBlockExplorer(txID));
        blockExplorerIcon.setOnMouseClicked(mouseEvent -> openBlockExplorer(txID));
        copyIcon.setOnMouseClicked(e -> Utilities.copyToClipboard(txID));
    }

    public void cleanup() {
        if (walletService != null && txConfidenceListener != null)
            walletService.removeTxConfidenceListener(txConfidenceListener);

        textField.setOnMouseClicked(null);
        blockExplorerIcon.setOnMouseClicked(null);
        copyIcon.setOnMouseClicked(null);
        textField.setText("");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void openBlockExplorer(String txID) {
        if (preferences != null)
            GUIUtil.openWebPage(preferences.getBlockChainExplorer().txUrl + txID);
    }

    private void updateConfidence(TransactionConfidence confidence) {
        GUIUtil.updateConfidence(confidence, progressIndicatorTooltip, txConfidenceIndicator);
        if (confidence != null) {
            if (txConfidenceIndicator.getProgress() != 0) {
                txConfidenceIndicator.setVisible(true);
                AnchorPane.setRightAnchor(txConfidenceIndicator, 0.0);
            }
        }
    }
}

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

package bisq.desktop.components;

import bisq.desktop.components.indicator.TxConfidenceIndicator;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.Res;
import bisq.core.user.BlockChainExplorer;
import bisq.core.user.Preferences;

import bisq.common.util.Utilities;

import org.bitcoinj.core.TransactionConfidence;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

public class TxIdTextField extends AnchorPane {
    @Setter
    private static Preferences preferences;
    @Setter
    private static BtcWalletService walletService;

    @Getter
    private final TextField textField;
    private final Tooltip progressIndicatorTooltip;
    private final TxConfidenceIndicator txConfidenceIndicator;
    private final Label copyIcon, blockExplorerIcon, missingTxWarningIcon;
    private TxConfidenceListener txConfidenceListener;
    @Setter
    private boolean isBsq;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxIdTextField() {
        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setFocusTraversable(false);
        txConfidenceIndicator.setMaxSize(20, 20);
        txConfidenceIndicator.setId("funds-confidence");
        txConfidenceIndicator.setLayoutY(1);
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setVisible(false);
        AnchorPane.setRightAnchor(txConfidenceIndicator, 0.0);
        AnchorPane.setTopAnchor(txConfidenceIndicator, 3.0);
        progressIndicatorTooltip = new Tooltip("-");
        txConfidenceIndicator.setTooltip(progressIndicatorTooltip);

        copyIcon = new Label();
        copyIcon.setLayoutY(3);
        copyIcon.getStyleClass().addAll("icon", "highlight");
        copyIcon.setTooltip(new Tooltip(Res.get("txIdTextField.copyIcon.tooltip")));
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        AnchorPane.setRightAnchor(copyIcon, 30.0);

        Tooltip tooltip = new Tooltip(Res.get("txIdTextField.blockExplorerIcon.tooltip"));

        blockExplorerIcon = new Label();
        blockExplorerIcon.getStyleClass().addAll("icon", "highlight");
        blockExplorerIcon.setTooltip(tooltip);
        AwesomeDude.setIcon(blockExplorerIcon, AwesomeIcon.EXTERNAL_LINK);
        blockExplorerIcon.setMinWidth(20);
        AnchorPane.setRightAnchor(blockExplorerIcon, 52.0);
        AnchorPane.setTopAnchor(blockExplorerIcon, 4.0);

        missingTxWarningIcon = new Label();
        missingTxWarningIcon.getStyleClass().addAll("icon", "error-icon");
        AwesomeDude.setIcon(missingTxWarningIcon, AwesomeIcon.WARNING_SIGN);
        missingTxWarningIcon.setTooltip(new Tooltip(Res.get("txIdTextField.missingTx.warning.tooltip")));
        missingTxWarningIcon.setMinWidth(20);
        AnchorPane.setRightAnchor(missingTxWarningIcon, 52.0);
        AnchorPane.setTopAnchor(missingTxWarningIcon, 4.0);
        missingTxWarningIcon.setVisible(false);
        missingTxWarningIcon.setManaged(false);

        textField = new JFXTextField();
        textField.setId("address-text-field");
        textField.setEditable(false);
        textField.setTooltip(tooltip);
        AnchorPane.setRightAnchor(textField, 80.0);
        AnchorPane.setLeftAnchor(textField, 0.0);
        textField.focusTraversableProperty().set(focusTraversableProperty().get());
        getChildren().addAll(textField, missingTxWarningIcon, blockExplorerIcon, copyIcon, txConfidenceIndicator);
    }

    public void setup(@Nullable String txId) {
        if (txConfidenceListener != null)
            walletService.removeTxConfidenceListener(txConfidenceListener);

        if (txId == null) {
            textField.setText(Res.get("shared.na"));
            textField.setId("address-text-field-error");
            blockExplorerIcon.setVisible(false);
            blockExplorerIcon.setManaged(false);
            copyIcon.setVisible(false);
            copyIcon.setManaged(false);
            txConfidenceIndicator.setVisible(false);
            missingTxWarningIcon.setVisible(true);
            missingTxWarningIcon.setManaged(true);
            return;
        }

        txConfidenceListener = new TxConfidenceListener(txId) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence);
            }
        };
        walletService.addTxConfidenceListener(txConfidenceListener);
        updateConfidence(walletService.getConfidenceForTxId(txId));

        textField.setText(txId);
        textField.setOnMouseClicked(mouseEvent -> openBlockExplorer(txId));
        blockExplorerIcon.setOnMouseClicked(mouseEvent -> openBlockExplorer(txId));
        copyIcon.setOnMouseClicked(e -> Utilities.copyToClipboard(txId));
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

    private void openBlockExplorer(String txId) {
        if (preferences != null) {
            BlockChainExplorer blockChainExplorer = isBsq ?
                    preferences.getBsqBlockChainExplorer() :
                    preferences.getBlockChainExplorer();
            GUIUtil.openWebPage(blockChainExplorer.txUrl + txId, false);
        }
    }

    private void updateConfidence(TransactionConfidence confidence) {
        GUIUtil.updateConfidence(confidence, progressIndicatorTooltip, txConfidenceIndicator);
        if (confidence != null) {
            if (txConfidenceIndicator.getProgress() != 0) {
                txConfidenceIndicator.setVisible(true);
                AnchorPane.setRightAnchor(txConfidenceIndicator, 0.0);
            }
        } else {
            //TODO we should show some placeholder in case of a tx which we are not aware of but which can be
            // confirmed already. This is for instance the case of the other peers trade fee tx, as it is not related
            // to our wallet we don't have a confidence object but we should show that it is in an unknown state instead
            // of not showing anything which causes confusion that the tx was not broadcasted. Best would be to request
            // it from a block explorer service but that is a bit too heavy for that use case...
            // Maybe a question mark with a tooltip explaining why we don't know about the confidence might be ok...
        }
    }
}

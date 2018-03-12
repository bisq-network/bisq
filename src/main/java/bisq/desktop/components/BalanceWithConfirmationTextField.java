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
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.wallet.BtcWalletService;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;

public class BalanceWithConfirmationTextField extends AnchorPane {

    private static BtcWalletService walletService;
    private BalanceListener balanceListener;
    private AddressConfidenceListener confidenceListener;

    public static void setWalletService(BtcWalletService walletService) {
        BalanceWithConfirmationTextField.walletService = walletService;
    }

    private final TextField textField;
    private final Tooltip progressIndicatorTooltip;
    private final TxConfidenceIndicator txConfidenceIndicator;

    private final Effect fundedEffect = new DropShadow(BlurType.THREE_PASS_BOX, Color.GREEN, 4, 0.0, 0, 0);
    private final Effect notFundedEffect = new DropShadow(BlurType.THREE_PASS_BOX, Color.ORANGERED, 4, 0.0, 0, 0);
    private BSFormatter formatter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BalanceWithConfirmationTextField() {
        textField = new TextField();
        textField.setFocusTraversable(false);
        textField.setEditable(false);

        txConfidenceIndicator = new TxConfidenceIndicator();
        txConfidenceIndicator.setFocusTraversable(false);
        txConfidenceIndicator.setPrefSize(24, 24);
        txConfidenceIndicator.setId("funds-confidence");
        txConfidenceIndicator.setLayoutY(1);
        txConfidenceIndicator.setProgress(0);
        txConfidenceIndicator.setVisible(false);

        progressIndicatorTooltip = new Tooltip("-");
        Tooltip.install(txConfidenceIndicator, progressIndicatorTooltip);

        AnchorPane.setRightAnchor(txConfidenceIndicator, 0.0);
        AnchorPane.setRightAnchor(textField, 55.0);
        AnchorPane.setLeftAnchor(textField, 0.0);

        getChildren().addAll(textField, txConfidenceIndicator);
    }

    public void cleanup() {
        walletService.removeBalanceListener(balanceListener);
        walletService.removeAddressConfidenceListener(confidenceListener);
    }

    public void setup(Address address, BSFormatter formatter) {
        this.formatter = formatter;
        confidenceListener = new AddressConfidenceListener(address) {
            @Override
            public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                updateConfidence(confidence);
            }
        };
        walletService.addAddressConfidenceListener(confidenceListener);
        updateConfidence(walletService.getConfidenceForAddress(address));

        balanceListener = new BalanceListener(address) {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance(balance);
            }
        };
        walletService.addBalanceListener(balanceListener);
        updateBalance(walletService.getBalanceForAddress(address));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateConfidence(TransactionConfidence confidence) {
        GUIUtil.updateConfidence(confidence, progressIndicatorTooltip, txConfidenceIndicator);
        if (confidence != null) {
            if (txConfidenceIndicator.getProgress() != 0) {
                txConfidenceIndicator.setVisible(true);
                AnchorPane.setRightAnchor(txConfidenceIndicator, 0.0);
                AnchorPane.setRightAnchor(textField, 35.0);
            }
        }
    }

    private void updateBalance(Coin balance) {
        textField.setText(formatter.formatCoinWithCode(balance));
        if (balance.isPositive())
            textField.setEffect(fundedEffect);
        else
            textField.setEffect(notFundedEffect);
    }

}

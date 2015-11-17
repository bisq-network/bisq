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
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.gui.util.BSFormatter;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

public class BalanceTextField extends AnchorPane {

    private static WalletService walletService;
    private BalanceListener balanceListener;

    public static void setWalletService(WalletService walletService) {
        BalanceTextField.walletService = walletService;
    }

    private final TextField textField;
    private final Effect fundedEffect = new DropShadow(BlurType.THREE_PASS_BOX, Color.GREEN, 4, 0.0, 0, 0);
    private final Effect notFundedEffect = new DropShadow(BlurType.THREE_PASS_BOX, Color.ORANGERED, 4, 0.0, 0, 0);
    private BSFormatter formatter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BalanceTextField() {
        textField = new TextField();
        textField.setFocusTraversable(false);
        textField.setEditable(false);

        AnchorPane.setRightAnchor(textField, 0.0);
        AnchorPane.setLeftAnchor(textField, 0.0);

        getChildren().addAll(textField);
    }

    public void setup(Address address, BSFormatter formatter) {
        this.formatter = formatter;

        balanceListener = new BalanceListener(address) {
            @Override
            public void onBalanceChanged(Coin balance) {
                updateBalance(balance);
            }
        };
        walletService.addBalanceListener(balanceListener);
        updateBalance(walletService.getBalanceForAddress(address));
    }

    public void disarm() {
        walletService.removeBalanceListener(balanceListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateBalance(Coin balance) {
        textField.setText(formatter.formatCoinWithCode(balance));
        if (balance.isPositive())
            textField.setEffect(fundedEffect);
        else
            textField.setEffect(notFundedEffect);
    }

}

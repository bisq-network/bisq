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

import io.bisq.gui.util.BSFormatter;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import org.bitcoinj.core.Coin;
import javax.annotation.Nullable;

public class BalanceTextField extends AnchorPane {

    private Coin targetAmount;
    private final TextField textField;
    private final Effect fundedEffect = new DropShadow(BlurType.THREE_PASS_BOX, Color.GREEN, 4, 0.0, 0, 0);
    private final Effect notFundedEffect = new DropShadow(BlurType.THREE_PASS_BOX, Color.ORANGERED, 4, 0.0, 0, 0);
    private BSFormatter formatter;
    @Nullable
    private Coin balance;


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

    public void setFormatter(BSFormatter formatter) {
        this.formatter = formatter;
    }

    public void setBalance(Coin balance) {
        this.balance = balance;

        updateBalance(balance);
    }

    public void setTargetAmount(Coin targetAmount) {
        this.targetAmount = targetAmount;

        if (this.balance != null)
            updateBalance(balance);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateBalance(Coin balance) {
        if (formatter != null)
            textField.setText(formatter.formatCoinWithCode(balance));

        if (targetAmount != null) {
            if (balance.compareTo(targetAmount) >= 0)
                textField.setEffect(fundedEffect);
            else
                textField.setEffect(notFundedEffect);
        } else {
            textField.setEffect(null);
        }
    }
}

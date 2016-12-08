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

package io.bitsquare.gui.main.dao.wallet.dashboard;

import io.bitsquare.btc.SquWalletService;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;

import javax.inject.Inject;
import java.util.List;

import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;
import static io.bitsquare.gui.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class TokenDashboardView extends ActivatableView<GridPane, Void> {

    private final BSFormatter formatter;
    private final Wallet squWalletService;
    private int gridRow = 0;
    private TextField confirmedBalance;
    private WalletEventListener walletEventListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TokenDashboardView(SquWalletService squWalletService, BSFormatter formatter) {
        this.squWalletService = squWalletService.getWallet();
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 1, "Balance");
        confirmedBalance = addLabelTextField(root, gridRow, "Confirmed SQU balance:", Layout.FIRST_ROW_DISTANCE).second;

        walletEventListener = new WalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                updateBalance();
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                updateBalance();
            }

            @Override
            public void onReorganize(Wallet wallet) {
                updateBalance();
            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                updateBalance();
            }

            @Override
            public void onWalletChanged(Wallet wallet) {
                updateBalance();
            }

            @Override
            public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
                updateBalance();
            }

            @Override
            public void onKeysAdded(List<ECKey> keys) {
                updateBalance();
            }
        };
    }

    @Override
    protected void activate() {
        squWalletService.addEventListener(walletEventListener);

        updateBalance();
    }

    @Override
    protected void deactivate() {
        squWalletService.removeEventListener(walletEventListener);
    }

    private void updateBalance() {
        confirmedBalance.setText(formatter.formatCoinWithCode(squWalletService.getBalance()));
    }
}


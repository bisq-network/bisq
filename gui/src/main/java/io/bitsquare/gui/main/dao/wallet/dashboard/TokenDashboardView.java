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

import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.gui.common.view.ActivatableView;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.SQUFormatter;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;
import static io.bitsquare.gui.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class TokenDashboardView extends ActivatableView<GridPane, Void> {

    private TextField confirmedBalance;

    private final SquWalletService squWalletService;
    private final SQUFormatter formatter;

    private final int gridRow = 0;
    private BalanceListener balanceListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TokenDashboardView(SquWalletService squWalletService, SQUFormatter formatter) {
        this.squWalletService = squWalletService;
        this.formatter = formatter;
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 1, "Balance");
        confirmedBalance = addLabelTextField(root, gridRow, "Confirmed SQU balance:", Layout.FIRST_ROW_DISTANCE).second;

        balanceListener = new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance(balance);
            }
        };
    }

    @Override
    protected void activate() {
        squWalletService.requestSquUtxo(() -> {
            updateBalance(squWalletService.getAvailableBalance());
        }, errorMessage -> {
            new Popup<>().warning(errorMessage);
        });
        squWalletService.addBalanceListener(balanceListener);

        updateBalance(squWalletService.getAvailableBalance());
    }

    @Override
    protected void deactivate() {
        squWalletService.removeBalanceListener(balanceListener);
    }

    private void updateBalance(Coin balance) {
        confirmedBalance.setText(formatter.formatCoinWithCode(balance));
    }
}


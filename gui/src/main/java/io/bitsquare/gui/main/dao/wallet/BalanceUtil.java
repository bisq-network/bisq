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

package io.bitsquare.gui.main.dao.wallet;

import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.SQUFormatter;
import javafx.scene.control.TextField;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class BalanceUtil {
    private static final Logger log = LoggerFactory.getLogger(BalanceUtil.class);

    private final SquWalletService squWalletService;
    private final SQUFormatter formatter;
    private TextField balanceTextField;
    private BalanceListener balanceListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BalanceUtil(SquWalletService squWalletService, SQUFormatter formatter) {
        this.squWalletService = squWalletService;
        this.formatter = formatter;
    }

    public void setBalanceTextField(TextField balanceTextField) {
        this.balanceTextField = balanceTextField;
    }

    public void initialize() {
        balanceListener = new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance(balance);
            }
        };
    }

    public void activate() {
        squWalletService.requestSquUtxo(() -> {
            updateBalance(squWalletService.getAvailableBalance());
        }, errorMessage -> {
            new Popup<>().warning(errorMessage);
        });
        squWalletService.addBalanceListener(balanceListener);

        updateBalance(squWalletService.getAvailableBalance());
    }

    public void deactivate() {
        squWalletService.removeBalanceListener(balanceListener);
    }

    private void updateBalance(Coin balance) {
        balanceTextField.setText(formatter.formatCoinWithCode(balance));
    }

}

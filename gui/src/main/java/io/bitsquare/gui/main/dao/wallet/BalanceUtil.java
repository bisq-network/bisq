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

import io.bitsquare.btc.wallet.SquWalletService;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.SQUFormatter;
import javafx.scene.control.TextField;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

public class BalanceUtil {
    private static final Logger log = LoggerFactory.getLogger(BalanceUtil.class);

    private final SquWalletService squWalletService;
    private final SQUFormatter formatter;
    private TextField balanceTextField;
    private WalletEventListener walletEventListener;

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
        walletEventListener = new WalletEventListener() {
            @Override
            public void onKeysAdded(List<ECKey> keys) {

            }

            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                requestUtxo();
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                requestUtxo();
            }

            @Override
            public void onReorganize(Wallet wallet) {
                requestUtxo();
            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                requestUtxo();
            }

            @Override
            public void onWalletChanged(Wallet wallet) {

            }

            @Override
            public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {

            }
        };
    }

    private void requestUtxo() {
        squWalletService.requestSquUtxo(() -> {
            balanceTextField.setText(formatter.formatCoinWithCode(squWalletService.getAvailableBalance()));
        }, errorMessage -> {
            new Popup<>().warning(errorMessage);
        });
    }

    public void activate() {
        requestUtxo();
        squWalletService.addEventListener(walletEventListener);
    }

    public void deactivate() {
        squWalletService.removeEventListener(walletEventListener);
    }

}

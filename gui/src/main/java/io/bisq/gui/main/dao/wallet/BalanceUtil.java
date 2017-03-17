/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.dao.wallet;

import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BsqFormatter;
import javafx.scene.control.TextField;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

public class BalanceUtil {
    private static final Logger log = LoggerFactory.getLogger(BalanceUtil.class);

    private final BsqWalletService bsqWalletService;
    private final BsqFormatter formatter;
    private TextField balanceTextField;
    private WalletEventListener walletEventListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private BalanceUtil(BsqWalletService bsqWalletService, BsqFormatter formatter) {
        this.bsqWalletService = bsqWalletService;
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
        bsqWalletService.requestBsqUtxo(() -> {
            balanceTextField.setText(formatter.formatCoinWithCode(bsqWalletService.getAvailableBalance()));
        }, errorMessage -> {
            new Popup<>().warning(errorMessage);
        });
    }

    public void activate() {
        requestUtxo();
        bsqWalletService.addEventListener(walletEventListener);
    }

    public void deactivate() {
        bsqWalletService.removeEventListener(walletEventListener);
    }

}

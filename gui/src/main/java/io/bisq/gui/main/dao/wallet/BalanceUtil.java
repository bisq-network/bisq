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

import io.bisq.core.btc.wallet.BsqBalanceListener;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.dao.blockchain.BsqBlockchainManager;
import io.bisq.core.dao.blockchain.BsqUTXOListener;
import io.bisq.gui.util.BsqFormatter;
import javafx.scene.control.TextField;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;

@Slf4j
public class BalanceUtil implements BsqBalanceListener {
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter formatter;
    private BsqBlockchainManager bsqBlockchainManager;
    private TextField balanceTextField;
    private BsqUTXOListener bsqUTXOListener;

    @Inject
    private BalanceUtil(BsqWalletService bsqWalletService, BsqFormatter formatter, BsqBlockchainManager bsqBlockchainManager) {
        this.bsqWalletService = bsqWalletService;
        this.formatter = formatter;
        this.bsqBlockchainManager = bsqBlockchainManager;
    }

    public void setBalanceTextField(TextField balanceTextField) {
        this.balanceTextField = balanceTextField;
        balanceTextField.setMouseTransparent(false);
    }

    public void initialize() {
        bsqUTXOListener = bsqUTXOMap -> updateAvailableBalance(bsqWalletService.getAvailableBalance());
    }

    public void activate() {
        updateAvailableBalance(bsqWalletService.getAvailableBalance());
        bsqWalletService.addBsqBalanceListener(this);
        bsqBlockchainManager.addUtxoListener(bsqUTXOListener);
    }

    public void deactivate() {
        bsqWalletService.removeBsqBalanceListener(this);
        bsqBlockchainManager.removeUtxoListener(bsqUTXOListener);
    }

    @Override
    public void updateAvailableBalance(Coin availableBalance) {
        balanceTextField.setText(formatter.formatCoinWithCode(availableBalance));
    }
}

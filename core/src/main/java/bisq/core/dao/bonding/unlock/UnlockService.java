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

package bisq.core.dao.bonding.unlock;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.TxMalleabilityException;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.TxOutput;

import bisq.common.handlers.ExceptionHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnlockService {
    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final BsqStateService bsqStateService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public UnlockService(WalletsManager walletsManager,
                         BsqWalletService bsqWalletService,
                         BtcWalletService btcWalletService,
                         BsqStateService bsqStateService) {
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.bsqStateService = bsqStateService;
    }

    public void publishUnlockTx(String lockupTxId, ResultHandler resultHandler,
                                ExceptionHandler exceptionHandler) {
        try {
            TxOutput lockupTxOutput = bsqStateService.getLockupTxOutput(lockupTxId).get();
            final Transaction unlockTx = getUnlockTx(lockupTxOutput);

            //noinspection Duplicates
            walletsManager.publishAndCommitBsqTx(unlockTx, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    resultHandler.handleResult();
                }

                @Override
                public void onTxMalleability(TxMalleabilityException exception) {
                    exceptionHandler.handleException(exception);
                }

                @Override
                public void onFailure(TxBroadcastException exception) {
                    exceptionHandler.handleException(exception);
                }
            });
        } catch (TransactionVerificationException | InsufficientMoneyException | WalletException exception) {
            exceptionHandler.handleException(exception);
        }
    }

    private Transaction getUnlockTx(TxOutput lockupTxOutput)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedUnlockTx(lockupTxOutput);
        Transaction txWithBtcFee = btcWalletService.completePreparedBsqTx(preparedTx, true, null);
        final Transaction transaction = bsqWalletService.signTx(txWithBtcFee);

        log.info("Unlock tx: " + transaction);
        return transaction;
    }
}

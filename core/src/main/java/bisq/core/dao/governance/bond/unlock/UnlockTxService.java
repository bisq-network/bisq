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

package bisq.core.dao.governance.bond.unlock;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxType;

import bisq.common.handlers.ExceptionHandler;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.util.Optional;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Service for publishing the unlock transaction.
 */
@Slf4j
public class UnlockTxService {
    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final DaoStateService daoStateService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public UnlockTxService(WalletsManager walletsManager,
                           BsqWalletService bsqWalletService,
                           BtcWalletService btcWalletService,
                           DaoStateService daoStateService) {
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.daoStateService = daoStateService;
    }

    public void publishUnlockTx(String lockupTxId, Consumer<String> resultHandler, ExceptionHandler exceptionHandler) {
        try {
            Transaction unlockTx = getUnlockTx(lockupTxId);
            walletsManager.publishAndCommitBsqTx(unlockTx, TxType.UNLOCK, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    resultHandler.accept(transaction.getTxId().toString());
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

    public Tuple2<Coin, Integer> getMiningFeeAndTxVsize(String lockupTxId)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction tx = getUnlockTx(lockupTxId);
        Coin miningFee = tx.getFee();
        int txVsize = tx.getVsize();
        return new Tuple2<>(miningFee, txVsize);
    }

    private Transaction getUnlockTx(String lockupTxId)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Optional<TxOutput> optionalLockupTxOutput = daoStateService.getLockupTxOutput(lockupTxId);
        checkArgument(optionalLockupTxOutput.isPresent(), "lockupTxOutput must be present");
        TxOutput lockupTxOutput = optionalLockupTxOutput.get();
        Transaction preparedTx = bsqWalletService.getPreparedUnlockTx(lockupTxOutput);
        Transaction txWithBtcFee = btcWalletService.completePreparedBsqTx(preparedTx, null);
        Transaction transaction = bsqWalletService.signTxAndVerifyNoDustOutputs(txWithBtcFee);
        log.info("Unlock tx: " + transaction);
        return transaction;
    }
}

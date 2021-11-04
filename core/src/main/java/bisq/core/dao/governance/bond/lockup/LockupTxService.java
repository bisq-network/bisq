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

package bisq.core.dao.governance.bond.lockup;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.state.model.blockchain.TxType;

import bisq.common.handlers.ExceptionHandler;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.io.IOException;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Service for publishing the lockup transaction.
 */
@Slf4j
public class LockupTxService {
    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public LockupTxService(WalletsManager walletsManager,
                           BsqWalletService bsqWalletService,
                           BtcWalletService btcWalletService) {
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
    }

    public void publishLockupTx(Coin lockupAmount, int lockTime, LockupReason lockupReason, byte[] hash,
                                Consumer<String> resultHandler, ExceptionHandler exceptionHandler) {
        checkArgument(lockTime <= BondConsensus.getMaxLockTime() &&
                lockTime >= BondConsensus.getMinLockTime(), "lockTime not in range");
        try {
            Transaction lockupTx = getLockupTx(lockupAmount, lockTime, lockupReason, hash);
            walletsManager.publishAndCommitBsqTx(lockupTx, TxType.LOCKUP, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    resultHandler.accept(transaction.getTxId().toString());
                }

                @Override
                public void onFailure(TxBroadcastException exception) {
                    exceptionHandler.handleException(exception);
                }
            });

        } catch (TransactionVerificationException | InsufficientMoneyException | WalletException |
                IOException exception) {
            exceptionHandler.handleException(exception);
        }
    }

    public Tuple2<Coin, Integer> getMiningFeeAndTxVsize(Coin lockupAmount, int lockTime, LockupReason lockupReason, byte[] hash)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException, IOException {
        Transaction tx = getLockupTx(lockupAmount, lockTime, lockupReason, hash);
        Coin miningFee = tx.getFee();
        int txVsize = tx.getVsize();
        return new Tuple2<>(miningFee, txVsize);
    }

    private Transaction getLockupTx(Coin lockupAmount, int lockTime, LockupReason lockupReason, byte[] hash)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException, IOException {
        byte[] opReturnData = BondConsensus.getLockupOpReturnData(lockTime, lockupReason, hash);
        Transaction preparedTx = bsqWalletService.getPreparedLockupTx(lockupAmount);
        Transaction txWithBtcFee = btcWalletService.completePreparedBsqTx(preparedTx, opReturnData);
        Transaction transaction = bsqWalletService.signTxAndVerifyNoDustOutputs(txWithBtcFee);
        log.info("Lockup tx: " + transaction);
        return transaction;
    }
}

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

package bisq.core.dao.bonding.lockup;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.TxMalleabilityException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.bonding.bond.BondWithHash;
import bisq.core.dao.governance.role.BondedRolesService;

import bisq.common.handlers.ExceptionHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class LockupService {
    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public LockupService(WalletsManager walletsManager,
                         BsqWalletService bsqWalletService,
                         BtcWalletService btcWalletService,
                         BondedRolesService bondedRolesService) {
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
    }

    public void publishLockupTx(Coin lockupAmount, int lockTime, LockupType lockupType, BondWithHash bondWithHash,
                                ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        checkArgument(lockTime <= BondingConsensus.getMaxLockTime() &&
                lockTime >= BondingConsensus.getMinLockTime(), "lockTime not in rage");
        try {

            byte[] hash = BondingConsensus.getHash(bondWithHash);
            byte[] opReturnData = BondingConsensus.getLockupOpReturnData(lockTime, lockupType, hash);
            final Transaction lockupTx = getLockupTx(lockupAmount, opReturnData);

            //noinspection Duplicates
            walletsManager.publishAndCommitBsqTx(lockupTx, new TxBroadcaster.Callback() {
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

        } catch (TransactionVerificationException | InsufficientMoneyException | WalletException |
                IOException exception) {
            exceptionHandler.handleException(exception);
        }
    }

    private Transaction getLockupTx(Coin lockupAmount, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedLockupTx(lockupAmount);
        Transaction txWithBtcFee = btcWalletService.completePreparedBsqTx(preparedTx, true, opReturnData);
        final Transaction transaction = bsqWalletService.signTx(txWithBtcFee);
        log.info("Lockup tx: " + transaction);
        return transaction;
    }
}

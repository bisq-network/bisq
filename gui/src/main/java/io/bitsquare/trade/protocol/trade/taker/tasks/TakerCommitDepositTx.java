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

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.btc.WalletService;
import io.bitsquare.util.handlers.ExceptionHandler;

import org.bitcoinj.core.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerCommitDepositTx {
    private static final Logger log = LoggerFactory.getLogger(TakerCommitDepositTx.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, WalletService walletService, String depositTxAsHex) {
        log.trace("Run PayDeposit task");
        try {
            Transaction transaction = walletService.takerCommitDepositTx(depositTxAsHex);
            resultHandler.onResult(transaction);
        } catch (Exception e) {
            log.error("takerCommitDepositTx failed with exception " + e);
            exceptionHandler.handleException(e);
        }
    }

    public interface ResultHandler {
        void onResult(Transaction transaction);
    }
}

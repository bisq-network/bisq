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

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.util.task.ExceptionHandler;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayTakeOfferFee {
    private static final Logger log = LoggerFactory.getLogger(PayTakeOfferFee.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, WalletFacade walletFacade,
                           String tradeId) {
        log.trace("Run task");
        try {
            walletFacade.payTakeOfferFee(tradeId, new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(Transaction transaction) {
                    log.debug("Take offer fee paid successfully. Transaction ID = " + transaction.getHashAsString());
                    resultHandler.onResult(transaction.getHashAsString());
                }

                @Override
                public void onFailure(@NotNull Throwable t) {
                    log.error("Take offer fee paid faultHandler.onFault with exception: " + t);
                    exceptionHandler.handleException(
                            new Exception("Take offer fee paid faultHandler.onFault with exception: " + t));
                }
            });
        } catch (InsufficientMoneyException e) {
            log.error("Take offer fee paid faultHandler.onFault due InsufficientMoneyException " + e);
            exceptionHandler.handleException(
                    new Exception("Take offer fee paid faultHandler.onFault due to InsufficientMoneyException " + e));
        }
    }

    public interface ResultHandler {
        void onResult(String takeOfferFeeTxId);
    }

}

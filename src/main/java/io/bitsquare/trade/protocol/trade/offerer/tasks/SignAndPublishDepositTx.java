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

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.handlers.ExceptionHandler;

import com.google.bitcoin.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishDepositTx {
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishDepositTx.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           WalletFacade walletFacade,
                           String preparedOffererDepositTxAsHex,
                           String signedTakerDepositTxAsHex,
                           String txConnOutAsHex,
                           String txScriptSigAsHex,
                           long offererTxOutIndex,
                           long takerTxOutIndex) {
        log.trace("Run task");
        try {
            walletFacade.offererSignAndPublishTx(preparedOffererDepositTxAsHex,
                    signedTakerDepositTxAsHex,
                    txConnOutAsHex,
                    txScriptSigAsHex,
                    offererTxOutIndex,
                    takerTxOutIndex,
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.trace("offererSignAndPublishTx succeeded " + transaction);
                            resultHandler.onResult(transaction);
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            log.error("offererSignAndPublishTx faultHandler.onFault:" + t);
                            exceptionHandler.onError(t);
                        }
                    });
        } catch (Exception e) {
            log.error("offererSignAndPublishTx faultHandler.onFault:" + e);
            exceptionHandler.onError(e);
        }
    }

    public interface ResultHandler {
        void onResult(Transaction depositTransaction);
    }

}

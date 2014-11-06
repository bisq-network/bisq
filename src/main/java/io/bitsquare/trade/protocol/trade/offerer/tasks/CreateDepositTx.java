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
import io.bitsquare.util.task.ExceptionHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDepositTx {
    private static final Logger log = LoggerFactory.getLogger(CreateDepositTx.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           WalletFacade walletFacade,
                           String tradeId,
                           Coin offererInputAmount,
                           String takerMultiSigPubKey,
                           String arbitratorPubKeyAsHex) {
        log.trace("Run task");
        try {
            String offererPubKey = walletFacade.getAddressInfoByTradeID(tradeId).getPubKeyAsHexString();
            Transaction transaction = walletFacade.offererCreatesMSTxAndAddPayment(
                    offererInputAmount, offererPubKey, takerMultiSigPubKey, arbitratorPubKeyAsHex, tradeId);

            String preparedOffererDepositTxAsHex = Utils.HEX.encode(transaction.bitcoinSerialize());
            long offererTxOutIndex = transaction.getInput(0).getOutpoint().getIndex();

            resultHandler.onResult(offererPubKey, preparedOffererDepositTxAsHex, offererTxOutIndex);
        } catch (InsufficientMoneyException e) {
            log.error("Create deposit tx faultHandler.onFault due InsufficientMoneyException " + e);
            exceptionHandler.handleException(
                    new Exception("Create deposit tx faultHandler.onFault due InsufficientMoneyException " + e));
        }
    }

    public interface ResultHandler {
        void onResult(String offererPubKey, String preparedOffererDepositTxAsHex, long offererTxOutIndex);
    }

}

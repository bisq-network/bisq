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

package io.bitsquare.trade.protocol.taker;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.handlers.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayDeposit
{
    private static final Logger log = LoggerFactory.getLogger(PayDeposit.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           WalletFacade walletFacade,
                           Coin collateral,
                           Coin tradeAmount,
                           String tradeId,
                           String pubKeyForThatTrade,
                           String arbitratorPubKey,
                           String offererPubKey,
                           String preparedOffererDepositTxAsHex)
    {
        log.trace("Run task");
        try
        {
            Coin amountToPay = tradeAmount.add(collateral);
            Coin msOutputAmount = amountToPay.add(collateral);

            Transaction signedTakerDepositTx = walletFacade.takerAddPaymentAndSignTx(amountToPay,
                                                                                     msOutputAmount,
                                                                                     offererPubKey,
                                                                                     pubKeyForThatTrade,
                                                                                     arbitratorPubKey,
                                                                                     preparedOffererDepositTxAsHex,
                                                                                     tradeId);

            log.trace("sharedModel.signedTakerDepositTx: " + signedTakerDepositTx);
            resultHandler.onResult(signedTakerDepositTx);
        } catch (InsufficientMoneyException e)
        {
            log.error("Pay deposit faultHandler.onFault due InsufficientMoneyException " + e);
            exceptionHandler.onError(new Exception("Pay deposit faultHandler.onFault due InsufficientMoneyException " + e));
        }
    }

    public interface ResultHandler
    {
        void onResult(Transaction signedTakerDepositTx);
    }


}

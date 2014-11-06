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

import io.bitsquare.bank.BankAccount;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.Contract;
import io.bitsquare.util.Utilities;
import io.bitsquare.util.task.ExceptionHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;

import java.security.PublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAndSignContract {
    private static final Logger log = LoggerFactory.getLogger(CreateAndSignContract.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           CryptoFacade cryptoFacade,
                           Offer offer,
                           Coin tradeAmount,
                           String takeOfferFeeTxId,
                           String accountId,
                           BankAccount bankAccount,
                           PublicKey peersMessagePublicKey,
                           PublicKey messagePublicKey,
                           String peersAccountId,
                           BankAccount peersBankAccount,
                           ECKey registrationKey) {
        log.trace("Run task");
        try {
            Contract contract = new Contract(offer, tradeAmount, takeOfferFeeTxId, peersAccountId, accountId,
                    peersBankAccount, bankAccount, peersMessagePublicKey, messagePublicKey);

            String contractAsJson = Utilities.objectToJson(contract);
            String signature = cryptoFacade.signContract(registrationKey, contractAsJson);
            resultHandler.onResult(contract, contractAsJson, signature);
        } catch (Throwable t) {
            log.error("Exception at sign contract " + t);
            exceptionHandler.handleException(t);
        }
    }

    public interface ResultHandler {
        void onResult(Contract contract, String contractAsJson, String signature);
    }

}

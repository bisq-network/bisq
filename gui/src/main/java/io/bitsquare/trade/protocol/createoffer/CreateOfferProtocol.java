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

package io.bitsquare.trade.protocol.createoffer;

import io.bitsquare.btc.WalletService;
import io.bitsquare.offer.Offer;
import io.bitsquare.offer.RemoteOfferBook;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.util.task.FaultHandler;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for coordinating tasks involved in the create offer process.
 * Executed on UI thread (single threaded)
 */
public class CreateOfferProtocol {

    private static final Logger log = LoggerFactory.getLogger(CreateOfferProtocol.class);

    private final Offer offer;
    private final WalletService walletService;
    private final TransactionResultHandler resultHandler;
    private final FaultHandler faultHandler;
    private final RemoteOfferBook remoteOfferBook;
    private int repeatAddOfferCallCounter = 0;

    public CreateOfferProtocol(Offer offer, WalletService walletService, TransactionResultHandler resultHandler,
                               FaultHandler faultHandler, RemoteOfferBook remoteOfferBook) {
        this.offer = offer;
        this.walletService = walletService;
        this.resultHandler = resultHandler;
        this.faultHandler = faultHandler;
        this.remoteOfferBook = remoteOfferBook;
    }

    public void createOffer() {
        try {
            validateOffer();
            Transaction transaction = createOfferFeeTx();

            TransactionResultHandler resultHandler1 = transaction1 -> addOffer(transaction1);
            FaultHandler faultHandler1 = (message, throwable) -> faultHandler.handleFault(message, throwable);
            broadcastCreateOfferFeeTx(transaction, resultHandler1, faultHandler1);

        } catch (Throwable t) {
        }

    }

    // 1. Validate offer data
    // Sync
    // In case of an error: No rollback activity needed
    void validateOffer() throws Exception {
        try {
            offer.validate();
        } catch (Exception ex) {
            faultHandler.handleFault("Offer validation failed", ex);
            throw ex;
        }
    }

    // 2. createOfferFeeTx
    // Sync
    // In case of an error: No rollback activity needed
    Transaction createOfferFeeTx() throws Exception {
        try {
            return walletService.createOfferFeeTx(offer.getId());
        } catch (InsufficientMoneyException ex) {
            faultHandler.handleFault(
                    "Offer fee payment failed because there is insufficient money in the trade wallet", ex);
            throw ex;
        } catch (Throwable t) {
            faultHandler.handleFault("Offer fee payment failed because of an exception occurred", t);
            throw t;
        }
    }

    // 3. broadcastCreateOfferFeeTx
    // Async
    // In case of an error: Not sure if there can be an inconsistent state in failure case. Assuming not but need to check further.
    void broadcastCreateOfferFeeTx(Transaction transaction, TransactionResultHandler resultHandler1, FaultHandler faultHandler1) throws Exception {
        try {
            walletService.broadcastCreateOfferFeeTx(transaction, new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(Transaction transaction) {
                    log.info("Broadcast of offer fee payment succeeded: transaction = " + transaction.toString());
                    if (transaction == null) {
                        Exception ex = new Exception("Broadcast of offer fee payment failed because transaction = null.");
                        faultHandler.handleFault("Broadcast of offer fee payment failed.", ex);
                    }
                    resultHandler1.onResult(transaction);
                }

                @Override
                public void onFailure(Throwable t) {
                    faultHandler1.handleFault("Broadcast of offer fee payment failed with an exception.", t);
                }
            });
        } catch (Throwable t) {
            faultHandler1.handleFault("Broadcast of offer fee payment failed with an exception.", t);
            throw t;
        }
    }

    // 4. addOffer
    // Async
    // In case of an error: Try again, afterwards give up.
    void addOffer(Transaction transaction) {
        remoteOfferBook.addOffer(offer,
                () -> {
                    offer.setOfferFeePaymentTxID(transaction.getHashAsString());
                    resultHandler.onResult(transaction);
                },
                (message, throwable) -> {
                    repeatAddOfferCallCounter++;
                    if (repeatAddOfferCallCounter > 1) {
                        faultHandler.handleFault(message, throwable);
                    }
                    else {
                        addOffer(transaction);
                    }
                });
    }
}

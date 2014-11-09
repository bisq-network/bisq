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

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.offer.Offer;
import io.bitsquare.offer.OfferRepository;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.util.task.FaultHandler;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for coordinating tasks involved in the create offer process.
 */
public class CreateOfferCoordinator {

    private static final Logger log = LoggerFactory.getLogger(CreateOfferCoordinator.class);

    private final Offer offer;
    private final WalletFacade walletFacade;
    private final TransactionResultHandler resultHandler;
    private final FaultHandler faultHandler;
    private final OfferRepository offerRepository;

    public CreateOfferCoordinator(Offer offer, WalletFacade walletFacade, TransactionResultHandler resultHandler,
                                  FaultHandler faultHandler, OfferRepository offerRepository) {
        this.offer = offer;
        this.walletFacade = walletFacade;
        this.resultHandler = resultHandler;
        this.faultHandler = faultHandler;
        this.offerRepository = offerRepository;
    }

    public void start() {
        try {
            offer.validate();
        } catch (Exception ex) {
            faultHandler.handleFault("Offer validation failed", ex);
            return;
        }

        Transaction transaction;

        try {
            transaction = walletFacade.createOfferFeeTx(offer.getId());
            offer.setOfferFeePaymentTxID(transaction.getHashAsString());
        } catch (InsufficientMoneyException ex) {
            faultHandler.handleFault(
                    "Offer fee payment failed because there is insufficient money in the trade wallet", ex);
            return;
        } catch (Throwable ex) {
            faultHandler.handleFault("Offer fee payment failed because of an exception occurred", ex);
            return;
        }

        try {
            walletFacade.broadcastCreateOfferFeeTx(transaction, new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(Transaction transaction) {
                    log.info("sendResult onSuccess:" + transaction);
                    if (transaction == null) {
                        faultHandler.handleFault("Offer fee payment failed.",
                                new Exception("Offer fee payment failed. Transaction = null."));
                        return;
                    }

                    try {
                        offerRepository.addOffer(offer, () -> resultHandler.onResult(transaction), faultHandler);
                    } catch (Exception e) {
                        faultHandler.handleFault("Offer fee payment failed.", e);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    faultHandler.handleFault("Offer fee payment failed with an exception.", t);
                }
            });
        } catch (Throwable t) {
            faultHandler.handleFault("Offer fee payment failed because an exception occurred.", t);
            return;
        }
    }
}

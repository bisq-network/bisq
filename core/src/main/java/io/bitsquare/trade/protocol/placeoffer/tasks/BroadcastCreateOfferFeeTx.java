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

package io.bitsquare.trade.protocol.placeoffer.tasks;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferModel;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import com.google.common.util.concurrent.FutureCallback;

import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BroadcastCreateOfferFeeTx extends Task<PlaceOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(BroadcastCreateOfferFeeTx.class);

    private boolean removeOfferFailed;
    private boolean addOfferFailed;

    public BroadcastCreateOfferFeeTx(TaskRunner taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);

        appendToErrorMessage("Broadcast of offer fee payment failed because transaction = null.");
        appendToErrorMessage("Maybe you have connection problems. Please try later again.");
    }

    @Override
    protected void doRun() {

        Coin totalsNeeded = model.offer.getSecurityDeposit().add(FeePolicy.CREATE_OFFER_FEE).add(FeePolicy.TX_FEE);
        AddressEntry addressEntry = model.walletService.getAddressEntry(model.offer.getId());
        Coin balance = model.walletService.getBalanceForAddress(addressEntry.getAddress());
        if (balance.compareTo(totalsNeeded) >= 0) {

            model.walletService.getTradeWalletService().broadcastCreateOfferFeeTx(model.getTransaction(), new FutureCallback<Transaction>() {
                @Override
                public void onSuccess(Transaction transaction) {
                    log.info("Broadcast of offer fee payment succeeded: transaction = " + transaction.toString());

                    if (model.getTransaction().getHashAsString().equals(transaction.getHashAsString())) {
                        // No tx malleability happened after broadcast (still not in blockchain)
                        complete();
                    }
                    else {
                        log.warn("Tx malleability happened after broadcast. We publish the changed offer to the DHT again.");
                        // Tx malleability happened after broadcast. We publish the changed offer to the DHT again.
                        model.offerBookService.removeOffer(model.offer,
                                () -> {
                                    log.info("We store now the changed txID to the offer and add that again.");
                                    // We store now the changed txID to the offer and add that again.
                                    model.offer.setOfferFeePaymentTxID(transaction.getHashAsString());
                                    model.offerBookService.addOffer(model.offer,
                                            () -> {
                                                complete();
                                            },
                                            (message, throwable) -> {
                                                log.error("addOffer failed");
                                                addOfferFailed = true;
                                                failed(throwable);
                                                updateStateOnFault();
                                            });
                                },
                                (message, throwable) -> {
                                    log.error("removeOffer failed");
                                    removeOfferFailed = true;
                                    failed(throwable);
                                    updateStateOnFault();
                                });
                    }
                }

                @Override
                public void onFailure(@NotNull Throwable t) {
                    failed(t);
                    updateStateOnFault();
                }
            });
        }
        else {
            failed("Not enough balance for placing the offer.");
            updateStateOnFault();
        }
    }

    private void updateStateOnFault() {
        if (!removeOfferFailed && !addOfferFailed) {
            // If broadcast fails we need to remove offer from offerbook
            model.offerBookService.removeOffer(model.offer,
                    () -> {
                        log.info("Offer removed from offerbook because broadcast failed.");
                    },
                    (message, throwable) -> {
                        log.error("removeOffer failed");
                        failed(throwable);
                    });
        }
    }

}

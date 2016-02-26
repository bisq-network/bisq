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

import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.peers.BroadcastHandler;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferModel;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BroadcastCreateOfferFeeTx extends Task<PlaceOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(BroadcastCreateOfferFeeTx.class);

    private boolean removeOfferFailed;
    private boolean addOfferFailed;

    public BroadcastCreateOfferFeeTx(TaskRunner taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Coin totalsNeeded = FeePolicy.getSecurityDeposit().add(FeePolicy.getCreateOfferFee()).add(FeePolicy.getFixedTxFeeForTrades());
            AddressEntry addressEntry = model.walletService.getAddressEntryByOfferId(model.offer.getId());
            Coin balance = model.walletService.getBalanceForAddress(addressEntry.getAddress());
            if (balance.compareTo(totalsNeeded) >= 0) {
                model.tradeWalletService.broadcastTx(model.getTransaction(), new FutureCallback<Transaction>() {
                    @Override
                    public void onSuccess(Transaction transaction) {
                        log.info("Broadcast of offer fee payment succeeded: transaction = " + transaction.toString());

                        if (model.getTransaction().getHashAsString().equals(transaction.getHashAsString())) {
                            model.offer.setState(Offer.State.OFFER_FEE_PAID);
                            // No tx malleability happened after broadcast (still not in blockchain)
                            complete();
                        } else {
                            log.warn("Tx malleability happened after broadcast. We publish the changed offer to the P2P network again.");
                            // Tx malleability happened after broadcast. We first remove the malleable offer.
                            // Then we publish the changed offer to the P2P network again after setting the new TxId.
                            // Normally we use a delay for broadcasting to the peers, but at shut down we want to get it fast out
                            BroadcastHandler.useDelay(false);
                            model.offerBookService.removeOffer(model.offer,
                                    () -> {
                                        log.info("We store now the changed txID to the offer and add that again.");
                                        // We store now the changed txID to the offer and add that again.
                                        model.offer.setOfferFeePaymentTxID(transaction.getHashAsString());
                                        model.setTransaction(transaction);
                                        model.offerBookService.addOffer(model.offer,
                                                () -> {
                                                    BroadcastHandler.useDelay(true);
                                                    complete();
                                                },
                                                errorMessage -> {
                                                    log.error("addOffer failed");
                                                    addOfferFailed = true;
                                                    updateStateOnFault();
                                                    model.offer.setErrorMessage("An error occurred when adding the offer to the P2P network.\n" +
                                                            "Error message:\n"
                                                            + errorMessage);
                                                    failed(errorMessage);
                                                });
                                    },
                                    errorMessage -> {
                                        log.error("removeOffer failed");
                                        removeOfferFailed = true;
                                        updateStateOnFault();
                                        model.offer.setErrorMessage("An error occurred when removing the offer from the P2P network.\n" +
                                                "Error message:\n"
                                                + errorMessage);
                                        failed(errorMessage);
                                    });
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t) {
                        updateStateOnFault();
                        model.offer.setErrorMessage("An error occurred.\n" +
                                "Error message:\n"
                                + t.getMessage());
                        failed(t);
                    }
                });
            } else {
                updateStateOnFault();
                model.offer.setErrorMessage("You don't have enough balance in your wallet for placing the offer.");
            }
        } catch (Throwable t) {
            model.offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());
            failed(t);
        }
    }

    private void updateStateOnFault() {
        if (!removeOfferFailed && !addOfferFailed) {
            // If broadcast fails we need to remove offer from offerbook
            model.offerBookService.removeOffer(model.offer,
                    () -> log.info("Offer removed from offerbook because broadcast failed."),
                    errorMessage -> log.error("removeOffer failed. " + errorMessage));
        }
    }

}

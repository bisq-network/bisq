/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.offer.placeoffer.tasks;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.taskrunner.Task;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.placeoffer.PlaceOfferModel;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BroadcastMakerFeeTx extends Task<PlaceOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(BroadcastMakerFeeTx.class);

    private boolean removeOfferFailed;
    private boolean addOfferFailed;

    @SuppressWarnings({"WeakerAccess", "unused"})
    public BroadcastMakerFeeTx(TaskRunner taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            final Transaction transaction = model.getTransaction();

            // TODO Try to republish tx?
            Timer timeoutTimer = UserThread.runAfter(() -> {
                log.warn("Broadcast not completed after 5 sec. We go on with the trade protocol.");
                model.getOffer().setState(Offer.State.OFFER_FEE_PAID);
                complete();
            }, 20);

            model.getTradeWalletService().broadcastTx(model.getTransaction(),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction tx) {
                            timeoutTimer.stop();
                            if (!completed) {
                                log.debug("Broadcast of offer fee payment succeeded: transaction = " + tx.toString());

                                if (transaction.getHashAsString().equals(tx.getHashAsString())) {
                                    model.getOffer().setState(Offer.State.OFFER_FEE_PAID);
                                    // No tx malleability happened after broadcast (still not in blockchain)
                                    complete();
                                } else {
                                    log.warn("Tx malleability happened after broadcast. We publish the changed offer to the P2P network again.");
                                    // Tx malleability happened after broadcast. We first remove the malleable offer.
                                    // Then we publish the changed offer to the P2P network again after setting the new TxId.
                                    // Normally we use a delay for broadcasting to the peers, but at shut down we want to get it fast out
                                    model.getOfferBookService().removeOffer(model.getOffer().getOfferPayload(),
                                            () -> {
                                                log.debug("We store now the changed txID to the offer and add that again.");
                                                // We store now the changed txID to the offer and add that again.
                                                model.getOffer().setOfferFeePaymentTxId(tx.getHashAsString());
                                                model.setTransaction(tx);
                                                model.getOfferBookService().addOffer(model.getOffer(),
                                                        BroadcastMakerFeeTx.this::complete,
                                                        errorMessage -> {
                                                            log.error("addOffer failed");
                                                            addOfferFailed = true;
                                                            updateStateOnFault();
                                                            model.getOffer().setErrorMessage("An error occurred when adding the offer to the P2P network.\n" +
                                                                    "Error message:\n"
                                                                    + errorMessage);
                                                            failed(errorMessage);
                                                        });
                                            },
                                            errorMessage -> {
                                                log.error("removeOffer failed");
                                                removeOfferFailed = true;
                                                updateStateOnFault();
                                                model.getOffer().setErrorMessage("An error occurred when removing the offer from the P2P network.\n" +
                                                        "Error message:\n"
                                                        + errorMessage);
                                                failed(errorMessage);
                                            });
                                }
                            } else {
                                log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                            }
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            if (!completed) {
                                timeoutTimer.stop();
                                updateStateOnFault();
                                model.getOffer().setErrorMessage("An error occurred.\n" +
                                        "Error message:\n"
                                        + t.getMessage());
                                failed(t);
                            } else {
                                log.warn("We got the onFailure callback called after the timeout has been triggered a complete().");
                            }
                        }
                    });
        } catch (Throwable t) {
            model.getOffer().setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());
            failed(t);
        }
    }

    private void updateStateOnFault() {
        if (!removeOfferFailed && !addOfferFailed) {
            // If broadcast fails we need to remove offer from offerbook
            model.getOfferBookService().removeOffer(model.getOffer().getOfferPayload(),
                    () -> log.debug("OfferPayload removed from offerbook because broadcast failed."),
                    errorMessage -> log.error("removeOffer failed. " + errorMessage));
        }
    }

}

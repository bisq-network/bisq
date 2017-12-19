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
import io.bisq.common.UserThread;
import io.bisq.common.taskrunner.Task;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.btc.wallet.WalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.placeoffer.PlaceOfferModel;
import io.bisq.core.trade.protocol.ArbitratorSelectionRule;
import io.bisq.network.p2p.NodeAddress;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class CreateMakerFeeTx extends Task<PlaceOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateMakerFeeTx.class);
    private Transaction tradeFeeTx = null;

    @SuppressWarnings({"WeakerAccess", "unused"})
    public CreateMakerFeeTx(TaskRunner taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        Offer offer = model.getOffer();

        try {
            runInterceptHook();

            String id = offer.getId();
            BtcWalletService walletService = model.getWalletService();

            NodeAddress selectedArbitratorNodeAddress = ArbitratorSelectionRule.select(model.getUser().getAcceptedArbitratorAddresses(),
                    model.getOffer());
            log.debug("selectedArbitratorAddress " + selectedArbitratorNodeAddress);
            Arbitrator selectedArbitrator = model.getUser().getAcceptedArbitratorByAddress(selectedArbitratorNodeAddress);
            checkNotNull(selectedArbitrator, "selectedArbitrator must not be null at CreateOfferFeeTx");

            Address fundingAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING).getAddress();
            Address reservedForTradeAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            Address changeAddress = walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();

            final TradeWalletService tradeWalletService = model.getTradeWalletService();

            // We dont use a timeout here as we need to get the tradeFee tx callback called to be sure the addressEntry is funded

            if (offer.isCurrencyForMakerFeeBtc()) {
                tradeFeeTx = tradeWalletService.createBtcTradingFeeTx(
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        model.getReservedFundsForOffer(),
                        model.isUseSavingsWallet(),
                        offer.getMakerFee(),
                        offer.getTxFee(),
                        selectedArbitrator.getBtcAddress(),
                        new FutureCallback<Transaction>() {
                            @Override
                            public void onSuccess(Transaction transaction) {
                                // we delay one render frame to be sure we don't get called before the method call has
                                // returned (tradeFeeTx would be null in that case)
                                UserThread.execute(() -> {
                                    if (!completed) {
                                        offer.setOfferFeePaymentTxId(transaction.getHashAsString());
                                        model.setTransaction(transaction);
                                        walletService.swapTradeEntryToAvailableEntry(id, AddressEntry.Context.OFFER_FUNDING);

                                        model.getOffer().setState(Offer.State.OFFER_FEE_PAID);

                                        complete();
                                    } else {
                                        log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                                    }
                                });
                            }

                            @Override
                            public void onFailure(@NotNull Throwable t) {
                                if (!completed) {
                                    failed(t);
                                } else {
                                    log.warn("We got the onFailure callback called after the timeout has been triggered a complete().");
                                }
                            }
                        });
            } else {
                final BsqWalletService bsqWalletService = model.getBsqWalletService();
                Transaction preparedBurnFeeTx = model.getBsqWalletService().getPreparedBurnFeeTx(offer.getMakerFee());
                Transaction txWithBsqFee = tradeWalletService.completeBsqTradingFeeTx(preparedBurnFeeTx,
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        model.getReservedFundsForOffer(),
                        model.isUseSavingsWallet(),
                        offer.getTxFee());

                Transaction signedTx = model.getBsqWalletService().signTx(txWithBsqFee);
                WalletService.checkAllScriptSignaturesForTx(signedTx);
                bsqWalletService.commitTx(signedTx);
                // We need to create another instance, otherwise the tx would trigger an invalid state exception
                // if it gets committed 2 times
                tradeWalletService.commitTx(tradeWalletService.getClonedTransaction(signedTx));

                // We dont use a timeout here as we need to get the tradeFee tx callback called to be sure the addressEntry is funded

                bsqWalletService.broadcastTx(signedTx, new FutureCallback<Transaction>() {
                    @Override
                    public void onSuccess(@Nullable Transaction transaction) {
                        if (transaction != null) {
                            offer.setOfferFeePaymentTxId(transaction.getHashAsString());
                            model.setTransaction(transaction);
                            log.debug("onSuccess, offerId={}, OFFER_FUNDING", id);
                            walletService.swapTradeEntryToAvailableEntry(id, AddressEntry.Context.OFFER_FUNDING);

                            log.debug("Successfully sent tx with id " + transaction.getHashAsString());
                            model.getOffer().setState(Offer.State.OFFER_FEE_PAID);

                            complete();
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t) {
                        log.error(t.toString());
                        t.printStackTrace();
                        offer.setErrorMessage("An error occurred.\n" +
                                "Error message:\n"
                                + t.getMessage());
                        failed(t);
                    }
                });
            }
        } catch (Throwable t) {
            offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());
            failed(t);
        }
    }
}

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

package bisq.core.offer.placeoffer.tasks;

import bisq.core.arbitration.Arbitrator;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.availability.ArbitratorSelection;
import bisq.core.offer.placeoffer.PlaceOfferModel;

import bisq.common.UserThread;
import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

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

            Arbitrator arbitrator = ArbitratorSelection.getLeastUsedArbitrator(model.getTradeStatisticsManager(),
                    model.getArbitratorManager());

            Address fundingAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING).getAddress();
            Address reservedForTradeAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            Address changeAddress = walletService.getFreshAddressEntry().getAddress();

            final TradeWalletService tradeWalletService = model.getTradeWalletService();

            if (offer.isCurrencyForMakerFeeBtc()) {
                tradeFeeTx = tradeWalletService.createBtcTradingFeeTx(
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        model.getReservedFundsForOffer(),
                        model.isUseSavingsWallet(),
                        offer.getMakerFee(),
                        offer.getTxFee(),
                        arbitrator.getBtcAddress(),
                        new TxBroadcaster.Callback() {
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
                            public void onFailure(TxBroadcastException exception) {
                                if (!completed) {
                                    failed(exception);
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

                bsqWalletService.broadcastTx(signedTx, new TxBroadcaster.Callback() {
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
                    public void onFailure(TxBroadcastException exception) {
                        log.error(exception.toString());
                        exception.printStackTrace();
                        offer.setErrorMessage("An error occurred.\n" +
                                "Error message:\n"
                                + exception.getMessage());
                        failed(exception);
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

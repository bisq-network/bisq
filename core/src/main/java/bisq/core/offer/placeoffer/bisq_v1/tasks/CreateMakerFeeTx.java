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

package bisq.core.offer.placeoffer.bisq_v1.tasks;

import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletService;
import bisq.core.dao.exceptions.DaoDisabledException;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.offer.Offer;
import bisq.core.offer.placeoffer.bisq_v1.PlaceOfferModel;
import bisq.core.util.FeeReceiverSelector;

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

    @SuppressWarnings({"unused"})
    public CreateMakerFeeTx(TaskRunner<PlaceOfferModel> taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        Offer offer = model.getOffer();

        try {
            runInterceptHook();

            String id = offer.getId();
            BtcWalletService walletService = model.getWalletService();

            Address fundingAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING).getAddress();
            Address reservedForTradeAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            Address changeAddress = walletService.getFreshAddressEntry().getAddress();

            TradeWalletService tradeWalletService = model.getTradeWalletService();

            String feeReceiver = FeeReceiverSelector.getAddress(model.getFilterManager());

            if (offer.isCurrencyForMakerFeeBtc()) {
                tradeWalletService.createBtcTradingFeeTx(
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        model.getReservedFundsForOffer(),
                        model.isUseSavingsWallet(),
                        offer.getMakerFee(),
                        offer.getTxFee(),
                        feeReceiver,
                        true,
                        new TxBroadcaster.Callback() {
                            @Override
                            public void onSuccess(Transaction transaction) {
                                // we delay one render frame to be sure we don't get called before the method call has
                                // returned (tradeFeeTx would be null in that case)
                                UserThread.execute(() -> {
                                    if (!completed) {
                                        offer.setOfferFeePaymentTxId(transaction.getTxId().toString());
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
                BsqWalletService bsqWalletService = model.getBsqWalletService();
                Transaction preparedBurnFeeTx = model.getBsqWalletService().getPreparedTradeFeeTx(offer.getMakerFee());
                Transaction txWithBsqFee = tradeWalletService.completeBsqTradingFeeTx(preparedBurnFeeTx,
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        model.getReservedFundsForOffer(),
                        model.isUseSavingsWallet(),
                        offer.getTxFee());

                Transaction signedTx = model.getBsqWalletService().signTxAndVerifyNoDustOutputs(txWithBsqFee);
                WalletService.checkAllScriptSignaturesForTx(signedTx);
                bsqWalletService.commitTx(signedTx, TxType.PAY_TRADE_FEE);
                // We need to create another instance, otherwise the tx would trigger an invalid state exception
                // if it gets committed 2 times
                tradeWalletService.commitTx(tradeWalletService.getClonedTransaction(signedTx));

                // We use a short timeout as there are issues with BSQ txs. See comment in TxBroadcaster
                bsqWalletService.broadcastTx(signedTx, new TxBroadcaster.Callback() {
                            @Override
                            public void onSuccess(@Nullable Transaction transaction) {
                                if (transaction != null) {
                                    offer.setOfferFeePaymentTxId(transaction.getTxId().toString());
                                    model.setTransaction(transaction);
                                    log.debug("onSuccess, offerId={}, OFFER_FUNDING", id);
                                    walletService.swapTradeEntryToAvailableEntry(id, AddressEntry.Context.OFFER_FUNDING);

                                    log.debug("Successfully sent tx with id " + transaction.getTxId().toString());
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
                        },
                        1);
            }
        } catch (Throwable t) {
            if (t instanceof DaoDisabledException) {
                offer.setErrorMessage("You cannot pay the trade fee in BSQ at the moment because the DAO features have been " +
                        "disabled due technical problems. Please use the BTC fee option until the issues are resolved. " +
                        "For more information please visit the Bisq Forum.");
            } else {
                offer.setErrorMessage("An error occurred.\n" +
                        "Error message:\n"
                        + t.getMessage());
            }

            failed(t);
        }
    }
}

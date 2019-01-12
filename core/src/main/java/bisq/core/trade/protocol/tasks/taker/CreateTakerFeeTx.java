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

package bisq.core.trade.protocol.tasks.taker;

import bisq.core.arbitration.Arbitrator;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletService;
import bisq.core.offer.availability.ArbitratorSelection;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class CreateTakerFeeTx extends TradeTask {

    @SuppressWarnings({"WeakerAccess", "unused"})
    public CreateTakerFeeTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Arbitrator arbitrator = ArbitratorSelection.getLeastUsedArbitrator(processModel.getTradeStatisticsManager(),
                    processModel.getArbitratorManager());
            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();
            AddressEntry addressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING);
            AddressEntry reservedForTradeAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE);
            AddressEntry changeAddressEntry = walletService.getFreshAddressEntry();
            Address fundingAddress = addressEntry.getAddress();
            Address reservedForTradeAddress = reservedForTradeAddressEntry.getAddress();
            Address changeAddress = changeAddressEntry.getAddress();
            final TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            if (trade.isCurrencyForTakerFeeBtc()) {
                tradeWalletService.createBtcTradingFeeTx(
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        processModel.getFundsNeededForTradeAsLong(),
                        processModel.isUseSavingsWallet(),
                        trade.getTakerFee(),
                        trade.getTxFee(),
                        arbitrator.getBtcAddress(),
                        new TxBroadcaster.Callback() {
                            @Override
                            public void onSuccess(Transaction transaction) {
                                if (!completed) {
                                    processModel.setTakeOfferFeeTx(transaction);
                                    trade.setTakerFeeTxId(transaction.getHashAsString());
                                    walletService.swapTradeEntryToAvailableEntry(id, AddressEntry.Context.OFFER_FUNDING);
                                    trade.setState(Trade.State.TAKER_PUBLISHED_TAKER_FEE_TX);
                                    complete();
                                } else {
                                    log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                                }
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
                final BsqWalletService bsqWalletService = processModel.getBsqWalletService();
                Transaction preparedBurnFeeTx = processModel.getBsqWalletService().getPreparedBurnFeeTx(trade.getTakerFee());
                Transaction txWithBsqFee = tradeWalletService.completeBsqTradingFeeTx(preparedBurnFeeTx,
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        processModel.getFundsNeededForTradeAsLong(),
                        processModel.isUseSavingsWallet(),
                        trade.getTxFee());

                Transaction signedTx = processModel.getBsqWalletService().signTx(txWithBsqFee);
                WalletService.checkAllScriptSignaturesForTx(signedTx);
                bsqWalletService.commitTx(signedTx);
                // We need to create another instance, otherwise the tx would trigger an invalid state exception
                // if it gets committed 2 times
                tradeWalletService.commitTx(tradeWalletService.getClonedTransaction(signedTx));

                bsqWalletService.broadcastTx(signedTx, new TxBroadcaster.Callback() {
                    @Override
                    public void onSuccess(@Nullable Transaction transaction) {
                        if (!completed) {
                            if (transaction != null) {
                                log.debug("Successfully sent tx with id " + transaction.getHashAsString());
                                trade.setTakerFeeTxId(transaction.getHashAsString());
                                processModel.setTakeOfferFeeTx(transaction);
                                walletService.swapTradeEntryToAvailableEntry(id, AddressEntry.Context.OFFER_FUNDING);
                                trade.setState(Trade.State.TAKER_PUBLISHED_TAKER_FEE_TX);

                                complete();
                            }
                        } else {
                            log.warn("We got the onSuccess callback called after the timeout has been triggered a complete().");
                        }
                    }

                    @Override
                    public void onFailure(TxBroadcastException exception) {
                        if (!completed) {
                            log.error(exception.toString());
                            exception.printStackTrace();
                            trade.setErrorMessage("An error occurred.\n" +
                                    "Error message:\n"
                                    + exception.getMessage());
                            failed(exception);
                        } else {
                            log.warn("We got the onFailure callback called after the timeout has been triggered a complete().");
                        }
                    }
                });
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}

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

package io.bisq.core.trade.protocol.tasks.taker;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.UserThread;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.btc.wallet.WalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.ArbitratorSelectionRule;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.core.user.User;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class CreateTakerFeeTx extends TradeTask {
    private Transaction tradeFeeTx;

    @SuppressWarnings({"WeakerAccess", "unused"})
    public CreateTakerFeeTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            User user = processModel.getUser();
            NodeAddress selectedArbitratorNodeAddress = ArbitratorSelectionRule.select(user.getAcceptedArbitratorAddresses(),
                    processModel.getOffer());
            log.debug("selectedArbitratorAddress " + selectedArbitratorNodeAddress);
            Arbitrator selectedArbitrator = user.getAcceptedArbitratorByAddress(selectedArbitratorNodeAddress);
            checkNotNull(selectedArbitrator, "selectedArbitrator must not be null at CreateTakeOfferFeeTx");

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();
            AddressEntry addressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING);
            AddressEntry reservedForTradeAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE);
            AddressEntry changeAddressEntry = walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE);
            Address fundingAddress = addressEntry.getAddress();
            Address reservedForTradeAddress = reservedForTradeAddressEntry.getAddress();
            Address changeAddress = changeAddressEntry.getAddress();
            final TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            if (trade.isCurrencyForTakerFeeBtc()) {
                tradeFeeTx = tradeWalletService.createBtcTradingFeeTx(
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        processModel.getFundsNeededForTradeAsLong(),
                        processModel.isUseSavingsWallet(),
                        trade.getTakerFee(),
                        trade.getTxFee(),
                        selectedArbitrator.getBtcAddress(),
                        new FutureCallback<Transaction>() {
                            @Override
                            public void onSuccess(Transaction transaction) {
                                // we delay one render frame to be sure we don't get called before the method call has
                                // returned (tradeFeeTx would be null in that case)
                                UserThread.execute(() -> {
                                    if (!completed) {
                                        processModel.setTakeOfferFeeTx(tradeFeeTx);
                                        trade.setTakerFeeTxId(tradeFeeTx.getHashAsString());
                                        walletService.swapTradeEntryToAvailableEntry(id, AddressEntry.Context.OFFER_FUNDING);
                                        trade.setState(Trade.State.TAKER_PUBLISHED_TAKER_FEE_TX);

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
                bsqWalletService.broadcastTx(signedTx, new FutureCallback<Transaction>() {
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
                    public void onFailure(@NotNull Throwable t) {
                        if (!completed) {
                            log.error(t.toString());
                            t.printStackTrace();
                            trade.setErrorMessage("An error occurred.\n" +
                                    "Error message:\n"
                                    + t.getMessage());
                            failed(t);
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

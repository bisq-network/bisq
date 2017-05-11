/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.taker;

import com.google.common.util.concurrent.FutureCallback;
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
import io.bisq.core.user.UserModel;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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

            UserModel userModel = processModel.getUserModel();
            NodeAddress selectedArbitratorNodeAddress = ArbitratorSelectionRule.select(userModel.getAcceptedArbitratorAddresses(),
                    processModel.getOffer());
            log.debug("selectedArbitratorAddress " + selectedArbitratorNodeAddress);
            Arbitrator selectedArbitrator = userModel.getAcceptedArbitratorByAddress(selectedArbitratorNodeAddress);
            checkNotNull(selectedArbitrator, "selectedArbitrator must not be null at CreateTakeOfferFeeTx");
            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();
            AddressEntry addressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING);
            AddressEntry reservedForTradeAddressEntry = walletService.getOrCreateAddressEntry(id,
                    AddressEntry.Context.RESERVED_FOR_TRADE);
            AddressEntry changeAddressEntry = walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE);
            Address fundingAddress = addressEntry.getAddress();
            Address reservedForTradeAddress = reservedForTradeAddressEntry.getAddress();
            Address changeAddress = changeAddressEntry.getAddress();
            final TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            if (trade.isCurrencyForTakerFeeBtc()) {
                Transaction createTakeOfferFeeTx = tradeWalletService.createBtcTradingFeeTx(
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        processModel.getFundsNeededForTradeAsLong(),
                        processModel.isUseSavingsWallet(),
                        trade.getTakerFee(),
                        trade.getTxFee(),
                        selectedArbitrator.getBtcAddress());

                //TODO use handler for broadcastTx success
                processModel.setTakeOfferFeeTx(createTakeOfferFeeTx);
                trade.setTakerFeeTxId(createTakeOfferFeeTx.getHashAsString());

                complete();
            } else {
                final BsqWalletService bsqWalletService = processModel.getBsqWalletService();
                Transaction preparedBurnFeeTx = processModel.getBsqWalletService().getPreparedBurnFeeTx(trade.getTakerFee());
                //Coin txFee = trade.getTxFee().subtract(trade.getTakerFee());
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
                        if (transaction != null) {
                            checkArgument(transaction.equals(signedTx));
                            trade.setTakerFeeTxId(transaction.getHashAsString());
                            processModel.setTakeOfferFeeTx(transaction);

                            complete();
                            log.debug("Successfully sent tx with id " + transaction.getHashAsString());
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t) {
                        log.error(t.toString());
                        t.printStackTrace();
                        trade.setErrorMessage("An error occurred.\n" +
                                "Error message:\n"
                                + t.getMessage());
                        failed(t);
                    }
                });
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}

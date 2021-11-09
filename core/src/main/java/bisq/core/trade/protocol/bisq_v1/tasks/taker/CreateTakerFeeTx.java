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

package bisq.core.trade.protocol.bisq_v1.tasks.taker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.dao.exceptions.DaoDisabledException;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.util.FeeReceiverSelector;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateTakerFeeTx extends TradeTask {

    public CreateTakerFeeTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            // We enforce here to create a MULTI_SIG and TRADE_PAYOUT address entry to avoid that the change output would be used later
            // for those address entries. Because we do not commit our fee tx yet the change address would
            // appear as unused and therefore selected for the outputs for the MS tx.
            // That would cause incorrect display of the balance as
            // the change output would be considered as not available balance (part of the locked trade amount).
            walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);

            AddressEntry fundingAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING);
            AddressEntry reservedForTradeAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE);
            AddressEntry changeAddressEntry = walletService.getFreshAddressEntry();
            Address fundingAddress = fundingAddressEntry.getAddress();
            Address reservedForTradeAddress = reservedForTradeAddressEntry.getAddress();
            Address changeAddress = changeAddressEntry.getAddress();
            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            Transaction transaction;

            String feeReceiver = FeeReceiverSelector.getAddress(processModel.getFilterManager());

            if (trade.isCurrencyForTakerFeeBtc()) {
                transaction = tradeWalletService.createBtcTradingFeeTx(
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        processModel.getFundsNeededForTrade(),
                        processModel.isUseSavingsWallet(),
                        trade.getTakerFee(),
                        trade.getTradeTxFee(),
                        feeReceiver,
                        false,
                        null);
            } else {
                Transaction preparedBurnFeeTx = processModel.getBsqWalletService().getPreparedTradeFeeTx(trade.getTakerFee());
                Transaction txWithBsqFee = tradeWalletService.completeBsqTradingFeeTx(preparedBurnFeeTx,
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        processModel.getFundsNeededForTrade(),
                        processModel.isUseSavingsWallet(),
                        trade.getTradeTxFee());
                transaction = processModel.getBsqWalletService().signTxAndVerifyNoDustOutputs(txWithBsqFee);
                WalletService.checkAllScriptSignaturesForTx(transaction);
            }

            // We did not broadcast and commit the tx yet to avoid issues with lost trade fee in case the
            // take offer attempt failed.

            // We do not set the takerFeeTxId yet to trade as it is not published.
            processModel.setTakeOfferFeeTxId(transaction.getTxId().toString());

            processModel.setTakeOfferFeeTx(transaction);
            walletService.swapTradeEntryToAvailableEntry(id, AddressEntry.Context.OFFER_FUNDING);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            if (t instanceof DaoDisabledException) {
                failed("You cannot pay the trade fee in BSQ at the moment because the DAO features have been " +
                        "disabled due technical problems. Please use the BTC fee option until the issues are resolved. " +
                        "For more information please visit the Bisq Forum.");
            } else {
                failed(t);
            }
        }
    }
}

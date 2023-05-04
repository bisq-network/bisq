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

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.offer.Offer;
import bisq.core.offer.placeoffer.bisq_v1.PlaceOfferModel;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.util.List;
import java.util.Optional;

//
public class CloneAddressEntryForSharedMakerFee extends Task<PlaceOfferModel> {
    @SuppressWarnings({"unused"})
    public CloneAddressEntryForSharedMakerFee(TaskRunner<PlaceOfferModel> taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        runInterceptHook();

        Offer offer = model.getOffer();
        String makerFeeTxId = offer.getOfferFeePaymentTxId();
        BtcWalletService walletService = model.getWalletService();
        for (AddressEntry reservedForTradeEntry : walletService.getAddressEntries(AddressEntry.Context.RESERVED_FOR_TRADE)) {
            if (findTxId(reservedForTradeEntry.getAddress())
                    .map(txId -> txId.equals(makerFeeTxId))
                    .orElse(false)) {
                walletService.getOrCloneAddressEntryWithOfferId(reservedForTradeEntry, offer.getId());
                complete();
                return;
            }
        }

        failed();
    }

    // We look up the most recent transaction with unspent outputs associated with the given address and return
    // the txId if found.
    private Optional<String> findTxId(Address address) {
        BtcWalletService walletService = model.getWalletService();
        List<Transaction> transactions = walletService.getAllRecentTransactions(false);
        for (Transaction transaction : transactions) {
            for (TransactionOutput output : transaction.getOutputs()) {
                if (walletService.isTransactionOutputMine(output) && WalletService.isOutputScriptConvertibleToAddress(output)) {
                    String addressString = WalletService.getAddressStringFromOutput(output);
                    // make sure the output is still unspent
                    if (addressString != null && addressString.equals(address.toString()) && output.getSpentBy() == null) {
                        return Optional.of(transaction.getTxId().toString());
                    }
                }
            }
        }
        return Optional.empty();
    }
}

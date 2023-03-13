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

public class CloneMakerFeeOco extends Task<PlaceOfferModel> {
    @SuppressWarnings({"unused"})
    public CloneMakerFeeOco(TaskRunner<PlaceOfferModel> taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        runInterceptHook();
        Offer newOcoOffer = model.getOffer();
        // newOcoOffer is cloned from an existing offer;
        // the clone needs a unique AddressEntry record associating the offerId with the reserved amount.
        BtcWalletService walletService = model.getWalletService();
        for (AddressEntry potentialOcoSource : walletService.getAddressEntries(AddressEntry.Context.RESERVED_FOR_TRADE)) {
            getTxIdFromAddress(walletService, potentialOcoSource.getAddress()).ifPresent(txId -> {
                if (txId.equalsIgnoreCase(newOcoOffer.getOfferFeePaymentTxId())) {
                    walletService.createAddressEntryForOcoOffer(potentialOcoSource, newOcoOffer.getId());
                    newOcoOffer.setState(Offer.State.OFFER_FEE_PAID);
                    complete();
                }
            });
            if (completed) {
                return;
            }
        }
        failed();
    }

    // AddressEntry and TxId are not linked, so do a reverse lookup
    private Optional<String> getTxIdFromAddress(BtcWalletService walletService, Address address) {
        List<Transaction> txns = walletService.getRecentTransactions(10, false);
        for (Transaction txn : txns) {
            for (TransactionOutput output : txn.getOutputs()) {
                if (walletService.isTransactionOutputMine(output) && WalletService.isOutputScriptConvertibleToAddress(output)) {
                    String addressString = WalletService.getAddressStringFromOutput(output);
                    assert addressString != null;
                    // make sure the output is still unspent
                    if (addressString.equalsIgnoreCase(address.toString()) && output.getSpentBy() == null) {
                        return Optional.of(txn.getTxId().toString());
                    }
                }
            }
        }
        return Optional.empty();
    }
}

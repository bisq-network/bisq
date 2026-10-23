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

import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.DEAD;

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
        BtcWalletService walletService = model.getWalletService();
        Transaction makerFeeTx = walletService.getTransaction(offer.getOfferFeePaymentTxId());
        if (makerFeeTx != null && makerFeeTx.getConfidence().getConfidenceType() != DEAD) {
            for (AddressEntry reservedForTradeEntry : walletService.getAddressEntries(AddressEntry.Context.RESERVED_FOR_TRADE)) {
                if (hasUnspentOutputTo(makerFeeTx, reservedForTradeEntry.getAddress())) {
                    walletService.getOrCloneAddressEntryWithOfferId(reservedForTradeEntry, offer.getId());
                    complete();
                    return;
                }
            }
        }

        failed();
    }

    // Other coins sent to the reserved address must not hide the reserved output, so we look only at the outputs of
    // the maker fee tx.
    private static boolean hasUnspentOutputTo(Transaction makerFeeTx, Address address) {
        return makerFeeTx.getOutputs().stream()
                .filter(TransactionOutput::isAvailableForSpending)
                .anyMatch(output -> address.equals(WalletService.getAddressFromOutput(output)));
    }
}

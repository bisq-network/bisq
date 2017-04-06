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

package io.bisq.core.offer.placeoffer.tasks;

import com.google.common.util.concurrent.FutureCallback;
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

public class CreateOfferFeeTx extends Task<PlaceOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferFeeTx.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public CreateOfferFeeTx(TaskRunner taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        Offer offer = model.offer;
        try {
            runInterceptHook();

            NodeAddress selectedArbitratorNodeAddress = ArbitratorSelectionRule.select(model.user.getAcceptedArbitratorAddresses(),
                    model.offer);
            log.debug("selectedArbitratorAddress " + selectedArbitratorNodeAddress);
            Arbitrator selectedArbitrator = model.user.getAcceptedArbitratorByAddress(selectedArbitratorNodeAddress);
            checkNotNull(selectedArbitrator, "selectedArbitrator must not be null at CreateOfferFeeTx");
            BtcWalletService walletService = model.walletService;
            String id = offer.getId();
            Address fundingAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING).getAddress();
            Address reservedForTradeAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            Address changeAddress = walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();

            if (model.payFeeInBtc) {
                Transaction btcTransaction = model.tradeWalletService.createBtcTradingFeeTx(
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        model.reservedFundsForOffer.subtract(model.createOfferFeeAsBtc),
                        model.useSavingsWallet,
                        offer.getCreateOfferFee(),
                        offer.getTxFee(),
                        selectedArbitrator.getBtcAddress());

                // We assume there will be no tx malleability. We add a check later in case the published offer has a different hash.
                // As the txId is part of the offer and therefore change the hash data we need to be sure to have no
                // tx malleability
                offer.setOfferFeePaymentTxId(btcTransaction.getHashAsString());
                model.setTransaction(btcTransaction);

                complete();
            } else {
                final BsqWalletService bsqWalletService = model.bsqWalletService;
                final TradeWalletService tradeWalletService = model.tradeWalletService;
                Transaction preparedBurnFeeTx = model.bsqWalletService.getPreparedBurnFeeTx(model.createOfferFeeAsBsq);
                Transaction txWithBsqFee = tradeWalletService.completeBsqTradingFeeTx(preparedBurnFeeTx,
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        model.reservedFundsForOffer,
                        model.useSavingsWallet,
                        offer.getTxFee());

                Transaction signedTx = model.bsqWalletService.signTx(txWithBsqFee);
                WalletService.checkAllScriptSignaturesForTx(signedTx);
                bsqWalletService.commitTx(txWithBsqFee);
                // We need to create another instance, otherwise the tx would trigger an invalid state exception 
                // if it gets committed 2 times 
                tradeWalletService.commitTx(tradeWalletService.getClonedTransaction(txWithBsqFee));
                bsqWalletService.broadcastTx(signedTx, new FutureCallback<Transaction>() {
                    @Override
                    public void onSuccess(@Nullable Transaction transaction) {
                        if (transaction != null) {
                            offer.setOfferFeePaymentTxId(transaction.getHashAsString());
                            model.setTransaction(transaction);

                            complete();
                            log.debug("Successfully sent tx with id " + transaction.getHashAsString());
                        }
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t) {
                        log.error(t.toString());
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

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

package bisq.core.trade.protocol.bisq_v1.tasks.seller_as_taker;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.DepositTxValidation.checkCanonicalDepositTxShape;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerAsTakerSignsDepositTx extends TradeTask {
    public SellerAsTakerSignsDepositTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            List<RawTransactionInput> sellerInputs = checkNotNull(processModel.getRawTransactionInputs(),
                    "sellerInputs must not be null");
            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            Optional<AddressEntry> optionalMultiSigAddressEntry = walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            checkArgument(optionalMultiSigAddressEntry.isPresent(), "addressEntryOptional must be present");
            AddressEntry sellerMultiSigAddressEntry = optionalMultiSigAddressEntry.get();
            byte[] sellerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(sellerMultiSigPubKey,
                    sellerMultiSigAddressEntry.getPubKey()),
                    "sellerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            Coin sellerInput = sellerInputs.stream()
                    .map(input -> Coin.valueOf(input.value))
                    .reduce(Coin.ZERO, Coin::add);

            Coin totalFee = trade.getTradeTxFee().multiply(2); // Fee for deposit and payout tx
            Coin multiSigValue = sellerInput.subtract(totalFee);
            processModel.getBtcWalletService().setCoinLockedInMultiSigAddressEntry(sellerMultiSigAddressEntry, multiSigValue.value);
            walletService.saveAddressEntryList();

            Offer offer = trade.getOffer();
            Coin msOutputAmount = offer.getBuyerSecurityDeposit().add(offer.getSellerSecurityDeposit()).add(trade.getTradeTxFee())
                    .add(checkNotNull(trade.getAmount()));

            TradingPeer tradingPeer = processModel.getTradePeer();
            List<RawTransactionInput> buyerInputs = checkNotNull(tradingPeer.getRawTransactionInputs());
            Transaction makersDepositTx = new Transaction(walletService.getParams(), checkNotNull(processModel.getPreparedDepositTx()));
            verifyPreparedDepositTxFromBuyerAsMaker(makersDepositTx);
            checkCanonicalDepositTxShape(makersDepositTx, buyerInputs, walletService.getParams());

            Transaction depositTx = processModel.getTradeWalletService().takerSignsDepositTx(
                    true,
                    makersDepositTx,
                    msOutputAmount,
                    buyerInputs,
                    sellerInputs,
                    tradingPeer.getMultiSigPubKey(),
                    sellerMultiSigPubKey);

            // We set the deposit tx to trade once we have it published
            processModel.setDepositTx(depositTx);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            // The multisig lock may have been set above before this throw; release it so the
            // funds are not stuck "locked in multisig" against a trade that never proceeds.
            // Idempotent — a no-op if the lock was never taken.
            // Use the persisted offerId, not processModel.getOffer() — the latter is transient and
            // may be null if this task fails before the offer was reattached on a restart.
            processModel.getBtcWalletService().resetCoinLockedInMultiSigAddressEntry(processModel.getOfferId());
            Contract contract = trade.getContract();
            if (contract != null)
                contract.printDiff(processModel.getTradePeer().getContractAsJson());
            failed(t);
        }
    }

    static void verifyPreparedDepositTxFromBuyerAsMaker(Transaction makersDepositTx)
            throws TransactionVerificationException {
        int outputCount = makersDepositTx.getOutputs().size();
        if (outputCount != 1) {
            throw new TransactionVerificationException("Maker's preparedDepositTx must not have a change output");
        }
    }
}

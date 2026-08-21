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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_taker;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.crypto.Hash;
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
public class BuyerAsTakerSignsDepositTx extends TradeTask {

    public BuyerAsTakerSignsDepositTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

           /* log.debug("\n\n------------------------------------------------------------\n"
                    + "Contract as json\n"
                    + trade.getContractAsJson()
                    + "\n------------------------------------------------------------\n");*/


            byte[] contractHash = Hash.getSha256Hash(checkNotNull(trade.getContractAsJson()));
            trade.setContractHash(contractHash);
            List<RawTransactionInput> buyerInputs = checkNotNull(processModel.getRawTransactionInputs(), "buyerInputs must not be null");
            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            Optional<AddressEntry> addressEntryOptional = walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            checkArgument(addressEntryOptional.isPresent(), "addressEntryOptional must be present");
            AddressEntry buyerMultiSigAddressEntry = addressEntryOptional.get();
            Coin buyerInput = buyerInputs.stream()
                    .map(input -> Coin.valueOf(input.value))
                    .reduce(Coin.ZERO, Coin::add);

            Coin multiSigValue = buyerInput.subtract(trade.getTradeTxFee().multiply(2));
            processModel.getBtcWalletService().setCoinLockedInMultiSigAddressEntry(buyerMultiSigAddressEntry, multiSigValue.value);
            walletService.saveAddressEntryList();

            Offer offer = trade.getOffer();
            Coin msOutputAmount = offer.getBuyerSecurityDeposit().add(offer.getSellerSecurityDeposit()).add(trade.getTradeTxFee())
                    .add(checkNotNull(trade.getAmount()));

            TradingPeer tradingPeer = processModel.getTradePeer();
            byte[] buyerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(buyerMultiSigPubKey, buyerMultiSigAddressEntry.getPubKey()),
                    "buyerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            List<RawTransactionInput> sellerInputs = checkNotNull(tradingPeer.getRawTransactionInputs());
            byte[] sellerMultiSigPubKey = tradingPeer.getMultiSigPubKey();
            Transaction makersDepositTx = new Transaction(walletService.getParams(), checkNotNull(processModel.getPreparedDepositTx()));
            verifyPreparedDepositTxFromSellerAsMaker(makersDepositTx,
                    offer.getSellerSecurityDeposit(),
                    offer.getMinAmount(),
                    offer.getAmount(),
                    checkNotNull(trade.getAmount()),
                    sellerInputs);
            checkCanonicalDepositTxShape(makersDepositTx, sellerInputs, walletService.getParams());

            Transaction depositTx = processModel.getTradeWalletService().takerSignsDepositTx(
                    false,
                    makersDepositTx,
                    msOutputAmount,
                    buyerInputs,
                    sellerInputs,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey);
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
            failed(t);
        }
    }

    static void verifyPreparedDepositTxFromSellerAsMaker(Transaction makersDepositTx,
                                                     Coin sellerSecurityDeposit,
                                                     Coin offerMinAmount,
                                                     Coin offerAmount,
                                                     Coin tradeAmount,
                                                     List<RawTransactionInput> sellerInputs)
            throws TransactionVerificationException {
        checkArgument(!tradeAmount.isLessThan(offerMinAmount),
                "tradeAmount must not be less than offerMinAmount");
        checkArgument(!tradeAmount.isGreaterThan(offerAmount),
                "tradeAmount must not be greater than offerAmount");
        Coin sellerInput = sellerInputs.stream()
                .map(input -> Coin.valueOf(input.value))
                .reduce(Coin.ZERO, Coin::add);
        Coin expectedMakerChange = sellerInput.subtract(sellerSecurityDeposit.add(tradeAmount));
        checkArgument(!expectedMakerChange.isNegative(), "expectedMakerChange must not be negative");
        checkArgument(!expectedMakerChange.isGreaterThan(offerAmount.subtract(tradeAmount)),
                "expectedMakerChange must not be greater than remaining offer amount");
        int outputCount = makersDepositTx.getOutputs().size();
        if (expectedMakerChange.isZero()) {
            if (outputCount != 1) {
                throw new TransactionVerificationException("Maker's preparedDepositTx must not have a change output");
            }
            return;
        }

        if (outputCount != 2) {
            throw new TransactionVerificationException("Maker's preparedDepositTx must have exactly one change output");
        }

        Coin makerChangeOutput = makersDepositTx.getOutput(1).getValue();
        if (!makerChangeOutput.equals(expectedMakerChange)) {
            throw new TransactionVerificationException("Maker's preparedDepositTx change output value does not match the expected maker change");
        }
    }
}

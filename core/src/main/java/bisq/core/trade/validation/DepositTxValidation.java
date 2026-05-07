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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.validation;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferValidation;
import bisq.core.offer.bisq_v1.MarketPriceNotAvailableException;
import bisq.core.provider.price.PriceFeedService;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.ScriptBuilder;

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;

import static bisq.core.trade.validation.TradeValidation.checkTransaction;
import static bisq.core.util.Validator.checkIsPositive;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class DepositTxValidation {
    private DepositTxValidation() {
    }

    /* --------------------------------------------------------------------- */
    // TradeAmount
    /* --------------------------------------------------------------------- */

    public static Coin checkTradeAmount(Coin tradeAmount, Coin offerMinAmount, Coin offerMaxAmount) {
        checkIsPositive(tradeAmount, "tradeAmount");
        checkIsPositive(offerMinAmount, "offerMinAmount");
        checkIsPositive(offerMaxAmount, "offerMaxAmount");

        checkArgument(!tradeAmount.isLessThan(offerMinAmount),
                "Trade amount must not be less than minimum offer amount. tradeAmount=%s, offerMinAmount=%s",
                tradeAmount.toFriendlyString(), offerMinAmount.toFriendlyString());
        checkArgument(!tradeAmount.isGreaterThan(offerMaxAmount),
                "Trade amount must not be higher than maximum offer amount. tradeAmount=%s, offerMaxAmount=%s",
                tradeAmount.toFriendlyString(), offerMaxAmount.toFriendlyString());
        return tradeAmount;
    }


    /* --------------------------------------------------------------------- */
    // TradePrice
    /* --------------------------------------------------------------------- */

    public static long checkTakersTradePrice(long takersTradePrice,
                                             PriceFeedService priceFeedService,
                                             Offer offer) {
        try {
            offer.verifyTakersTradePrice(takersTradePrice);
            // We allow 50% tolerance to the max allowed price percentage to avoid failing trades in
            // high volatility environments
            OfferValidation.verifyPriceInBounds(priceFeedService, offer, TradeValidation.MAX_TRADE_PRICE_DEVIATION);
            return takersTradePrice;
        } catch (TradePriceOutOfToleranceException | MarketPriceNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }


    /* --------------------------------------------------------------------- */
    // DepositTx
    /* --------------------------------------------------------------------- */

    public static Transaction checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(Transaction depositTx,
                                                                                  Transaction expectedDepositTx,
                                                                                  BtcWalletService btcWalletService) {
        checkNotNull(depositTx, "depositTx must not be null");
        checkNotNull(expectedDepositTx, "expectedDepositTx must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        NetworkParameters params = checkNotNull(btcWalletService.getParams(), "params must not be null");
        return checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx, expectedDepositTx, params);
    }

    @VisibleForTesting
    static Transaction checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(Transaction depositTx,
                                                                           Transaction expectedDepositTx,
                                                                           NetworkParameters params) {
        checkNotNull(depositTx, "depositTx must not be null");
        checkNotNull(expectedDepositTx, "expectedDepositTx must not be null");
        checkNotNull(params, "params must not be null");

        checkTransaction(depositTx);
        checkTransaction(expectedDepositTx);

        byte[] strippedDepositTx = strippedSerializedTransaction(depositTx, params);
        byte[] strippedExpectedDepositTx = strippedSerializedTransaction(expectedDepositTx, params);
        checkArgument(Arrays.equals(strippedDepositTx, strippedExpectedDepositTx),
                "Deposit tx does not match expected deposit tx when witness and scriptSig data is stripped. " +
                        "depositTxId=%s, expectedDepositTxId=%s",
                depositTx.getTxId(),
                expectedDepositTx.getTxId());
        return depositTx;
    }

    private static byte[] strippedSerializedTransaction(Transaction transaction, NetworkParameters params) {
        Transaction strippedTransaction = new Transaction(params, transaction.bitcoinSerialize());
        strippedTransaction.getInputs().forEach(DepositTxValidation::stripWitnessAndScriptSig);
        return strippedTransaction.bitcoinSerialize(false);
    }

    private static void stripWitnessAndScriptSig(TransactionInput input) {
        input.setScriptSig(ScriptBuilder.createEmpty());
        input.setWitness(TransactionWitness.EMPTY);
    }

}

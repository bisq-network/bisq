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

package bisq.core.btc.wallet.utils;

import bisq.core.btc.model.RawTransactionInput;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class DepositTransactionUtils {
    private DepositTransactionUtils() {
    }

    /**
     * Creates a serialized transaction without witness and scriptSig for the given transaction.
     *
     * @param transaction the transaction to serialize
     * @param params      the network parameters
     * @return the newly constructed serialized transaction without witness and scriptSig
     */
    public static byte[] toSerializedTransactionWithoutWitnessAndScriptSig(Transaction transaction,
                                                                           NetworkParameters params) {
        checkNotNull(transaction, "transaction can't be null");
        checkNotNull(params, "params can't be null");
        Transaction strippedTransaction = new Transaction(params, transaction.bitcoinSerialize());
        strippedTransaction.getInputs().forEach(DepositTransactionUtils::removeWitnessAndScriptSigFromInput);
        return strippedTransaction.bitcoinSerialize(false);
    }

    private static void removeWitnessAndScriptSigFromInput(TransactionInput input) {
        input.setScriptSig(ScriptBuilder.createEmpty());
        input.setWitness(TransactionWitness.EMPTY);
    }

    public static List<RawTransactionInput> combinedInputs(List<RawTransactionInput> makerInputs,
                                                           List<RawTransactionInput> takerInputs) {
        checkNotNull(makerInputs, "makerInputs can't be null");
        checkNotNull(takerInputs, "takerInputs can't be null");
        List<RawTransactionInput> inputs = new ArrayList<>(makerInputs.size() + takerInputs.size());
        inputs.addAll(makerInputs);
        inputs.addAll(takerInputs);
        return inputs;
    }

    public static Coin sumInputValues(List<RawTransactionInput> inputs) {
        checkNotNull(inputs, "inputs can't be null");
        Coin sum = Coin.ZERO;
        for (int i = 0; i < inputs.size(); i++) {
            RawTransactionInput input = checkNotNull(inputs.get(i),
                    "input at position %s must not be null",
                    i);
            checkArgument(input.value > 0,
                    "input at position %s must have positive value",
                    i);
            sum = sum.add(Coin.valueOf(input.value));
        }
        return sum;
    }

    public static Script get2of2MultiSigOutputScript(byte[] buyerPubKey, byte[] sellerPubKey) {
        checkNotNull(buyerPubKey, "buyerPubKey can't be null");
        checkNotNull(sellerPubKey, "sellerPubKey can't be null");
        Script redeemScript = PayoutTransactionUtil.get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        return ScriptBuilder.createP2WSHOutputScript(redeemScript);
    }
}

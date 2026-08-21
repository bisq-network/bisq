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

package bisq.core.trade.validation;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import java.util.ArrayList;
import java.util.List;

import static bisq.core.util.Validator.checkIsNotNegative;
import static bisq.core.util.Validator.checkIsPositive;
import static bisq.core.util.Validator.checkNonBlankString;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Shared code between PayoutTxValidation and MediatedPayoutTxValidation
final class PayoutTxValidationUtils {
    private PayoutTxValidationUtils() {
    }


    /* --------------------------------------------------------------------- */
    // Payout transaction input
    /* --------------------------------------------------------------------- */

    static Transaction checkPayoutTxInputSpendsDepositOutputZero(Transaction payoutTx,
                                                                 Transaction depositTx,
                                                                 String txName) {
        Transaction checkedPayoutTx = checkNotNull(payoutTx, "payoutTx must not be null");
        Transaction checkedDepositTx = checkNotNull(depositTx, "depositTx must not be null");
        String checkedTxName = checkNonBlankString(txName, "txName");
        TransactionOutput depositOutput = getDepositOutputZero(checkedDepositTx);

        checkArgument(checkedPayoutTx.getInputs().size() == 1,
                "%s must have exactly one input. inputCount=%s",
                checkedTxName,
                checkedPayoutTx.getInputs().size());

        TransactionInput input = checkedPayoutTx.getInput(0);
        TransactionOutPoint outpoint = checkNotNull(input.getOutpoint(),
                "%s input outpoint must not be null",
                checkedTxName);
        checkArgument(outpoint.getHash().equals(checkedDepositTx.getTxId()) &&
                        outpoint.getIndex() == depositOutput.getIndex(),
                "%s input must spend depositTx output 0. payoutTxInput=%s:%s, depositTxOutput=%s:%s",
                checkedTxName,
                outpoint.getHash(),
                outpoint.getIndex(),
                checkedDepositTx.getTxId(),
                depositOutput.getIndex());
        return checkedPayoutTx;
    }


    /* --------------------------------------------------------------------- */
    // Payout transaction output sum
    /* --------------------------------------------------------------------- */

    static Transaction checkPayoutTxOutputSumNotGreaterThanDepositOutputValue(Transaction depositTx,
                                                                              Coin buyerPayoutAmount,
                                                                              Coin sellerPayoutAmount,
                                                                              String txName) {
        Transaction checkedDepositTx = checkNotNull(depositTx, "depositTx must not be null");
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");
        String checkedTxName = checkNonBlankString(txName, "txName");

        Coin depositOutputValue = checkIsPositive(getDepositOutputZero(checkedDepositTx).getValue(),
                "depositTx output 0 value");
        Coin payoutOutputSum = checkedBuyerPayoutAmount.add(checkedSellerPayoutAmount);
        checkArgument(!payoutOutputSum.isGreaterThan(depositOutputValue),
                "%s output sum must not be greater than depositTx output 0 value. " +
                        "payoutOutputSum=%s, depositOutputValue=%s",
                checkedTxName,
                payoutOutputSum.toFriendlyString(),
                depositOutputValue.toFriendlyString());
        return checkedDepositTx;
    }


    /* --------------------------------------------------------------------- */
    // Payout transaction outputs
    /* --------------------------------------------------------------------- */

    static Transaction checkPayoutTxOutputAmountsAndAddresses(Transaction payoutTx,
                                                              Coin buyerPayoutAmount,
                                                              Coin sellerPayoutAmount,
                                                              String buyerPayoutAddressString,
                                                              String sellerPayoutAddressString,
                                                              NetworkParameters params,
                                                              String txName,
                                                              String noPositivePayoutMessage) {
        Transaction checkedPayoutTx = checkNotNull(payoutTx, "payoutTx must not be null");
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");
        NetworkParameters checkedParams = checkNotNull(params, "params must not be null");
        String checkedTxName = checkNonBlankString(txName, "txName");
        String checkedNoPositivePayoutMessage = checkNonBlankString(noPositivePayoutMessage,
                "noPositivePayoutMessage");

        List<ExpectedOutput> expectedOutputs = getExpectedOutputs(checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString);
        checkArgument(!expectedOutputs.isEmpty(), checkedNoPositivePayoutMessage);
        checkArgument(checkedPayoutTx.getOutputs().size() == expectedOutputs.size(),
                "%s output count mismatch. outputCount=%s, expectedOutputCount=%s",
                checkedTxName,
                checkedPayoutTx.getOutputs().size(),
                expectedOutputs.size());

        for (int i = 0; i < expectedOutputs.size(); i++) {
            checkPayoutTxOutput(checkedPayoutTx.getOutput(i), expectedOutputs.get(i), checkedParams, checkedTxName);
        }
        return checkedPayoutTx;
    }

    private static List<ExpectedOutput> getExpectedOutputs(Coin buyerPayoutAmount,
                                                           Coin sellerPayoutAmount,
                                                           String buyerPayoutAddressString,
                                                           String sellerPayoutAddressString) {
        List<ExpectedOutput> expectedOutputs = new ArrayList<>();
        if (buyerPayoutAmount.isPositive()) {
            expectedOutputs.add(new ExpectedOutput("buyer",
                    buyerPayoutAmount,
                    checkNonBlankString(buyerPayoutAddressString, "buyerPayoutAddressString")));
        }
        if (sellerPayoutAmount.isPositive()) {
            expectedOutputs.add(new ExpectedOutput("seller",
                    sellerPayoutAmount,
                    checkNonBlankString(sellerPayoutAddressString, "sellerPayoutAddressString")));
        }
        return expectedOutputs;
    }

    private static void checkPayoutTxOutput(TransactionOutput output,
                                            ExpectedOutput expectedOutput,
                                            NetworkParameters params,
                                            String txName) {
        checkArgument(output.getValue().equals(expectedOutput.amount),
                "%s %s output amount mismatch. payoutAmount=%s, expectedPayoutAmount=%s",
                txName,
                expectedOutput.name,
                output.getValue().toFriendlyString(),
                expectedOutput.amount.toFriendlyString());

        Address expectedAddress = Address.fromString(params, expectedOutput.addressString);
        Address payoutAddress = output.getScriptPubKey().getToAddress(params);
        checkArgument(payoutAddress.equals(expectedAddress),
                "%s %s output address mismatch. payoutAddress=%s, expectedPayoutAddress=%s",
                txName,
                expectedOutput.name,
                payoutAddress,
                expectedAddress);
    }


    /* --------------------------------------------------------------------- */
    // Deposit transaction output
    /* --------------------------------------------------------------------- */

    static TransactionOutput getDepositOutputZero(Transaction depositTx) {
        Transaction checkedDepositTx = checkNotNull(depositTx, "depositTx must not be null");
        checkArgument(!checkedDepositTx.getOutputs().isEmpty(), "depositTx must not be empty");
        return checkedDepositTx.getOutput(0);
    }


    /* --------------------------------------------------------------------- */
    // Expected output
    /* --------------------------------------------------------------------- */

    private static final class ExpectedOutput {
        private final String name;
        private final Coin amount;
        private final String addressString;

        private ExpectedOutput(String name, Coin amount, String addressString) {
            this.name = name;
            this.amount = amount;
            this.addressString = addressString;
        }
    }
}

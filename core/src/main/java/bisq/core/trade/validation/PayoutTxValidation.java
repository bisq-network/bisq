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

import bisq.core.btc.wallet.BtcWalletService;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.util.Arrays;

import static bisq.core.trade.validation.TransactionValidation.checkMultiSigPubKey;
import static bisq.core.trade.validation.TransactionValidation.checkTransaction;
import static bisq.core.trade.validation.TransactionValidation.toVerifiedTransaction;
import static bisq.core.util.Validator.checkIsNotNegative;
import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class PayoutTxValidation {
    private PayoutTxValidation() {
    }


    /* --------------------------------------------------------------------- */
    // Payout transaction
    /* --------------------------------------------------------------------- */

    public static byte[] checkPayoutTx(byte[] serializedPayoutTx,
                                       BtcWalletService btcWalletService,
                                       Transaction depositTx,
                                       Coin buyerPayoutAmount,
                                       Coin sellerPayoutAmount,
                                       String buyerPayoutAddressString,
                                       String sellerPayoutAddressString,
                                       byte[] buyerMultiSigPubKey,
                                       byte[] sellerMultiSigPubKey) {
        byte[] checkedSerializedPayoutTx = checkNonEmptyBytes(serializedPayoutTx, "serializedPayoutTx");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        NetworkParameters params = checkNotNull(btcWalletService.getParams(),
                "btcWalletService.getParams() must not be null");

        Transaction verifiedPayoutTx = toVerifiedTransaction(checkedSerializedPayoutTx, btcWalletService);
        checkPayoutTx(verifiedPayoutTx,
                depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString,
                buyerMultiSigPubKey,
                sellerMultiSigPubKey,
                params);
        return checkedSerializedPayoutTx;
    }

    public static Transaction checkPayoutTx(Transaction payoutTx,
                                            Transaction depositTx,
                                            Coin buyerPayoutAmount,
                                            Coin sellerPayoutAmount,
                                            String buyerPayoutAddressString,
                                            String sellerPayoutAddressString,
                                            byte[] buyerMultiSigPubKey,
                                            byte[] sellerMultiSigPubKey,
                                            NetworkParameters params) {
        Transaction checkedPayoutTx = checkNotNull(payoutTx, "payoutTx must not be null");
        Transaction checkedDepositTx = checkNotNull(depositTx, "depositTx must not be null");
        Coin checkedBuyerPayoutAmount = checkIsNotNegative(buyerPayoutAmount, "buyerPayoutAmount");
        Coin checkedSellerPayoutAmount = checkIsNotNegative(sellerPayoutAmount, "sellerPayoutAmount");
        String checkedBuyerPayoutAddressString = checkNonBlankString(buyerPayoutAddressString,
                "buyerPayoutAddressString");
        String checkedSellerPayoutAddressString = checkNonBlankString(sellerPayoutAddressString,
                "sellerPayoutAddressString");
        byte[] checkedBuyerMultiSigPubKey = checkMultiSigPubKey(buyerMultiSigPubKey);
        byte[] checkedSellerMultiSigPubKey = checkMultiSigPubKey(sellerMultiSigPubKey);
        NetworkParameters checkedParams = checkNotNull(params, "params must not be null");

        checkArgument(checkedBuyerPayoutAmount.isPositive() || checkedSellerPayoutAmount.isPositive(),
                "At least one payout amount must be positive");

        checkTransaction(checkedPayoutTx);

        Script redeemScript = get2of2MultiSigRedeemScript(checkedBuyerMultiSigPubKey, checkedSellerMultiSigPubKey);
        PayoutTxValidationUtils.checkPayoutTxOutputSumNotGreaterThanDepositOutputValue(checkedDepositTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                "payoutTx");
        PayoutTxValidationUtils.checkPayoutTxInputSpendsDepositOutputZero(checkedPayoutTx,
                checkedDepositTx,
                "payoutTx");
        PayoutTxValidationUtils.checkPayoutTxOutputAmountsAndAddresses(checkedPayoutTx,
                checkedBuyerPayoutAmount,
                checkedSellerPayoutAmount,
                checkedBuyerPayoutAddressString,
                checkedSellerPayoutAddressString,
                checkedParams,
                "payoutTx",
                "At least one payout amount must be positive");
        TransactionOutput depositOutput = PayoutTxValidationUtils.getDepositOutputZero(checkedDepositTx);
        checkPayoutTxInputScript(checkedPayoutTx,
                depositOutput,
                redeemScript,
                checkedBuyerMultiSigPubKey,
                checkedSellerMultiSigPubKey);

        return checkedPayoutTx;
    }


    /* --------------------------------------------------------------------- */
    // Payout transaction input script
    /* --------------------------------------------------------------------- */

    private static void checkPayoutTxInputScript(Transaction payoutTx,
                                                 TransactionOutput depositOutput,
                                                 Script redeemScript,
                                                 byte[] buyerMultiSigPubKey,
                                                 byte[] sellerMultiSigPubKey) {
        Script depositOutputScript = depositOutput.getScriptPubKey();
        boolean isSegwitPayout = Arrays.equals(depositOutputScript.getProgram(),
                ScriptBuilder.createP2WSHOutputScript(redeemScript).getProgram());
        checkArgument(isSegwitPayout,
                "depositTx output 0 must be the expected P2WSH 2-of-2 multisig script");

        try {
            TransactionInput input = payoutTx.getInput(0);
            input.getScriptSig().correctlySpends(payoutTx,
                    0,
                    input.getWitness(),
                    depositOutput.getValue(),
                    depositOutput.getScriptPubKey(),
                    Script.ALL_VERIFY_FLAGS);
        } catch (Throwable t) {
            throw new IllegalArgumentException("payoutTx input script does not spend depositTx output 0", t);
        }

        checkP2wshSignatures(payoutTx, depositOutput, redeemScript, buyerMultiSigPubKey, sellerMultiSigPubKey);
    }

    private static void checkP2wshSignatures(Transaction payoutTx,
                                             TransactionOutput depositOutput,
                                             Script redeemScript,
                                             byte[] buyerMultiSigPubKey,
                                             byte[] sellerMultiSigPubKey) {
        TransactionWitness witness = payoutTx.getInput(0).getWitness();
        checkArgument(!TransactionWitness.EMPTY.equals(witness), "payoutTx input witness must not be empty");

        try {
            checkArgument(witness.getPush(0).length == 0, "payoutTx witness dummy element must be empty");
            byte[] sellerSignatureBytes = witness.getPush(1);
            byte[] buyerSignatureBytes = witness.getPush(2);
            byte[] witnessRedeemScript = witness.getPush(3);
            checkArgument(Arrays.equals(witnessRedeemScript, redeemScript.getProgram()),
                    "payoutTx witness redeem script does not match the expected 2-of-2 multisig script");

            TransactionSignature sellerSignature = TransactionSignature.decodeFromBitcoin(sellerSignatureBytes,
                    true,
                    true);
            TransactionSignature buyerSignature = TransactionSignature.decodeFromBitcoin(buyerSignatureBytes,
                    true,
                    true);
            checkArgument(sellerSignature.sigHashMode() == Transaction.SigHash.ALL && !sellerSignature.anyoneCanPay(),
                    "seller payoutTx witness signature must use SIGHASH_ALL");
            checkArgument(buyerSignature.sigHashMode() == Transaction.SigHash.ALL && !buyerSignature.anyoneCanPay(),
                    "buyer payoutTx witness signature must use SIGHASH_ALL");
            Sha256Hash sigHash = payoutTx.hashForWitnessSignature(0,
                    redeemScript,
                    depositOutput.getValue(),
                    Transaction.SigHash.ALL,
                    false);
            ECKey buyerKey = ECKey.fromPublicOnly(buyerMultiSigPubKey);
            ECKey sellerKey = ECKey.fromPublicOnly(sellerMultiSigPubKey);
            checkArgument(buyerKey.verify(sigHash, buyerSignature) && sellerKey.verify(sigHash, sellerSignature),
                    "payoutTx witness signatures are invalid");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage();
            throw new IllegalArgumentException(
                    message == null || message.isEmpty()
                            ? "payoutTx witness signatures are invalid"
                            : "payoutTx witness signatures are invalid: " + message,
                    e);
        }
    }


    /* --------------------------------------------------------------------- */
    // Multisig redeem script
    /* --------------------------------------------------------------------- */

    private static Script get2of2MultiSigRedeemScript(byte[] buyerPubKey, byte[] sellerPubKey) {
        ECKey buyerKey = ECKey.fromPublicOnly(buyerPubKey);
        ECKey sellerKey = ECKey.fromPublicOnly(sellerPubKey);
        return ScriptBuilder.createMultiSigOutputScript(2, Arrays.asList(sellerKey, buyerKey));
    }
}

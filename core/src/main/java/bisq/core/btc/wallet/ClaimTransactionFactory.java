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

package bisq.core.btc.wallet;

import bisq.core.btc.exceptions.TransactionVerificationException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import org.bouncycastle.crypto.params.KeyParameter;

import static com.google.common.base.Preconditions.checkNotNull;

public class ClaimTransactionFactory {
    private final NetworkParameters params;

    public ClaimTransactionFactory(NetworkParameters params) {
        this.params = params;
    }

    public Transaction createSignedClaimTransaction(Transaction warningTx,
                                                    long nSequence,
                                                    Address payoutAddress,
                                                    long miningFee,
                                                    DeterministicKey myMultiSigKeyPair,
                                                    KeyParameter aesKey) throws TransactionVerificationException {
        Transaction claimTx = createUnsignedClaimTransaction(warningTx, nSequence, payoutAddress, miningFee);
        byte[] mySignature = signClaimTransaction(claimTx, warningTx, myMultiSigKeyPair, aesKey);
        return finalizeClaimTransaction(warningTx, claimTx, mySignature);
    }

    private Transaction createUnsignedClaimTransaction(Transaction warningTx,
                                                       long nSequence,
                                                       Address payoutAddress,
                                                       long miningFee)
            throws AddressFormatException, TransactionVerificationException {

        Transaction claimTx = new Transaction(params);

        TransactionOutput warningTxOutput = warningTx.getOutput(0);
        claimTx.addInput(warningTxOutput);
        claimTx.getInput(0).setSequenceNumber(nSequence);

        Coin amountWithoutMiningFee = warningTxOutput.getValue()
                .subtract(Coin.valueOf(miningFee));
        claimTx.addOutput(amountWithoutMiningFee, payoutAddress);

        WalletService.printTx("Unsigned claimTx", claimTx);
        WalletService.verifyTransaction(claimTx);
        return claimTx;
    }

    private byte[] signClaimTransaction(Transaction claimTx,
                                        Transaction warningTx,
                                        DeterministicKey myMultiSigKeyPair,
                                        KeyParameter aesKey)
            throws AddressFormatException, TransactionVerificationException {

        TransactionOutput warningTxPayoutOutput = warningTx.getOutput(0);
        Script redeemScript = warningTxPayoutOutput.getScriptPubKey();
        Coin redirectionTxInputValue = warningTxPayoutOutput.getValue();

        Sha256Hash sigHash = claimTx.hashForWitnessSignature(0, redeemScript,
                redirectionTxInputValue, Transaction.SigHash.ALL, false);

        checkNotNull(myMultiSigKeyPair, "myMultiSigKeyPair must not be null");
        if (myMultiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }

        ECKey.ECDSASignature mySignature = myMultiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();
        WalletService.printTx("claimTx for sig creation", claimTx);
        WalletService.verifyTransaction(claimTx);
        return mySignature.encodeToDER();
    }

    private Transaction finalizeClaimTransaction(Transaction warningTx,
                                                 Transaction claimTx,
                                                 byte[] mySignature)
            throws AddressFormatException, TransactionVerificationException {

        TransactionInput input = claimTx.getInput(0);
        input.setScriptSig(ScriptBuilder.createEmpty());

        Script redeemScript = createRedeemScript(mySignature);
        TransactionWitness witness = TransactionWitness.redeemP2WSH(redeemScript);
        input.setWitness(witness);

        WalletService.printTx("finalizeRedirectionTransaction", claimTx);
        WalletService.verifyTransaction(claimTx);

        Script scriptPubKey = warningTx.getOutput(0).getScriptPubKey();
        input.getScriptSig().correctlySpends(claimTx, 0, witness, input.getValue(), scriptPubKey, Script.ALL_VERIFY_FLAGS);
        return claimTx;
    }

    private Script createRedeemScript(byte[] mySignature) {
        return new ScriptBuilder()
                .data(mySignature)
                .number(0)
                .build();
    }
}

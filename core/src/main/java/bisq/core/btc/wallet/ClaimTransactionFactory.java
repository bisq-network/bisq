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
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import org.bouncycastle.crypto.params.KeyParameter;

import static com.google.common.base.Preconditions.checkArgument;

public class ClaimTransactionFactory {
    private final NetworkParameters params;

    public ClaimTransactionFactory(NetworkParameters params) {
        this.params = params;
    }

    public Transaction createSignedClaimTransaction(TransactionOutput warningTxOutput,
                                                    boolean isBuyer,
                                                    long claimDelay,
                                                    Address payoutAddress,
                                                    long miningFee,
                                                    byte[] peersMultiSigPubKey,
                                                    DeterministicKey myMultiSigKeyPair,
                                                    KeyParameter aesKey)
            throws AddressFormatException, TransactionVerificationException {

        Transaction claimTx = createUnsignedClaimTransaction(warningTxOutput, claimDelay, payoutAddress, miningFee);
        byte[] buyerPubKey = isBuyer ? myMultiSigKeyPair.getPubKey() : peersMultiSigPubKey;
        byte[] sellerPubKey = isBuyer ? peersMultiSigPubKey : myMultiSigKeyPair.getPubKey();
        ECKey.ECDSASignature mySignature = signClaimTransaction(claimTx, warningTxOutput, isBuyer, claimDelay,
                buyerPubKey, sellerPubKey, myMultiSigKeyPair, aesKey);
        return finalizeClaimTransaction(claimTx, warningTxOutput, isBuyer, claimDelay, buyerPubKey, sellerPubKey, mySignature);
    }

    private Transaction createUnsignedClaimTransaction(TransactionOutput warningTxOutput,
                                                       long claimDelay,
                                                       Address payoutAddress,
                                                       long miningFee)
            throws AddressFormatException, TransactionVerificationException {

        Transaction claimTx = new Transaction(params);
        claimTx.setVersion(2); // needed to enable relative lock time

        claimTx.addInput(warningTxOutput);
        claimTx.getInput(0).setSequenceNumber(claimDelay);

        Coin amountWithoutMiningFee = warningTxOutput.getValue().subtract(Coin.valueOf(miningFee));
        claimTx.addOutput(amountWithoutMiningFee, payoutAddress);

        WalletService.printTx("Unsigned claimTx", claimTx);
        WalletService.verifyTransaction(claimTx);
        return claimTx;
    }

    private ECKey.ECDSASignature signClaimTransaction(Transaction claimTx,
                                                      TransactionOutput warningTxOutput,
                                                      boolean isBuyer,
                                                      long claimDelay,
                                                      byte[] buyerPubKey,
                                                      byte[] sellerPubKey,
                                                      DeterministicKey myMultiSigKeyPair,
                                                      KeyParameter aesKey)
            throws TransactionVerificationException {

        Script redeemScript = WarningTransactionFactory.createRedeemScript(isBuyer, buyerPubKey, sellerPubKey, claimDelay);
        checkArgument(ScriptBuilder.createP2WSHOutputScript(redeemScript).equals(warningTxOutput.getScriptPubKey()),
                "Redeem script does not hash to expected ScriptPubKey");

        Coin claimTxInputValue = warningTxOutput.getValue();
        Sha256Hash sigHash = claimTx.hashForWitnessSignature(0, redeemScript, claimTxInputValue,
                Transaction.SigHash.ALL, false);

        ECKey.ECDSASignature mySignature = myMultiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();
        WalletService.printTx("claimTx for sig creation", claimTx);
        WalletService.verifyTransaction(claimTx);
        return mySignature;
    }

    private Transaction finalizeClaimTransaction(Transaction claimTx,
                                                 TransactionOutput warningTxOutput,
                                                 boolean isBuyer,
                                                 long claimDelay,
                                                 byte[] buyerPubKey,
                                                 byte[] sellerPubKey,
                                                 ECKey.ECDSASignature mySignature)
            throws TransactionVerificationException {

        Script redeemScript = WarningTransactionFactory.createRedeemScript(isBuyer, buyerPubKey, sellerPubKey, claimDelay);
        TransactionSignature myTxSig = new TransactionSignature(mySignature, Transaction.SigHash.ALL, false);

        TransactionInput input = claimTx.getInput(0);
        TransactionWitness witness = redeemP2WSH(redeemScript, myTxSig);
        input.setWitness(witness);

        WalletService.printTx("finalizeClaimTransaction", claimTx);
        WalletService.verifyTransaction(claimTx);

        Coin inputValue = warningTxOutput.getValue();
        Script scriptPubKey = warningTxOutput.getScriptPubKey();
        input.getScriptSig().correctlySpends(claimTx, 0, witness, inputValue, scriptPubKey, Script.ALL_VERIFY_FLAGS);
        return claimTx;
    }

    private static TransactionWitness redeemP2WSH(Script witnessScript, TransactionSignature mySignature) {
        var witness = new TransactionWitness(3);
        witness.setPush(0, mySignature.encodeToBitcoin());
        witness.setPush(1, new byte[]{});
        witness.setPush(2, witnessScript.getProgram());
        return witness;
    }
}

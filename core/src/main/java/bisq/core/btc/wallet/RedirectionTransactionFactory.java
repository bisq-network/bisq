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

import bisq.common.util.Tuple2;

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

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class RedirectionTransactionFactory {

    private final NetworkParameters params;

    public RedirectionTransactionFactory(NetworkParameters params) {
        this.params = params;
    }

    public Transaction createUnsignedRedirectionTransaction(TransactionOutput warningTxOutput,
                                                            List<Tuple2<Long, String>> receivers,
                                                            Tuple2<Long, String> feeBumpOutputAmountAndAddress)
            throws AddressFormatException, TransactionVerificationException {

        Transaction redirectionTx = new Transaction(params);
        redirectionTx.addInput(warningTxOutput);

        checkArgument(!receivers.isEmpty(), "receivers must not be empty");
        receivers.forEach(receiver -> redirectionTx.addOutput(Coin.valueOf(receiver.first), Address.fromString(params, receiver.second)));

        redirectionTx.addOutput(
                Coin.valueOf(feeBumpOutputAmountAndAddress.first),
                Address.fromString(params, feeBumpOutputAmountAndAddress.second)
        );

        WalletService.printTx("Unsigned redirectionTx", redirectionTx);
        WalletService.verifyTransaction(redirectionTx);

        return redirectionTx;
    }

    public byte[] signRedirectionTransaction(Transaction redirectionTx,
                                             TransactionOutput warningTxOutput,
                                             DeterministicKey myMultiSigKeyPair,
                                             KeyParameter aesKey)
            throws AddressFormatException, TransactionVerificationException {

        Script redeemScript = warningTxOutput.getScriptPubKey();
        Coin redirectionTxInputValue = warningTxOutput.getValue();

        Sha256Hash sigHash = redirectionTx.hashForWitnessSignature(0, redeemScript,
                redirectionTxInputValue, Transaction.SigHash.ALL, false);

        checkNotNull(myMultiSigKeyPair, "myMultiSigKeyPair must not be null");
        if (myMultiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }

        ECKey.ECDSASignature mySignature = myMultiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();
        WalletService.printTx("redirectionTx for sig creation", redirectionTx);
        WalletService.verifyTransaction(redirectionTx);
        return mySignature.encodeToDER();
    }

    public Transaction finalizeRedirectionTransaction(TransactionOutput warningTxOutput,
                                                      Transaction redirectionTx,
                                                      byte[] buyerSignature,
                                                      byte[] sellerSignature,
                                                      Coin inputValue)
            throws AddressFormatException, TransactionVerificationException {

        TransactionInput input = redirectionTx.getInput(0);
        input.setScriptSig(ScriptBuilder.createEmpty());

        Script redeemScript = createRedeemScript(buyerSignature, sellerSignature);
        TransactionWitness witness = TransactionWitness.redeemP2WSH(redeemScript);
        input.setWitness(witness);

        WalletService.printTx("finalizeRedirectionTransaction", redirectionTx);
        WalletService.verifyTransaction(redirectionTx);

        Script scriptPubKey = warningTxOutput.getScriptPubKey();
        input.getScriptSig().correctlySpends(redirectionTx, 0, witness, inputValue, scriptPubKey, Script.ALL_VERIFY_FLAGS);
        return redirectionTx;
    }

    private Script createRedeemScript(byte[] buyerSignature, byte[] sellerSignature) {
        return new ScriptBuilder()
                .number(0)
                .data(buyerSignature)
                .data(sellerSignature)
                .number(1)
                .build();
    }
}

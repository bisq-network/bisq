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
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import org.bouncycastle.crypto.params.KeyParameter;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.script.ScriptOpCodes.*;

public class WarningTransactionFactory {
    private final NetworkParameters params;

    public WarningTransactionFactory(NetworkParameters params) {
        this.params = params;
    }

    public Transaction createUnsignedWarningTransaction(boolean isBuyer,
                                                        Transaction depositTx,
                                                        long lockTime,
                                                        byte[] buyerPubKey,
                                                        byte[] sellerPubKey,
                                                        int claimDelay,
                                                        long miningFee,
                                                        Tuple2<Long, String> feeBumpOutputAmountAndAddress)
            throws TransactionVerificationException {

        Transaction warningTx = new Transaction(params);
        TradeWalletService.applyLockTime(lockTime, warningTx);

        TransactionOutput depositTxOutput = depositTx.getOutput(0);
        warningTx.addInput(depositTxOutput);

        Coin warningTxOutputCoin = depositTxOutput.getValue()
                .subtract(Coin.valueOf(miningFee))
                .subtract(Coin.valueOf(feeBumpOutputAmountAndAddress.first));
        Script outputScript = createOutputScript(isBuyer, buyerPubKey, sellerPubKey, claimDelay);
        warningTx.addOutput(warningTxOutputCoin, outputScript);

        warningTx.addOutput(
                Coin.valueOf(feeBumpOutputAmountAndAddress.first),
                Address.fromString(params, feeBumpOutputAmountAndAddress.second)
        );

        WalletService.printTx("Unsigned warningTx", warningTx);
        WalletService.verifyTransaction(warningTx);
        return warningTx;
    }

    public byte[] signWarningTransaction(Transaction warningTx,
                                         Transaction preparedDepositTx,
                                         DeterministicKey myMultiSigKeyPair,
                                         byte[] buyerPubKey,
                                         byte[] sellerPubKey,
                                         KeyParameter aesKey)
            throws AddressFormatException, TransactionVerificationException {

        Script redeemScript = TradeWalletService.get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        Coin warningTxInputValue = preparedDepositTx.getOutput(0).getValue();

        Sha256Hash sigHash = warningTx.hashForWitnessSignature(0, redeemScript,
                warningTxInputValue, Transaction.SigHash.ALL, false);

        checkNotNull(myMultiSigKeyPair, "myMultiSigKeyPair must not be null");
        if (myMultiSigKeyPair.isEncrypted()) {
            checkNotNull(aesKey);
        }

        ECKey.ECDSASignature mySignature = myMultiSigKeyPair.sign(sigHash, aesKey).toCanonicalised();
        WalletService.printTx("warningTx for sig creation", warningTx);
        WalletService.verifyTransaction(warningTx);
        return mySignature.encodeToDER();
    }

    public Transaction finalizeWarningTransaction(Transaction warningTx,
                                                  byte[] buyerPubKey,
                                                  byte[] sellerPubKey,
                                                  byte[] buyerSignature,
                                                  byte[] sellerSignature,
                                                  Coin inputValue)
            throws AddressFormatException, TransactionVerificationException, SignatureDecodeException {

        Script redeemScript = TradeWalletService.get2of2MultiSigRedeemScript(buyerPubKey, sellerPubKey);
        ECKey.ECDSASignature buyerECDSASignature = ECKey.ECDSASignature.decodeFromDER(buyerSignature);
        ECKey.ECDSASignature sellerECDSASignature = ECKey.ECDSASignature.decodeFromDER(sellerSignature);

        TransactionSignature buyerTxSig = new TransactionSignature(buyerECDSASignature, Transaction.SigHash.ALL, false);
        TransactionSignature sellerTxSig = new TransactionSignature(sellerECDSASignature, Transaction.SigHash.ALL, false);

        TransactionInput input = warningTx.getInput(0);
        input.setScriptSig(ScriptBuilder.createEmpty());
        TransactionWitness witness = TransactionWitness.redeemP2WSH(redeemScript, sellerTxSig, buyerTxSig);
        input.setWitness(witness);

        WalletService.printTx("finalizeWarningTransaction", warningTx);
        WalletService.verifyTransaction(warningTx);

        Script scriptPubKey = TradeWalletService.get2of2MultiSigOutputScript(buyerPubKey, sellerPubKey, false);
        input.getScriptSig().correctlySpends(warningTx, 0, witness, inputValue, scriptPubKey, Script.ALL_VERIFY_FLAGS);
        return warningTx;
    }

    private Script createOutputScript(boolean isBuyer, byte[] buyerPubKey, byte[] sellerPubKey, int claimDelay) {
        var scriptBuilder = new ScriptBuilder();
        scriptBuilder.op(OP_IF)
                .number(2)
                .data(buyerPubKey)
                .data(sellerPubKey)
                .number(2)
                .op(OP_CHECKMULTISIG);

        scriptBuilder.op(OP_ELSE)
                .number(claimDelay)
                .op(OP_CHECKSEQUENCEVERIFY)
                .op(OP_DROP)
                .data(isBuyer ? buyerPubKey : sellerPubKey)
                .op(OP_CHECKSIG);

        return scriptBuilder.op(OP_ENDIF)
                .build();
    }
}
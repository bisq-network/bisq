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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.signers.MissingSigResolutionSigner;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.wallet.DecryptingKeyBag;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.RedeemData;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

@Slf4j
public class BisqWallet extends Wallet {
    public BisqWallet(NetworkParameters params, KeyChainGroup keyChainGroup) {
        super(params, keyChainGroup);
    }

    // Largely copied from super, but modified to supply instances of LowRSigningKey to the tx signers,
    // instead of deterministic keys, so that low-R signatures always result when using the SendRequest
    // API. This likely breaks the keychain marrying functionality of bitcoinj, used for multisig inputs,
    // but we don't use that anywhere in Bisq.
    @Override
    public void signTransaction(SendRequest req) {
        lock.lock();
        try {
            Transaction tx = req.tx;
            List<TransactionInput> inputs = tx.getInputs();
            List<TransactionOutput> outputs = tx.getOutputs();
            checkState(inputs.size() > 0);
            checkState(outputs.size() > 0);

            KeyBag maybeDecryptingKeyBag = new LowRSigningKeyBag(new DecryptingKeyBag(this, req.aesKey));

            int numInputs = tx.getInputs().size();
            for (int i = 0; i < numInputs; i++) {
                TransactionInput txIn = tx.getInput(i);
                TransactionOutput connectedOutput = txIn.getConnectedOutput();
                if (connectedOutput == null) {
                    // Missing connected output, assuming already signed.
                    continue;
                }
                Script scriptPubKey = connectedOutput.getScriptPubKey();

                try {
                    // We assume if its already signed, its hopefully got a SIGHASH type that will not invalidate when
                    // we sign missing pieces (to check this would require either assuming any signatures are signing
                    // standard output types or a way to get processed signatures out of script execution)
                    txIn.getScriptSig().correctlySpends(tx, i, txIn.getWitness(), connectedOutput.getValue(),
                            connectedOutput.getScriptPubKey(), Script.ALL_VERIFY_FLAGS);
                    log.warn("Input {} already correctly spends output, assuming SIGHASH type used will be safe and skipping signing.", i);
                    continue;
                } catch (ScriptException e) {
                    log.debug("Input contained an incorrect signature", e);
                    // Expected.
                }

                RedeemData redeemData = txIn.getConnectedRedeemData(maybeDecryptingKeyBag);
                checkNotNull(redeemData, "Transaction exists in wallet that we cannot redeem: %s", txIn.getOutpoint().getHash());
                txIn.setScriptSig(scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript));
                txIn.setWitness(scriptPubKey.createEmptyWitness(redeemData.keys.get(0)));
            }

            TransactionSigner.ProposedTransaction proposal = new TransactionSigner.ProposedTransaction(tx);
            for (TransactionSigner signer : getTransactionSigners()) {
                if (!signer.signInputs(proposal, maybeDecryptingKeyBag))
                    log.info("{} returned false for the tx", signer.getClass().getName());
            }

            // resolve missing sigs if any
            new MissingSigResolutionSigner(req.missingSigsMode).signInputs(proposal, maybeDecryptingKeyBag);
        } finally {
            lock.unlock();
        }
    }
}

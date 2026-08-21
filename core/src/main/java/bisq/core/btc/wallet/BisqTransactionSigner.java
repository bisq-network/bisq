package bisq.core.btc.wallet;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.signers.MissingSigResolutionSigner;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.wallet.DecryptingKeyBag;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.RedeemData;
import org.bitcoinj.wallet.Wallet;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BisqTransactionSigner {
    public static void sign(Wallet wallet, Transaction tx, int inputOffset) {
        var localOffsetTransactionSigner = new LocalOffsetTransactionSigner(inputOffset);
        KeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(wallet, null);

        int numInputs = tx.getInputs().size();
        for (int i = 0; i < numInputs; i++) {
            if (i < inputOffset) {
                continue;
            }

            TransactionInput txIn = tx.getInput(i);
            TransactionOutput connectedOutput = txIn.getConnectedOutput();
            Objects.requireNonNull(connectedOutput, "Connected output of transaction input #" + i + " is null");

            Script scriptPubKey = connectedOutput.getScriptPubKey();

            RedeemData redeemData = txIn.getConnectedRedeemData(maybeDecryptingKeyBag);
            Objects.requireNonNull(redeemData, "Transaction exists in wallet that we cannot redeem: " + txIn.getOutpoint().getHash());
            txIn.setScriptSig(scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript));
            txIn.setWitness(scriptPubKey.createEmptyWitness(redeemData.keys.get(0)));
        }

        TransactionSigner.ProposedTransaction proposal = new TransactionSigner.ProposedTransaction(tx);
        if (!localOffsetTransactionSigner.signInputs(proposal, maybeDecryptingKeyBag)) {
            log.info("{} returned false for the tx", localOffsetTransactionSigner.getClass().getName());
        }

        // resolve missing sigs if any
        new MissingSigResolutionSigner(Wallet.MissingSigsMode.THROW).signInputs(proposal, maybeDecryptingKeyBag);
    }
}

/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc;

import io.bitsquare.btc.exceptions.SigningException;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.inject.internal.util.$Preconditions.*;

// 
/*

    Deposit tx:
        To keep the multiple partial deposit tx consistent with the final deposit tx used for publishing 
        we use always use offerers in/outputs first then takers in/outputs.
        
    IN[0] offerer (mandatory) e.g. 0.1 BTC
    IN[...] optional additional offerer inputs (normally never used as we pay from trade fee tx and always have 1 output there)
    IN[...] taker (mandatory) e.g. 1.1001 BTC
    IN[...] optional additional taker inputs (normally never used as we pay from trade fee tx and always have 1 output there)
    OUT[0] Multisig output (include tx fee for payout tx) e.g. 1.2001
    OUT[1] offerer change (normally never used as we pay from trade fee tx and always have 1 output there)
    OUT[...] optional additional offerer outputs (supported but no use case yet for that)
    OUT[...] taker change (normally never used as we pay from trade fee tx and always have 1 output there)
    OUT[...] optional additional taker outputs (supported but no use case yet for that)
    FEE tx fee 0.0001 BTC
    
    Payout tx:
    IN[0] Multisig output form deposit Tx (signed by offerer and trader)
    OUT[0] Offerer payout address
    OUT[1] Taker payout address
    
 */
public class TradeService {
    private static final Logger log = LoggerFactory.getLogger(TradeService.class);

    private final NetworkParameters params;
    private final Wallet wallet;
    private WalletAppKit walletAppKit;
    private FeePolicy feePolicy;

    public TradeService(NetworkParameters params, Wallet wallet, WalletAppKit walletAppKit, FeePolicy feePolicy) {
        this.params = params;
        this.wallet = wallet;
        this.walletAppKit = walletAppKit;
        this.feePolicy = feePolicy;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction createOfferFeeTx(AddressEntry addressEntry) throws InsufficientMoneyException {
        Transaction createOfferFeeTx = new Transaction(params);
        Coin fee = FeePolicy.CREATE_OFFER_FEE.subtract(FeePolicy.TX_FEE);
        createOfferFeeTx.addOutput(fee, feePolicy.getAddressForCreateOfferFee());
        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(createOfferFeeTx);
        sendRequest.shuffleOutputs = false;
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, addressEntry, true);
        sendRequest.changeAddress = addressEntry.getAddress();
        wallet.completeTx(sendRequest);
        printTxWithInputs("createOfferFeeTx", createOfferFeeTx);
        return createOfferFeeTx;
    }

    public void broadcastCreateOfferFeeTx(Transaction createOfferFeeTx, FutureCallback<Transaction> callback) {
        ListenableFuture<Transaction> future = walletAppKit.peerGroup().broadcastTransaction(createOfferFeeTx);
        Futures.addCallback(future, callback);
    }

    public void payTakeOfferFee(AddressEntry addressEntry, FutureCallback<Transaction> callback) throws InsufficientMoneyException {
        Transaction takeOfferFeeTx = new Transaction(params);
        Coin fee = FeePolicy.TAKE_OFFER_FEE.subtract(FeePolicy.TX_FEE);
        takeOfferFeeTx.addOutput(fee, feePolicy.getAddressForTakeOfferFee());

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(takeOfferFeeTx);
        sendRequest.shuffleOutputs = false;
        // we allow spending of unconfirmed takeOfferFeeTx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, addressEntry, true);
        sendRequest.changeAddress = addressEntry.getAddress();
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printTxWithInputs("takeOfferFeeTx", takeOfferFeeTx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TransactionDataResult offererCreatesDepositTxInputs(Coin inputAmount, AddressEntry addressInfo) throws InsufficientMoneyException,
            TransactionVerificationException, WalletException {

        // We pay the tx fee 2 times to the deposit tx:
        // 1. Will be spent when publishing the deposit tx (paid by offerer)
        // 2. Will be added to the MS amount, so when publishing the payout tx the fee is already there and the outputs are not changed by fee reduction
        // The fee for the payout will be paid by the taker.

        // inputAmount includes the tx fee. So we subtract the fee to get the dummyOutputAmount.
        Coin dummyOutputAmount = inputAmount.subtract(FeePolicy.TX_FEE);

        Transaction dummyTX = new Transaction(params);
        // The output is just used to get the right inputs and change outputs, so we use an anonymous ECKey, as it will never be used for anything.
        // We don't care about fee calculation differences between the real tx and that dummy tx as we use a static tx fee.
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTX, dummyOutputAmount, new ECKey().toAddress(params));
        dummyTX.addOutput(dummyOutput);

        // Fin the needed inputs to pay the output, optional add change output.
        // Normally only 1 input and no change output is used, but we support multiple inputs and outputs. Our spending transaction output is from the create
        // offer fee payment. In future changes (in case of no offer fee) multiple inputs might become used.
        addAvailableInputsAndChangeOutputs(dummyTX, addressInfo);

        // The completeTx() call signs the input, but we don't want to pass over signed tx inputs
        // But to be safe and to support future changes (in case of no offer fee) we handle potential multiple inputs
        removeSignatures(dummyTX);

        verifyTransaction(dummyTX);
        checkWalletConsistency();

        // The created tx looks like:
         /*
        IN[0]  any input > inputAmount (including tx fee) (unsigned)
        IN[1...n] optional inputs supported, but currently there is just 1 input (unsigned)
        OUT[0] dummyOutputAmount (inputAmount - tx fee)
        OUT[1] Optional Change = inputAmount - dummyOutputAmount - tx fee
        OUT[2...n] optional more outputs are supported, but currently there is just max. 1 optional change output
         */

        printTxWithInputs("dummyTX", dummyTX);

        List<TransactionOutput> connectedOutputsForAllInputs = new ArrayList<>();
        for (TransactionInput input : dummyTX.getInputs()) {
            connectedOutputsForAllInputs.add(input.getConnectedOutput());
        }

        // Only save offerer outputs, the MS output is ignored
        List<TransactionOutput> outputs = new ArrayList<>();
        for (TransactionOutput output : dummyTX.getOutputs()) {
            if (output.equals(dummyOutput))
                continue;
            outputs.add(output);
        }

        return new TransactionDataResult(connectedOutputsForAllInputs, outputs);
    }

    public TransactionDataResult takerCreatesAndSignsDepositTx(Coin takerInputAmount,
                                                               Coin msOutputAmount,
                                                               List<TransactionOutput> offererConnectedOutputsForAllInputs,
                                                               List<TransactionOutput> offererOutputs,
                                                               AddressEntry addressInfo,
                                                               byte[] offererPubKey,
                                                               byte[] takerPubKey,
                                                               byte[] arbitratorPubKey) throws InsufficientMoneyException, SigningException,
            TransactionVerificationException, WalletException {

        checkArgument(offererConnectedOutputsForAllInputs.size() > 0);

        // First we construct a dummy TX to get the inputs and outputs we want to use for the real deposit tx. Same as in first step at offerer.
        Transaction dummyTx = new Transaction(params);
        Coin dummyOutputAmount = takerInputAmount.subtract(FeePolicy.TX_FEE);
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTx, dummyOutputAmount, new ECKey().toAddress(params));
        dummyTx.addOutput(dummyOutput);
        addAvailableInputsAndChangeOutputs(dummyTx, addressInfo);
        List<TransactionInput> takerInputs = dummyTx.getInputs();
        List<TransactionOutput> takerOutputs = new ArrayList<>();
        // we store optional change outputs (ignoring dummyOutput)
        for (int i = 1; i < dummyTx.getOutputs().size(); i++) {
            takerOutputs.add(dummyTx.getOutput(i));
        }

        // Now we construct real deposit tx
        Transaction depositTx = new Transaction(params);

        // Add offerer inputs (normally its just 1 input)
        for (TransactionOutput connectedOutputForInput : offererConnectedOutputsForAllInputs) {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, connectedOutputForInput.getIndex(), connectedOutputForInput.getParentTransaction());
            TransactionInput transactionInput = new TransactionInput(params, depositTx, new byte[]{}, outPoint, connectedOutputForInput.getValue());
            depositTx.addInput(transactionInput);
        }

        // Add taker inputs
        List<TransactionOutput> connectedOutputsForAllTakerInputs = new ArrayList<>();
        for (TransactionInput input : takerInputs) {
            depositTx.addInput(input);
            connectedOutputsForAllTakerInputs.add(input.getConnectedOutput());
        }

        // Add MultiSig output
        Script multiSigOutputScript = getMultiSigOutputScript(offererPubKey, takerPubKey, arbitratorPubKey);
        // Tx fee for deposit tx will be paid by offerer.
        TransactionOutput msOutput = new TransactionOutput(params, depositTx, msOutputAmount, multiSigOutputScript.getProgram());
        depositTx.addOutput(msOutput);

        // Add optional offerer outputs 
        for (TransactionOutput output : offererOutputs) {
            depositTx.addOutput(output);
        }

        Coin takersSpendingAmount = Coin.ZERO;

        // Add optional taker outputs 
        for (TransactionOutput output : takerOutputs) {
            depositTx.addOutput(output);

            // subtract change amount
            takersSpendingAmount = takersSpendingAmount.subtract(output.getValue());
        }

        // Sign inputs
        for (int i = offererConnectedOutputsForAllInputs.size(); i < depositTx.getInputs().size(); i++) {
            TransactionInput input = depositTx.getInput(i);
            signInput(depositTx, input, i);
            checkScriptSig(depositTx, input, i);

            // add up spending amount
            takersSpendingAmount = takersSpendingAmount.add(input.getConnectedOutput().getValue());
        }

        if (takerInputAmount.compareTo(takersSpendingAmount) != 0)
            throw new TransactionVerificationException("Takers input amount does not match required value.");

        verifyTransaction(depositTx);
        checkWalletConsistency();

        printTxWithInputs("depositTx", depositTx);
        return new TransactionDataResult(depositTx, connectedOutputsForAllTakerInputs, takerOutputs);
    }

    public void offererSignsAndPublishTx(Transaction takersDepositTx,
                                         List<TransactionOutput> offererConnectedOutputsForAllInputs,
                                         List<TransactionOutput> takerConnectedOutputsForAllInputs,
                                         List<TransactionOutput> offererOutputs,
                                         Coin offererInputAmount,
                                         byte[] offererPubKey,
                                         byte[] takerPubKey,
                                         byte[] arbitratorPubKey,
                                         FutureCallback<Transaction> callback) throws SigningException, TransactionVerificationException, WalletException {

        checkArgument(offererConnectedOutputsForAllInputs.size() > 0);
        checkArgument(takerConnectedOutputsForAllInputs.size() > 0);

        // Check if takers Multisig script is identical to mine
        Script multiSigOutputScript = getMultiSigOutputScript(offererPubKey, takerPubKey, arbitratorPubKey);
        if (!takersDepositTx.getOutput(0).getScriptPubKey().equals(multiSigOutputScript))
            throw new TransactionVerificationException("Takers multiSigOutputScript does not match to my multiSigOutputScript");

        // The outpoints are not available from the serialized takersDepositTx, so we cannot use that tx directly, but we use it to construct a new depositTx
        Transaction depositTx = new Transaction(params);

        // Add offerer inputs
        Coin offererSpendingAmount = Coin.ZERO;
        for (TransactionOutput connectedOutputForInput : offererConnectedOutputsForAllInputs) {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, connectedOutputForInput.getIndex(), connectedOutputForInput.getParentTransaction());
            TransactionInput input = new TransactionInput(params, depositTx, new byte[]{}, outPoint, connectedOutputForInput.getValue());
            depositTx.addInput(input);

            // add up spending amount
            offererSpendingAmount = offererSpendingAmount.add(input.getConnectedOutput().getValue());
        }

        // Add taker inputs and apply signature
        List<TransactionInput> takerInputs = new ArrayList<>();
        for (TransactionOutput connectedOutputForInput : takerConnectedOutputsForAllInputs) {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, connectedOutputForInput.getIndex(), connectedOutputForInput.getParentTransaction());

            // We grab the signature from the takersDepositTx and apply it to the new tx input
            TransactionInput takerInput = takersDepositTx.getInputs().get(offererConnectedOutputsForAllInputs.size());
            byte[] scriptProgram = takerInput.getScriptSig().getProgram();
            if (scriptProgram.length == 0)
                throw new TransactionVerificationException("Inputs from taker not singed.");

            TransactionInput transactionInput = new TransactionInput(params, depositTx, scriptProgram, outPoint, connectedOutputForInput.getValue());
            takerInputs.add(transactionInput);
            depositTx.addInput(transactionInput);
        }

        // Add all outputs from takersDepositTx to depositTx
        for (TransactionOutput output : takersDepositTx.getOutputs()) {
            depositTx.addOutput(output);
        }

        // Sign inputs
        for (int i = 0; i < offererConnectedOutputsForAllInputs.size(); i++) {
            TransactionInput input = depositTx.getInput(i);
            signInput(depositTx, input, i);
            checkScriptSig(depositTx, input, i);
        }

        // subtract change amount
        for (int i = 1; i < offererOutputs.size() + 1; i++) {
            offererSpendingAmount = offererSpendingAmount.subtract(depositTx.getOutput(i).getValue());
        }

        if (offererInputAmount.compareTo(offererSpendingAmount) != 0)
            throw new TransactionVerificationException("Offerers input amount does not match required value.");

        verifyTransaction(depositTx);
        checkWalletConsistency();

        // Broadcast depositTx
        printTxWithInputs("depositTx", depositTx);
        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(depositTx);
        Futures.addCallback(broadcastComplete, callback);
    }

    public Transaction takerCommitsDepositTx(Transaction depositTx) throws WalletException {
        // We need to recreate the tx we get a null pointer otherwise
        depositTx = new Transaction(params, depositTx.bitcoinSerialize());

        try {
            wallet.receivePending(depositTx, null, true);
        } catch (Throwable t) {
            log.error(t.getMessage());
            t.printStackTrace();
            throw new WalletException(t);
        }

        return depositTx;
    }

    public TransactionDataResult offererCreatesAndSignsPayoutTx(Transaction depositTx,
                                                                Coin offererPayoutAmount,
                                                                Coin takerPayoutAmount,
                                                                String takerAddressString,
                                                                AddressEntry addressEntry)
            throws AddressFormatException, TransactionVerificationException, WalletException {

        Transaction payoutTx = createPayoutTx(depositTx, offererPayoutAmount, takerPayoutAmount, addressEntry.getAddressString(), takerAddressString);

        TransactionInput input = payoutTx.getInput(0);
        TransactionOutput multiSigOutput = input.getConnectedOutput();
        Script multiSigScript = multiSigOutput.getScriptPubKey();
        Sha256Hash sigHash = payoutTx.hashForSignature(0, multiSigScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature offererSignature = addressEntry.getKeyPair().sign(sigHash);

        verifyTransaction(payoutTx);

        return new TransactionDataResult(payoutTx, offererSignature);
    }

    public void takerSignsAndPublishPayoutTx(Transaction depositTx,
                                             ECKey.ECDSASignature offererSignature,
                                             Coin offererPayoutAmount,
                                             Coin takerPayoutAmount,
                                             String offererAddressString,
                                             AddressEntry addressEntry,
                                             FutureCallback<Transaction> callback)
            throws AddressFormatException, TransactionVerificationException, WalletException {

        Transaction payoutTx = createPayoutTx(depositTx, offererPayoutAmount, takerPayoutAmount, offererAddressString, addressEntry.getAddressString());

        TransactionInput input = payoutTx.getInput(0);
        TransactionOutput multiSigOutput = input.getConnectedOutput();
        Script multiSigScript = multiSigOutput.getScriptPubKey();
        Sha256Hash sigHash = payoutTx.hashForSignature(0, multiSigScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature takerSignature = addressEntry.getKeyPair().sign(sigHash);
        TransactionSignature takerTxSig = new TransactionSignature(takerSignature, Transaction.SigHash.ALL, false);
        TransactionSignature offererTxSig = new TransactionSignature(offererSignature, Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createMultiSigInputScript(ImmutableList.of(offererTxSig, takerTxSig));
        input.setScriptSig(inputScript);

        verifyTransaction(payoutTx);
        checkWalletConsistency();
        checkScriptSig(payoutTx, input, 0);
        input.verify(multiSigOutput);

        printTxWithInputs("payoutTx", payoutTx);
        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(payoutTx);
        Futures.addCallback(broadcastComplete, callback);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Script getMultiSigOutputScript(byte[] offererPubKey, byte[] takerPubKey, byte[] arbitratorPubKey) {
        ECKey offererKey = ECKey.fromPublicOnly(offererPubKey);
        ECKey takerKey = ECKey.fromPublicOnly(takerPubKey);
        ECKey arbitratorKey = ECKey.fromPublicOnly(arbitratorPubKey);

        List<ECKey> keys = ImmutableList.of(offererKey, takerKey, arbitratorKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }

    private Transaction createPayoutTx(Transaction depositTx, Coin offererPayoutAmount, Coin takerPayoutAmount,
                                       String offererAddressString, String takerAddressString) throws AddressFormatException {

        TransactionOutput multiSigOutput = depositTx.getOutput(0);
        Transaction tx = new Transaction(params);
        tx.addInput(multiSigOutput);
        tx.addOutput(offererPayoutAmount, new Address(params, offererAddressString));
        tx.addOutput(takerPayoutAmount, new Address(params, takerAddressString));
        return tx;
    }

    public static void printTxWithInputs(String tracePrefix, Transaction tx) {
        log.trace(tracePrefix + ": " + tx.toString());
        for (TransactionInput input : tx.getInputs()) {
            if (input.getConnectedOutput() != null)
                log.trace(tracePrefix + " input value: " + input.getConnectedOutput().getValue().toFriendlyString());
            else
                log.trace(tracePrefix + ": Transaction already has inputs but we don't have the connected outputs, so we don't know the value.");
        }
    }

    private void checkWalletConsistency() throws WalletException {
        try {
            log.trace("Check if wallet is consistent before commit.");
            checkState(wallet.isConsistent());
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new WalletException(t);
        }
    }

    private void verifyTransaction(Transaction transaction) throws TransactionVerificationException {
        try {
            log.trace("Verify transaction");
            transaction.verify();
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new TransactionVerificationException(t);
        }
    }

    private void signInput(Transaction transaction, TransactionInput input, int inputIndex) throws SigningException, TransactionVerificationException {
        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        ECKey sigKey = input.getOutpoint().getConnectedKey(wallet);
        Sha256Hash hash = transaction.hashForSignature(inputIndex, scriptPubKey, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature signature = sigKey.sign(hash);
        TransactionSignature txSig = new TransactionSignature(signature, Transaction.SigHash.ALL, false);
        if (scriptPubKey.isSentToRawPubKey()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig));
        }
        else if (scriptPubKey.isSentToAddress()) {
            input.setScriptSig(ScriptBuilder.createInputScript(txSig, sigKey));
        }
        else {
            throw new SigningException("Don't know how to sign for this kind of scriptPubKey: " + scriptPubKey);
        }
    }

    private void checkScriptSig(Transaction transaction, TransactionInput input, int inputIndex) throws TransactionVerificationException {
        try {
            log.trace("Verifies that this script (interpreted as a scriptSig) correctly spends the given scriptPubKey. Check input at index: " + inputIndex);
            input.getScriptSig().correctlySpends(transaction, inputIndex, input.getConnectedOutput().getScriptPubKey());
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new TransactionVerificationException(t);
        }
    }

    /*private void checkScriptSigForAllInputs(Transaction transaction) throws TransactionVerificationException {
        int inputIndex = 0;
        for (TransactionInput input : transaction.getInputs()) {
            checkScriptSig(transaction, input, inputIndex);
            inputIndex++;
        }
    }*/

    private void removeSignatures(Transaction transaction) throws InsufficientMoneyException {
        for (TransactionInput input : transaction.getInputs()) {
            input.setScriptSig(new Script(new byte[]{}));
        }
    }

    private void addAvailableInputsAndChangeOutputs(Transaction transaction, AddressEntry addressEntry) throws WalletException {
        try {
            // Lets let the framework do the work to find the right inputs
            Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(transaction);
            sendRequest.shuffleOutputs = false;
            // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to wait for 1 confirmation)
            sendRequest.coinSelector = new AddressBasedCoinSelector(params, addressEntry, true);
            sendRequest.changeAddress = addressEntry.getAddress();
            // With the usage of completeTx() we get all the work done with fee calculation, validation and coin selection.
            // We don't commit that tx to the wallet as it will be changed later and it's not signed yet.
            // So it will not change the wallet balance.
            wallet.completeTx(sendRequest);
        } catch (Throwable t) {
            throw new WalletException(t);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    public class TransactionDataResult {
        private List<TransactionOutput> connectedOutputsForAllInputs;
        private List<TransactionOutput> outputs;
        private Transaction depositTx;


        private Transaction payoutTx;
        private ECKey.ECDSASignature offererSignature;

        public TransactionDataResult(List<TransactionOutput> connectedOutputsForAllInputs, List<TransactionOutput> outputs) {
            this.connectedOutputsForAllInputs = connectedOutputsForAllInputs;
            this.outputs = outputs;
        }

        public TransactionDataResult(Transaction depositTx, List<TransactionOutput> connectedOutputsForAllInputs, List<TransactionOutput> outputs) {
            this.depositTx = depositTx;
            this.connectedOutputsForAllInputs = connectedOutputsForAllInputs;
            this.outputs = outputs;
        }

        public TransactionDataResult(Transaction payoutTx, ECKey.ECDSASignature offererSignature) {

            this.payoutTx = payoutTx;
            this.offererSignature = offererSignature;
        }

        public List<TransactionOutput> getOutputs() {
            return outputs;
        }

        public List<TransactionOutput> getConnectedOutputsForAllInputs() {
            return connectedOutputsForAllInputs;
        }

        public Transaction getDepositTx() {
            return depositTx;
        }

        public Transaction getPayoutTx() {
            return payoutTx;
        }

        public ECKey.ECDSASignature getOffererSignature() {
            return offererSignature;
        }
    }
}

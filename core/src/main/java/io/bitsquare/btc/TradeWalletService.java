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
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
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
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.inject.internal.util.$Preconditions.*;

// TradeService handles all trade relevant transactions as a delegate for WalletService
/*

    Deposit tx:
        To keep the multiple partial deposit tx consistent with the final deposit tx used for publishing 
        we use always use buyers in/outputs first then sellers in/outputs.
        
    IN[0] buyer (mandatory) e.g. 0.1 BTC
    IN[...] optional additional buyer inputs (normally never used as we pay from trade fee tx and always have 1 output there)
    IN[...] seller (mandatory) e.g. 1.1001 BTC
    IN[...] optional additional seller inputs (normally never used as we pay from trade fee tx and always have 1 output there)
    OUT[0] Multisig output (include tx fee for payout tx) e.g. 1.2001
    OUT[1] buyer change (normally never used as we pay from trade fee tx and always have 1 output there)
    OUT[...] optional additional buyer outputs (supported but no use case yet for that)
    OUT[...] seller change (normally never used as we pay from trade fee tx and always have 1 output there)
    OUT[...] optional additional seller outputs (supported but no use case yet for that)
    FEE tx fee 0.0001 BTC
    
    Payout tx:
    IN[0] Multisig output form deposit Tx (signed by buyer and trader)
    OUT[0] Buyer payout address
    OUT[1] Seller payout address
    
 */
public class TradeWalletService {
    private static final Logger log = LoggerFactory.getLogger(TradeWalletService.class);

    private final NetworkParameters params;
    private Wallet wallet;
    private WalletAppKit walletAppKit;
    private final FeePolicy feePolicy;

    @Inject
    public TradeWalletService(BitcoinNetwork bitcoinNetwork, FeePolicy feePolicy) {
        this.params = bitcoinNetwork.getParameters();
        this.feePolicy = feePolicy;
    }

    public void setWalletAppKit(WalletAppKit walletAppKit) {
        this.walletAppKit = walletAppKit;
        wallet = walletAppKit.wallet();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction createOfferFeeTx(AddressEntry buyerAddressEntry) throws InsufficientMoneyException {
        Transaction createOfferFeeTx = new Transaction(params);
        Coin fee = FeePolicy.CREATE_OFFER_FEE.subtract(FeePolicy.TX_FEE);
        createOfferFeeTx.addOutput(fee, feePolicy.getAddressForCreateOfferFee());
        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(createOfferFeeTx);
        sendRequest.shuffleOutputs = false;
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, buyerAddressEntry, true);
        sendRequest.changeAddress = buyerAddressEntry.getAddress();
        wallet.completeTx(sendRequest);
        printTxWithInputs("createOfferFeeTx", createOfferFeeTx);
        return createOfferFeeTx;
    }

    public void broadcastCreateOfferFeeTx(Transaction createOfferFeeTx, FutureCallback<Transaction> callback) {
        ListenableFuture<Transaction> future = walletAppKit.peerGroup().broadcastTransaction(createOfferFeeTx).future();
        Futures.addCallback(future, callback);
    }

    public Transaction createTakeOfferFeeTx(AddressEntry sellerAddressEntry) throws InsufficientMoneyException {
        Transaction takeOfferFeeTx = new Transaction(params);
        Coin fee = FeePolicy.TAKE_OFFER_FEE.subtract(FeePolicy.TX_FEE);
        takeOfferFeeTx.addOutput(fee, feePolicy.getAddressForTakeOfferFee());

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(takeOfferFeeTx);
        sendRequest.shuffleOutputs = false;
        // we allow spending of unconfirmed takeOfferFeeTx (double spend risk is low and usability would suffer if we need to
        // wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(params, sellerAddressEntry, true);
        sendRequest.changeAddress = sellerAddressEntry.getAddress();
        wallet.completeTx(sendRequest);
        printTxWithInputs("takeOfferFeeTx", takeOfferFeeTx);
        return takeOfferFeeTx;
    }

    public void broadcastTakeOfferFeeTx(Transaction takeOfferFeeTx, FutureCallback<Transaction> callback) {
        ListenableFuture<Transaction> future = walletAppKit.peerGroup().broadcastTransaction(takeOfferFeeTx).future();
        Futures.addCallback(future, callback);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Coin getBalance(List<TransactionOutput> transactionOutputs, Address address) {
        Coin balance = Coin.ZERO;
        for (TransactionOutput transactionOutput : transactionOutputs) {
            if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
                if (addressOutput.equals(address))
                    balance = balance.add(transactionOutput.getValue());
            }
        }
        return balance;
    }


    public Result createDepositTxInputs(Coin buyerInputAmount, AddressEntry buyerAddressEntry) throws
            TransactionVerificationException, WalletException {
        log.trace("createDepositTxInputs called");
        log.trace("buyerInputAmount " + buyerInputAmount.toFriendlyString());
        log.trace("buyerAddressEntry " + buyerAddressEntry.toString());

        Coin balance = getBalance(wallet.calculateAllSpendCandidates(true), buyerAddressEntry.getAddress());
        log.trace("balance " + balance.toFriendlyString());

        // We pay the tx fee 2 times to the deposit tx:
        // 1. Will be spent when publishing the deposit tx (paid by buyer)
        // 2. Will be added to the MS amount, so when publishing the payout tx the fee is already there and the outputs are not changed by fee reduction
        // The fee for the payout will be paid by the seller.

        // buyerInputAmount includes the tx fee. So we subtract the fee to get the dummyOutputAmount.
        Coin dummyOutputAmount = buyerInputAmount.subtract(FeePolicy.TX_FEE);

        Transaction dummyTX = new Transaction(params);
        // The output is just used to get the right inputs and change outputs, so we use an anonymous ECKey, as it will never be used for anything.
        // We don't care about fee calculation differences between the real tx and that dummy tx as we use a static tx fee.
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTX, dummyOutputAmount, new ECKey().toAddress(params));
        dummyTX.addOutput(dummyOutput);

        // Fin the needed inputs to pay the output, optional add change output.
        // Normally only 1 input and no change output is used, but we support multiple inputs and outputs. Our spending transaction output is from the create
        // offer fee payment. In future changes (in case of no offer fee) multiple inputs might become used.
        addAvailableInputsAndChangeOutputs(dummyTX, buyerAddressEntry);

        // The completeTx() call signs the input, but we don't want to pass over signed tx inputs
        // But to be safe and to support future changes (in case of no offer fee) we handle potential multiple inputs
        removeSignatures(dummyTX);

        verifyTransaction(dummyTX);

        // The created tx looks like:
         /*
        IN[0]  any input > buyerInputAmount (including tx fee) (unsigned)
        IN[1...n] optional inputs supported, but currently there is just 1 input (unsigned)
        OUT[0] dummyOutputAmount (buyerInputAmount - tx fee)
        OUT[1] Optional Change = buyerInputAmount - dummyOutputAmount - tx fee
        OUT[2...n] optional more outputs are supported, but currently there is just max. 1 optional change output
         */

        printTxWithInputs("dummyTX", dummyTX);

        List<TransactionOutput> connectedOutputsForAllInputs = dummyTX.getInputs().stream().map(TransactionInput::getConnectedOutput)
                .collect(Collectors.toList());

        // Only save buyer outputs, the dummy output (index 1) is ignored
        List<TransactionOutput> outputs = new ArrayList<>();
        for (int i = 1; i < dummyTX.getOutputs().size(); i++) {
            outputs.add(dummyTX.getOutputs().get(i));
        }

        return new Result(connectedOutputsForAllInputs, outputs);
    }

    public Result createAndSignDepositTx(Coin sellerInputAmount,
                                         Coin msOutputAmount,
                                         List<TransactionOutput> buyerConnectedOutputsForAllInputs,
                                         List<TransactionOutput> buyerOutputs,
                                         AddressEntry sellerAddressInfo,
                                         byte[] buyerPubKey,
                                         byte[] sellerPubKey,
                                         byte[] arbitratorPubKey) throws SigningException, TransactionVerificationException, WalletException {
        log.trace("createAndSignDepositTx called");
        log.trace("sellerInputAmount " + sellerInputAmount.toFriendlyString());
        log.trace("msOutputAmount " + msOutputAmount.toFriendlyString());
        log.trace("buyerConnectedOutputsForAllInputs " + buyerConnectedOutputsForAllInputs.toString());
        log.trace("buyerOutputs " + buyerOutputs.toString());
        log.trace("sellerAddressInfo " + sellerAddressInfo.toString());
        log.trace("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.trace("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.trace("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        checkArgument(buyerConnectedOutputsForAllInputs.size() > 0);

        // First we construct a dummy TX to get the inputs and outputs we want to use for the real deposit tx. 
        Transaction dummyTx = new Transaction(params);
        Coin dummyOutputAmount = sellerInputAmount.subtract(FeePolicy.TX_FEE);
        TransactionOutput dummyOutput = new TransactionOutput(params, dummyTx, dummyOutputAmount, new ECKey().toAddress(params));
        dummyTx.addOutput(dummyOutput);
        addAvailableInputsAndChangeOutputs(dummyTx, sellerAddressInfo);
        List<TransactionInput> sellererInputs = dummyTx.getInputs();
        List<TransactionOutput> sellerOutputs = new ArrayList<>();
        // we store optional change outputs (ignoring dummyOutput)
        for (int i = 1; i < dummyTx.getOutputs().size(); i++) {
            sellerOutputs.add(dummyTx.getOutput(i));
        }

        // Now we construct the real deposit tx
        Transaction preparedDepositTx = new Transaction(params);

        // Add buyer inputs (normally its just 1 input)
        for (TransactionOutput connectedOutputForInput : buyerConnectedOutputsForAllInputs) {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, connectedOutputForInput.getIndex(), connectedOutputForInput.getParentTransaction());
            TransactionInput transactionInput = new TransactionInput(params, preparedDepositTx, new byte[]{}, outPoint, connectedOutputForInput.getValue());
            preparedDepositTx.addInput(transactionInput);
        }

        // Add seller inputs
        List<TransactionOutput> sellerConnectedOutputsForAllInputs = new ArrayList<>();
        for (TransactionInput input : sellererInputs) {
            preparedDepositTx.addInput(input);
            sellerConnectedOutputsForAllInputs.add(input.getConnectedOutput());
        }

        // Add MultiSig output
        Script p2SHMultiSigOutputScript = getP2SHMultiSigOutputScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        // Tx fee for deposit tx will be paid by buyer.
        TransactionOutput p2SHMultiSigOutput = new TransactionOutput(params, preparedDepositTx, msOutputAmount, p2SHMultiSigOutputScript.getProgram());
        preparedDepositTx.addOutput(p2SHMultiSigOutput);

        // Add optional buyer outputs 
        buyerOutputs.forEach(preparedDepositTx::addOutput);

        Coin sellersSpendingAmount = Coin.ZERO;

        // Add optional seller outputs 
        for (TransactionOutput output : sellerOutputs) {
            preparedDepositTx.addOutput(output);

            // subtract change amount
            sellersSpendingAmount = sellersSpendingAmount.subtract(output.getValue());
        }

        // Sign inputs (start after buyer inputs)
        for (int i = buyerConnectedOutputsForAllInputs.size(); i < preparedDepositTx.getInputs().size(); i++) {
            TransactionInput input = preparedDepositTx.getInput(i);
            signInput(preparedDepositTx, input, i);
            checkScriptSig(preparedDepositTx, input, i);

            // add up spending amount
            assert input.getConnectedOutput() != null;
            sellersSpendingAmount = sellersSpendingAmount.add(input.getConnectedOutput().getValue());
        }

        if (sellerInputAmount.compareTo(sellersSpendingAmount) != 0)
            throw new TransactionVerificationException("Sellers input amount does not match required value.");

        verifyTransaction(preparedDepositTx);

        printTxWithInputs("preparedDepositTx", preparedDepositTx);
        return new Result(preparedDepositTx, sellerConnectedOutputsForAllInputs, sellerOutputs);
    }

    public void signAndPublishDepositTx(Transaction preparedDepositTx,
                                        List<TransactionOutput> buyerConnectedOutputsForAllInputs,
                                        List<TransactionOutput> sellerConnectedOutputsForAllInputs,
                                        List<TransactionOutput> buyerOutputs,
                                        Coin buyerInputAmount,
                                        byte[] buyerPubKey,
                                        byte[] sellerPubKey,
                                        byte[] arbitratorPubKey,
                                        FutureCallback<Transaction> callback) throws SigningException, TransactionVerificationException,
            WalletException {
        log.trace("signAndPublishDepositTx called");
        log.trace("preparedDepositTx " + preparedDepositTx.toString());
        log.trace("buyerConnectedOutputsForAllInputs " + buyerConnectedOutputsForAllInputs.toString());
        log.trace("sellerConnectedOutputsForAllInputs " + sellerConnectedOutputsForAllInputs.toString());
        log.trace("buyerOutputs " + buyerOutputs.toString());
        log.trace("buyerInputAmount " + buyerInputAmount.toFriendlyString());
        log.trace("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.trace("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.trace("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        checkArgument(buyerConnectedOutputsForAllInputs.size() > 0);
        checkArgument(sellerConnectedOutputsForAllInputs.size() > 0);

        // Check if sellers Multisig script is identical to mine
        Script p2SHMultiSigOutputScript = getP2SHMultiSigOutputScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        if (!preparedDepositTx.getOutput(0).getScriptPubKey().equals(p2SHMultiSigOutputScript))
            throw new TransactionVerificationException("Sellers p2SHMultiSigOutputScript does not match to my p2SHMultiSigOutputScript");

        // The outpoints are not available from the serialized preparedDepositTx, so we cannot use that tx directly, but we use it to construct a new 
        // depositTx
        Transaction depositTx = new Transaction(params);

        // Add buyer inputs
        Coin buyerSpendingAmount = Coin.ZERO;
        for (TransactionOutput connectedOutputForInput : buyerConnectedOutputsForAllInputs) {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, connectedOutputForInput.getIndex(), connectedOutputForInput.getParentTransaction());
            TransactionInput input = new TransactionInput(params, depositTx, new byte[]{}, outPoint, connectedOutputForInput.getValue());
            depositTx.addInput(input);

            // add up spending amount
            assert input.getConnectedOutput() != null;
            buyerSpendingAmount = buyerSpendingAmount.add(input.getConnectedOutput().getValue());
        }

        // Add seller inputs and apply signature
        for (TransactionOutput connectedOutputForInput : sellerConnectedOutputsForAllInputs) {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, connectedOutputForInput.getIndex(), connectedOutputForInput.getParentTransaction());

            // We grab the signature from the preparedDepositTx and apply it to the new tx input
            TransactionInput sellerInput = preparedDepositTx.getInputs().get(buyerConnectedOutputsForAllInputs.size());
            byte[] scriptProgram = sellerInput.getScriptSig().getProgram();
            if (scriptProgram.length == 0)
                throw new TransactionVerificationException("Inputs from seller not singed.");

            TransactionInput transactionInput = new TransactionInput(params, depositTx, scriptProgram, outPoint, connectedOutputForInput.getValue());
            depositTx.addInput(transactionInput);
        }

        // Add all outputs from preparedDepositTx to depositTx
        preparedDepositTx.getOutputs().forEach(depositTx::addOutput);

        // Sign inputs
        for (int i = 0; i < buyerConnectedOutputsForAllInputs.size(); i++) {
            TransactionInput input = depositTx.getInput(i);
            signInput(depositTx, input, i);
            checkScriptSig(depositTx, input, i);
        }

        // subtract change amount
        for (int i = 1; i < buyerOutputs.size() + 1; i++) {
            buyerSpendingAmount = buyerSpendingAmount.subtract(depositTx.getOutput(i).getValue());
        }

        if (buyerInputAmount.compareTo(buyerSpendingAmount) != 0)
            throw new TransactionVerificationException("Buyers input amount does not match required value.");

        verifyTransaction(depositTx);
        checkWalletConsistency();

        // Broadcast depositTx
        printTxWithInputs("depositTx", depositTx);
        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(depositTx).future();
        Futures.addCallback(broadcastComplete, callback);
    }

    // Commits the tx to the wallet and returns that
    public Transaction commitTx(Transaction tx) throws VerificationException {
        log.trace("commitTx called");
        log.trace("tx " + tx.toString());

        // We need to recreate the tx we get a null pointer otherwise
        Transaction transaction = new Transaction(params, tx.bitcoinSerialize());
        log.trace("transaction " + transaction.toString());

        wallet.receivePending(transaction, null, true);
        return transaction;
    }

    // Returns local existing wallet transaction 
    public Transaction getWalletTx(Transaction tx) throws VerificationException {
        log.trace("getWalleTx called");
        log.trace("tx " + tx.toString());

        return wallet.getTransaction(tx.getHash());
    }

    public byte[] createAndSignPayoutTx(Transaction depositTx,
                                        Coin buyerPayoutAmount,
                                        Coin sellerPayoutAmount,
                                        AddressEntry buyerAddressEntry,
                                        String sellerPayoutAddressString,
                                        byte[] buyerPubKey,
                                        byte[] sellerPubKey,
                                        byte[] arbitratorPubKey)
            throws AddressFormatException, TransactionVerificationException, SigningException {
        log.trace("createAndSignPayoutTx called");
        log.trace("depositTx " + depositTx.toString());
        log.trace("buyerPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.trace("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.trace("buyerAddressEntry " + buyerAddressEntry.toString());
        log.trace("sellerPayoutAddressString " + sellerPayoutAddressString);
        log.trace("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.trace("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.trace("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        Transaction preparedPayoutTx = createPayoutTx(depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerAddressEntry.getAddressString(),
                sellerPayoutAddressString);
        // MS redeemScript
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        Sha256Hash sigHash = preparedPayoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature buyerSignature = buyerAddressEntry.getKeyPair().sign(sigHash).toCanonicalised();

        verifyTransaction(preparedPayoutTx);

        printTxWithInputs("preparedPayoutTx", preparedPayoutTx);
        log.trace("buyerSignature r " + buyerSignature.toCanonicalised().r.toString());
        log.trace("buyerSignature s " + buyerSignature.toCanonicalised().s.toString());
        Sha256Hash hashForSignature = preparedPayoutTx.hashForSignature(0, redeemScript.getProgram(), (byte) 1);
        log.trace("hashForSignature " + Utils.HEX.encode(hashForSignature.getBytes()));

        return buyerSignature.encodeToDER();
    }

    public void signAndPublishPayoutTx(Transaction depositTx,
                                       byte[] buyerSignature,
                                       Coin buyerPayoutAmount,
                                       Coin sellerPayoutAmount,
                                       String buyerAddressString,
                                       AddressEntry sellerAddressEntry,
                                       byte[] buyerPubKey,
                                       byte[] sellerPubKey,
                                       byte[] arbitratorPubKey,
                                       FutureCallback<Transaction> callback)
            throws AddressFormatException, TransactionVerificationException, WalletException, SigningException {
        log.trace("signAndPublishPayoutTx called");
        log.trace("depositTx " + depositTx.toString());
        log.trace("buyerSignature r " + ECKey.ECDSASignature.decodeFromDER(buyerSignature).r.toString());
        log.trace("buyerSignature s " + ECKey.ECDSASignature.decodeFromDER(buyerSignature).s.toString());
        log.trace("buyerPayoutAmount " + buyerPayoutAmount.toFriendlyString());
        log.trace("sellerPayoutAmount " + sellerPayoutAmount.toFriendlyString());
        log.trace("buyerAddressString " + buyerAddressString);
        log.trace("sellerAddressEntry " + sellerAddressEntry);
        log.trace("buyerPubKey " + ECKey.fromPublicOnly(buyerPubKey).toString());
        log.trace("sellerPubKey " + ECKey.fromPublicOnly(sellerPubKey).toString());
        log.trace("arbitratorPubKey " + ECKey.fromPublicOnly(arbitratorPubKey).toString());

        Transaction payoutTx = createPayoutTx(depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerAddressString,
                sellerAddressEntry.getAddressString());
        Script redeemScript = getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey);
        Sha256Hash sigHash = payoutTx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature sellerSignature = sellerAddressEntry.getKeyPair().sign(sigHash).toCanonicalised();

        log.trace("sellerSignature r " + sellerSignature.r.toString());
        log.trace("sellerSignature s " + sellerSignature.s.toString());

        Sha256Hash hashForSignature = payoutTx.hashForSignature(0, redeemScript.getProgram(), (byte) 1);
        log.trace("hashForSignature " + Utils.HEX.encode(hashForSignature.getBytes()));

        TransactionSignature buyerTxSig = new TransactionSignature(ECKey.ECDSASignature.decodeFromDER(buyerSignature),
                Transaction.SigHash.ALL, false);
        TransactionSignature sellerTxSig = new TransactionSignature(sellerSignature, Transaction.SigHash.ALL, false);
        // Take care of order of signatures. See comment below at getMultiSigRedeemScript
        Script inputScript = ScriptBuilder.createP2SHMultiSigInputScript(ImmutableList.of(sellerTxSig, buyerTxSig), redeemScript);
        TransactionInput input = payoutTx.getInput(0);
        input.setScriptSig(inputScript);

        verifyTransaction(payoutTx);
        checkWalletConsistency();
        checkScriptSig(payoutTx, input, 0);
        assert input.getConnectedOutput() != null;
        input.verify(input.getConnectedOutput());

        printTxWithInputs("payoutTx", payoutTx);

        ListenableFuture<Transaction> broadcastComplete = walletAppKit.peerGroup().broadcastTransaction(payoutTx).future();
        Futures.addCallback(broadcastComplete, callback);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Don't use ScriptBuilder.createRedeemScript and ScriptBuilder.createP2SHOutputScript as they use a sorting 
    // (Collections.sort(pubKeys, ECKey.PUBKEY_COMPARATOR);) which can lead to a non-matching list of signatures with pubKeys and the executeMultiSig does 
    // not iterate all possible combinations of sig/pubKeys leading to a verification fault. That nasty bug happens just randomly as the list after sorting 
    // might differ from the provided one or not.
    // Changing the while loop in executeMultiSig to fix that does not help as the reference implementation seems to behave the same (not iterating all 
    // possibilities) .
    // Furthermore the executed list is reversed to the provided.
    // Best practice is to provide the list sorted by the least probable successful candidates first (arbitrator is first -> will be last in execution loop, so
    // avoiding unneeded expensive ECKey.verify calls)
    private Script getMultiSigRedeemScript(byte[] buyerPubKey, byte[] sellerPubKey, byte[] arbitratorPubKey) {
        ECKey buyerKey = ECKey.fromPublicOnly(buyerPubKey);
        ECKey sellerKey = ECKey.fromPublicOnly(sellerPubKey);
        ECKey arbitratorKey = ECKey.fromPublicOnly(arbitratorPubKey);
        // Take care of sorting!
        List<ECKey> keys = ImmutableList.of(arbitratorKey, sellerKey, buyerKey);
        return ScriptBuilder.createMultiSigOutputScript(2, keys);
    }

    private Script getP2SHMultiSigOutputScript(byte[] buyerPubKey, byte[] sellerPubKey, byte[] arbitratorPubKey) {
        return ScriptBuilder.createP2SHOutputScript(getMultiSigRedeemScript(buyerPubKey, sellerPubKey, arbitratorPubKey));
    }

    private Transaction createPayoutTx(Transaction depositTx,
                                       Coin buyerPayoutAmount,
                                       Coin sellerPayoutAmount,
                                       String buyerAddressString,
                                       String sellerAddressString) throws AddressFormatException {

        TransactionOutput p2SHMultiSigOutput = depositTx.getOutput(0);
        Transaction transaction = new Transaction(params);
        transaction.addInput(p2SHMultiSigOutput);
        transaction.addOutput(buyerPayoutAmount, new Address(params, buyerAddressString));
        transaction.addOutput(sellerPayoutAmount, new Address(params, sellerAddressString));
        return transaction;
    }

    private static void printTxWithInputs(String tracePrefix, Transaction tx) {
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

    private void signInput(Transaction transaction, TransactionInput input, int inputIndex) throws SigningException {
        assert input.getConnectedOutput() != null;
        Script scriptPubKey = input.getConnectedOutput().getScriptPubKey();
        ECKey sigKey = input.getOutpoint().getConnectedKey(wallet);
        assert sigKey != null;
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
            assert input.getConnectedOutput() != null;
            input.getScriptSig().correctlySpends(transaction, inputIndex, input.getConnectedOutput().getScriptPubKey());
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new TransactionVerificationException(t);
        }
    }

    private void removeSignatures(Transaction transaction) {
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

    public class Result {
        private final List<TransactionOutput> connectedOutputsForAllInputs;
        private final List<TransactionOutput> outputs;
        private Transaction depositTx;


        private byte[] buyerSignature;

        public Result(List<TransactionOutput> connectedOutputsForAllInputs, List<TransactionOutput> outputs) {
            this.connectedOutputsForAllInputs = connectedOutputsForAllInputs;
            this.outputs = outputs;
        }

        public Result(Transaction depositTx, List<TransactionOutput> connectedOutputsForAllInputs, List<TransactionOutput> outputs) {
            this.depositTx = depositTx;
            this.connectedOutputsForAllInputs = connectedOutputsForAllInputs;
            this.outputs = outputs;
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

        public byte[] getBuyerSignature() {
            return buyerSignature;
        }
    }
}
